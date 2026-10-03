#include "UsbIoctl.h"
#include <algorithm>
#include <array>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <new>
#include <poll.h>
#include <sys/ioctl.h>
#include <vector>

namespace {
    constexpr int packetsPerTransfer = 16;
    constexpr int pipelineDepth = 8;
    using Clock = std::chrono::steady_clock;

    struct Transfer {
        usbdevfs_urb *urb = nullptr;
        std::vector<unsigned char> bytes;
        bool pending = false;

        Transfer()
            : urb(static_cast<usbdevfs_urb *>(
                  std::calloc(1, sizeof(usbdevfs_urb) +
                                     packetsPerTransfer * sizeof(usbdevfs_iso_packet_desc)))) {}

        ~Transfer() { std::free(urb); }
    };

    struct Session {
        int fd;
        bool used = false;
        std::atomic<bool> cancelled{false};
        std::array<Transfer, pipelineDepth> transfers;

        explicit Session(int descriptor) : fd(descriptor) {}
    };

    Session *session(jlong handle) { return reinterpret_cast<Session *>(handle); }

    int deviceSpeed(int fd) {
        const int result = retryUsbIoctl(fd, USBDEVFS_GET_SPEED, nullptr);
        return result < 0 ? -errno : result;
    }

    jlongArray resultArray(JNIEnv *env, const std::array<jlong, 7> &values) {
        const auto result = env->NewLongArray(values.size());
        if (result)
            env->SetLongArrayRegion(result, 0, values.size(), values.data());
        return result;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_pxr_cymatic_usb_UsbIsochronousConnection_create(JNIEnv *, jobject, jint fd) {
    if (fd < 0)
        return 0;
    auto *value = new (std::nothrow) Session(fd);
    if (!value)
        return 0;
    for (const auto &transfer : value->transfers) {
        if (!transfer.urb) {
            delete value;
            return 0;
        }
    }
    return reinterpret_cast<jlong>(value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pxr_cymatic_usb_UsbIsochronousConnection_getSpeed(JNIEnv *, jobject, jlong handle) {
    return deviceSpeed(session(handle)->fd);
}

extern "C" JNIEXPORT jint JNICALL Java_com_pxr_cymatic_usb_UsbIsochronousConnection_reconnect(
    JNIEnv *, jobject, jlong handle, jint number) {
    usbdevfs_ioctl command{};
    command.ifno = number;
    command.ioctl_code = USBDEVFS_CONNECT;
    const int result = retryUsbIoctl(session(handle)->fd, USBDEVFS_IOCTL, &command);
    return result < 0 ? -errno : result;
}

extern "C" JNIEXPORT jstring JNICALL Java_com_pxr_cymatic_usb_UsbIsochronousConnection_getDriver(
    JNIEnv *env, jobject, jlong handle, jint number) {
    usbdevfs_getdriver driver{};
    driver.interface = number;
    if (retryUsbIoctl(session(handle)->fd, USBDEVFS_GETDRIVER, &driver) < 0) {
        const int error = errno;
        if (error == ENODATA)
            return nullptr;
        char message[128];
        std::snprintf(message, sizeof(message), "USB interface %d driver query failed (errno %d)",
                      number, error);
        const auto exception = env->FindClass("java/io/IOException");
        if (exception)
            env->ThrowNew(exception, message);
        return nullptr;
    }
    driver.driver[USBDEVFS_MAXDRIVERNAME] = '\0';
    return env->NewStringUTF(driver.driver);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pxr_cymatic_usb_UsbIsochronousConnection_destroy(JNIEnv *, jobject, jlong handle) {
    delete session(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pxr_cymatic_usb_UsbIsochronousConnection_cancelTransfer(JNIEnv *, jobject, jlong handle) {
    session(handle)->cancelled.store(true);
}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_pxr_cymatic_usb_UsbIsochronousConnection_transfer(
    JNIEnv *env, jobject, jlong handle, jint endpoint, jint capacity, jint interval, jint rate,
    jint frameBytes, jbyteArray source) {
    std::array<jlong, 7> stats{};
    auto *state = session(handle);
    const int speed = deviceSpeed(state->fd);
    if (state->used || (speed != 2 && speed != 3) || interval < 1 || interval > 16 ||
        endpoint < 1 || endpoint > 15 || capacity < 1 || capacity > 3072 || rate < 8000 ||
        rate > 384000 || frameBytes < 1 || frameBytes > 32) {
        stats[0] = speed < 0 ? speed : -EINVAL;
        return resultArray(env, stats);
    }
    state->used = true;
    const int ticksPerSecond = speed == 2 ? 1000 : 8000;
    const int serviceTicks = 1 << (interval - 1);
    if (serviceTicks > ticksPerSecond ||
        ((static_cast<int64_t>(rate) * serviceTicks + ticksPerSecond - 1) / ticksPerSecond) *
                frameBytes >
            capacity) {
        stats[0] = -EMSGSIZE;
        return resultArray(env, stats);
    }
    const int sourceSize = env->GetArrayLength(source);
    if (sourceSize <= 0 || sourceSize % frameBytes != 0 ||
        sourceSize > static_cast<int64_t>(rate) * frameBytes * 2) {
        stats[0] = -EINVAL;
        return resultArray(env, stats);
    }
    std::vector<unsigned char> pcm;
    try {
        pcm.resize(sourceSize);
        for (auto &transfer : state->transfers)
            transfer.bytes.resize(capacity * packetsPerTransfer);
    } catch (const std::bad_alloc &) {
        stats[0] = -ENOMEM;
        return resultArray(env, stats);
    }
    env->GetByteArrayRegion(source, 0, sourceSize, reinterpret_cast<jbyte *>(pcm.data()));
    if (env->ExceptionCheck())
        return nullptr;
    int position = 0;
    int64_t remainder = 0;
    int pending = 0;
    auto submit = [&](Transfer &transfer) {
        std::memset(transfer.urb, 0,
                    sizeof(usbdevfs_urb) + packetsPerTransfer * sizeof(usbdevfs_iso_packet_desc));
        auto *urb = transfer.urb;
        urb->type = USBDEVFS_URB_TYPE_ISO;
        urb->endpoint = endpoint;
        urb->flags = USBDEVFS_URB_ISO_ASAP;
        urb->buffer = transfer.bytes.data();
        while (urb->number_of_packets < packetsPerTransfer && position < sourceSize) {
            remainder += static_cast<int64_t>(rate) * serviceTicks;
            const int frames = remainder / ticksPerSecond;
            remainder %= ticksPerSecond;
            const int length = std::min(frames * frameBytes, sourceSize - position);
            auto &packet = urb->iso_frame_desc[urb->number_of_packets++];
            packet.length = length;
            std::memcpy(transfer.bytes.data() + urb->buffer_length, pcm.data() + position, length);
            urb->buffer_length += length;
            position += length;
        }
        if (retryUsbIoctl(state->fd, USBDEVFS_SUBMITURB, urb) < 0)
            return -errno;
        transfer.pending = true;
        ++pending;
        return 0;
    };
    for (auto &transfer : state->transfers) {
        if (position >= sourceSize)
            break;
        if (state->cancelled.load()) {
            stats[0] = -ECANCELED;
            break;
        }
        const int error = submit(transfer);
        if (error) {
            stats[0] = error;
            break;
        }
    }
    const auto deadline = Clock::now() + std::chrono::milliseconds(4000);
    while (pending > 0 && stats[0] == 0) {
        if (state->cancelled.load()) {
            stats[0] = -ECANCELED;
            break;
        }
        if (Clock::now() >= deadline) {
            stats[0] = -ETIMEDOUT;
            break;
        }
        void *completed = nullptr;
        if (retryUsbIoctl(state->fd, USBDEVFS_REAPURBNDELAY, &completed) < 0) {
            if (errno != EAGAIN) {
                stats[0] = -errno;
                break;
            }
            pollfd descriptor{state->fd, POLLOUT, 0};
            const int polled = poll(&descriptor, 1, 20);
            if (polled < 0 && errno != EINTR) {
                stats[0] = -errno;
                break;
            }
            if (descriptor.revents & (POLLHUP | POLLERR | POLLNVAL)) {
                stats[0] = -ENODEV;
                break;
            }
            continue;
        }
        auto found = std::find_if(
            state->transfers.begin(), state->transfers.end(),
            [&](const auto &transfer) { return transfer.urb == completed && transfer.pending; });
        if (found == state->transfers.end()) {
            stats[0] = -EPROTO;
            break;
        }
        found->pending = false;
        --pending;
        auto *urb = found->urb;
        ++stats[6];
        if (urb->status != 0) {
            stats[0] = urb->status;
            break;
        }
        for (int index = 0; index < urb->number_of_packets; ++index) {
            const auto &packet = urb->iso_frame_desc[index];
            ++stats[1];
            if (packet.status != 0)
                ++stats[4];
            if (packet.actual_length != packet.length)
                ++stats[5];
            stats[3] += packet.actual_length;
            stats[2] += packet.actual_length / frameBytes;
        }
        if (stats[4] || stats[5]) {
            stats[0] = -EIO;
            break;
        }
        if (position < sourceSize) {
            const int error = submit(*found);
            if (error) {
                stats[0] = error;
                break;
            }
        }
    }
    for (auto &transfer : state->transfers) {
        if (transfer.pending)
            retryUsbIoctl(state->fd, USBDEVFS_DISCARDURB, transfer.urb);
    }
    return resultArray(env, stats);
}

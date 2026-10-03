#include "UsbFeedback.h"
#include "UsbIoctl.h"
#include <algorithm>
#include <array>
#include <cerrno>
#include <chrono>
#include <condition_variable>
#include <cstdlib>
#include <cstring>
#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <mutex>
#include <poll.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <thread>
#include <vector>

namespace {
    constexpr int packetCount = 16;
    constexpr int transferCount = 8;
    using Clock = std::chrono::steady_clock;

    struct Transfer {
        usbdevfs_urb *urb;
        std::vector<unsigned char> bytes;
        bool pending = false;
        std::array<unsigned, packetCount> primingBytes{};

        Transfer()
            : urb(static_cast<usbdevfs_urb *>(std::calloc(
                  1, sizeof(usbdevfs_urb) + packetCount * sizeof(usbdevfs_iso_packet_desc)))) {}

        ~Transfer() { std::free(urb); }

        void prepare(int endpoint) {
            std::memset(urb, 0,
                        sizeof(usbdevfs_urb) + packetCount * sizeof(usbdevfs_iso_packet_desc));
            urb->type = USBDEVFS_URB_TYPE_ISO;
            urb->endpoint = endpoint;
            urb->flags = USBDEVFS_URB_ISO_ASAP;
            urb->buffer = bytes.data();
        }
    };

    struct Stream {
        int fd, endpoint, capacity, serviceTicks, ticksPerSecond, rate, frameBytes, packetsPerUrb;
        int feedbackEndpoint, feedbackCapacity, feedbackPacketsPerUrb;
        size_t initialPrimingBytes, primingRemaining;
        int64_t submittedPrimingBytes = 0, completedPrimingBytes = 0;
        std::chrono::milliseconds feedbackTimeout;
        UsbFeedback feedback;
        std::mutex mutex;
        std::condition_variable changed;
        std::thread worker;
        std::array<Transfer, transferCount> transfers;
        std::array<Transfer, transferCount> feedbackTransfers;
        std::vector<unsigned char> ring;
        size_t readAt = 0, writeAt = 0, queued = 0;
        int pending = 0, feedbackPending = 0;
        int64_t remainder = 0, completedFrames = 0, submittedBytes = 0, completedBytes = 0;
        int64_t packetErrors = 0, shortPackets = 0, underruns = 0;
        int64_t feedbackTimeouts = 0, completedPackets = 0, completedTransfers = 0;
        int error = 0;
        bool playing = false, ending = false, stopping = false, started = false;
        bool feedbackRunning = false;
        Clock::time_point lastCompletion = Clock::now(), feedbackDeadline = Clock::now();

        Stream(int descriptor, int address, int maximum, int interval, int frequency, int bytes,
               int ticks, int feedbackAddress, int feedbackMaximum, int feedbackInterval,
               int feedbackRefreshMs, int primingFrames)
            : fd(descriptor), endpoint(address), capacity(maximum),
              serviceTicks(1 << (interval - 1)), ticksPerSecond(ticks), rate(frequency),
              frameBytes(bytes),
              packetsPerUrb(std::clamp(ticks / (serviceTicks * 500), 1, packetCount)),
              feedbackEndpoint(feedbackAddress), feedbackCapacity(feedbackMaximum),
              feedbackPacketsPerUrb(
                  feedbackAddress == 0
                      ? 0
                      : std::clamp(ticks / ((1 << (feedbackInterval - 1)) * 500), 1, packetCount)),
              initialPrimingBytes(static_cast<size_t>(primingFrames) * bytes),
              primingRemaining(initialPrimingBytes),
              feedbackTimeout(
                  feedbackAddress == 0
                      ? 250
                      : std::clamp(
                            std::max((static_cast<int64_t>(1 << (feedbackInterval - 1)) * 4000 +
                                      ticks - 1) /
                                         ticks,
                                     static_cast<int64_t>(feedbackRefreshMs) * 4),
                            int64_t{250}, int64_t{3000})),
              feedback(frequency, ticks, serviceTicks, maximum / bytes),
              ring(static_cast<size_t>(frequency) * bytes / 2) {
            for (auto &transfer : transfers) {
                if (!transfer.urb)
                    throw std::bad_alloc();
                transfer.bytes.resize(capacity * packetCount);
            }
            for (auto &transfer : feedbackTransfers) {
                if (!transfer.urb)
                    throw std::bad_alloc();
                if (feedbackEndpoint != 0)
                    transfer.bytes.resize(feedbackCapacity * packetCount);
            }
        }

        ~Stream() { stop(); }

        void stop() {
            {
                std::lock_guard<std::mutex> lock(mutex);
                stopping = true;
                changed.notify_all();
            }
            if (worker.joinable())
                worker.join();
        }

        bool submit(Transfer &transfer) {
            transfer.prepare(endpoint);
            transfer.primingBytes.fill(0);
            auto *urb = transfer.urb;
            int64_t nextRemainder = remainder;
            const int64_t denominator = feedbackEndpoint == 0 ? ticksPerSecond : 65536;
            const int64_t increment = feedbackEndpoint == 0 ? rate : feedback.value;
            const size_t available = queued + primingRemaining;
            size_t needed = 0;
            size_t primeLeft = primingRemaining;
            for (int index = 0; index < packetsPerUrb; ++index) {
                nextRemainder += increment * serviceTicks;
                int length = (nextRemainder / denominator) * frameBytes;
                nextRemainder %= denominator;
                if (length > capacity) {
                    error = -EMSGSIZE;
                    return false;
                }
                if (needed + length > available) {
                    if (!ending)
                        return false;
                    length = available - needed;
                }
                if (length == 0 && ending)
                    break;
                const auto priming = std::min(primeLeft, static_cast<size_t>(length));
                transfer.primingBytes[index] = priming;
                primeLeft -= priming;
                urb->iso_frame_desc[urb->number_of_packets++].length = length;
                needed += length;
                if (ending && needed == available)
                    break;
            }
            if (needed == 0)
                return false;
            const size_t priming = primingRemaining - primeLeft;
            const size_t music = needed - priming;
            std::memset(transfer.bytes.data(), 0, priming);
            const auto first = std::min(music, ring.size() - readAt);
            std::memcpy(transfer.bytes.data() + priming, ring.data() + readAt, first);
            std::memcpy(transfer.bytes.data() + priming + first, ring.data(), music - first);
            urb->buffer_length = needed;
            if (retryUsbIoctl(fd, USBDEVFS_SUBMITURB, urb) < 0) {
                error = -errno;
                return false;
            }
            readAt = (readAt + music) % ring.size();
            queued -= music;
            primingRemaining = primeLeft;
            submittedBytes += music;
            submittedPrimingBytes += priming;
            remainder = nextRemainder;
            transfer.pending = true;
            ++pending;
            if (pending == 1)
                lastCompletion = Clock::now();
            return true;
        }

        bool submitFeedback(Transfer &transfer) {
            transfer.prepare(feedbackEndpoint);
            auto *urb = transfer.urb;
            urb->number_of_packets = feedbackPacketsPerUrb;
            urb->buffer_length = feedbackCapacity * feedbackPacketsPerUrb;
            for (int index = 0; index < feedbackPacketsPerUrb; ++index)
                urb->iso_frame_desc[index].length = feedbackCapacity;
            if (retryUsbIoctl(fd, USBDEVFS_SUBMITURB, urb) < 0) {
                error = -errno;
                return false;
            }
            transfer.pending = true;
            ++feedbackPending;
            return true;
        }

        void receiveFeedback(Transfer &transfer) {
            transfer.pending = false;
            --feedbackPending;
            auto *urb = transfer.urb;
            if (urb->status != 0) {
                error = urb->status;
                return;
            }
            size_t offset = 0;
            for (int index = 0; index < urb->number_of_packets; ++index) {
                const auto &packet = urb->iso_frame_desc[index];
                if (packet.actual_length > packet.length ||
                    packet.length > static_cast<unsigned>(feedbackCapacity) ||
                    offset + packet.length > transfer.bytes.size()) {
                    error = -EPROTO;
                    return;
                }
                if (feedback.receive(transfer.bytes.data() + offset, packet.actual_length,
                                     packet.status))
                    feedbackDeadline = Clock::now() + feedbackTimeout;
                if (feedback.consecutiveRejected >= 32) {
                    error = -EBADMSG;
                    return;
                }
                offset += packet.length;
            }
        }

        void receiveOutput(Transfer &transfer) {
            transfer.pending = false;
            --pending;
            lastCompletion = Clock::now();
            ++completedTransfers;
            auto *urb = transfer.urb;
            if (urb->status != 0) {
                error = urb->status;
                return;
            }
            for (int index = 0; index < urb->number_of_packets; ++index) {
                const auto &packet = urb->iso_frame_desc[index];
                ++completedPackets;
                if (packet.status)
                    ++packetErrors;
                if (packet.actual_length != packet.length)
                    ++shortPackets;
                const unsigned priming =
                    std::min(packet.actual_length, transfer.primingBytes[index]);
                completedPrimingBytes += priming;
                completedBytes += packet.actual_length - priming;
                completedFrames += (packet.actual_length - priming) / frameBytes;
            }
            if (packetErrors || shortPackets)
                error = -EIO;
        }

        void reap() {
            for (int index = 0; index < transferCount * 2 && !error; ++index) {
                void *completed = nullptr;
                if (retryUsbIoctl(fd, USBDEVFS_REAPURBNDELAY, &completed) < 0) {
                    if (errno != EAGAIN)
                        error = -errno;
                    return;
                }
                const auto matches = [&](const auto &value) {
                    return value.urb == completed && value.pending;
                };
                auto output = std::find_if(transfers.begin(), transfers.end(), matches);
                if (output != transfers.end()) {
                    receiveOutput(*output);
                    continue;
                }
                auto input =
                    std::find_if(feedbackTransfers.begin(), feedbackTransfers.end(), matches);
                if (input != feedbackTransfers.end())
                    receiveFeedback(*input);
                else
                    error = -EPROTO;
            }
            changed.notify_all();
        }

        void run() {
            setpriority(PRIO_PROCESS, 0, -16);
            for (;;) {
                {
                    std::unique_lock<std::mutex> lock(mutex);
                    if (stopping || error)
                        break;
                    if (pending || feedbackPending)
                        reap();
                    if (error)
                        break;
                    if (playing && !started &&
                        (queued >= static_cast<size_t>(rate * frameBytes / 5) || ending)) {
                        started = true;
                        feedbackDeadline = Clock::now() + feedbackTimeout;
                    }
                    const bool audioRemaining =
                        queued != 0 || primingRemaining != 0 || pending != 0 || !ending;
                    const bool validationOutstanding =
                        started && feedbackEndpoint != 0 && feedback.accepted == 0;
                    if (started && feedbackEndpoint != 0 &&
                        ((playing && audioRemaining) || feedbackRunning) &&
                        (audioRemaining || validationOutstanding)) {
                        feedbackRunning = true;
                        for (auto &transfer : feedbackTransfers)
                            if (!transfer.pending && !submitFeedback(transfer))
                                break;
                    }
                    if (playing && started && feedbackRunning &&
                        (audioRemaining || validationOutstanding) &&
                        Clock::now() > feedbackDeadline) {
                        ++feedbackTimeouts;
                        error = -ENODATA;
                    }
                    if (error)
                        break;
                    if (playing && started) {
                        for (auto &transfer : transfers)
                            if (!transfer.pending && !submit(transfer))
                                break;
                        if (pending == 0 && !ending) {
                            ++underruns;
                            error = -EPIPE;
                        }
                    }
                    if (error)
                        break;
                    if (pending != 0 && Clock::now() - lastCompletion > std::chrono::seconds(1)) {
                        error = -ETIMEDOUT;
                        break;
                    }
                    if (pending == 0 && feedbackPending == 0) {
                        changed.wait_for(lock, std::chrono::milliseconds(10));
                        continue;
                    }
                }
                pollfd descriptor{fd, POLLOUT, 0};
                const int polled = poll(&descriptor, 1, 5);
                if (polled < 0 && errno == EINTR)
                    continue;
                std::lock_guard<std::mutex> lock(mutex);
                if (polled < 0)
                    error = -errno;
                else if (descriptor.revents & (POLLHUP | POLLERR | POLLNVAL))
                    error = -ENODEV;
            }
            for (auto &transfer : transfers)
                if (transfer.pending)
                    retryUsbIoctl(fd, USBDEVFS_DISCARDURB, transfer.urb);
            for (auto &transfer : feedbackTransfers)
                if (transfer.pending)
                    retryUsbIoctl(fd, USBDEVFS_DISCARDURB, transfer.urb);
        }
    };

    Stream *stream(jlong handle) { return reinterpret_cast<Stream *>(handle); }
}

extern "C" JNIEXPORT jlong JNICALL Java_com_pxr_cymatic_usb_UsbPcmStream_create(
    JNIEnv *, jobject, jint fd, jint endpoint, jint capacity, jint interval, jint rate,
    jint frameBytes, jint feedbackEndpoint, jint feedbackCapacity, jint feedbackInterval,
    jint feedbackRefreshMs, jint primingFrames) {
    const int speed = retryUsbIoctl(fd, USBDEVFS_GET_SPEED, nullptr);
    const int ticks = speed == 2 ? 1000 : 8000;
    if (fd < 0 || (speed != 2 && speed != 3) || endpoint < 1 || endpoint > 15 || capacity < 1 ||
        capacity > 3072 || interval < 1 || interval > 16 || rate < 8000 || rate > 384000 ||
        frameBytes < 1 || frameBytes > 32 || feedbackRefreshMs < 0 || feedbackRefreshMs > 512 ||
        primingFrames < 0 || primingFrames > rate * 2)
        return 0;
    if (feedbackEndpoint != 0 &&
        (feedbackEndpoint < 0x81 || feedbackEndpoint > 0x8f ||
         feedbackCapacity < (speed == 3 ? 4 : 3) || feedbackCapacity > 4 || feedbackInterval < 1 ||
         feedbackInterval > 16 || (1 << (feedbackInterval - 1)) > ticks / 4))
        return 0;
    const int serviceTicks = 1 << (interval - 1);
    if (serviceTicks > ticks ||
        ((static_cast<int64_t>(rate) * serviceTicks + ticks - 1) / ticks) * frameBytes > capacity)
        return 0;
    Stream *value = nullptr;
    try {
        value =
            new Stream(fd, endpoint, capacity, interval, rate, frameBytes, ticks, feedbackEndpoint,
                       feedbackCapacity, feedbackInterval, feedbackRefreshMs, primingFrames);
        value->worker = std::thread([value] { value->run(); });
        return reinterpret_cast<jlong>(value);
    } catch (...) {
        delete value;
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL Java_com_pxr_cymatic_usb_UsbPcmStream_enqueue(
    JNIEnv *env, jobject, jlong handle, jobject buffer, jint offset, jint length) {
    auto *value = stream(handle);
    const auto size = env->GetDirectBufferCapacity(buffer);
    auto *bytes = static_cast<unsigned char *>(env->GetDirectBufferAddress(buffer));
    if (!bytes || offset < 0 || length < 0 || offset > size || length > size - offset ||
        length % value->frameBytes != 0)
        return -EINVAL;
    std::lock_guard<std::mutex> lock(value->mutex);
    if (value->error)
        return value->error;
    if (value->stopping || value->ending)
        return -ECANCELED;
    auto count = std::min(static_cast<size_t>(length), value->ring.size() - value->queued);
    count -= count % value->frameBytes;
    const auto first = std::min(count, value->ring.size() - value->writeAt);
    std::memcpy(value->ring.data() + value->writeAt, bytes + offset, first);
    std::memcpy(value->ring.data(), bytes + offset + first, count - first);
    value->writeAt = (value->writeAt + count) % value->ring.size();
    value->queued += count;
    value->changed.notify_all();
    return count;
}

extern "C" JNIEXPORT void JNICALL Java_com_pxr_cymatic_usb_UsbPcmStream_setPlaying(
    JNIEnv *, jobject, jlong handle, jboolean playing) {
    auto *value = stream(handle);
    std::lock_guard<std::mutex> lock(value->mutex);
    if (playing && !value->playing) {
        value->feedbackDeadline = Clock::now() + value->feedbackTimeout;
        if (value->pending == 0 && value->queued != 0)
            value->primingRemaining = std::max(value->primingRemaining, value->initialPrimingBytes);
    }
    value->playing = playing;
    value->changed.notify_all();
}

extern "C" JNIEXPORT void JNICALL Java_com_pxr_cymatic_usb_UsbPcmStream_endInput(JNIEnv *, jobject,
                                                                                 jlong handle) {
    auto *value = stream(handle);
    std::lock_guard<std::mutex> lock(value->mutex);
    value->ending = true;
    value->changed.notify_all();
}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_pxr_cymatic_usb_UsbPcmStream_stats(JNIEnv *env,
                                                                                    jobject,
                                                                                    jlong handle) {
    auto *value = stream(handle);
    std::lock_guard<std::mutex> lock(value->mutex);
    const int validationBytes = value->feedbackEndpoint != 0 && value->started &&
                                        value->feedback.accepted == 0 && value->error == 0 &&
                                        !value->stopping
                                    ? value->frameBytes
                                    : 0;
    const jlong fields[] = {value->error,
                            value->completedFrames,
                            static_cast<jlong>(value->queued + value->primingRemaining) +
                                value->submittedBytes - value->completedBytes +
                                value->submittedPrimingBytes - value->completedPrimingBytes +
                                validationBytes,
                            value->packetErrors,
                            value->shortPackets,
                            value->underruns,
                            value->completedBytes,
                            value->feedback.accepted,
                            value->feedback.rejected,
                            value->feedback.empty,
                            value->feedback.packetErrors,
                            static_cast<jlong>(value->feedback.value),
                            static_cast<jlong>(value->feedback.minimum),
                            static_cast<jlong>(value->feedback.maximum),
                            value->feedbackTimeouts,
                            value->completedPackets,
                            value->completedTransfers,
                            static_cast<jlong>(value->feedback.lastPayload),
                            value->feedback.lastPayloadBytes,
                            value->completedPrimingBytes / value->frameBytes,
                            (static_cast<jlong>(value->primingRemaining) +
                             value->submittedPrimingBytes - value->completedPrimingBytes) /
                                value->frameBytes,
                            value->completedBytes + value->completedPrimingBytes};
    const auto count = static_cast<jsize>(sizeof(fields) / sizeof(fields[0]));
    const auto result = env->NewLongArray(count);
    if (result)
        env->SetLongArrayRegion(result, 0, count, fields);
    return result;
}

extern "C" JNIEXPORT void JNICALL Java_com_pxr_cymatic_usb_UsbPcmStream_stopWorker(JNIEnv *,
                                                                                   jobject,
                                                                                   jlong handle) {
    stream(handle)->stop();
}

extern "C" JNIEXPORT void JNICALL Java_com_pxr_cymatic_usb_UsbPcmStream_destroy(JNIEnv *, jobject,
                                                                                jlong handle) {
    delete stream(handle);
}

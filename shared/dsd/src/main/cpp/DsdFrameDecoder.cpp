#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <jni.h>
#include <memory>
#include <vector>

namespace {
    constexpr int blockBytes = 4096;
    constexpr double pi = 3.14159265358979323846;

    uint8_t reverse(uint8_t value) {
        value = ((value & 0x55) << 1) | ((value >> 1) & 0x55);
        value = ((value & 0x33) << 2) | ((value >> 2) & 0x33);
        return (value << 4) | (value >> 4);
    }

    struct Decoder {
        int channels, decimation, bytesPerFrame, cursor = 0;
        bool lsbFirst, dop;
        uint8_t marker = 0x05;
        std::vector<std::array<double, 256>> tables;
        std::array<std::vector<uint8_t>, 2> history;

        Decoder(int rate, int outputRate, int channelCount, bool lsb, bool overPcm)
            : channels(channelCount), decimation(rate / outputRate),
              bytesPerFrame(decimation / 8), lsbFirst(lsb), dop(overPcm) {
            if (!dop) {
                const int taps = decimation * 48;
                std::vector<double> coefficients(taps);
                double sum = 0;
                const double cutoff = 40000.0 / rate;
                for (int tap = 0; tap < taps; ++tap) {
                    const double position = tap - (taps - 1) * 0.5;
                    const double phase = 2 * pi * tap / (taps - 1);
                    const double window = 0.35875 - 0.48829 * std::cos(phase) +
                                          0.14128 * std::cos(2 * phase) -
                                          0.01168 * std::cos(3 * phase);
                    coefficients[tap] = std::sin(2 * pi * cutoff * position) /
                                        (pi * position) * window;
                    sum += coefficients[tap];
                }
                tables.resize(taps / 8);
                for (size_t byte = 0; byte < tables.size(); ++byte) {
                    for (int value = 0; value < 256; ++value) {
                        double total = 0;
                        for (int bit = 0; bit < 8; ++bit)
                            total += coefficients[byte * 8 + bit] / sum *
                                     ((value & (1 << bit)) ? 1.0 : -1.0);
                        tables[byte][value] = total;
                    }
                }
                for (int channel = 0; channel < channels; ++channel)
                    history[channel].resize(tables.size());
            }
            reset();
        }

        void reset() {
            cursor = 0;
            marker = 0x05;
            for (auto &channel : history)
                std::fill(channel.begin(), channel.end(), 0x69);
        }

        uint8_t byte(const uint8_t *packet, int channel, int index, int samples) const {
            const int valid = std::clamp(samples - index * 8, 0, 8);
            if (valid == 0)
                return 0x69;
            uint8_t value = packet[4 + channel * blockBytes + index];
            if (lsbFirst)
                value = reverse(value);
            const uint8_t mask = static_cast<uint8_t>(0xff << (8 - valid));
            return (value & mask) | (0x69 & ~mask);
        }

        int decode(const uint8_t *packet, uint8_t *output, int samples) {
            const int frames = (samples + decimation - 1) / decimation;
            int written = 0;
            for (int frame = 0; frame < frames; ++frame) {
                if (dop) {
                    for (int channel = 0; channel < channels; ++channel) {
                        output[written++] = byte(packet, channel, frame * 2 + 1, samples);
                        output[written++] = byte(packet, channel, frame * 2, samples);
                        output[written++] = marker;
                    }
                    marker ^= 0xff;
                    continue;
                }
                for (int offset = 0; offset < bytesPerFrame; ++offset) {
                    for (int channel = 0; channel < channels; ++channel)
                        history[channel][cursor] = byte(packet, channel, frame * bytesPerFrame + offset, samples);
                    cursor = (cursor + 1) % static_cast<int>(tables.size());
                }
                for (int channel = 0; channel < channels; ++channel) {
                    double value = 0;
                    int index = cursor;
                    for (size_t tap = 0; tap < tables.size(); ++tap) {
                        if (--index < 0)
                            index = static_cast<int>(tables.size()) - 1;
                        value += tables[tap][history[channel][index]];
                    }
                    const int32_t sample = static_cast<int32_t>(
                        std::llround(std::clamp(value, -1.0, 1.0) * 8388607.0));
                    const uint32_t packed = static_cast<uint32_t>(sample);
                    output[written++] = packed & 0xff;
                    output[written++] = (packed >> 8) & 0xff;
                    output[written++] = (packed >> 16) & 0xff;
                }
            }
            return written;
        }
    };

    void fail(JNIEnv *env, const char *message) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_com_pxr_cymatic_dsd_DsdFrameDecoder_create(
    JNIEnv *env, jobject, jint rate, jint outputRate, jint channels, jboolean lsbFirst,
    jboolean dop) {
    const bool sourceRate = rate == 2822400 || rate == 5644800 || rate == 11289600 ||
                            rate == 22579200 || rate == 3072000 || rate == 6144000 ||
                            rate == 12288000 || rate == 24576000;
    if (!sourceRate || channels < 1 || channels > 2 || outputRate <= 0 || rate % outputRate ||
        (dop ? rate / outputRate != 16 :
               outputRate != (rate % 44100 == 0 ? 176400 : 192000))) {
        fail(env, "Invalid DSD decoder configuration");
        return 0;
    }
    try {
        return reinterpret_cast<jlong>(new Decoder(rate, outputRate, channels, lsbFirst, dop));
    } catch (...) {
        fail(env, "DSD decoder allocation failed");
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL Java_com_pxr_cymatic_dsd_DsdFrameDecoder_decodeFrame(
    JNIEnv *env, jobject, jlong handle, jobject input, jint offset, jint length, jobject output,
    jint capacity, jboolean reset) {
    auto *state = reinterpret_cast<Decoder *>(handle);
    auto *source = static_cast<uint8_t *>(env->GetDirectBufferAddress(input));
    auto *destination = static_cast<uint8_t *>(env->GetDirectBufferAddress(output));
    if (!state || !source || !destination || offset < 0 ||
        length != blockBytes * state->channels + 4 ||
        static_cast<jlong>(offset) + length > env->GetDirectBufferCapacity(input) || capacity < 0 ||
        capacity > env->GetDirectBufferCapacity(output)) {
        fail(env, "Invalid DSD packet buffers");
        return 0;
    }
    source += offset;
    const uint32_t samples = static_cast<uint32_t>(source[0]) |
                             (static_cast<uint32_t>(source[1]) << 8) |
                             (static_cast<uint32_t>(source[2]) << 16) |
                             (static_cast<uint32_t>(source[3]) << 24);
    if (samples == 0 || samples > blockBytes * 8 ||
        static_cast<uint64_t>((samples + state->decimation - 1) / state->decimation) *
                state->channels * 3 > static_cast<uint64_t>(capacity)) {
        fail(env, "DSD packet length exceeds its configuration");
        return 0;
    }
    if (reset)
        state->reset();
    return state->decode(source, destination, samples);
}

extern "C" JNIEXPORT void JNICALL Java_com_pxr_cymatic_dsd_DsdFrameDecoder_destroy(
    JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Decoder *>(handle);
}

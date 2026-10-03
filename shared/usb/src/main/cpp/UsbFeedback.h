#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>

struct UsbFeedback {
    const uint64_t nominal;
    const int ticksPerSecond;
    const int serviceTicks;
    const int maxFrames;
    uint64_t value;
    uint64_t minimum = 0, maximum = 0;
    uint64_t lastPayload = 0;
    unsigned lastPayloadBytes = 0;
    int64_t accepted = 0, rejected = 0, empty = 0, packetErrors = 0;
    int consecutiveRejected = 0;

    UsbFeedback(int rate, int ticks, int intervalTicks, int frames)
            : nominal((static_cast<uint64_t>(rate) << 16) / ticks), ticksPerSecond(ticks),
              serviceTicks(intervalTicks), maxFrames(frames), value(nominal) {}

    bool receive(const unsigned char *bytes, unsigned length, unsigned status) {
        lastPayload = 0;
        lastPayloadBytes = length;
        if (status != 0) {
            ++packetErrors;
            ++consecutiveRejected;
            return false;
        }
        if (length == 0) {
            ++empty;
            return false;
        }
        if (length > 4)
            return reject();
        for (unsigned index = 0; index < length; ++index)
            lastPayload |= static_cast<uint64_t>(bytes[index]) << (index * 8);
        if (length != 4 && !(ticksPerSecond == 1000 && length == 3))
            return reject();
        uint64_t decoded = lastPayload;
        if (length == 3)
            decoded <<= 2;
        if (decoded == 0) {
            ++empty;
            return false;
        }
        if (decoded < nominal * 95 / 100 || decoded > nominal * 105 / 100 ||
            (decoded * serviceTicks + 65535) / 65536 > static_cast<uint64_t>(maxFrames))
            return reject();
        value = decoded;
        minimum = minimum == 0 ? decoded : std::min(minimum, decoded);
        maximum = std::max(maximum, decoded);
        ++accepted;
        consecutiveRejected = 0;
        return true;
    }

private:
    bool reject() {
        ++rejected;
        ++consecutiveRejected;
        return false;
    }
};

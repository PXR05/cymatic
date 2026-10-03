/*
 * Copyright (c) 2011 Apple Inc. All rights reserved.
 * Modified by Cymatic in 2026 for bounded integer decoding.
 *
 * @APPLE_APACHE_LICENSE_HEADER_START@
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * @APPLE_APACHE_LICENSE_HEADER_END@
 */

#include "ALACBitUtilities.h"
#include <stddef.h>

static int available(BitBuffer *bits, uint32_t count) {
    if (bits->failed || bits->cur > bits->end || bits->bitIndex > 7 ||
        (uint64_t)(bits->end - bits->cur) * 8 < (uint64_t)count + bits->bitIndex) {
        bits->failed = 1;
        return 0;
    }
    return 1;
}

void BitBufferInit(BitBuffer *bits, uint8_t *buffer, uint32_t byteSize) {
    bits->cur = buffer;
    bits->end = buffer + byteSize;
    bits->bitIndex = 0;
    bits->byteSize = byteSize;
    bits->failed = 0;
}

uint32_t BitBufferRead(BitBuffer *bits, uint8_t count) {
    if (count > 32 || !available(bits, count)) {
        bits->failed = 1;
        return 0;
    }
    uint32_t value = 0;
    for (uint8_t i = 0; i < count; ++i) {
        value = (value << 1) | ((*bits->cur >> (7 - bits->bitIndex)) & 1);
        if (++bits->bitIndex == 8) {
            ++bits->cur;
            bits->bitIndex = 0;
        }
    }
    return value;
}

uint8_t BitBufferReadSmall(BitBuffer *bits, uint8_t count) {
    return (uint8_t)BitBufferRead(bits, count);
}

uint8_t BitBufferReadOne(BitBuffer *bits) {
    return (uint8_t)BitBufferRead(bits, 1);
}

uint32_t BitBufferPeek(BitBuffer *bits, uint8_t count) {
    BitBuffer copy = *bits;
    uint32_t value = BitBufferRead(&copy, count);
    bits->failed |= copy.failed;
    return value;
}

uint32_t BitBufferPeekOne(BitBuffer *bits) {
    return BitBufferPeek(bits, 1);
}

uint32_t BitBufferGetPosition(BitBuffer *bits) {
    return (uint32_t)(bits->cur - (bits->end - bits->byteSize)) * 8 + bits->bitIndex;
}

void BitBufferAdvance(BitBuffer *bits, uint32_t count) {
    if (!available(bits, count)) return;
    uint64_t next = (uint64_t)bits->bitIndex + count;
    bits->cur += next / 8;
    bits->bitIndex = next % 8;
}

void BitBufferByteAlign(BitBuffer *bits, int32_t addZeros) {
    if (addZeros) {
        bits->failed = 1;
        return;
    }
    if (bits->bitIndex != 0) BitBufferAdvance(bits, 8 - bits->bitIndex);
}

void BitBufferReset(BitBuffer *bits) {
    BitBufferInit(bits, bits->end - bits->byteSize, bits->byteSize);
}


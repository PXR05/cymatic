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

#include "aglib.h"
#include "ALACBitUtilities.h"
#include "ALACAudioTypes.h"

void set_ag_params(AGParamRecPtr params, uint32_t m, uint32_t p, uint32_t k,
                   uint32_t f, uint32_t s, uint32_t maxrun) {
    params->mb = params->mb0 = m;
    params->pb = p;
    params->kb = k;
    params->wb = (1u << k) - 1;
    params->qb = QB - p;
    params->fw = f;
    params->sw = s;
    params->maxrun = maxrun;
}

static uint32_t leading(uint32_t value) {
    return value == 0 ? 32 : (uint32_t)__builtin_clz(value);
}

static uint32_t rice(BitBuffer *bits, uint32_t m, uint32_t k, uint32_t width, int run) {
    if (k < 1 || k > 14 || width < 1 || width > 32) {
        bits->failed = 1;
        return 0;
    }
    uint32_t prefix = 0;
    while (prefix < 9 && BitBufferReadOne(bits) != 0) ++prefix;
    if (bits->failed) return 0;
    if (prefix == 9) return BitBufferRead(bits, (uint8_t)width);
    if (!run && k == 1) return prefix;
    uint32_t suffix = BitBufferPeek(bits, (uint8_t)k);
    BitBufferAdvance(bits, k - (suffix < 2 ? 1 : 0));
    return prefix * m + (suffix < 2 ? 0 : suffix - 1);
}

int32_t dyn_decomp(AGParamRecPtr params, BitBuffer *bits, int32_t *samples,
                   int32_t numSamples, int32_t width, uint32_t *outNumBits) {
    if (bits == 0 || samples == 0 || outNumBits == 0 || numSamples < 1 ||
        numSamples > 16384 || width < 1 || width > 32 ||
        params->kb < 1 || params->kb > 14 || bits->failed) return kALAC_ParamError;
    uint32_t start = BitBufferGetPosition(bits);
    uint32_t mean = params->mb0;
    uint32_t zeroMode = 0;
    uint32_t count = 0;
    while (count < (uint32_t)numSamples && !bits->failed) {
        uint32_t k = 31 - leading((mean >> QBSHIFT) + 3);
        if (k > params->kb) k = params->kb;
        uint32_t n = rice(bits, (1u << k) - 1, k, (uint32_t)width, 0);
        if (bits->failed) break;
        if (zeroMode != 0 && n == UINT32_MAX) {
            bits->failed = 1;
            break;
        }
        uint32_t decoded = n + zeroMode;
        uint32_t magnitude = (uint32_t)(((uint64_t)decoded + 1) >> 1);
        samples[count++] = (int32_t)((decoded & 1) ? 0u - magnitude : magnitude);
        mean = params->pb * (n + zeroMode) + mean - ((params->pb * mean) >> QBSHIFT);
        if (n > 0xffff) mean = 0xffff;
        zeroMode = 0;
        if ((mean << MMULSHIFT) < QB && count < (uint32_t)numSamples) {
            zeroMode = 1;
            k = leading(mean) - BITOFF + ((mean + MOFF) >> MDENSHIFT);
            if (k < 1 || k > 14) {
                bits->failed = 1;
                break;
            }
            n = rice(bits, ((1u << k) - 1) & params->wb, k, 16, 1);
            if (bits->failed || n > (uint32_t)numSamples - count) {
                bits->failed = 1;
                break;
            }
            for (uint32_t j = 0; j < n; ++j) samples[count++] = 0;
            if (n >= 65535) zeroMode = 0;
            mean = 0;
        }
    }
    *outNumBits = BitBufferGetPosition(bits) - start;
    return bits->failed ? kALAC_ParamError : ALAC_noErr;
}


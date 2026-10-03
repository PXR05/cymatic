#include "ALACBitUtilities.h"
#include "ALACDecoder.h"
#include <array>
#include <cstdint>
#include <jni.h>
#include <memory>
#include <new>

namespace {
    struct Decoder {
        ALACDecoder codec;
        bool failed = false;
    };

    void fail(JNIEnv *env, const char *message) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_pxr_cymatic_alac_AlacFrameDecoder_create(JNIEnv *env, jobject, jbyteArray config) {
    if (config == nullptr || env->GetArrayLength(config) != 24) {
        fail(env, "Invalid ALAC configuration");
        return 0;
    }
    std::array<uint8_t, 24> bytes{};
    env->GetByteArrayRegion(config, 0, bytes.size(), reinterpret_cast<jbyte *>(bytes.data()));
    if (env->ExceptionCheck())
        return 0;
    auto state = std::unique_ptr<Decoder>(new(std::nothrow) Decoder());
    if (!state || state->codec.Init(bytes.data(), bytes.size()) != ALAC_noErr) {
        fail(env, "Unsupported ALAC configuration or allocation failed");
        return 0;
    }
    return reinterpret_cast<jlong>(state.release());
}

extern "C" JNIEXPORT jint JNICALL Java_com_pxr_cymatic_alac_AlacFrameDecoder_decodeFrame(
        JNIEnv *env, jobject, jlong handle, jobject input, jint offset, jint length, jobject output,
        jint capacity) {
    auto *state = reinterpret_cast<Decoder *>(handle);
    auto *source = static_cast<uint8_t *>(env->GetDirectBufferAddress(input));
    auto *destination = static_cast<uint8_t *>(env->GetDirectBufferAddress(output));
    const jlong inputCapacity = env->GetDirectBufferCapacity(input);
    const jlong outputCapacity = env->GetDirectBufferCapacity(output);
    if (state == nullptr || source == nullptr || destination == nullptr || offset < 0 ||
        length < 1 || length > 1024 * 1024 || static_cast<jlong>(offset) + length > inputCapacity ||
        capacity < 1 || capacity > outputCapacity) {
        fail(env, "Invalid ALAC packet buffers");
        return 0;
    }
    const auto &config = state->codec.mConfig;
    const uint32_t frameBytes = config.numChannels * ((config.bitDepth + 7) / 8);
    if (state->failed || static_cast<uint32_t>(capacity) < config.frameLength * frameBytes ||
        (config.maxFrameBytes != 0 && static_cast<uint32_t>(length) > config.maxFrameBytes)) {
        fail(env, "ALAC packet exceeds its configuration or decoder has failed");
        return 0;
    }
    BitBuffer bits{};
    BitBufferInit(&bits, source + offset, length);
    uint32_t frames = 0;
    const int32_t result =
            state->codec.Decode(&bits, destination, config.frameLength, config.numChannels,
                                &frames);
    if (result != ALAC_noErr || bits.failed || bits.cur != bits.end || bits.bitIndex != 0 ||
        frames == 0 || frames > config.frameLength) {
        state->failed = true;
        fail(env, "Malformed or incomplete ALAC packet");
        return 0;
    }
    return static_cast<jint>(frames * frameBytes);
}

extern "C" JNIEXPORT void JNICALL Java_com_pxr_cymatic_alac_AlacFrameDecoder_destroy(JNIEnv *,
                                                                                     jobject,
                                                                                     jlong handle) {
    delete reinterpret_cast<Decoder *>(handle);
}

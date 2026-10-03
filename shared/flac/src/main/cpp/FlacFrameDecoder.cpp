#include <FLAC/stream_decoder.h>
#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <jni.h>
#include <memory>
#include <new>

namespace {

    struct Decoder {
        FLAC__StreamDecoder *codec = FLAC__stream_decoder_new();
        const uint8_t *input = nullptr;
        size_t inputSize = 0;
        size_t cursor = 0;
        uint8_t *output = nullptr;
        size_t outputCapacity = 0;
        size_t written = 0;
        unsigned rate = 0;
        unsigned bits = 0;
        unsigned channels = 0;
        unsigned maxBlockSize = 0;
        uint64_t totalSamples = 0;
        uint64_t nextSample = 0;
        bool haveNextSample = false;
        bool haveMetadata = false;
        const char *error = nullptr;

        ~Decoder() {
            if (codec != nullptr) {
                FLAC__stream_decoder_finish(codec);
                FLAC__stream_decoder_delete(codec);
            }
        }
    };

    void fail(JNIEnv *env, const char *message) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
    }

    FLAC__StreamDecoderReadStatus read(const FLAC__StreamDecoder *, FLAC__byte buffer[],
                                       size_t *bytes, void *data) {
        auto &state = *static_cast<Decoder *>(data);
        if (*bytes == 0 || state.error != nullptr)
            return FLAC__STREAM_DECODER_READ_STATUS_ABORT;
        *bytes = std::min(*bytes, state.inputSize - state.cursor);
        if (*bytes == 0)
            return FLAC__STREAM_DECODER_READ_STATUS_END_OF_STREAM;
        std::memcpy(buffer, state.input + state.cursor, *bytes);
        state.cursor += *bytes;
        return FLAC__STREAM_DECODER_READ_STATUS_CONTINUE;
    }

    FLAC__StreamDecoderTellStatus tell(const FLAC__StreamDecoder *, FLAC__uint64 *offset,
                                       void *data) {
        *offset = static_cast<Decoder *>(data)->cursor;
        return FLAC__STREAM_DECODER_TELL_STATUS_OK;
    }

    void metadata(const FLAC__StreamDecoder *, const FLAC__StreamMetadata *block, void *data) {
        auto &state = *static_cast<Decoder *>(data);
        if (block->type != FLAC__METADATA_TYPE_STREAMINFO) {
            state.error = "Unexpected FLAC metadata block";
            return;
        }
        const auto &info = block->data.stream_info;
        if (info.channels != state.channels || info.sample_rate != state.rate ||
            info.bits_per_sample != state.bits || info.max_blocksize != state.maxBlockSize ||
            info.min_blocksize < 16 || info.min_blocksize > info.max_blocksize) {
            state.error = "FLAC STREAMINFO does not match the selected source format";
            return;
        }
        state.totalSamples = info.total_samples;
        state.haveMetadata = true;
    }

    void error(const FLAC__StreamDecoder *, FLAC__StreamDecoderErrorStatus status, void *data) {
        auto &state = *static_cast<Decoder *>(data);
        if (state.error == nullptr)
            state.error = FLAC__StreamDecoderErrorStatusString[status];
    }

    FLAC__StreamDecoderWriteStatus write(const FLAC__StreamDecoder *, const FLAC__Frame *frame,
                                         const FLAC__int32 *const samples[], void *data) {
        auto &state = *static_cast<Decoder *>(data);
        const auto &header = frame->header;
        const size_t bytes =
                static_cast<size_t>(header.blocksize) * state.channels * ((state.bits + 7) / 8);
        if (state.error != nullptr)
            return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        if (state.output == nullptr || state.written != 0 || header.channels != state.channels ||
            header.sample_rate != state.rate || header.bits_per_sample != state.bits ||
            header.blocksize == 0 || header.blocksize > state.maxBlockSize ||
            bytes > state.outputCapacity ||
            header.number_type != FLAC__FRAME_NUMBER_TYPE_SAMPLE_NUMBER) {
            state.error = "FLAC frame does not match STREAMINFO or output capacity";
            return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        }
        const uint64_t first = header.number.sample_number;
        if ((state.haveNextSample && first != state.nextSample) ||
            (state.totalSamples != 0 &&
             (first > state.totalSamples || header.blocksize > state.totalSamples - first))) {
            state.error = "Missing, overlapping or out-of-range FLAC samples";
            return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        }
        for (unsigned sample = 0; sample < header.blocksize; ++sample) {
            for (unsigned channel = 0; channel < state.channels; ++channel) {
                const unsigned container = (state.bits + 7) / 8;
                const auto value = static_cast<uint32_t>(samples[channel][sample])
                        << (container * 8 - state.bits);
                for (unsigned byte = 0; byte < container; ++byte) {
                    state.output[state.written++] = static_cast<uint8_t>(value >> (byte * 8));
                }
            }
        }
        state.nextSample = first + header.blocksize;
        state.haveNextSample = true;
        return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
    }

}

extern "C" JNIEXPORT jlong JNICALL Java_com_pxr_cymatic_flac_FlacFrameDecoder_create(
        JNIEnv *env, jobject, jbyteArray info, jint rate, jint bits, jint channels,
        jint maxBlockSize) {
    if (info == nullptr || env->GetArrayLength(info) != 42 || rate < 8000 || rate > 384000 ||
        (bits != 16 && bits != 20 && bits != 24 && bits != 32) || channels < 1 || channels > 2 ||
        maxBlockSize < 16 || maxBlockSize > 65535) {
        fail(env, "Unsupported FLAC STREAMINFO");
        return 0;
    }
    std::array<uint8_t, 42> bytes{};
    env->GetByteArrayRegion(info, 0, bytes.size(), reinterpret_cast<jbyte *>(bytes.data()));
    if (env->ExceptionCheck())
        return 0;
    if (std::memcmp(bytes.data(), "fLaC", 4) != 0 || bytes[4] != 0x80 || bytes[5] != 0 ||
        bytes[6] != 0 || bytes[7] != 34) {
        fail(env, "Invalid FLAC initialization header");
        return 0;
    }
    auto state = std::unique_ptr<Decoder>(new(std::nothrow) Decoder());
    if (!state || state->codec == nullptr) {
        fail(env, "FLAC decoder allocation failed");
        return 0;
    }
    state->rate = rate;
    state->bits = bits;
    state->channels = channels;
    state->maxBlockSize = maxBlockSize;
    state->input = bytes.data();
    state->inputSize = bytes.size();
    FLAC__stream_decoder_set_md5_checking(state->codec, false);
    const auto result = FLAC__stream_decoder_init_stream(
            state->codec, read, nullptr, tell, nullptr, nullptr, write, metadata, error,
            state.get());
    if (result != FLAC__STREAM_DECODER_INIT_STATUS_OK ||
        !FLAC__stream_decoder_process_until_end_of_metadata(state->codec) ||
        state->error != nullptr || !state->haveMetadata) {
        fail(env, state->error != nullptr ? state->error : "FLAC metadata initialization failed");
        return 0;
    }
    state->input = nullptr;
    state->inputSize = state->cursor = 0;
    return reinterpret_cast<jlong>(state.release());
}

extern "C" JNIEXPORT jint JNICALL Java_com_pxr_cymatic_flac_FlacFrameDecoder_decodeFrame(
        JNIEnv *env, jobject, jlong handle, jobject input, jint offset, jint length, jobject output,
        jint capacity, jboolean reset) {
    auto *state = reinterpret_cast<Decoder *>(handle);
    auto *source = static_cast<uint8_t *>(env->GetDirectBufferAddress(input));
    auto *destination = static_cast<uint8_t *>(env->GetDirectBufferAddress(output));
    const jlong inputCapacity = env->GetDirectBufferCapacity(input);
    const jlong outputCapacity = env->GetDirectBufferCapacity(output);
    if (state == nullptr || source == nullptr || destination == nullptr || offset < 0 ||
        length <= 0 || length > 1024 * 1024 ||
        static_cast<jlong>(offset) + length > inputCapacity || capacity <= 0 ||
        capacity > outputCapacity) {
        fail(env, "Invalid FLAC frame buffers");
        return 0;
    }
    if (state->error != nullptr || !FLAC__stream_decoder_flush(state->codec)) {
        fail(env, state->error != nullptr ? state->error : "FLAC flush failed");
        return 0;
    }
    if (reset)
        state->haveNextSample = false;
    state->input = source + offset;
    state->inputSize = length;
    state->cursor = 0;
    state->output = destination;
    state->outputCapacity = capacity;
    state->written = 0;
    const bool decoded = FLAC__stream_decoder_process_single(state->codec);
    FLAC__uint64 consumed = 0;
    const bool complete = FLAC__stream_decoder_get_decode_position(state->codec, &consumed) &&
                          consumed == static_cast<FLAC__uint64>(length);
    state->input = nullptr;
    state->output = nullptr;
    if (!decoded || state->error != nullptr || state->written == 0 || !complete) {
        fail(env, state->error != nullptr ? state->error : "Incomplete or malformed FLAC frame");
        return 0;
    }
    return static_cast<jint>(state->written);
}

extern "C" JNIEXPORT void JNICALL Java_com_pxr_cymatic_flac_FlacFrameDecoder_destroy(JNIEnv *,
                                                                                     jobject,
                                                                                     jlong handle) {
    delete reinterpret_cast<Decoder *>(handle);
}

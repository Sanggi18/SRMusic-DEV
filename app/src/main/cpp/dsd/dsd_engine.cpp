#include "dsd_engine.h"
#include "../terminal_logger.h"
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <mutex>

#define LOG_TAG "SRMusicDsdEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace srmusic {
namespace dsd {

// 64-tap Normalized Windowed-Sinc FIR Filter (Cutoff ~28 kHz, sum = 1.0)
static const float DSD_FIR_COEFFS[64] = {
    -0.00000000f, -0.00001796f, -0.00006227f, -0.00011476f,
    -0.00015257f, -0.00014889f, -0.00007398f,  0.00010367f,
     0.00041604f,  0.00089410f,  0.00156640f,  0.00245759f,
     0.00358709f,  0.00496785f,  0.00660531f,  0.00849668f,
     0.01063041f,  0.01298604f,  0.01553435f,  0.01823788f,
     0.02105174f,  0.02392475f,  0.02680084f,  0.02962066f,
     0.03232336f,  0.03484851f,  0.03713795f,  0.03913772f,
     0.04079979f,  0.04208368f,  0.04295774f,  0.04340029f,
     0.04340029f,  0.04295774f,  0.04208368f,  0.04079979f,
     0.03913772f,  0.03713795f,  0.03484851f,  0.03232336f,
     0.02962066f,  0.02680084f,  0.02392475f,  0.02105174f,
     0.01823788f,  0.01553435f,  0.01298604f,  0.01063041f,
     0.00849668f,  0.00660531f,  0.00496785f,  0.00358709f,
     0.00245759f,  0.00156640f,  0.00089410f,  0.00041604f,
     0.00010367f, -0.00007398f, -0.00014889f, -0.00015257f,
    -0.00011476f, -0.00006227f, -0.00001796f, -0.00000000f
};

// 8x256 Precomputed Lookup Table for 8-bit block convolution
static float sFirLut[8][256];
static std::once_flag sLutOnceFlag;

void DsdDecimator::initLutOnce() {
    std::call_once(sLutOnceFlag, []() {
        for (int byteIdx = 0; byteIdx < 8; ++byteIdx) {
            for (int val = 0; val < 256; ++val) {
                float sum = 0.0f;
                // Sony DSF: LSB first
                for (int bit = 0; bit < 8; ++bit) {
                    if ((val >> bit) & 1) {
                        sum += DSD_FIR_COEFFS[byteIdx * 8 + bit];
                    }
                }
                sFirLut[byteIdx][val] = sum;
            }
        }
    });
}

DsdDecimator::DsdDecimator(uint32_t decimationFactor, float gainFactor)
    : mDecimationFactor(decimationFactor > 0 ? decimationFactor : 32),
      mGain(gainFactor),
      mHistoryBits(0xAAAAAAAAAAAAAAAAULL), // Init with DSD silence (0xAA)
      mBitsProcessed(0) {
    initLutOnce();
}

void DsdDecimator::reset() {
    mHistoryBits = 0xAAAAAAAAAAAAAAAAULL;
    mBitsProcessed = 0;
}

void DsdDecimator::setGain(float gainFactor) {
    mGain = gainFactor;
}

void DsdDecimator::setDecimationFactor(uint32_t factor) {
    if (factor > 0) {
        mDecimationFactor = factor;
    }
}

size_t DsdDecimator::convertChannel(const uint8_t* dsdIn, size_t dsdByteCount,
                                   float* outPcm, size_t maxOutSamples) {
    if (!dsdIn || !outPcm || dsdByteCount == 0 || maxOutSamples == 0) {
        return 0;
    }

    size_t outIndex = 0;

    for (size_t b = 0; b < dsdByteCount && outIndex < maxOutSamples; ++b) {
        uint8_t byteVal = dsdIn[b];
        mHistoryBits = (mHistoryBits << 8) | static_cast<uint64_t>(byteVal);
        mBitsProcessed += 8;

        while (mBitsProcessed >= mDecimationFactor && outIndex < maxOutSamples) {
            float sum = sFirLut[7][(mHistoryBits >> 0)  & 0xFF] +
                        sFirLut[6][(mHistoryBits >> 8)  & 0xFF] +
                        sFirLut[5][(mHistoryBits >> 16) & 0xFF] +
                        sFirLut[4][(mHistoryBits >> 24) & 0xFF] +
                        sFirLut[3][(mHistoryBits >> 32) & 0xFF] +
                        sFirLut[2][(mHistoryBits >> 40) & 0xFF] +
                        sFirLut[1][(mHistoryBits >> 48) & 0xFF] +
                        sFirLut[0][(mHistoryBits >> 56) & 0xFF];

            float pcmVal = (2.0f * sum - 1.0f) * mGain;
            if (pcmVal > 1.0f) pcmVal = 1.0f;
            else if (pcmVal < -1.0f) pcmVal = -1.0f;

            outPcm[outIndex++] = pcmVal;
            mBitsProcessed -= mDecimationFactor;
        }
    }

    return outIndex;
}

size_t DsdDecimator::convertChannel16(const uint8_t* dsdIn, size_t dsdByteCount,
                                     int16_t* outPcm16, size_t maxOutSamples) {
    if (!dsdIn || !outPcm16 || dsdByteCount == 0 || maxOutSamples == 0) {
        return 0;
    }

    size_t outIndex = 0;

    for (size_t b = 0; b < dsdByteCount && outIndex < maxOutSamples; ++b) {
        uint8_t byteVal = dsdIn[b];
        mHistoryBits = (mHistoryBits << 8) | static_cast<uint64_t>(byteVal);
        mBitsProcessed += 8;

        while (mBitsProcessed >= mDecimationFactor && outIndex < maxOutSamples) {
            float sum = sFirLut[7][(mHistoryBits >> 0)  & 0xFF] +
                        sFirLut[6][(mHistoryBits >> 8)  & 0xFF] +
                        sFirLut[5][(mHistoryBits >> 16) & 0xFF] +
                        sFirLut[4][(mHistoryBits >> 24) & 0xFF] +
                        sFirLut[3][(mHistoryBits >> 32) & 0xFF] +
                        sFirLut[2][(mHistoryBits >> 40) & 0xFF] +
                        sFirLut[1][(mHistoryBits >> 48) & 0xFF] +
                        sFirLut[0][(mHistoryBits >> 56) & 0xFF];

            float pcmVal = (2.0f * sum - 1.0f) * mGain;
            if (pcmVal > 1.0f) pcmVal = 1.0f;
            else if (pcmVal < -1.0f) pcmVal = -1.0f;

            int32_t s32 = static_cast<int32_t>(pcmVal * 32767.0f);
            if (s32 > 32767) s32 = 32767;
            else if (s32 < -32768) s32 = -32768;

            outPcm16[outIndex++] = static_cast<int16_t>(s32);
            mBitsProcessed -= mDecimationFactor;
        }
    }

    return outIndex;
}

} // namespace dsd
} // namespace srmusic

extern "C" {

JNIEXPORT jint JNICALL
Java_com_example_core_audio_dac_DsdEngine_nativeConvertDsdToPcm16(
    JNIEnv* env, jclass /* clazz */,
    jbyteArray dsdBytes, jint numBytes,
    jint channels, jint decimationFactor,
    jfloat gain, jbyteArray outPcmBytes) {

    if (!dsdBytes || !outPcmBytes || numBytes <= 0 || channels <= 0) {
        return 0;
    }

    jboolean isCopy = JNI_FALSE;
    jbyte* dsdData = reinterpret_cast<jbyte*>(env->GetPrimitiveArrayCritical(dsdBytes, &isCopy));
    jbyte* pcmData = reinterpret_cast<jbyte*>(env->GetPrimitiveArrayCritical(outPcmBytes, &isCopy));

    if (!dsdData || !pcmData) {
        if (dsdData) env->ReleasePrimitiveArrayCritical(dsdBytes, dsdData, JNI_ABORT);
        if (pcmData) env->ReleasePrimitiveArrayCritical(outPcmBytes, pcmData, 0);
        return 0;
    }

    jsize maxOutBytes = env->GetArrayLength(outPcmBytes);
    srmusic::dsd::DsdDecimator decimator(static_cast<uint32_t>(decimationFactor), gain);

    size_t bytesWritten = 0;
    if (channels == 1) {
        size_t maxSamples = maxOutBytes / 2;
        thread_local std::vector<int16_t> mono16;
        if (mono16.size() < maxSamples) mono16.resize(maxSamples);
        size_t samples = decimator.convertChannel16(
            reinterpret_cast<const uint8_t*>(dsdData),
            static_cast<size_t>(numBytes),
            mono16.data(),
            maxSamples
        );
        for (size_t i = 0; i < samples && (bytesWritten + 2) <= static_cast<size_t>(maxOutBytes); ++i) {
            int16_t s = mono16[i];
            pcmData[bytesWritten++] = static_cast<jbyte>(s & 0xFF);
            pcmData[bytesWritten++] = static_cast<jbyte>((s >> 8) & 0xFF);
        }
    } else if (channels == 2) {
        size_t halfBytes = numBytes / 2;
        size_t maxChannelSamples = maxOutBytes / 4;
        thread_local std::vector<int16_t> left16;
        thread_local std::vector<int16_t> right16;
        if (left16.size() < maxChannelSamples) left16.resize(maxChannelSamples);
        if (right16.size() < maxChannelSamples) right16.resize(maxChannelSamples);

        size_t leftSamples = decimator.convertChannel16(
            reinterpret_cast<const uint8_t*>(dsdData),
            halfBytes,
            left16.data(),
            maxChannelSamples
        );
        decimator.reset();
        size_t rightSamples = decimator.convertChannel16(
            reinterpret_cast<const uint8_t*>(dsdData + halfBytes),
            halfBytes,
            right16.data(),
            maxChannelSamples
        );

        size_t minSamples = std::min(leftSamples, rightSamples);
        for (size_t i = 0; i < minSamples && (bytesWritten + 4) <= static_cast<size_t>(maxOutBytes); ++i) {
            int16_t l = left16[i];
            int16_t r = right16[i];
            pcmData[bytesWritten++] = static_cast<jbyte>(l & 0xFF);
            pcmData[bytesWritten++] = static_cast<jbyte>((l >> 8) & 0xFF);
            pcmData[bytesWritten++] = static_cast<jbyte>(r & 0xFF);
            pcmData[bytesWritten++] = static_cast<jbyte>((r >> 8) & 0xFF);
        }
    }

    env->ReleasePrimitiveArrayCritical(dsdBytes, dsdData, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(outPcmBytes, pcmData, 0);

    return static_cast<jint>(bytesWritten);
}

JNIEXPORT jint JNICALL
Java_com_example_core_audio_dac_DsdEngine_nativeConvertDsdToPcmFloat(
    JNIEnv* env, jclass /* clazz */,
    jbyteArray dsdBytes, jint numBytes,
    jint channels, jint decimationFactor,
    jfloat gain, jfloatArray outPcm) {

    if (!dsdBytes || !outPcm || numBytes <= 0 || channels <= 0) {
        return 0;
    }

    jboolean isCopy = JNI_FALSE;
    jbyte* dsdData = reinterpret_cast<jbyte*>(env->GetPrimitiveArrayCritical(dsdBytes, &isCopy));
    jfloat* pcmData = reinterpret_cast<jfloat*>(env->GetPrimitiveArrayCritical(outPcm, &isCopy));

    if (!dsdData || !pcmData) {
        if (dsdData) env->ReleasePrimitiveArrayCritical(dsdBytes, dsdData, JNI_ABORT);
        if (pcmData) env->ReleasePrimitiveArrayCritical(outPcm, pcmData, 0);
        return 0;
    }

    jsize pcmLen = env->GetArrayLength(outPcm);
    srmusic::dsd::DsdDecimator decimator(static_cast<uint32_t>(decimationFactor), gain);

    size_t convertedSamples = 0;
    if (channels == 1) {
        convertedSamples = decimator.convertChannel(
            reinterpret_cast<const uint8_t*>(dsdData),
            static_cast<size_t>(numBytes),
            pcmData,
            static_cast<size_t>(pcmLen)
        );
    } else if (channels == 2) {
        size_t halfBytes = numBytes / 2;
        size_t halfPcmLen = static_cast<size_t>(pcmLen / 2);
        thread_local std::vector<float> leftPcm;
        thread_local std::vector<float> rightPcm;
        if (leftPcm.size() < halfPcmLen) leftPcm.resize(halfPcmLen);
        if (rightPcm.size() < halfPcmLen) rightPcm.resize(halfPcmLen);

        size_t leftSamples = decimator.convertChannel(
            reinterpret_cast<const uint8_t*>(dsdData),
            halfBytes,
            leftPcm.data(),
            halfPcmLen
        );
        decimator.reset();
        size_t rightSamples = decimator.convertChannel(
            reinterpret_cast<const uint8_t*>(dsdData + halfBytes),
            halfBytes,
            rightPcm.data(),
            halfPcmLen
        );

        size_t minSamples = std::min(leftSamples, rightSamples);
        for (size_t i = 0; i < minSamples && (i * 2 + 1) < static_cast<size_t>(pcmLen); ++i) {
            pcmData[i * 2] = leftPcm[i];
            pcmData[i * 2 + 1] = rightPcm[i];
        }
        convertedSamples = minSamples * 2;
    }

    env->ReleasePrimitiveArrayCritical(dsdBytes, dsdData, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(outPcm, pcmData, 0);

    return static_cast<jint>(convertedSamples);
}

} // extern "C"

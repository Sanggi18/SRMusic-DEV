#ifndef SRMUSIC_DSD_ENGINE_H
#define SRMUSIC_DSD_ENGINE_H

#include <cstdint>
#include <cstddef>
#include <vector>
#include <jni.h>

namespace srmusic {
namespace dsd {

// DSD Standard Sample Rates
constexpr uint32_t DSD_RATE_64  = 2822400;   // 64 * 44.1 kHz
constexpr uint32_t DSD_RATE_128 = 5644800;   // 128 * 44.1 kHz
constexpr uint32_t DSD_RATE_256 = 11289600;  // 256 * 44.1 kHz

// Continuous Sliding-Window Decimation FIR Filter
class DsdDecimator {
public:
    explicit DsdDecimator(uint32_t decimationFactor = 32, float gainFactor = 1.9952623f);
    ~DsdDecimator() = default;

    void reset();
    void setGain(float gainFactor);
    void setDecimationFactor(uint32_t factor);

    // Convert raw 1-bit DSD bytes (LSB first per Sony DSF) to 32-bit float PCM
    size_t convertChannel(const uint8_t* dsdIn, size_t dsdByteCount, float* outPcm, size_t maxOutSamples);

    // Convert raw 1-bit DSD bytes (LSB first per Sony DSF) to 16-bit integer PCM Little-Endian
    size_t convertChannel16(const uint8_t* dsdIn, size_t dsdByteCount, int16_t* outPcm16, size_t maxOutSamples);

private:
    uint32_t mDecimationFactor;
    float mGain;
    uint64_t mHistoryBits;
    uint32_t mBitsProcessed;

    static void initLutOnce();
};

} // namespace dsd
} // namespace srmusic

#endif // SRMUSIC_DSD_ENGINE_H

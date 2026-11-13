#pragma once

#include <atomic>
#include <cstdint>
#include <linux/usbdevice_fs.h>

#define USB_AUDIO_PACKETS_PER_URB 8

#define USB_AUDIO_NUM_URBS 80

#define USB_AUDIO_URB_BUFFER_SIZE 4096

struct UrbSlot {
    struct usbdevfs_urb *urb;

    uint8_t *buffer;

    int dataLength;
};

struct UsbAudioContext {
    int fd;

    int interfaceId;

    int endpointOut;

    int endpointFeedback;

    int32_t sampleRate;

    int32_t channelCount;

    int32_t bitDepth;

    int32_t bytesPerSample;

    int32_t bytesPerFrame;

    int32_t maxPacketSize;

    std::atomic<bool> running;

    uint8_t *transferBuffer;

    int32_t transferBufferCapacity;

    uint8_t *mergeBuffer;

    int32_t mergeBufferCapacity;

    int64_t framesWritten;

    int64_t writeCallCount;

    int64_t lastWriteUs;

    int32_t lastSamplesCount;

    bool interfaceClaimed;

    UrbSlot ring[USB_AUDIO_NUM_URBS];

    int submitIdx;

    int reapIdx;

    int urbsInFlight;

    int maxInFlight;

    int packetsPerSec;

    int64_t urbsSubmitted;

    bool ringAllocated;

    double frameAccumulator;

    double calibratedFpmf;

    uint8_t residualBuffer[USB_AUDIO_URB_BUFFER_SIZE];

    int residualBytes;

    struct usbdevfs_urb *feedbackUrb;

    uint8_t feedbackBuffer[4];

    bool feedbackInFlight;
};

void submitPcmToUrbs(UsbAudioContext *ctx, const uint8_t *pcmData, int totalBytes);

void padInt16ToInt32(const uint8_t *src, uint8_t *dst, int numSamples);

void padInt24ToInt32(const uint8_t *src, uint8_t *dst, int numSamples);

void shiftInt32From24(const uint8_t *src, uint8_t *dst, int numSamples);

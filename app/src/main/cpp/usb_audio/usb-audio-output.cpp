#include "usb-audio-output.h"
#include <jni.h>
#include <android/log.h>
#include <cerrno>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <new>
#include <unistd.h>
#include <time.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>

#ifndef USBDEVFS_URB_ISO_ASAP
#define USBDEVFS_URB_ISO_ASAP 0x02
#endif

#ifndef USBDEVFS_CONNECT
#define USBDEVFS_CONNECT _IO('U', 23)
#endif

#include "../terminal_logger.h"
#include <cstdarg>

#define TAG "UsbAudioOutput"

static inline void usb_diag_log(const char* prio, const char* fmt, ...) {
    char buf[512];

    va_list args;

    va_start(args, fmt);

    vsnprintf(buf, sizeof(buf), fmt, args);

    va_end(args);

    terminal_log_diag(TAG, prio, buf);
}

#define LOGI(...) do { __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__); usb_diag_log("I", __VA_ARGS__); } while(0)

#define LOGW(...) do { __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__); usb_diag_log("W", __VA_ARGS__); } while(0)

#define LOGE(...) do { __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__); usb_diag_log("E", __VA_ARGS__); } while(0)

static inline float clampf(float v) { return v > 1.0f ? 1.0f : (v < -1.0f ? -1.0f : v); }

static void convertFloatToInt16(const float *src, uint8_t *dst, int n) {
    auto *out = reinterpret_cast<int16_t *>(dst);

    for (int i = 0; i < n; i++) {
        float s = clampf(src[i]) * 32768.0f;

        if (s > 32767.0f) s = 32767.0f;

        if (s < -32768.0f) s = -32768.0f;

        out[i] = (int16_t)s;
    }
}

static void convertFloatToInt24(const float *src, uint8_t *dst, int n) {
    for (int i = 0; i < n; i++) {
        float s = clampf(src[i]) * 8388608.0f;

        if (s > 8388607.0f) s = 8388607.0f;

        if (s < -8388608.0f) s = -8388608.0f;

        int32_t v = (int32_t)s;

        dst[i*3] = v & 0xFF; dst[i*3+1] = (v>>8) & 0xFF; dst[i*3+2] = (v>>16) & 0xFF;
    }
}

static void convertFloatToInt32(const float *src, uint8_t *dst, int n) {
    auto *out = reinterpret_cast<int32_t *>(dst);

    for (int i = 0; i < n; i++) {
        double s = (double)clampf(src[i]) * 2147483648.0;

        if (s > 2147483647.0) s = 2147483647.0;

        if (s < -2147483648.0) s = -2147483648.0;

        out[i] = (int32_t)s;
    }
}

static bool allocRing(UsbAudioContext *ctx) {
    size_t urbStructSize = sizeof(struct usbdevfs_urb) +
                           USB_AUDIO_PACKETS_PER_URB * sizeof(struct usbdevfs_iso_packet_desc);

    for (int i = 0; i < USB_AUDIO_NUM_URBS; i++) {
        ctx->ring[i].urb = (struct usbdevfs_urb *)calloc(1, urbStructSize);

        ctx->ring[i].buffer = (uint8_t *)malloc(USB_AUDIO_URB_BUFFER_SIZE);

        ctx->ring[i].dataLength = 0;

        if (!ctx->ring[i].urb || !ctx->ring[i].buffer) {
            LOGE("allocRing: OOM at slot %d", i);

            for (int j = 0; j <= i; j++) {
                free(ctx->ring[j].urb);
                ctx->ring[j].urb = nullptr;

                free(ctx->ring[j].buffer);
                ctx->ring[j].buffer = nullptr;
            }

            return false;
        }
    }

    ctx->ringAllocated = true;

    return true;
}

static void freeRing(UsbAudioContext *ctx) {
    if (!ctx->ringAllocated) return;

    for (int i = 0; i < USB_AUDIO_NUM_URBS; i++) {
        free(ctx->ring[i].urb);

        free(ctx->ring[i].buffer);

        ctx->ring[i].urb = nullptr;

        ctx->ring[i].buffer = nullptr;
    }

    ctx->ringAllocated = false;
}

static double readFeedback(int fd, int ep, bool fullSpeed) {
    uint8_t fb[4] = {};

    size_t sz = sizeof(struct usbdevfs_urb) + sizeof(struct usbdevfs_iso_packet_desc);

    auto *u = (struct usbdevfs_urb *)calloc(1, sz);

    if (!u) return 0;

    u->type = USBDEVFS_URB_TYPE_ISO;

    u->flags = USBDEVFS_URB_ISO_ASAP;

    u->endpoint = (unsigned char)ep;

    u->buffer = fb;

    u->buffer_length = 4;

    u->number_of_packets = 1;

    u->iso_frame_desc[0].length = 4;

    if (ioctl(fd, USBDEVFS_SUBMITURB, u) < 0) { free(u); return 0; }

    struct usbdevfs_urb *c = nullptr;
    bool reapedTarget = false;

    for (int attempt = 0; attempt < 25; attempt++) {
        usleep(200);
        if (ioctl(fd, USBDEVFS_REAPURBNDELAY, &c) == 0 && c != nullptr) {
            if (c == u) {
                reapedTarget = true;
                break;
            }
        }
    }

    if (!reapedTarget) {
        ioctl(fd, USBDEVFS_DISCARDURB, u);
        for (int attempt = 0; attempt < 50; attempt++) {
            if (ioctl(fd, USBDEVFS_REAPURBNDELAY, &c) == 0 && c == u) {
                break;
            }
            usleep(100);
        }
        free(u);
        return 0;
    }

    double r = 0;

    if (fullSpeed && u->iso_frame_desc[0].actual_length >= 3) {
        uint32_t raw = fb[0] | (fb[1] << 8) | (fb[2] << 16);

        r = raw / 16384.0;
    } else if (u->iso_frame_desc[0].actual_length >= 4) {
        uint32_t raw = fb[0] | (fb[1] << 8) | (fb[2] << 16) | (fb[3] << 24);

        r = raw / 65536.0;
    }

    free(u);

    return r;
}

static bool allocFeedbackUrb(UsbAudioContext *ctx) {
    size_t sz = sizeof(struct usbdevfs_urb) + sizeof(struct usbdevfs_iso_packet_desc);

    ctx->feedbackUrb = (struct usbdevfs_urb *)calloc(1, sz);

    if (!ctx->feedbackUrb) return false;

    ctx->feedbackInFlight = false;

    memset(ctx->feedbackBuffer, 0, sizeof(ctx->feedbackBuffer));

    return true;
}

static bool submitFeedbackUrb(UsbAudioContext *ctx) {
    if (ctx->endpointFeedback <= 0 || !ctx->feedbackUrb || ctx->feedbackInFlight)

        return false;

    struct usbdevfs_urb *u = ctx->feedbackUrb;

    size_t sz = sizeof(struct usbdevfs_urb) + sizeof(struct usbdevfs_iso_packet_desc);

    memset(u, 0, sz);

    u->type = USBDEVFS_URB_TYPE_ISO;

    u->flags = USBDEVFS_URB_ISO_ASAP;

    u->endpoint = (unsigned char)ctx->endpointFeedback;

    u->buffer = ctx->feedbackBuffer;

    u->buffer_length = 4;

    u->number_of_packets = 1;

    u->iso_frame_desc[0].length = 4;

    if (ioctl(ctx->fd, USBDEVFS_SUBMITURB, u) < 0) {
        LOGW("submitFeedbackUrb: failed errno=%d (%s)", errno, strerror(errno));

        return false;
    }

    ctx->feedbackInFlight = true;

    return true;
}

static int64_t g_feedbackCount = 0;

static void handleFeedbackCompletion(UsbAudioContext *ctx) {
    ctx->feedbackInFlight = false;

    g_feedbackCount++;

    const bool fullSpeed = ctx->packetsPerSec == 1000;
    const unsigned got = ctx->feedbackUrb->iso_frame_desc[0].actual_length;
    if (got >= (fullSpeed ? 3u : 4u)) {
        uint8_t *fb = ctx->feedbackBuffer;

        double newFpmf = fullSpeed
            ? (fb[0] | (fb[1] << 8) | (fb[2] << 16)) / 16384.0
            : (uint32_t)(fb[0] | (fb[1] << 8) | (fb[2] << 16) | (fb[3] << 24)) / 65536.0;

        double nominal = ctx->sampleRate / (double)ctx->packetsPerSec;

        if (newFpmf > nominal * 0.99 && newFpmf < nominal * 1.01) {
            ctx->calibratedFpmf = ctx->calibratedFpmf * 0.995 + newFpmf * 0.005;

            if (g_feedbackCount % 10000 == 0) {
                LOGI("Feedback #%lld: fpmf=%.4f (%.1f Hz)",
                     (long long)g_feedbackCount, ctx->calibratedFpmf, ctx->calibratedFpmf * ctx->packetsPerSec);
            }
        }
    }

    submitFeedbackUrb(ctx);
}

static int submitRingUrb(UsbAudioContext *ctx, const int *pktSizes, int numPackets, int totalBytes) {
    UrbSlot *slot = &ctx->ring[ctx->submitIdx];

    struct usbdevfs_urb *u = slot->urb;

    size_t clearSize = sizeof(struct usbdevfs_urb) +
                       numPackets * sizeof(struct usbdevfs_iso_packet_desc);

    memset(u, 0, clearSize);

    u->type = USBDEVFS_URB_TYPE_ISO;

    u->flags = USBDEVFS_URB_ISO_ASAP;

    u->endpoint = (unsigned char)ctx->endpointOut;

    u->buffer = slot->buffer;

    u->buffer_length = totalBytes;

    u->number_of_packets = numPackets;

    for (int i = 0; i < numPackets; i++)

        u->iso_frame_desc[i].length = (unsigned)pktSizes[i];

    slot->dataLength = totalBytes;

    int ret = ioctl(ctx->fd, USBDEVFS_SUBMITURB, u);

    if (ret < 0) {
        LOGE("SUBMITURB FAILED slot=%d ep=0x%02x bytes=%d pkts=%d errno=%d (%s)",
             ctx->submitIdx, ctx->endpointOut, totalBytes, numPackets, errno, strerror(errno));

        return -1;
    }

    ctx->submitIdx = (ctx->submitIdx + 1) % USB_AUDIO_NUM_URBS;

    ctx->urbsInFlight++;

    ctx->urbsSubmitted++;

    return 0;
}

static int reapOldestUrb(UsbAudioContext *ctx, int timeoutMs) {
    struct usbdevfs_urb *c = nullptr;

    int iterations = timeoutMs * 8;

    for (int i = 0; i < iterations; i++) {
        int ret = ioctl(ctx->fd, USBDEVFS_REAPURBNDELAY, &c);

        if (ret == 0 && c != nullptr) {
            if (c == ctx->feedbackUrb) {
                handleFeedbackCompletion(ctx);

                c = nullptr;

                continue;
            }

            if (c->status != 0 || c->error_count > 0) {
                static int64_t g_urbErrors = 0;
                if (g_urbErrors++ % 50 == 0) {
                    LOGW("URB error #%lld: status=%d error_count=%d pkts=%d",
                         (long long)g_urbErrors, c->status, c->error_count, c->number_of_packets);
                }
            }

            ctx->reapIdx = (ctx->reapIdx + 1) % USB_AUDIO_NUM_URBS;

            ctx->urbsInFlight--;

            return 0;
        }

        if (ret < 0 && errno != EAGAIN) {
            LOGE("reapOldestUrb: error errno=%d (%s)", errno, strerror(errno));

            return -1;
        }

        usleep(125);
    }

    return -2;
}

static int drainAllUrbs(UsbAudioContext *ctx) {
    int drained = 0;

    int initialCount = ctx->urbsInFlight;

    LOGI("drainAllUrbs: draining %d URBs (feedback=%s)...",
         initialCount, ctx->feedbackInFlight ? "in-flight" : "idle");

    if (ctx->feedbackInFlight && ctx->feedbackUrb) {
        ioctl(ctx->fd, USBDEVFS_DISCARDURB, ctx->feedbackUrb);

        struct usbdevfs_urb *c = nullptr;

        for (int i = 0; i < 50; i++) {
            if (ioctl(ctx->fd, USBDEVFS_REAPURBNDELAY, &c) == 0 && c == ctx->feedbackUrb) {
                break;
            }

            usleep(100);
        }

        ctx->feedbackInFlight = false;
    }

    while (ctx->urbsInFlight > 0) {
        int result = reapOldestUrb(ctx, 500);

        if (result == 0) {
            drained++;
        } else {
            break;
        }
    }

    if (ctx->urbsInFlight > 0) {
        LOGW("drainAllUrbs: %d/%d reaped naturally, discarding remaining %d",
             drained, initialCount, ctx->urbsInFlight);

        int toDiscard = ctx->urbsInFlight;

        for (int i = 0; i < toDiscard; i++) {
            int slotIdx = (ctx->reapIdx + i) % USB_AUDIO_NUM_URBS;

            ioctl(ctx->fd, USBDEVFS_DISCARDURB, ctx->ring[slotIdx].urb);
        }

        int reaped = 0;

        for (int attempt = 0; attempt < 500 && reaped < toDiscard; attempt++) {
            struct usbdevfs_urb *c = nullptr;

            int ret = ioctl(ctx->fd, USBDEVFS_REAPURBNDELAY, &c);

            if (ret == 0 && c != nullptr) {
                reaped++;
            } else if (ret < 0 && errno != EAGAIN) {
                LOGE("drainAllUrbs: reap error errno=%d", errno);

                break;
            } else {
                usleep(1000);
            }
        }

        LOGI("drainAllUrbs: discarded %d, reaped %d completions", toDiscard, reaped);

        drained += reaped;

        for (int i = 0; i < 10; i++) {
            struct usbdevfs_urb *c = nullptr;

            if (ioctl(ctx->fd, USBDEVFS_REAPURBNDELAY, &c) == 0 && c != nullptr) {
                LOGW("drainAllUrbs: flushed stale completion %p", c);

                drained++;
            } else {
                break;
            }
        }
    }

    ctx->urbsInFlight = 0;

    ctx->submitIdx = 0;

    ctx->reapIdx = 0;

    LOGI("drainAllUrbs: drained %d/%d, ring reset", drained, initialCount);

    return drained;
}

void submitPcmToUrbs(UsbAudioContext *ctx, const uint8_t *pcmData, int totalBytes);

extern "C" {
JNIEXPORT jlong JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeUsbAudioCreate(
        JNIEnv *, jobject, jint fd, jint ifId, jint epOut, jint epFb,
        jint rate, jint ch, jint bits, jint maxPkt) {
    LOGI("Create: fd=%d ep=0x%02x rate=%d ch=%d bits=%d maxPkt=%d",
         fd, epOut, rate, ch, bits, maxPkt);

    auto *ctx = new(std::nothrow) UsbAudioContext();

    if (!ctx) return 0;

    ctx->fd = fd;

    ctx->interfaceId = ifId;

    ctx->endpointOut = epOut;

    ctx->endpointFeedback = epFb;

    ctx->sampleRate = rate;

    ctx->channelCount = ch;

    ctx->bitDepth = bits;

    ctx->bytesPerSample = bits / 8;

    ctx->bytesPerFrame = (bits / 8) * ch;

    ctx->maxPacketSize = maxPkt;

    ctx->running.store(false);

    constexpr int32_t INITIAL_BUF_CAP = 65536;

    ctx->transferBuffer = (uint8_t *)malloc(INITIAL_BUF_CAP);

    ctx->transferBufferCapacity = ctx->transferBuffer ? INITIAL_BUF_CAP : 0;

    ctx->mergeBuffer = (uint8_t *)malloc(INITIAL_BUF_CAP);

    ctx->mergeBufferCapacity = ctx->mergeBuffer ? INITIAL_BUF_CAP : 0;

    ctx->framesWritten = 0;

    ctx->writeCallCount = 0;

    ctx->lastWriteUs = 0;

    ctx->lastSamplesCount = 0;

    ctx->interfaceClaimed = true;

    ctx->submitIdx = 0;

    ctx->reapIdx = 0;

    ctx->urbsInFlight = 0;

    ctx->urbsSubmitted = 0;

    ctx->ringAllocated = false;

    ctx->frameAccumulator = 0.0;

#ifdef USBDEVFS_GET_SPEED
    int speed = ioctl(fd, USBDEVFS_GET_SPEED, NULL);
#else
    int speed = ioctl(fd, _IO('U', 31), NULL);
#endif
    bool fullSpeed = (speed == 1 /* low */ || speed == 2 /* full */);
    ctx->packetsPerSec = fullSpeed ? 1000 : 8000;
    ctx->maxInFlight = fullSpeed ? (USB_AUDIO_NUM_URBS / 4) : USB_AUDIO_NUM_URBS;
    LOGI("Create: usb speed=%d -> %d packets/s, %d URBs in flight", speed, ctx->packetsPerSec, ctx->maxInFlight);

    ctx->calibratedFpmf = rate / (double)ctx->packetsPerSec;

    ctx->residualBytes = 0;

    memset(ctx->residualBuffer, 0, sizeof(ctx->residualBuffer));

    memset(ctx->ring, 0, sizeof(ctx->ring));

    ctx->feedbackUrb = nullptr;

    ctx->feedbackInFlight = false;

    memset(ctx->feedbackBuffer, 0, sizeof(ctx->feedbackBuffer));

    if (!allocRing(ctx)) {
        free(ctx->transferBuffer);
        free(ctx->mergeBuffer);
        delete ctx;

        return 0;
    }

    if (!allocFeedbackUrb(ctx)) {
        freeRing(ctx);
        free(ctx->transferBuffer);
        free(ctx->mergeBuffer);
        delete ctx;

        return 0;
    }

    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jboolean JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeUsbAudioSetAltSetting(
        JNIEnv *, jobject, jlong h, jint alt) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return JNI_FALSE;

    struct usbdevfs_setinterface si = {};

    si.interface = (unsigned)ctx->interfaceId;

    si.altsetting = (unsigned)alt;

    int r = ioctl(ctx->fd, USBDEVFS_SETINTERFACE, &si);

    LOGI("setAlt(%d,%d): ret=%d errno=%d", ctx->interfaceId, alt, r, errno);

    return r >= 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeUsbAudioSetSampleRate(
        JNIEnv *, jobject, jlong h, jint rate, jint csId) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return JNI_FALSE;

    ctx->sampleRate = rate;

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeUsbAudioStart(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return JNI_FALSE;

    ctx->running.store(true);

    ctx->framesWritten = 0;

    ctx->writeCallCount = 0;

    ctx->lastWriteUs = 0;

    ctx->lastSamplesCount = 0;

    ctx->submitIdx = 0;

    ctx->reapIdx = 0;

    ctx->urbsInFlight = 0;

    ctx->urbsSubmitted = 0;

    ctx->frameAccumulator = 0.0;

    ctx->residualBytes = 0;

    double nominalFpmf = ctx->sampleRate / (double)ctx->packetsPerSec;

    ctx->calibratedFpmf = nominalFpmf;

    if (ctx->endpointFeedback > 0) {
        double fb = readFeedback(ctx->fd, ctx->endpointFeedback, ctx->packetsPerSec == 1000);

        if (fb > nominalFpmf * 0.98 && fb < nominalFpmf * 1.02) {
            ctx->calibratedFpmf = fb;

            LOGI("Start: initial feedback=%.4f fpmf (%.1f Hz), nominal=%.4f (%.1f Hz)",
                 fb, fb * ctx->packetsPerSec, nominalFpmf, nominalFpmf * ctx->packetsPerSec);
        } else if (fb > 0) {
            LOGW("Start: ignoring out-of-range initial feedback=%.4f fpmf (%.1f Hz), using nominal=%.4f (%.1f Hz)",
                 fb, fb * ctx->packetsPerSec, nominalFpmf, nominalFpmf * ctx->packetsPerSec);
        } else {
            LOGW("Start: feedback not responding, using nominal %.4f fpmf", nominalFpmf);
        }

        submitFeedbackUrb(ctx);
    }

    LOGI("Start: rate=%d ch=%d bits=%d ring=%d slots×%dpkt fpmf=%.4f feedback=%s",
         ctx->sampleRate, ctx->channelCount, ctx->bitDepth,
         USB_AUDIO_NUM_URBS, USB_AUDIO_PACKETS_PER_URB,
         ctx->calibratedFpmf,
         ctx->feedbackInFlight ? "continuous" : "one-shot");

    return JNI_TRUE;
}

JNIEXPORT void JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeUsbAudioWrite(
        JNIEnv *env, jobject, jlong h, jfloatArray pcm) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx || !ctx->running.load()) return;

    jint totalSamples = env->GetArrayLength(pcm);

    if (totalSamples <= 0) return;

    int totalFrames = totalSamples / ctx->channelCount;

    int totalBytes = totalSamples * ctx->bytesPerSample;

    if (!ctx->transferBuffer || ctx->transferBufferCapacity < totalBytes) {
        free(ctx->transferBuffer);

        ctx->transferBuffer = (uint8_t *)malloc(totalBytes);

        ctx->transferBufferCapacity = totalBytes;
    }

    struct timespec writeStart, writeEnd;

    clock_gettime(CLOCK_MONOTONIC, &writeStart);

    ctx->writeCallCount++;

    jfloat *f = env->GetFloatArrayElements(pcm, nullptr);

    if (!f) return;

    switch (ctx->bitDepth) {
        case 16: convertFloatToInt16(f, ctx->transferBuffer, totalSamples); break;

        case 24: convertFloatToInt24(f, ctx->transferBuffer, totalSamples); break;

        case 32: convertFloatToInt32(f, ctx->transferBuffer, totalSamples); break;

        default: env->ReleaseFloatArrayElements(pcm, f, JNI_ABORT); return;
    }

    env->ReleaseFloatArrayElements(pcm, f, JNI_ABORT);

    submitPcmToUrbs(ctx, ctx->transferBuffer, totalBytes);

    int64_t prevSec = ctx->sampleRate > 0 ? (ctx->framesWritten / ctx->sampleRate) : 0;

    ctx->framesWritten += totalFrames;

    int64_t curSec = ctx->sampleRate > 0 ? (ctx->framesWritten / ctx->sampleRate) : 0;

    clock_gettime(CLOCK_MONOTONIC, &writeEnd);

    long writeUs = (writeEnd.tv_sec - writeStart.tv_sec) * 1000000L +
                   (writeEnd.tv_nsec - writeStart.tv_nsec) / 1000L;

    ctx->lastWriteUs = writeUs;

    ctx->lastSamplesCount = totalSamples;

    if (writeUs > 10000 || ctx->writeCallCount % 100 == 0 || ctx->writeCallCount == 1) {
        LOGI("nativeWrite #%lld: %d samples, %ldus (%.1fms), inflight=%d",
             (long long)ctx->writeCallCount, totalSamples, writeUs, writeUs / 1000.0, ctx->urbsInFlight);
    }

    if (curSec != prevSec || ctx->writeCallCount == 1) {
        LOGI("Write: %lld frames (~%.0f sec) inflight=%d fpmf=%.4f (%.1f Hz)",
             (long long)ctx->framesWritten, (double)ctx->framesWritten/ctx->sampleRate,
             ctx->urbsInFlight, ctx->calibratedFpmf, ctx->calibratedFpmf * ctx->packetsPerSec);
    }
}

JNIEXPORT void JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeUsbAudioStop(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return;

    ctx->running.store(false);

    LOGI("Stop: %lld frames, %d URBs in flight",
         (long long)ctx->framesWritten, ctx->urbsInFlight);
}

JNIEXPORT void JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeFlush(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return;

    ctx->frameAccumulator = 0.0;

    ctx->residualBytes = 0;

    ctx->framesWritten = 0;

    LOGI("Flush: frameAccumulator, residual, and framesWritten reset");
}

JNIEXPORT jint JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeDrainUrbs(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return 0;

    ctx->running.store(false);

    return drainAllUrbs(ctx);
}

JNIEXPORT void JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeUsbAudioDestroy(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return;

    ctx->running.store(false);

    if (ctx->urbsInFlight > 0 || ctx->feedbackInFlight) {
        drainAllUrbs(ctx);
    }

    freeRing(ctx);

    free(ctx->feedbackUrb);

    ctx->feedbackUrb = nullptr;

    free(ctx->transferBuffer);

    ctx->transferBuffer = nullptr;

    free(ctx->mergeBuffer);

    ctx->mergeBuffer = nullptr;

    LOGI("Destroy: %lld frames total", (long long)ctx->framesWritten);

    delete ctx;
}

JNIEXPORT jboolean JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeIsRunning(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return JNI_FALSE;

    return ctx->running.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeGetFramesWritten(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return 0;

    return (jlong)ctx->framesWritten;
}

JNIEXPORT jlong JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeGetWriteCallCount(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return 0;

    return (jlong)ctx->writeCallCount;
}

JNIEXPORT jlong JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeGetLastWriteUs(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return 0;

    return (jlong)ctx->lastWriteUs;
}

JNIEXPORT jint JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeGetLastSamplesCount(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return 0;

    return (jint)ctx->lastSamplesCount;
}

JNIEXPORT jint JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeGetUrbsInFlight(
        JNIEnv *, jobject, jlong h) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx) return 0;

    return (jint)ctx->urbsInFlight;
}

JNIEXPORT jint JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeUsbReset(
        JNIEnv *, jclass, jint fd) {
    LOGI("USBDEVFS_RESET fd=%d", fd);

    int ret = ioctl(fd, USBDEVFS_RESET, 0);

    if (ret < 0) { LOGE("RESET FAILED errno=%d", errno); return ret; }

    LOGI("RESET OK, claiming interfaces...");

    struct usbdevfs_ioctl cmd = {}; cmd.ifno = 0; cmd.ioctl_code = USBDEVFS_DISCONNECT;

    ioctl(fd, USBDEVFS_IOCTL, &cmd);

    int i0 = 0; ioctl(fd, USBDEVFS_CLAIMINTERFACE, &i0);

    cmd.ifno = 1; ioctl(fd, USBDEVFS_IOCTL, &cmd);

    int i1 = 1; ioctl(fd, USBDEVFS_CLAIMINTERFACE, &i1);

    struct usbdevfs_setinterface si = {}; si.interface = 1; si.altsetting = 0;

    ioctl(fd, USBDEVFS_SETINTERFACE, &si);

    return 0;
}

JNIEXPORT jint JNICALL
Java_com_example_core_audio_usb_UsbAudioStream_nativeUsbSpeed(
        JNIEnv *, jclass, jint fd) {
#ifdef USBDEVFS_GET_SPEED
    return ioctl(fd, USBDEVFS_GET_SPEED, NULL);
#else
    return ioctl(fd, _IO('U', 31), NULL);
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_example_core_audio_usb_UsbAudioDevice_nativeReattachKernelDriver(
        JNIEnv *, jobject, jint fd, jint interfaceId) {
    struct usbdevfs_ioctl cmd = {};
    cmd.ifno = interfaceId;
    cmd.ioctl_code = USBDEVFS_CONNECT;
    cmd.data = nullptr;
    int ret = ioctl(fd, USBDEVFS_IOCTL, &cmd);
    LOGI("USBDEVFS_CONNECT iface=%d ret=%d errno=%d", interfaceId, ret, errno);
    return ret >= 0 ? JNI_TRUE : JNI_FALSE;
}
}

void padInt16ToInt32(const uint8_t *src, uint8_t *dst, int numSamples) {
    auto *out = reinterpret_cast<int32_t *>(dst);

    auto *in16 = reinterpret_cast<const int16_t *>(src);

    for (int i = 0; i < numSamples; i++) out[i] = (int32_t)in16[i] << 16;
}

void padInt16ToInt24(const uint8_t *src, uint8_t *dst, int numSamples) {
    auto *in16 = reinterpret_cast<const int16_t *>(src);

    for (int i = 0; i < numSamples; i++) {
        int32_t val = (int32_t)in16[i] << 8;

        dst[i * 3 + 0] = static_cast<uint8_t>(val & 0xFF);

        dst[i * 3 + 1] = static_cast<uint8_t>((val >> 8) & 0xFF);

        dst[i * 3 + 2] = static_cast<uint8_t>((val >> 16) & 0xFF);
    }
}

void padInt24ToInt16(const uint8_t *src, uint8_t *dst, int numSamples) {
    auto *out16 = reinterpret_cast<int16_t *>(dst);

    for (int i = 0; i < numSamples; i++) {
        out16[i] = static_cast<int16_t>(src[i * 3 + 1] | (src[i * 3 + 2] << 8));
    }
}

void shiftInt32From24(const uint8_t *src, uint8_t *dst, int numSamples) {
    auto *out = reinterpret_cast<int32_t *>(dst);

    auto *in32 = reinterpret_cast<const int32_t *>(src);

    for (int i = 0; i < numSamples; i++) out[i] = in32[i] << 8;
}

void padInt32ToInt24(const uint8_t *src, uint8_t *dst, int numSamples) {
    auto *in32 = reinterpret_cast<const int32_t *>(src);

    for (int i = 0; i < numSamples; i++) {
        int32_t val = in32[i] >> 8;

        dst[i * 3 + 0] = static_cast<uint8_t>(val & 0xFF);

        dst[i * 3 + 1] = static_cast<uint8_t>((val >> 8) & 0xFF);

        dst[i * 3 + 2] = static_cast<uint8_t>((val >> 16) & 0xFF);
    }
}

void padInt32ToInt16(const uint8_t *src, uint8_t *dst, int numSamples) {
    auto *out16 = reinterpret_cast<int16_t *>(dst);

    auto *in32 = reinterpret_cast<const int32_t *>(src);

    for (int i = 0; i < numSamples; i++) {
        out16[i] = static_cast<int16_t>(in32[i] >> 16);
    }
}

void padInt24ToInt32(const uint8_t *src, uint8_t *dst, int numSamples) {
    auto *out = reinterpret_cast<int32_t *>(dst);
    for (int i = 0; i < numSamples; i++) {
        int32_t s = src[i * 3] | (src[i * 3 + 1] << 8) | (src[i * 3 + 2] << 16);
        if (s & 0x800000) s |= 0xFF000000;
        out[i] = s << 8;
    }
}

void submitPcmToUrbs(UsbAudioContext *ctx, const uint8_t *pcmData, int totalBytes) {
    const uint8_t *data = pcmData;

    int dataLen = totalBytes;

    if (ctx->residualBytes > 0 && totalBytes > 0) {
        dataLen = ctx->residualBytes + totalBytes;

        if (!ctx->mergeBuffer || ctx->mergeBufferCapacity < dataLen) {
            free(ctx->mergeBuffer);

            ctx->mergeBuffer = (uint8_t *)malloc(dataLen + 4096);

            ctx->mergeBufferCapacity = dataLen + 4096;
        }

        if (ctx->mergeBuffer) {
            memcpy(ctx->mergeBuffer, ctx->residualBuffer, ctx->residualBytes);

            memcpy(ctx->mergeBuffer + ctx->residualBytes, pcmData, totalBytes);

            data = ctx->mergeBuffer;
        }

        ctx->residualBytes = 0;
    }

    int offset = 0;

    double fpmf = ctx->calibratedFpmf;

    while (offset < dataLen && ctx->running.load()) {
        int pktSizes[USB_AUDIO_PACKETS_PER_URB];

        int numPackets = 0;

        int urbBytes = 0;

        for (int p = 0; p < USB_AUDIO_PACKETS_PER_URB; p++) {
            int remaining = dataLen - offset - urbBytes;

            if (remaining <= 0) break;

            ctx->frameAccumulator += fpmf;

            int frames = (int)ctx->frameAccumulator;

            ctx->frameAccumulator -= frames;

            int b = frames * ctx->bytesPerFrame;

            if (b > remaining) {
                break;
            }

            pktSizes[p] = b;

            urbBytes += b;

            numPackets++;
        }

        if (numPackets <= 0 || urbBytes <= 0) break;

        if (numPackets < USB_AUDIO_PACKETS_PER_URB) {
            int leftover = dataLen - offset;

            if (leftover > 0 && leftover < (int)sizeof(ctx->residualBuffer)) {
                memcpy(ctx->residualBuffer, data + offset, leftover);

                ctx->residualBytes = leftover;
            }

            break;
        }

        if (ctx->urbsInFlight >= ctx->maxInFlight) {
            int result = reapOldestUrb(ctx, 200);

            if (result == -2) {
                LOGE("submitPcmToUrbs: reap timeout, inflight=%d", ctx->urbsInFlight);

                drainAllUrbs(ctx);

                ctx->running.store(false);

                return;
            } else if (result < 0) {
                ctx->running.store(false);

                return;
            }
        }

        UrbSlot *slot = &ctx->ring[ctx->submitIdx];

        memcpy(slot->buffer, data + offset, urbBytes);

        if (ctx->urbsSubmitted > ctx->maxInFlight) {
            while (ctx->urbsInFlight > 0) {
                struct usbdevfs_urb *done = nullptr;
                if (ioctl(ctx->fd, USBDEVFS_REAPURBNDELAY, &done) != 0 || done == nullptr) break;
                if (done == ctx->feedbackUrb) { handleFeedbackCompletion(ctx); continue; }
                if (done->status != 0 || done->error_count > 0) {
                    static int64_t g_urbErrorsFast = 0;
                    if (g_urbErrorsFast++ % 50 == 0) {
                        LOGW("URB error #%lld: status=%d error_count=%d pkts=%d",
                             (long long)g_urbErrorsFast, done->status, done->error_count, done->number_of_packets);
                    }
                }
                ctx->reapIdx = (ctx->reapIdx + 1) % USB_AUDIO_NUM_URBS;
                ctx->urbsInFlight--;
            }
            if (ctx->urbsInFlight == 0) {
                static int64_t g_underruns = 0;
                LOGW("Underrun #%lld after %lld URBs: USB queue ran empty",
                     (long long)++g_underruns, (long long)ctx->urbsSubmitted);
            }
        }

        if (submitRingUrb(ctx, pktSizes, numPackets, urbBytes) < 0) {
            LOGE("submitPcmToUrbs: submit failed, stopping stream");

            ctx->running.store(false);

            return;
        }

        offset += urbBytes;
    }

    int leftover = dataLen - offset;

    if (leftover > 0 && leftover < (int)sizeof(ctx->residualBuffer) && ctx->residualBytes == 0) {
        memcpy(ctx->residualBuffer, data + offset, leftover);

        ctx->residualBytes = leftover;
    }
}

extern "C" {
JNIEXPORT void JNICALL

Java_com_example_core_audio_usb_UsbAudioStream_nativeUsbAudioWriteRaw(
        JNIEnv *env, jobject, jlong h, jbyteArray pcm, jint inputBitDepth) {
    auto *ctx = reinterpret_cast<UsbAudioContext *>(h);

    if (!ctx || !ctx->running.load()) return;

    jint inputBytes = env->GetArrayLength(pcm);

    if (inputBytes <= 0) return;

    int inputBps = inputBitDepth / 8;

    int totalSamples = inputBytes / inputBps;

    int totalFrames = totalSamples / ctx->channelCount;

    int outputBytes = totalSamples * ctx->bytesPerSample;

    if (!ctx->transferBuffer || ctx->transferBufferCapacity < outputBytes) {
        free(ctx->transferBuffer);

        ctx->transferBuffer = (uint8_t *)malloc(outputBytes);

        ctx->transferBufferCapacity = outputBytes;
    }

    ctx->writeCallCount++;

    struct timespec writeStart, writeEnd;

    clock_gettime(CLOCK_MONOTONIC, &writeStart);

    jbyte *rawData = env->GetByteArrayElements(pcm, nullptr);

    if (!rawData) return;

    if (inputBitDepth == ctx->bitDepth) {
        memcpy(ctx->transferBuffer, rawData, inputBytes);
    } else if (inputBitDepth == 16 && ctx->bitDepth == 24) {
        padInt16ToInt24((const uint8_t *)rawData, ctx->transferBuffer, totalSamples);
    } else if (inputBitDepth == 16 && ctx->bitDepth == 32) {
        padInt16ToInt32((const uint8_t *)rawData, ctx->transferBuffer, totalSamples);
    } else if (inputBitDepth == 24 && ctx->bitDepth == 16) {
        padInt24ToInt16((const uint8_t *)rawData, ctx->transferBuffer, totalSamples);
    } else if (inputBitDepth == 24 && ctx->bitDepth == 32) {
        padInt24ToInt32((const uint8_t *)rawData, ctx->transferBuffer, totalSamples);
    } else if (inputBitDepth == 32 && ctx->bitDepth == 16) {
        padInt32ToInt16((const uint8_t *)rawData, ctx->transferBuffer, totalSamples);
    } else if (inputBitDepth == 32 && ctx->bitDepth == 24) {
        padInt32ToInt24((const uint8_t *)rawData, ctx->transferBuffer, totalSamples);
    } else {
        LOGW("WriteRaw: unoptimized bit-depth copy %d → %d", inputBitDepth, ctx->bitDepth);

        memcpy(ctx->transferBuffer, rawData, inputBytes < outputBytes ? inputBytes : outputBytes);
    }

    env->ReleaseByteArrayElements(pcm, rawData, JNI_ABORT);

    submitPcmToUrbs(ctx, ctx->transferBuffer, outputBytes);

    int64_t prevSec = ctx->sampleRate > 0 ? (ctx->framesWritten / ctx->sampleRate) : 0;

    ctx->framesWritten += totalFrames;

    int64_t curSec = ctx->sampleRate > 0 ? (ctx->framesWritten / ctx->sampleRate) : 0;

    clock_gettime(CLOCK_MONOTONIC, &writeEnd);

    long writeUs = (writeEnd.tv_sec - writeStart.tv_sec) * 1000000L +
                   (writeEnd.tv_nsec - writeStart.tv_nsec) / 1000L;

    ctx->lastWriteUs = writeUs;

    ctx->lastSamplesCount = totalSamples;

    if (writeUs > 10000 || ctx->writeCallCount % 100 == 0 || ctx->writeCallCount == 1) {
        LOGI("nativeWrite #%lld: %d samples, %ldus (%.1fms), inflight=%d",
             (long long)ctx->writeCallCount, totalSamples, writeUs, writeUs / 1000.0, ctx->urbsInFlight);
    }

    if (curSec != prevSec || ctx->writeCallCount == 1) {
        LOGI("Write: %lld frames (~%.0f sec) inflight=%d fpmf=%.4f (%.1f Hz)",
             (long long)ctx->framesWritten, (double)ctx->framesWritten/ctx->sampleRate,
             ctx->urbsInFlight, ctx->calibratedFpmf, ctx->calibratedFpmf * ctx->packetsPerSec);
    }
}
}

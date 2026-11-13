#include "terminal_logger.h"
#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <sys/types.h>
#include <time.h>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <deque>
#include <mutex>
#include <algorithm>

#define TAG "SRMusicTerminalNative"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)

static constexpr size_t MAX_LOG_ENTRIES = 250;

static std::mutex g_logMutex;

static std::deque<std::string> g_logs;

static std::deque<std::string> g_diagLogs;

static std::string g_lastRawMessage;

static uint64_t g_logGeneration = 0;

static void format_timestamp(char* out, size_t max_len) {
    struct timespec ts;

    clock_gettime(CLOCK_REALTIME, &ts);

    struct tm tm_buf;

    localtime_r(&ts.tv_sec, &tm_buf);

    snprintf(out, max_len, "%02d:%02d:%02d",
             tm_buf.tm_hour, tm_buf.tm_min, tm_buf.tm_sec);
}

static void format_diag_timestamp(char* out, size_t max_len) {
    struct timespec ts;

    clock_gettime(CLOCK_REALTIME, &ts);

    struct tm tm_buf;

    localtime_r(&ts.tv_sec, &tm_buf);

    snprintf(out, max_len, "[%02d:%02d:%02d]",
             tm_buf.tm_hour, tm_buf.tm_min, tm_buf.tm_sec);
}

extern "C" {
void terminal_log(const char* msg) {
    if (!msg) return;

    std::string s(msg);

    size_t first = s.find_first_not_of(" \t\n\r");

    if (first == std::string::npos) return;

    size_t last = s.find_last_not_of(" \t\n\r");

    std::string trimmed = s.substr(first, (last - first + 1));

    if (trimmed.find("Decoded Stream:") != std::string::npos) {
        return;
    }

    std::lock_guard<std::mutex> lock(g_logMutex);

    if (trimmed == g_lastRawMessage && trimmed.find("Selected track") == std::string::npos) {
        return;
    }

    g_lastRawMessage = trimmed;

    std::string entry;

    if (trimmed.rfind("[", 0) == 0 || trimmed.rfind("---", 0) == 0 || trimmed.rfind("===", 0) == 0) {
        entry = trimmed;
    } else {
        char timeBuf[32];

        format_timestamp(timeBuf, sizeof(timeBuf));

        entry = "[" + std::string(timeBuf) + "] " + trimmed;
    }

    if (g_logs.size() >= MAX_LOG_ENTRIES) {
        g_logs.pop_front();
    }

    g_logs.push_back(std::move(entry));

    g_logGeneration++;
}

void terminal_log_diag_raw(const char* line) {
    if (!line) return;

    std::string s(line);

    std::lock_guard<std::mutex> lock(g_logMutex);

    bool isNativeWrite = s.find("UsbAudioOutput: nativeWrite #") != std::string::npos;

    bool isWriteFrames = s.find("UsbAudioOutput: Write:") != std::string::npos;

    if (isNativeWrite) {
        bool replaced = false;

        if (!g_diagLogs.empty()) {
            for (int i = static_cast<int>(g_diagLogs.size()) - 1;

                 i >= 0 && i >= static_cast<int>(g_diagLogs.size()) - 3;

                 --i) {
                if (g_diagLogs[i].find("UsbAudioOutput: nativeWrite #") != std::string::npos) {
                    g_diagLogs[i] = s;

                    replaced = true;

                    break;
                }
            }
        }

        if (!replaced) {
            if (g_diagLogs.size() >= MAX_LOG_ENTRIES) g_diagLogs.pop_front();

            g_diagLogs.push_back(s);
        }
    } else if (isWriteFrames) {
        bool replaced = false;

        if (!g_diagLogs.empty()) {
            for (int i = static_cast<int>(g_diagLogs.size()) - 1;

                 i >= 0 && i >= static_cast<int>(g_diagLogs.size()) - 3;

                 --i) {
                if (g_diagLogs[i].find("UsbAudioOutput: Write:") != std::string::npos) {
                    g_diagLogs[i] = s;

                    replaced = true;

                    break;
                }
            }
        }

        if (!replaced) {
            if (g_diagLogs.size() >= MAX_LOG_ENTRIES) g_diagLogs.pop_front();

            g_diagLogs.push_back(s);
        }
    } else {
        if (g_diagLogs.size() >= MAX_LOG_ENTRIES) {
            g_diagLogs.pop_front();
        }

        g_diagLogs.push_back(s);
    }

    g_logGeneration++;
}

void terminal_log_diag(const char* tag, const char* priority, const char* msg) {
    if (!tag || !priority || !msg) return;

    char timeBuf[32];

    format_diag_timestamp(timeBuf, sizeof(timeBuf));

    pid_t pid = getpid();

    pid_t tid = gettid();

    char line[512];

    if (strcmp(tag, "SRMusicAudioDiag") == 0) {
        snprintf(line, sizeof(line), "%s %5d %5d %s : %s",
                 timeBuf, (int)pid, (int)tid, priority, msg);
    } else {
        snprintf(line, sizeof(line), "%s %5d %5d %s %s: %s",
                 timeBuf, (int)pid, (int)tid, priority, tag, msg);
    }

    terminal_log_diag_raw(line);
}

void terminal_clear(void) {
    std::lock_guard<std::mutex> lock(g_logMutex);

    g_logs.clear();

    g_diagLogs.clear();

    g_lastRawMessage.clear();

    g_logGeneration++;
}

static jobjectArray convertDequeToJObjectArray(JNIEnv *env, const std::deque<std::string>& deq) {
    jclass strCls = env->FindClass("java/lang/String");

    if (!strCls) return nullptr;

    jsize size = static_cast<jsize>(deq.size());

    jobjectArray arr = env->NewObjectArray(size, strCls, nullptr);

    if (!arr) {
        env->DeleteLocalRef(strCls);
        return nullptr;
    }

    for (jsize i = 0; i < size; ++i) {
        jstring js = env->NewStringUTF(deq[i].c_str());

        if (js) {
            env->SetObjectArrayElement(arr, i, js);
            env->DeleteLocalRef(js);
        }
    }

    env->DeleteLocalRef(strCls);

    return arr;
}

JNIEXPORT void JNICALL

Java_com_example_core_audio_AudioTerminalLogger_nativeLog(
        JNIEnv *env, jclass, jstring msg) {
    if (!msg) return;

    const char *chars = env->GetStringUTFChars(msg, nullptr);

    if (chars) {
        terminal_log(chars);

        env->ReleaseStringUTFChars(msg, chars);
    }
}

JNIEXPORT void JNICALL

Java_com_example_core_audio_AudioTerminalLogger_nativeLogDiagRaw(
        JNIEnv *env, jclass, jstring line) {
    if (!line) return;

    const char *chars = env->GetStringUTFChars(line, nullptr);

    if (chars) {
        terminal_log_diag_raw(chars);

        env->ReleaseStringUTFChars(line, chars);
    }
}

JNIEXPORT void JNICALL

Java_com_example_core_audio_AudioTerminalLogger_nativeLogDiag(
        JNIEnv *env, jclass, jstring tag, jstring priority, jstring msg) {
    if (!tag || !priority || !msg) return;

    const char *tagChars = env->GetStringUTFChars(tag, nullptr);

    const char *prioChars = env->GetStringUTFChars(priority, nullptr);

    const char *msgChars = env->GetStringUTFChars(msg, nullptr);

    if (tagChars && prioChars && msgChars) {
        terminal_log_diag(tagChars, prioChars, msgChars);
    }

    if (msgChars) env->ReleaseStringUTFChars(msg, msgChars);

    if (prioChars) env->ReleaseStringUTFChars(priority, prioChars);

    if (tagChars) env->ReleaseStringUTFChars(tag, tagChars);
}

JNIEXPORT jobjectArray JNICALL

Java_com_example_core_audio_AudioTerminalLogger_nativeGetLogs(
        JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lock(g_logMutex);

    return convertDequeToJObjectArray(env, g_logs);
}

JNIEXPORT jobjectArray JNICALL

Java_com_example_core_audio_AudioTerminalLogger_nativeGetDiagLogs(
        JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lock(g_logMutex);

    return convertDequeToJObjectArray(env, g_diagLogs);
}

JNIEXPORT jlong JNICALL

Java_com_example_core_audio_AudioTerminalLogger_nativeGetGeneration(
        JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_logMutex);

    return static_cast<jlong>(g_logGeneration);
}

JNIEXPORT void JNICALL

Java_com_example_core_audio_AudioTerminalLogger_nativeClear(
        JNIEnv *, jclass) {
    terminal_clear();
}
}

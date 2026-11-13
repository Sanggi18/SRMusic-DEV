#ifndef SRMUSIC_TERMINAL_LOGGER_H

#define SRMUSIC_TERMINAL_LOGGER_H

#include <stddef.h>

#ifdef __cplusplus

extern "C" {
#endif

void terminal_log(const char* msg);

void terminal_log_diag(const char* tag, const char* priority, const char* msg);

void terminal_log_diag_raw(const char* line);

void terminal_clear(void);

#ifdef __cplusplus
}

#endif

#endif

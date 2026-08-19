#ifndef HWINFO_DAEMON_H
#define HWINFO_DAEMON_H

#include <stddef.h>

typedef struct hwinfo_ctx hwinfo_ctx;

/* Create a listening UNIX socket at sock_path. On success returns 0 and sets
 * *out; on failure returns -1. */
int hwinfo_create(hwinfo_ctx **out, const char *sock_path);

/* Run the accept loop until hwinfo_stop() is called. */
void hwinfo_run(hwinfo_ctx *ctx);

/* Ask the loop to exit; safe to call from a signal handler. */
void hwinfo_stop(hwinfo_ctx *ctx);

void hwinfo_destroy(hwinfo_ctx *ctx);

#endif
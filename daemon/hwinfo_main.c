#include <signal.h>

#include "../libhwinfo/hwinfo_daemon.h"

static hwinfo_ctx *g_ctx;

static void handle_signal(int sig)
{
    (void)sig;
    if (g_ctx)
        hwinfo_stop(g_ctx);
}

int main(int argc, char **argv)
{
    const char *sock_path = (argc > 1) ? argv[1] : "/data/local/tmp/hwinfo.sock";

    if (hwinfo_create(&g_ctx, sock_path) < 0)
        return 1;

    signal(SIGINT, handle_signal);
    signal(SIGTERM, handle_signal);
    signal(SIGPIPE, SIG_IGN);

    hwinfo_run(g_ctx);
    hwinfo_destroy(g_ctx);
    return 0;
}
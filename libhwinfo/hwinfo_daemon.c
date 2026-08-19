#include <errno.h>
#include <signal.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include "hwinfo_daemon.h"
#include "../common/socket_utils.h"

#define MAX_EVENTS 16
#define MAX_CLIENTS 8

struct hwinfo_ctx {
    int listen_fd;
    int epoll_fd;
    volatile bool running;
    char sock_path[sizeof(((struct sockaddr_un *)0)->sun_path)];
};

/* ---- JSON building ----------------------------------------------------- */

struct jbuf {
    char *data;
    size_t len;
    size_t cap;
};

static void jb_grow(struct jbuf *b, size_t need)
{
    if (b->len + need + 1 <= b->cap)
        return;
    size_t ncap = b->cap ? b->cap * 2 : 512;
    while (b->len + need + 1 > ncap)
        ncap *= 2;
    char *nd = realloc(b->data, ncap);
    if (!nd)
        return;
    b->data = nd;
    b->cap = ncap;
}

static void jb_put(struct jbuf *b, const char *s)
{
    size_t l = strlen(s);
    jb_grow(b, l);
    memcpy(b->data + b->len, s, l);
    b->len += l;
    b->data[b->len] = '\0';
}

static void jb_putf(struct jbuf *b, const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    va_list ap2;
    va_copy(ap2, ap);
    int n = vsnprintf(NULL, 0, fmt, ap);
    va_end(ap);
    if (n < 0) {
        va_end(ap2);
        return;
    }
    jb_grow(b, (size_t)n);
    vsnprintf(b->data + b->len, b->cap - b->len, fmt, ap2);
    va_end(ap2);
    b->len += (size_t)n;
}

static void jb_str(struct jbuf *b, const char *s)
{
    jb_put(b, "\"");
    for (const char *p = s; *p; p++) {
        switch (*p) {
        case '"':
        case '\\':
            jb_putf(b, "\\%c", *p);
            break;
        case '\n':
            jb_put(b, "\\n");
            break;
        case '\r':
            jb_put(b, "\\r");
            break;
        case '\t':
            jb_put(b, "\\t");
            break;
        default:
            if ((unsigned char)*p < 0x20)
                jb_putf(b, "\\u%04x", (unsigned char)*p);
            else
                jb_putf(b, "%c", *p);
        }
    }
    jb_put(b, "\"");
}

/* ---- data collection --------------------------------------------------- */

/* Run a command and capture stdout. Returns malloc'd buffer or NULL. */
static char *cmd_out(const char *cmd)
{
    FILE *fp = popen(cmd, "r");
    if (!fp)
        return NULL;
    size_t cap = 4096, len = 0;
    char *buf = malloc(cap);
    if (!buf) {
        pclose(fp);
        return NULL;
    }
    size_t n;
    while ((n = fread(buf + len, 1, cap - len - 1, fp)) > 0) {
        len += n;
        if (len + 1 >= cap) {
            cap *= 2;
            char *nb = realloc(buf, cap);
            if (!nb) {
                free(buf);
                pclose(fp);
                return NULL;
            }
            buf = nb;
        }
    }
    buf[len] = '\0';
    pclose(fp);
    return buf;
}

/* Find the line starting at 'key' in str, return trimmed value or ""
 * Only matches when key begins a line (leading whitespace allowed) so that
 * "voltage:" never matches "Max charging voltage: 0". */
static const char *line_value(const char *str, const char *key)
{
    static char tmp[512];
    size_t klen = strlen(key);
    const char *p = str;
    while (*p) {
        const char *eol = strchr(p, '\n');
        size_t span = eol ? (size_t)(eol - p) : strlen(p);
        const char *s = p;
        size_t off = 0;
        while (off < span && (s[off] == ' ' || s[off] == '\t'))
            off++;
        if (span - off >= klen && strncmp(s + off, key, klen) == 0) {
            const char *v = s + off + klen;
            while (*v == ' ' || *v == '\t' || *v == ':')
                v++;
            size_t i = 0;
            while (*v && *v != '\n' && i < sizeof(tmp) - 1) {
                tmp[i++] = *v;
                v++;
            }
            while (i > 0 && (tmp[i - 1] == ' ' || tmp[i - 1] == '\r'))
                i--;
            tmp[i] = '\0';
            return tmp;
        }
        p = eol ? eol + 1 : p + span;
        if (!eol)
            break;
    }
    tmp[0] = '\0';
    return tmp;
}

static int line_int(const char *str, const char *key, int def)
{
    const char *v = line_value(str, key);
    if (!v[0])
        return def;
    return atoi(v);
}

static bool line_bool(const char *str, const char *key, bool def)
{
    const char *v = line_value(str, key);
    if (!v[0])
        return def;
    return strcmp(v, "true") == 0 || strcmp(v, "1") == 0 ||
           strcasecmp(v, "yes") == 0;
}

static void collect_battery(struct jbuf *b)
{
    char *out = cmd_out("dumpsys battery 2>/dev/null");
    if (!out) {
        jb_put(b, "\"battery\":{\"level\":0,\"status\":0,\"present\":false,"
                  "\"technology\":\"\",\"voltage\":0}");
        return;
    }
    int level = line_int(out, "level:", -1);
    int status = line_int(out, "status:", -1);
    bool present = line_bool(out, "present:", false);
    const char *tech = line_value(out, "technology:");
    char tech_buf[64];
    snprintf(tech_buf, sizeof(tech_buf), "%s", tech);
    int volt = line_int(out, "voltage:", -1);
    jb_put(b, "\"battery\":{");
    jb_putf(b, "\"level\":%d,\"status\":%d,\"present\":%s,",
            level, status, present ? "true" : "false");
    jb_put(b, "\"technology\":");
    jb_str(b, tech_buf);
    jb_putf(b, ",\"voltage\":%d}", volt);
    free(out);
}

/* SSID may contain quotes/escapes from dumpsys; strip surrounding quotes. */
static void collect_wifi(struct jbuf *b)
{
    char *out = cmd_out("dumpsys wifi 2>/dev/null");
    bool enabled = false, connected = false;
    char ssid[128] = "", bssid[32] = "", freq[16] = "", strength[16] = "";
    if (out) {
        /* Scan every mWifiInfo record; skip records that are not the connected
         * network so a later <unknown ssid>/DISCONNECTED record never overrides
         * the real one. First usable record wins. */
        char *p = out;
        while (!ssid[0] && (p = strstr(p, "mWifiInfo"))) {
            char *eol = strchr(p, '\n');
            size_t span = eol ? (size_t)(eol - p) : strlen(p);
            char rec[1024];
            size_t rl = span < sizeof(rec) - 1 ? span : sizeof(rec) - 1;
            memcpy(rec, p, rl);
            rec[rl] = '\0';

            const char *s = strstr(rec, "SSID: ");
            if (!s) {
                p = eol ? eol + 1 : p + span;
                continue;
            }
            s += 6;
            if (*s == '"')
                s++;
            const char *eq = strchr(s, '"');
            size_t sl = eq ? (size_t)(eq - s) : strcspn(s, ",");
            if (sl >= sizeof(ssid))
                sl = sizeof(ssid) - 1;
            memcpy(ssid, s, sl);
            ssid[sl] = '\0';

            if (!ssid[0] || strcmp(ssid, "<unknown ssid>") == 0) {
                ssid[0] = '\0';
                p = eol ? eol + 1 : p + span;
                continue;
            }

            /* connected? */
            if (strstr(rec, "Supplicant state: COMPLETED") ||
                strstr(rec, "Supplicant state: CONNECTED"))
                connected = true;
            enabled = true;

            const char *bs = strstr(rec, "BSSID: ");
            if (bs) {
                bs += 7;
                size_t l = strcspn(bs, ",");
                if (l && l < sizeof(bssid))
                    memcpy(bssid, bs, l), bssid[l] = '\0';
                if (strcmp(bssid, "<none>") == 0)
                    bssid[0] = '\0';
            }
            const char *f = strstr(rec, "Frequency: ");
            if (f) {
                f += 11;
                size_t l = strcspn(f, "M");
                if (l && l < sizeof(freq))
                    memcpy(freq, f, l), freq[l] = '\0';
            }
            const char *r = strstr(rec, "RSSI: ");
            if (r) {
                r += 6;
                size_t l = strcspn(r, ",");
                if (l && l < sizeof(strength))
                    memcpy(strength, r, l), strength[l] = '\0';
            }
            p = eol ? eol + 1 : p + span;
        }
        free(out);
    }

    /* Skip RSSI: -127 (out of range) entries; keep real negative dBm values */
    int str_int = atoi(strength);
    if (str_int <= -100 || str_int > 200)
        strength[0] = '\0';

    jb_put(b, "\"wifi\":{");
    jb_putf(b, "\"enabled\":%s,\"connected\":%s,",
            enabled ? "true" : "false", connected ? "true" : "false");
    jb_put(b, "\"ssid\":");
    jb_str(b, ssid);
    jb_put(b, ",\"strength\":");
    if (strength[0])
        jb_putf(b, "%s", strength);
    else
        jb_put(b, "0");
    jb_put(b, ",\"frequency\":");
    if (freq[0] && strcmp(freq, "-1") != 0)
        jb_putf(b, "%s", freq);
    else
        jb_put(b, "2412");
    jb_put(b, ",\"bssid\":");
    jb_str(b, bssid);
    jb_put(b, "}");
}

static void collect_bluetooth(struct jbuf *b)
{
    char *out = cmd_out("dumpsys bluetooth_manager 2>/dev/null");
    bool enabled = false;
    char name[128] = "", addr[32] = "";
    if (out) {
        /* mEnable: true/false */
        const char *e = strstr(out, "mEnable:");
        if (e) {
            e += 8;
            while (*e == ' ' || *e == '\t')
                e++;
            if (strncmp(e, "true", 4) == 0)
                enabled = true;
        }
        const char *n = strstr(out, "name:");
        if (n) {
            n += 5;
            while (*n == ' ' || *n == '\t')
                n++;
            size_t l = strcspn(n, "\r\n");
            if (l && l < sizeof(name))
                memcpy(name, n, l), name[l] = '\0';
        }
        const char *a = strstr(out, "address:");
        if (a) {
            a += 8;
            while (*a == ' ' || *a == '\t')
                a++;
            size_t l = strcspn(a, "\r\n");
            if (l && l < sizeof(addr))
                memcpy(addr, a, l), addr[l] = '\0';
        }
        free(out);
    }
    jb_put(b, "\"bluetooth\":{");
    jb_putf(b, "\"enabled\":%s,", enabled ? "true" : "false");
    jb_put(b, "\"name\":");
    jb_str(b, name);
    jb_put(b, ",\"address\":");
    jb_str(b, addr);
    jb_put(b, "}");
}

static int count_cpus(void)
{
    FILE *f = fopen("/proc/cpuinfo", "r");
    if (!f)
        return 0;
    char line[256];
    int n = 0;
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "processor", 9) == 0)
            n++;
    }
    fclose(f);
    return n;
}

static void collect_device(struct jbuf *b)
{
    char man[128] = "", model[128] = "", dev[128] = "";
    char plat[128] = "", hw[128] = "";
    /* getprop returns exit code 1 for missing keys; capture stdout only */
    char *m = cmd_out("getprop ro.product.manufacturer 2>/dev/null");
    if (m) {
        m[strcspn(m, "\r\n")] = '\0';
        snprintf(man, sizeof(man), "%s", m);
        free(m);
    }
    char *mo = cmd_out("getprop ro.product.model 2>/dev/null");
    if (mo) {
        mo[strcspn(mo, "\r\n")] = '\0';
        snprintf(model, sizeof(model), "%s", mo);
        free(mo);
    }
    char *d = cmd_out("getprop ro.product.device 2>/dev/null");
    if (d) {
        d[strcspn(d, "\r\n")] = '\0';
        snprintf(dev, sizeof(dev), "%s", d);
        free(d);
    }
    char *p = cmd_out("getprop ro.board.platform 2>/dev/null");
    if (p) {
        p[strcspn(p, "\r\n")] = '\0';
        snprintf(plat, sizeof(plat), "%s", p);
        free(p);
    }
    char *h = cmd_out("getprop ro.hardware 2>/dev/null");
    if (h) {
        h[strcspn(h, "\r\n")] = '\0';
        snprintf(hw, sizeof(hw), "%s", h);
        free(h);
    }
    int cores = count_cpus();

    jb_put(b, "\"device\":{");
    jb_put(b, "\"manufacturer\":");
    jb_str(b, man);
    jb_put(b, ",\"model\":");
    jb_str(b, model);
    jb_put(b, ",\"device\":");
    jb_str(b, dev);
    jb_put(b, ",\"platform\":");
    jb_str(b, plat);
    jb_put(b, ",\"hardware\":");
    jb_str(b, hw);
    jb_putf(b, ",\"cpu_cores\":%d}", cores);
}

static int build_json(char *out, size_t out_size)
{
    struct jbuf b = {0};
    jb_put(&b, "{");
    collect_battery(&b);
    jb_put(&b, ",");
    collect_wifi(&b);
    jb_put(&b, ",");
    collect_bluetooth(&b);
    jb_put(&b, ",");
    collect_device(&b);
    jb_put(&b, "}\n");
    if (!b.data) {
        out[0] = '\0';
        return -1;
    }
    size_t l = b.len < out_size - 1 ? b.len : out_size - 1;
    memcpy(out, b.data, l);
    out[l] = '\0';
    free(b.data);
    return 0;
}

/* ---- socket handling --------------------------------------------------- */

static void handle_connection(hwinfo_ctx *ctx, int client_fd)
{
    (void)ctx;
    char json[8192];
    if (build_json(json, sizeof(json)) == 0)
        send_all(client_fd, json, strlen(json));
    close(client_fd);
}

int hwinfo_create(hwinfo_ctx **out, const char *sock_path)
{
    hwinfo_ctx *ctx = calloc(1, sizeof(*ctx));
    if (!ctx)
        return -1;
    ctx->listen_fd = -1;
    ctx->epoll_fd = -1;
    ctx->running = true;
    strncpy(ctx->sock_path, sock_path, sizeof(ctx->sock_path) - 1);

    unlink(sock_path);
    ctx->listen_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (ctx->listen_fd < 0) {
        perror("socket");
        hwinfo_destroy(ctx);
        return -1;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, sock_path, sizeof(addr.sun_path) - 1);

    if (bind(ctx->listen_fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        perror("bind");
        hwinfo_destroy(ctx);
        return -1;
    }
    if (listen(ctx->listen_fd, 4) < 0) {
        perror("listen");
        hwinfo_destroy(ctx);
        return -1;
    }

    ctx->epoll_fd = epoll_create1(0);
    if (ctx->epoll_fd < 0) {
        perror("epoll_create1");
        hwinfo_destroy(ctx);
        return -1;
    }
    struct epoll_event ev = { .events = EPOLLIN, .data.ptr = NULL };
    epoll_ctl(ctx->epoll_fd, EPOLL_CTL_ADD, ctx->listen_fd, &ev);

    fprintf(stderr, "hwinfo: listening on %s\n", sock_path);

    *out = ctx;
    return 0;
}

void hwinfo_run(hwinfo_ctx *ctx)
{
    struct epoll_event events[MAX_EVENTS];
    while (ctx->running) {
        int nfds = epoll_wait(ctx->epoll_fd, events, MAX_EVENTS, 1000);
        for (int i = 0; i < nfds; i++) {
            if (events[i].data.ptr != NULL)
                continue;
            /* accept one, handle synchronously, then rearm */
            int client_fd = accept(ctx->listen_fd, NULL, NULL);
            if (client_fd >= 0)
                handle_connection(ctx, client_fd);
        }
    }
}

void hwinfo_stop(hwinfo_ctx *ctx)
{
    if (ctx)
        ctx->running = false;
}

void hwinfo_destroy(hwinfo_ctx *ctx)
{
    if (!ctx)
        return;
    if (ctx->listen_fd >= 0)
        close(ctx->listen_fd);
    if (ctx->epoll_fd >= 0)
        close(ctx->epoll_fd);
    if (ctx->sock_path[0])
        unlink(ctx->sock_path);
    fprintf(stderr, "hwinfo: shutdown\n");
    free(ctx);
}
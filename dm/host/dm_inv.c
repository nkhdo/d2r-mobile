// The player's carried items for the bottom-screen Deck (see dm_inv.h). Addresses are offsets in the original 3.3.94012 image.
#include "dm_inv.h"
#include "dm.h"
#include <stdio.h>
#include <pthread.h>
#include <stdatomic.h>
#include <string.h>

static _Atomic int g_want, g_gen;
static pthread_mutex_t g_mu = PTHREAD_MUTEX_INITIALIZER;
static dm_inv_item g_items[DM_INV_MAX];
static int g_n;

void dm_inv_want(int on) { atomic_store(&g_want, on != 0); }

int dm_inv_get(dm_inv_item *out, int cap, int *gen) {
    pthread_mutex_lock(&g_mu);
    int n = g_n < cap ? g_n : cap;
    memcpy(out, g_items, sizeof *out * (size_t)n);
    if (gen) *gen = atomic_load(&g_gen);
    pthread_mutex_unlock(&g_mu);
    return n;
}

static void publish(const dm_inv_item *items, int n) {
    pthread_mutex_lock(&g_mu);
    if (n != g_n || memcmp(items, g_items, sizeof *items * (size_t)n)) {
        memcpy(g_items, items, sizeof *items * (size_t)n); g_n = n;
        atomic_fetch_add(&g_gen, 1);
        static int logged;
        if (logged < 20) {   // first 20 changes: one line to cross-check the reader on a device
            logged++; char line[640]; int k = snprintf(line, sizeof line, "Deck bag: %d items:", n);
            for (int i = 0; i < n && k < (int)sizeof line - 40; i++)
                k += snprintf(line + k, sizeof line - k, " %s/%c%d,%d %dx%d q%d%s%s", items[i].code, "BWL"[items[i].where % 3],
                              items[i].where == DM_INV_WORN ? items[i].loc : items[i].x, items[i].y, items[i].w, items[i].h, items[i].quality,
                              items[i].unid ? " unid" : "", items[i].row >= 0 ? (items[i].quality == 7 ? " u" : " s") : "");
            LOG("%s", line);
        }
    }
    pthread_mutex_unlock(&g_mu);
}

// cells occupied: item table record (stride 0x1c0) +0x11e width, +0x11f height; table by the item's version byte +0x1bd (as dm_cursor_item.c)
static void item_size(uint8_t *b, uint64_t item, int *w, int *h) {
    *w = *h = 1;
    uint8_t ver = *(uint8_t *)(item + 0x1bd); uint64_t t = ver >= 1 && ver <= 3 ? *(uint64_t *)(b + 0x2d64a18 + (uint64_t)ver * 16) : 0; uint32_t ci = *(uint32_t *)(item + 4);
    if (t && ci < *(uint32_t *)(t + 0x15c8)) { uint64_t rec = *(uint64_t *)(t + 0x15c0) + (uint64_t)ci * 0x1c0; *w = *(uint8_t *)(rec + 0x11e); *h = *(uint8_t *)(rec + 0x11f); }
}

void dm_inv_tick(uint8_t *b, uint64_t pl) {
    static dm_inv_item cur[DM_INV_MAX];
    if (!atomic_load(&g_want)) return;
    int n = 0;
    uint64_t inv = pl ? *(uint64_t *)(pl + 0x90) : 0, it = inv && *(uint32_t *)inv == 0x01020304 ? *(uint64_t *)(inv + 0x10) : 0;
    for (int k = 0; it && k < 1024 && n < DM_INV_MAX; k++) {
        uint64_t d = *(uint64_t *)(it + 0x10); if (!d) break;
        uint32_t mode = *(uint32_t *)(it + 0xc);
        int keep = (mode == 0 && *(uint8_t *)(d + 0x55) == 0) || mode == 1 || mode == 2;   // page 0 = the bag (3 cube and 4 stash are left for later)
        if (keep) {
            dm_inv_item *o = &cur[n++]; memset(o, 0, sizeof *o);
            o->id = *(uint32_t *)(it + 8);
            uint32_t code = ((uint32_t (*)(uint64_t))(b + 0x570260))(it);
            for (int i = 0; i < 3; i++) { char c = (char)(code >> (8 * i)); o->code[i] = c == ' ' ? 0 : c; }
            o->where = mode == 1 ? DM_INV_WORN : mode == 2 ? DM_INV_BELT : DM_INV_BAG;
            o->quality = (uint8_t)*(uint32_t *)d;
            o->unid = !(*(uint32_t *)(d + 0x18) & 0x10);
            o->loc = mode == 1 ? *(uint8_t *)(d + 0x54) : 0;
            o->row = o->quality == 5 || o->quality == 7 ? (int16_t)*(uint32_t *)(d + 0x34) : -1;
            uint64_t pp = *(uint64_t *)(it + 0x38);
            if (pp && mode != 1) { o->x = (int8_t)*(int32_t *)(pp + 0x10); o->y = (int8_t)*(int32_t *)(pp + 0x14); }
            int w = 1, h = 1; if (mode != 2) item_size(b, it, &w, &h);
            o->w = (int8_t)w; o->h = (int8_t)h;
        }
        it = *(uint64_t *)(d + 0xb0);
    }
    publish(cur, n);
}

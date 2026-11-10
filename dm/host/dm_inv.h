#pragma once
#include <stdint.h>
// The player's carried items for the bottom-screen Deck's Bag tab (dual-screen handhelds): bag, worn items and belt.
// Read on the server tick only while someone asked for it (dm_inv_want), so normal play pays nothing.
// Source: the server player's item chain, the same walk as dm_pouch.c / dm_actions.c: [player+0x90] inventory object (tag 0x01020304),
// first item [+0x10]; per item: +8 id, +0xc mode (0 stored, 1 worn, 2 belt), data [+0x10] (+0 quality, +0x18 bit 0x10 identified,
// +0x34 UniqueItems / SetItems row, +0x54 body location, +0x55 page, +0xb0 next item), cell [[item+0x38]+0x10 / +0x14].
enum { DM_INV_BAG = 0, DM_INV_WORN = 1, DM_INV_BELT = 2 };
#define DM_INV_MAX 128
typedef struct {
    uint32_t id;
    char code[4];          // item code, trailing spaces removed, 0-terminated ("hp1", "cap")
    uint8_t where;         // DM_INV_*
    uint8_t quality;       // 1 low, 2 normal, 3 superior, 4 magic, 5 set, 6 rare, 7 unique, 8 crafted
    uint8_t unid;          // 1 = not identified
    uint8_t loc;           // worn: body location (1 head, 2 neck, 3 torso, 4 right hand, 5 left hand, 6 right ring, 7 left ring, 8 belt, 9 feet, 10 gloves, 11 / 12 other weapon set)
    int8_t x, y, w, h;     // bag: top-left cell and size in cells; belt: x = slot (column = x % 4, row = x / 4)
    int16_t row;           // set / unique: the row in SetItems / UniqueItems (picture key "s<row>" / "u<row>"); -1 otherwise
} dm_inv_item;
void dm_inv_want(int on);   // any thread: the Bag tab is showing (1) or not (0)
int dm_inv_get(dm_inv_item *out, int cap, int *gen);   // any thread: copies the latest list, returns the count; gen changes whenever the list changes
void dm_inv_tick(uint8_t *main_base, uint64_t player);   // server tick (diag.c); player = 0 outside a run

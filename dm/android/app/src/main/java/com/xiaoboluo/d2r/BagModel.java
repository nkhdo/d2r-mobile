package com.xiaoboluo.d2r;

// The Deck's Bag tab without Android types (tested by BagModelTest): the host's item list (deck_jni.c nativeBag, from dm_inv.c)
// and where each item goes on the bottom screen. Layout, top to bottom: one row of worn-item slots, the 10 x 4 bag grid, the belt.
final class BagModel {
    static final int BAG = 0, WORN = 1, BELT = 2;
    static final int INTS = 5;   // ints per item in nativeBag's output
    static final int F_USE = 1, F_BELT = 2;   // item flags from the host: can be used (potion, scroll, tome, cube), can go in the belt
    static final int ACT_USE = 1, ACT_BELT = 2, ACT_SORT = 3;   // the item sheet's buttons
    static final int GRID_W = 10, GRID_H = 4, BELT_ROWS = 4;
    // worn slots left to right, by body location: head, neck, torso, right hand, left hand, right ring, left ring, belt, gloves, feet
    // (11 and 12 are the other weapon set: not shown)
    private static final int[] WORN_ORDER = { 1, 2, 3, 4, 5, 6, 7, 8, 10, 9 };

    static final class Item {
        int id, where, quality, loc, x, y, w, h, row, flags;
        boolean unid;
        String code;
        // key into the pack's deck/items.tsv: its own picture for set and unique items, else the item code's
        String pictureKey() { return row >= 0 && quality == 7 ? "u" + row : row >= 0 && quality == 5 ? "s" + row : code; }
    }

    static Item[] decode(int[] raw, int n) {
        n = Math.max(0, Math.min(n, raw.length / INTS));
        Item[] out = new Item[n];
        for (int i = 0; i < n; i++) {
            int o = i * INTS; Item it = new Item();
            it.id = raw[o];
            StringBuilder c = new StringBuilder();
            for (int k = 0; k < 3; k++) { int ch = raw[o + 1] >> (8 * k) & 0xff; if (ch != 0) c.append((char) ch); }
            it.code = c.toString();
            it.where = raw[o + 2] & 0xff; it.quality = raw[o + 2] >> 8 & 0xff; it.unid = (raw[o + 2] >> 16 & 0xff) != 0; it.loc = raw[o + 2] >>> 24;
            it.x = (byte) raw[o + 3]; it.y = (byte) (raw[o + 3] >> 8); it.w = raw[o + 3] >> 16 & 0xff; it.h = raw[o + 3] >>> 24;
            it.row = (short) raw[o + 4]; it.flags = raw[o + 4] >>> 16;
            out[i] = it;
        }
        return out;
    }

    // the buttons on the sheet for this item, in order. Worn items get none (equip and unequip come later, if at all)
    static int[] actions(Item it) {
        if (it.where == WORN) return new int[0];
        if (it.where == BELT) return (it.flags & F_USE) != 0 ? new int[] { ACT_USE } : new int[0];
        int[] out = new int[3]; int n = 0;
        if ((it.flags & F_USE) != 0) out[n++] = ACT_USE;
        if ((it.flags & F_BELT) != 0) out[n++] = ACT_BELT;
        out[n++] = ACT_SORT;
        return java.util.Arrays.copyOf(out, n);
    }

    static final class Layout {
        final float x0, y0, slot, gap, cell, gridX, gridY, beltCell, beltX, beltY;

        Layout(float x0, float y0, float w, float h, float gap) {
            this.x0 = x0; this.y0 = y0; this.gap = gap;
            slot = (w - (WORN_ORDER.length - 1) * gap) / WORN_ORDER.length;
            // height = worn row + gap + 4 bag cells + gap + 4 belt cells (0.7 of a bag cell)
            cell = Math.min(w / GRID_W, (h - slot - 2 * gap) / (GRID_H + BELT_ROWS * 0.7f));
            gridX = x0 + (w - GRID_W * cell) / 2; gridY = y0 + slot + gap;
            beltCell = cell * 0.7f; beltX = gridX; beltY = gridY + GRID_H * cell + gap;
        }

        float[] slotRect(int i) { float x = x0 + i * (slot + gap); return new float[] { x, y0, x + slot, y0 + slot }; }
        int slots() { return WORN_ORDER.length; }

        // left, top, right, bottom; null = not drawn
        float[] rect(Item it) {
            if (it.where == BAG) {
                float l = gridX + it.x * cell, t = gridY + it.y * cell;
                return new float[] { l, t, l + it.w * cell, t + it.h * cell };
            }
            if (it.where == WORN) {
                for (int i = 0; i < WORN_ORDER.length; i++) if (WORN_ORDER[i] == it.loc) return slotRect(i);
                return null;
            }
            int col = it.x % 4, row = it.x / 4;
            if (it.x < 0 || row >= BELT_ROWS) return null;
            float l = beltX + col * beltCell, t = beltY + (BELT_ROWS - 1 - row) * beltCell;   // the bottom row is the one a potion button drinks
            return new float[] { l, t, l + beltCell, t + beltCell };
        }

        int itemAt(Item[] items, float x, float y) {
            for (int i = 0; i < items.length; i++) {
                float[] r = rect(items[i]);
                if (r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3]) return i;
            }
            return -1;
        }
    }
}

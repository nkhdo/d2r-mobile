package com.xiaoboluo.d2r;

// The Deck's Bag tab without Android types (tested by BagModelTest): the host's item list (deck_jni.c nativeBag, from dm_inv.c)
// and where each item goes on the bottom screen: on the game's own inventory panel art (deck/ui/inventory.png, 507 x 700), worn
// items in its paper-doll boxes, the bag in its 10 x 4 grid and the belt's bottom row in its four gold boxes; the belt's upper
// rows in a column beside the panel (deck/ui/beltrow.png, 246 x 56 per row, row 3 on top).
final class BagModel {
    static final int BAG = 0, WORN = 1, BELT = 2;
    static final int INTS = 5;   // ints per item in nativeBag's output
    static final int F_USE = 1, F_BELT = 2;   // item flags from the host: can be used (potion, scroll, tome, cube), can go in the belt
    static final int ACT_USE = 1, ACT_BELT = 2, ACT_SORT = 3;   // the item sheet's buttons
    static final int GRID_W = 10, GRID_H = 4, BELT_ROWS = 4;
    static final float PANEL_W = 507, PANEL_H = 700, BELT_ROW_W = 246, BELT_ROW_H = 56;
    private static final float GRID_X = 9, GRID_Y = 425, CELL = 49;
    // the panel's paper-doll boxes (left, top, right, bottom in panel pixels) by body location; 11 and 12 (the other weapon set) are not shown
    private static final float[][] DOLL = new float[13][];
    static {
        DOLL[1] = new float[] { 196, 0, 309, 110 };     // head
        DOLL[2] = new float[] { 318, 82, 383, 148 };    // neck
        DOLL[3] = new float[] { 196, 113, 309, 270 };   // torso
        DOLL[4] = new float[] { 4, 67, 119, 270 };      // right hand (weapon, the left column on screen)
        DOLL[5] = new float[] { 389, 67, 503, 270 };    // left hand (shield)
        DOLL[6] = new float[] { 122, 272, 187, 338 };   // right ring
        DOLL[7] = new float[] { 319, 272, 385, 339 };   // left ring
        DOLL[8] = new float[] { 196, 273, 309, 335 };   // belt
        DOLL[9] = new float[] { 389, 272, 503, 387 };   // feet
        DOLL[10] = new float[] { 4, 272, 119, 387 };    // gloves
    }
    // the belt's bottom row: the panel's four gold boxes
    private static final float[] GOLD_X = { 124, 191, 255, 319 }, GOLD_R = { 188, 252, 316, 383 };
    private static final float GOLD_Y = 351, GOLD_B = 409;

    // the weapon set tabs: a strip above each hand column, I on its left half and II on its right half. The game keeps the set in hand
    // at body locations 4 / 5 and moves the other set to 11 / 12 on a swap, so the boxes always show the set whose tab is lit
    private static final float[][] WEAPON_STRIP = { { 4, 44, 119, 66 }, { 389, 44, 503, 66 } };
    private static final float TAB_REACH = 30;   // a touch this far above a strip still counts (the strip alone is thin)

    static float[] weaponTab(int side, int set) {
        float[] s = WEAPON_STRIP[side]; float mid = (s[0] + s[2]) / 2;
        return set == 0 ? new float[] { s[0], s[1], mid, s[3] } : new float[] { mid, s[1], s[2], s[3] };
    }

    static float[] dollBox(int loc) { return loc >= 0 && loc < DOLL.length ? DOLL[loc] : null; }
    static int[] dollLocs() { return new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }; }

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
        final float px, py, k, panelW, panelH, beltX, beltY, beltW, beltRowH;

        // the panel at (px, py) scaled by k; the upper belt rows in a column of width beltW at (beltX, beltY)
        Layout(float px, float py, float k, float beltX, float beltY, float beltW) {
            this.px = px; this.py = py; this.k = k; panelW = PANEL_W * k; panelH = PANEL_H * k;
            this.beltX = beltX; this.beltY = beltY; this.beltW = beltW; beltRowH = beltW * BELT_ROW_H / BELT_ROW_W;
        }

        float[] box(float[] b) { return b == null ? null : new float[] { px + b[0] * k, py + b[1] * k, px + b[2] * k, py + b[3] * k }; }

        // the column's row r (1..3) of the belt, r = 3 on top
        float[] beltRow(int r) { float t = beltY + (BELT_ROWS - 1 - r) * beltRowH; return new float[] { beltX, t, beltX + beltW, t + beltRowH }; }

        // left, top, right, bottom; null = not drawn
        float[] rect(Item it) {
            if (it.where == BAG) {
                float l = px + (GRID_X + it.x * CELL) * k, t = py + (GRID_Y + it.y * CELL) * k;
                return new float[] { l, t, l + it.w * CELL * k, t + it.h * CELL * k };
            }
            if (it.where == WORN) return box(dollBox(it.loc));
            int col = it.x % 4, row = it.x / 4;
            if (it.x < 0 || row >= BELT_ROWS) return null;
            if (row == 0) return box(new float[] { GOLD_X[col], GOLD_Y, GOLD_R[col], GOLD_B });   // the row a potion button drinks
            float[] r = beltRow(row); float w = beltW / 4;
            return new float[] { beltX + col * w, r[1], beltX + (col + 1) * w, r[3] };
        }

        // which weapon set tab a touch is on: 0 = I, 1 = II, -1 = none
        int weaponTabAt(float x, float y) {
            float u = (x - px) / k, v = (y - py) / k;
            for (int side = 0; side < 2; side++)
                for (int set = 0; set < 2; set++) {
                    float[] t = weaponTab(side, set);
                    if (u >= t[0] && u < t[2] && v >= t[1] - TAB_REACH && v < t[3]) return set;
                }
            return -1;
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

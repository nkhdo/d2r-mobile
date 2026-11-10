package com.xiaoboluo.d2r;

// The host's item list for the bottom-screen Deck, without Android types (tested by BagModelTest): decoding deck_jni.c nativeBag's
// output (from dm_inv.c). Main's potion row draws each belt slot with its own item's picture from it.
final class BagModel {
    static final int BAG = 0, WORN = 1, BELT = 2;
    static final int INTS = 5;   // ints per item in nativeBag's output
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
}

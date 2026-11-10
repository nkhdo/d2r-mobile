package com.xiaoboluo.d2r;

// The host's item list for the Deck (BagModel): decoding nativeBag's output.
public class BagModelTest {
    private static int cases;
    private static void check(String what, Object want, Object actual) {
        cases++;
        if (!java.util.Objects.equals(want, actual)) throw new AssertionError(what + ": want=" + want + " actual=" + actual);
    }
    private static void near(String what, float want, float actual) {
        cases++;
        if (Math.abs(want - actual) > 0.01f) throw new AssertionError(what + ": want=" + want + " actual=" + actual);
    }
    // one item as nativeBag packs it: id, code bytes, where | quality << 8 | unid << 16 | loc << 24, x | y << 8 | w << 16 | h << 24, row & 0xffff | flags << 16
    private static int[] item(int id, String code, int where, int quality, int unid, int loc, int x, int y, int w, int h, int row) {
        return item(id, code, where, quality, unid, loc, x, y, w, h, row, 0);
    }
    private static int[] item(int id, String code, int where, int quality, int unid, int loc, int x, int y, int w, int h, int row, int flags) {
        int c = 0; for (int i = 0; i < code.length(); i++) c |= code.charAt(i) << (8 * i);
        return new int[] { id, c, where | quality << 8 | unid << 16 | loc << 24, (x & 0xff) | (y & 0xff) << 8 | w << 16 | h << 24, row & 0xffff | flags << 16 };
    }
    private static int[] pack(int[]... items) {
        int[] raw = new int[items.length * BagModel.INTS];
        for (int i = 0; i < items.length; i++) System.arraycopy(items[i], 0, raw, i * BagModel.INTS, BagModel.INTS);
        return raw;
    }
    public static void main(String[] args) {
        BagModel.Item[] items = BagModel.decode(pack(
            item(11, "lsd", BagModel.BAG, 7, 0, 0, 2, 1, 2, 3, 42),   // a unique long sword at cells (2,1), 2 x 3
            item(12, "hp1", BagModel.BAG, 2, 0, 0, 9, 3, 1, 1, -1, 3),
            item(13, "cap", BagModel.WORN, 4, 1, 1, 0, 0, 2, 2, -1),   // worn on the head, unidentified magic
            item(14, "rin", BagModel.WORN, 5, 0, 7, 0, 0, 1, 1, 9),   // left ring, set item row 9
            item(15, "axe", BagModel.WORN, 2, 0, 11, 0, 0, 2, 3, -1), // other weapon set: not drawn
            item(16, "mp2", BagModel.BELT, 2, 0, 0, 5, 0, 1, 1, -1, 3)), 6);
        check("count", 6, items.length);
        check("code", "lsd", items[0].code);
        check("quality", 7, items[0].quality);
        check("unique picture key", "u42", items[0].pictureKey());
        check("set picture key", "s9", items[3].pictureKey());
        check("plain picture key", "hp1", items[1].pictureKey());
        check("unidentified", true, items[2].unid);
        check("body location", 1, items[2].loc);
        check("row survives the flags beside it", 42, items[0].row);
        check("no row", -1, items[1].row);

        check("flags beside the row", 3, items[1].flags);
        check("belt slot", 5, items[5].x);
        check("short list", 0, BagModel.decode(new int[3], 1).length);
        System.out.println("BagModelTest OK (" + cases + " cases)");
    }
}

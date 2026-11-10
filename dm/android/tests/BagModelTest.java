package com.xiaoboluo.d2r;

// The Deck's Bag tab (BagModel): decoding the host's item list, where each item is drawn, and which item a touch lands on.
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
    // one item as nativeBag packs it: id, code bytes, where | quality << 8 | unid << 16 | loc << 24, x | y << 8 | w << 16 | h << 24, row
    private static int[] item(int id, String code, int where, int quality, int unid, int loc, int x, int y, int w, int h, int row) {
        int c = 0; for (int i = 0; i < code.length(); i++) c |= code.charAt(i) << (8 * i);
        return new int[] { id, c, where | quality << 8 | unid << 16 | loc << 24, (x & 0xff) | (y & 0xff) << 8 | w << 16 | h << 24, row };
    }
    private static int[] pack(int[]... items) {
        int[] raw = new int[items.length * BagModel.INTS];
        for (int i = 0; i < items.length; i++) System.arraycopy(items[i], 0, raw, i * BagModel.INTS, BagModel.INTS);
        return raw;
    }
    public static void main(String[] args) {
        BagModel.Item[] items = BagModel.decode(pack(
            item(11, "lsd", BagModel.BAG, 7, 0, 0, 2, 1, 2, 3, 42),   // a unique long sword at cells (2,1), 2 x 3
            item(12, "hp1", BagModel.BAG, 2, 0, 0, 9, 3, 1, 1, -1),
            item(13, "cap", BagModel.WORN, 4, 1, 1, 0, 0, 2, 2, -1),   // worn on the head, unidentified magic
            item(14, "rin", BagModel.WORN, 5, 0, 7, 0, 0, 1, 1, 9),   // left ring, set item row 9
            item(15, "axe", BagModel.WORN, 2, 0, 11, 0, 0, 2, 3, -1), // other weapon set: not drawn
            item(16, "mp2", BagModel.BELT, 2, 0, 0, 5, 0, 1, 1, -1)), 6);
        check("count", 6, items.length);
        check("code", "lsd", items[0].code);
        check("quality", 7, items[0].quality);
        check("unique picture key", "u42", items[0].pictureKey());
        check("set picture key", "s9", items[3].pictureKey());
        check("plain picture key", "hp1", items[1].pictureKey());
        check("unidentified", true, items[2].unid);
        check("body location", 1, items[2].loc);

        // content 1200 x 1000 at (0, 100)
        BagModel.Layout l = new BagModel.Layout(0, 100, 1200, 1000, 12);
        float s = l.cell;   // one bag cell
        float[] r = l.rect(items[0]);
        near("bag item left = grid left + 2 cells", l.gridX + 2 * s, r[0]);
        near("bag item top = grid top + 1 cell", l.gridY + s, r[1]);
        near("bag item width = 2 cells", 2 * s, r[2] - r[0]);
        near("bag item height = 3 cells", 3 * s, r[3] - r[1]);
        check("grid is 10 cells wide and fits", true, l.gridX + 10 * s <= 1200.01f);
        check("worn row sits above the grid", true, l.rect(items[2])[3] <= l.gridY);
        check("head and left ring have different slots", false, l.rect(items[2])[0] == l.rect(items[3])[0]);
        check("other weapon set is not drawn", null, l.rect(items[4]));
        float[] belt = l.rect(items[5]);   // slot 5 = column 1, row 1 (drawn from the bottom up, as in the game)
        check("belt below the grid", true, belt[1] >= l.gridY + 4 * s);
        near("belt column 1", l.beltX + l.beltCell, belt[0]);
        check("everything fits in the content height", true, l.beltY + 4 * l.beltCell <= 1100.01f);

        check("touch on the sword's lower-right cell", 0, l.itemAt(items, l.gridX + 3.5f * s, l.gridY + 3.5f * s));
        check("touch on an empty bag cell", -1, l.itemAt(items, l.gridX + 0.5f * s, l.gridY + 0.5f * s));
        check("touch on the worn cap", 2, l.itemAt(items, (l.rect(items[2])[0] + l.rect(items[2])[2]) / 2, (l.rect(items[2])[1] + l.rect(items[2])[3]) / 2));
        check("touch outside everything", -1, l.itemAt(items, 5, 5));
        check("short list", 0, BagModel.decode(new int[3], 1).length);
        System.out.println("BagModelTest OK (" + cases + " cases)");
    }
}

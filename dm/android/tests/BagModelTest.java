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
    // one item as nativeBag packs it: id, code bytes, where | quality << 8 | unid << 16 | loc << 24, x | y << 8 | w << 16 | h << 24, row & 0xffff | flags << 16
    private static int[] item(int id, String code, int where, int quality, int unid, int loc, int x, int y, int w, int h, int row) {
        return item(id, code, where, quality, unid, loc, x, y, w, h, row, 0);
    }
    private static int[] item(int id, String code, int where, int quality, int unid, int loc, int x, int y, int w, int h, int row, int flags) {
        int c = 0; for (int i = 0; i < code.length(); i++) c |= code.charAt(i) << (8 * i);
        return new int[] { id, c, where | quality << 8 | unid << 16 | loc << 24, (x & 0xff) | (y & 0xff) << 8 | w << 16 | h << 24, row & 0xffff | flags << 16 };
    }
    private static String acts(BagModel.Item it) { return java.util.Arrays.toString(BagModel.actions(it)); }
    private static int[] pack(int[]... items) {
        int[] raw = new int[items.length * BagModel.INTS];
        for (int i = 0; i < items.length; i++) System.arraycopy(items[i], 0, raw, i * BagModel.INTS, BagModel.INTS);
        return raw;
    }
    public static void main(String[] args) {
        BagModel.Item[] items = BagModel.decode(pack(
            item(11, "lsd", BagModel.BAG, 7, 0, 0, 2, 1, 2, 3, 42),   // a unique long sword at cells (2,1), 2 x 3
            item(12, "hp1", BagModel.BAG, 2, 0, 0, 9, 3, 1, 1, -1, BagModel.F_USE | BagModel.F_BELT),
            item(13, "cap", BagModel.WORN, 4, 1, 1, 0, 0, 2, 2, -1),   // worn on the head, unidentified magic
            item(14, "rin", BagModel.WORN, 5, 0, 7, 0, 0, 1, 1, 9),   // left ring, set item row 9
            item(15, "axe", BagModel.WORN, 2, 0, 11, 0, 0, 2, 3, -1), // other weapon set: not drawn
            item(16, "mp2", BagModel.BELT, 2, 0, 0, 5, 0, 1, 1, -1, BagModel.F_USE | BagModel.F_BELT)), 6);
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

        // the item sheet's buttons
        String use = String.valueOf(BagModel.ACT_USE), toBelt = String.valueOf(BagModel.ACT_BELT), sort = String.valueOf(BagModel.ACT_SORT);
        check("potion in the bag: use, to belt, sort", "[" + use + ", " + toBelt + ", " + sort + "]", acts(items[1]));
        check("sword in the bag: sort only", "[" + sort + "]", acts(items[0]));
        check("worn item: nothing to do", "[]", acts(items[2]));
        check("potion already in the belt: use only", "[" + use + "]", acts(items[5]));

        // the game's inventory panel (507 x 700 art) drawn at (10, 100), scale 1.4; the upper belt rows in a 480-wide column at (740, 120)
        BagModel.Layout l = new BagModel.Layout(10, 100, 1.4f, 740, 120, 480);
        float k = 1.4f, c = 49 * k;
        float[] r = l.rect(items[0]);
        near("bag item left = panel grid x 9 + 2 cells", 10 + (9 + 2 * 49) * k, r[0]);
        near("bag item top = panel grid y 425 + 1 cell", 100 + (425 + 49) * k, r[1]);
        near("bag item width = 2 cells", 2 * c, r[2] - r[0]);
        near("bag item height = 3 cells", 3 * c, r[3] - r[1]);
        r = l.rect(items[2]);
        near("worn helm in the panel's helm box (x 196)", 10 + 196 * k, r[0]);
        near("worn helm box top (y 0)", 100, r[1]);
        r = l.rect(items[3]);
        near("left ring in the right-hand ring box (x 319)", 10 + 319 * k, r[0]);
        check("other weapon set is not drawn", null, l.rect(items[4]));
        float rowH = 480 * 56f / 246, slot = 480 / 4f;
        float[] belt = l.rect(items[5]);   // slot 5 = column 1, row 1: the lowest of the three upper rows on the right
        near("belt row 1 column 1, x", 740 + slot, belt[0]);
        near("belt row 1, y (rows 3, 2, 1 top to bottom)", 120 + 2 * rowH, belt[1]);
        BagModel.Item low = BagModel.decode(pack(item(17, "hp2", BagModel.BELT, 2, 0, 0, 2, 0, 1, 1, -1)), 1)[0];
        r = l.rect(low);   // bottom row: the panel's four gold boxes (column 2 = x 255)
        near("belt row 0 in the panel's gold box", 10 + 255 * k, r[0]);
        near("gold boxes at y 351", 100 + 351 * k, r[1]);
        near("panel width", 507 * k, l.panelW);

        check("touch on the sword's lower-right cell", 0, l.itemAt(items, 10 + (9 + 3.5f * 49) * k, 100 + (425 + 3.5f * 49) * k));
        check("touch on an empty bag cell", -1, l.itemAt(items, 10 + (9 + 0.5f * 49) * k, 100 + (425 + 0.5f * 49) * k));
        check("touch on the worn helm", 2, l.itemAt(items, 10 + 250 * k, 100 + 50 * k));
        check("touch outside everything", -1, l.itemAt(items, 5, 5));

        // weapon set tabs I / II on the strips above the two hand columns (left strip x 4..119, right x 389..503, y 44..66)
        r = l.box(BagModel.weaponTab(0, 1));
        near("left strip, tab II starts half way", 10 + (4 + 115 / 2f) * k, r[0]);
        near("tab top", 100 + 44 * k, r[1]);
        check("tap on the left strip's I", 0, l.weaponTabAt(10 + 20 * k, 100 + 55 * k));
        check("tap on the right strip's II", 1, l.weaponTabAt(10 + 480 * k, 100 + 55 * k));
        check("tap a little above a strip still counts", 0, l.weaponTabAt(10 + 400 * k, 100 + 20 * k));
        check("tap in the helm box is no tab", -1, l.weaponTabAt(10 + 250 * k, 100 + 55 * k));
        check("tap in the weapon box is no tab", -1, l.weaponTabAt(10 + 60 * k, 100 + 100 * k));
        check("short list", 0, BagModel.decode(new int[3], 1).length);
        System.out.println("BagModelTest OK (" + cases + " cases)");
    }
}

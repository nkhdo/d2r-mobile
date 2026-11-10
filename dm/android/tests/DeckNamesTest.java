package com.xiaoboluo.d2r;

// Area names on the bottom-screen Deck (DeckNames): the import pack's deck/levels.tsv, picked by the game's language number.
public class DeckNamesTest {
    private static int cases;
    private static void check(String what, String want, String actual) {
        cases++;
        if (!want.equals(actual)) throw new AssertionError(what + ": want=" + want + " actual=" + actual);
    }
    public static void main(String[] args) {
        String tsv = "id\tenUS\tdeDE\tesES\tfrFR\titIT\tkoKR\tplPL\truRU\tzhCN\tzhTW\tesMX\tjaJP\tptBR\n"
            + "0\t\t\t\t\t\t\t\t\t\t\t\t\t\n"
            + "1\tRogue Encampment\tLager der Jägerinnen\t\t\t\t\t\t\t罗格营地\t\t\t\t\n"
            + "2\tBlood Moor\n";   // short row: missing columns count as empty
        DeckNames n = DeckNames.parse(tsv);
        check("English", "Rogue Encampment", n.area(1, 0));
        check("German", "Lager der Jägerinnen", n.area(1, 1));
        check("Simplified Chinese (8)", "罗格营地", n.area(1, 8));
        check("missing language falls back to English", "Rogue Encampment", n.area(1, 2));
        check("short row falls back to English", "Blood Moor", n.area(2, 11));
        check("language number out of range falls back to English", "Blood Moor", n.area(2, 99));
        check("negative language falls back to English", "Blood Moor", n.area(2, -1));
        check("area 0 (not read yet) has no name", "", n.area(0, 0));
        check("unknown area has no name", "", n.area(500, 0));
        check("no table at all", "", DeckNames.parse("").area(1, 0));
        check("CRLF line ends", "Blood Moor", DeckNames.parse(tsv.replace("\n", "\r\n")).area(2, 0));
        DeckNames items = DeckNames.parse("key\tenUS\tdeDE\n" + "u0\tThe Gnasher\tder Knirscher\n" + "hax\tHand Axe\n");
        check("item key, German", "der Knirscher", items.name("u0", 1));
        check("item key, falls back to English", "Hand Axe", items.name("hax", 1));
        check("unknown item key", "", items.name("zzz", 0));
        System.out.println("DeckNamesTest OK (" + cases + " cases)");
    }
}

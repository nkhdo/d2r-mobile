package com.xiaoboluo.d2r;

import java.util.HashMap;
import java.util.Map;

// Item names for the Build tab in the game's language, from the import pack (stage_deck_assets.py deck/itemnames.tsv): rows keyed by
// the English name in lowercase letters and digits, then the picture key (items.tsv: code, "u<ID>", "s<ID>"; "rw" for a runeword),
// a runeword's rune codes, and the 13 languages. A guide item the table does not know (or a pack without the table) is shown as
// written. Without Android types (tested by BuildPlanTest).
final class ItemNames {
    private static final int LANGS = 13;

    static final class Row {
        final String ref;
        final String[] runes, names;
        Row(String ref, String[] runes, String[] names) { this.ref = ref; this.runes = runes; this.names = names; }
        String name(int lang) { String s = lang >= 0 && lang < LANGS ? names[lang] : ""; return s.isEmpty() ? names[0] : s; }
    }

    private final Map<String, Row> byKey = new HashMap<>(), byRef = new HashMap<>();

    static ItemNames parse(String tsv) {
        ItemNames n = new ItemNames();
        String[] lines = tsv.split("\r?\n");
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split("\t", -1);
            if (f.length < 4 || f[0].isEmpty()) continue;
            String[] names = new String[LANGS];
            for (int l = 0; l < LANGS; l++) names[l] = 3 + l < f.length ? f[3 + l] : "";
            Row r = new Row(f[1], f[2].isEmpty() ? new String[0] : f[2].split(" "), names);
            n.byKey.put(f[0], r);
            if (!f[1].isEmpty() && !f[1].equals("rw")) n.byRef.putIfAbsent(f[1], r);
        }
        return n;
    }

    static String key(String english) { return english.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", ""); }

    Row row(String english) { return byKey.get(key(english)); }

    // a guide item ("Spirit" or "Spirit|Crystal Sword") in the game's language: "Spirit · Crystal Sword"
    String display(String item, int lang) {
        String[] p = item.split("\\|", 2);
        StringBuilder b = new StringBuilder(one(p[0].trim(), lang));
        if (p.length > 1) b.append(" · ").append(one(p[1].trim(), lang));
        return b.toString();
    }

    private String one(String english, int lang) { Row r = row(english); return r == null ? english : r.name(lang); }

    // a runeword's runes in the game's language ("Tal Thul Ort Amn"; English drops the word "Rune"), "" for anything else
    String runes(String item, int lang) {
        Row r = row(item.split("\\|", 2)[0].trim());
        if (r == null || r.runes.length == 0) return "";
        StringBuilder b = new StringBuilder();
        for (String code : r.runes) {
            Row rune = byRef.get(code);
            String n = rune == null ? code : rune.name(lang);
            if (n.endsWith(" Rune")) n = n.substring(0, n.length() - 5);
            if (b.length() > 0) b.append(' ');
            b.append(n);
        }
        return b.toString();
    }

    // the picture key (DeckIcons) for a guide item, or null (runewords and unknown items have none)
    String picture(String item) {
        Row r = row(item.split("\\|", 2)[0].trim());
        if (r == null || r.ref.equals("rw") || r.ref.isEmpty()) {
            String[] p = item.split("\\|", 2);   // a runeword: its base's picture
            if (p.length > 1) { Row b = row(p[1].trim()); if (b != null && !b.ref.equals("rw")) return b.ref; }
            return null;
        }
        return r.ref;
    }
}

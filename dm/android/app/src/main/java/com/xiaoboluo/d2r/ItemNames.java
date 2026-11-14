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
            if (b.length() > 0) b.append(' ');
            b.append(runeName(code, lang));
        }
        return b.toString();
    }

    // Colour marks in runeLine / expand(.., have) text (BuildTab.paragraph draws them): RUNE starts a rune name, HAVE a held mark,
    // END goes back to the text's own colour
    static final char RUNE = '\u0001', HAVE = '\u0002', END = '\u0003';

    // a runeword's runes with colour marks, each one the player holds (have: rune code -> count, a rune used twice needs two) followed
    // by a check: "<RUNE>Tal<END> <HAVE>✓<END> <RUNE>Eth<END>"; "" for anything else
    String runeLine(String item, int lang, Map<String, Integer> have) {
        Row r = row(item.split("\\|", 2)[0].trim());
        if (r == null || r.runes.length == 0) return "";
        Map<String, Integer> left = new HashMap<>(have);
        StringBuilder b = new StringBuilder();
        for (String code : r.runes) {
            if (b.length() > 0) b.append(' ');
            b.append(RUNE).append(runeName(code, lang)).append(END);
            int n = left.getOrDefault(code, 0);
            if (n > 0) { left.put(code, n - 1); b.append(' ').append(HAVE).append('✓').append(END); }
        }
        return b.toString();
    }

    // where the runeword's highest rune still missing drops first (treasureclassex.txt: the Countess drops "Runes 4" = up to Ral in
    // Normal, "Runes 8" = up to Lum in Nightmare, "Runes 12" = up to Ist in Hell); "" when every rune is held or it is not a runeword.
    // English, like the notes.
    String source(String item, Map<String, Integer> have) {
        Row r = row(item.split("\\|", 2)[0].trim());
        if (r == null) return "";
        Map<String, Integer> left = new HashMap<>(have);
        int top = 0;
        for (String code : r.runes) {
            int n = left.getOrDefault(code, 0);
            if (n > 0) { left.put(code, n - 1); continue; }
            try { top = Math.max(top, Integer.parseInt(code.substring(1))); } catch (RuntimeException e) { return ""; }
        }
        return top == 0 ? "" : top <= 8 ? "Countess, Normal" : top <= 16 ? "Countess, Nightmare" : top <= 24 ? "Countess, Hell"
            : "rare: Hell drops or the Cube";
    }

    private String runeName(String code, int lang) {
        Row rune = byRef.get(code);
        String n = rune == null ? code : rune.name(lang);
        return n.endsWith(" Rune") ? n.substring(0, n.length() - 5) : n;
    }

    // expand with colour marks and held runes (runeLine) for drawing
    String expand(String text, int lang, Map<String, Integer> have) { return expand(text, lang, have, true); }

    // our note text with each "[Item]" in the game's language, a runeword with its runes: "kept for [Stealth]" -> "kept for Stealth
    // (Tal Eth)", so a note can name a runeword without leaving the player to look up how to make it. An unclosed "[" is kept as is.
    String expand(String text, int lang) { return expand(text, lang, null, false); }

    private String expand(String text, int lang, Map<String, Integer> have, boolean marks) {
        int open = text.indexOf('[');
        if (open < 0) return text;
        StringBuilder b = new StringBuilder();
        int at = 0;
        while (open >= 0) {
            int close = text.indexOf(']', open);
            if (close < 0) break;
            String item = text.substring(open + 1, close), runes = marks ? runeLine(item, lang, have) : runes(item, lang);
            b.append(text, at, open).append(display(item, lang));
            if (!runes.isEmpty()) b.append(" (").append(runes).append(')');
            at = close + 1;
            open = text.indexOf('[', at);
        }
        return b.append(text, at, text.length()).toString();
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

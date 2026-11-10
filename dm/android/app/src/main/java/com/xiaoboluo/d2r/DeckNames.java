package com.xiaoboluo.d2r;

import java.util.HashMap;
import java.util.Map;

// Names for the bottom-screen Deck, from the import pack (stage_deck_assets.py), made from the player's own game files:
// deck/levels.tsv (area names by levels.txt Id): a header line, then key + 13 columns in the game's language-number order (0 enUS ... 12 ptBR).
// A missing or empty name in the game's language falls back to English; an unknown key has no name.
final class DeckNames {
    private static final int LANGS = 13;
    private final Map<String, String[]> rows;

    private DeckNames(Map<String, String[]> rows) { this.rows = rows; }

    static DeckNames parse(String tsv) {
        Map<String, String[]> rows = new HashMap<>();
        String[] lines = tsv.split("\r?\n");
        for (int i = 1; i < lines.length; i++) {   // line 0 is the header
            String[] f = lines[i].split("\t", -1);
            if (f[0].isEmpty()) continue;
            String[] names = new String[LANGS];
            for (int l = 0; l < LANGS; l++) names[l] = l + 1 < f.length ? f[l + 1] : "";
            rows.put(f[0], names);
        }
        return new DeckNames(rows);
    }

    String area(int id, int lang) { return name(String.valueOf(id), lang); }

    String name(String key, int lang) {
        String[] names = rows.get(key);
        if (names == null) return "";
        String s = lang >= 0 && lang < LANGS ? names[lang] : "";
        return s.isEmpty() ? names[0] : s;
    }
}

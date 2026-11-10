package com.xiaoboluo.d2r;

import java.util.HashMap;
import java.util.Map;

// Area names for the bottom-screen Deck. The table is deck/levels.tsv in the import pack (stage_deck_assets.py), made from the
// player's own levels.txt and level strings: header "id" + 13 columns in the game's language-number order (0 enUS ... 12 ptBR).
// A missing or empty name in the game's language falls back to English; an unknown area has no name.
final class DeckNames {
    private static final int LANGS = 13;
    private final Map<Integer, String[]> rows;

    private DeckNames(Map<Integer, String[]> rows) { this.rows = rows; }

    static DeckNames parse(String tsv) {
        Map<Integer, String[]> rows = new HashMap<>();
        String[] lines = tsv.split("\r?\n");
        for (int i = 1; i < lines.length; i++) {   // line 0 is the header
            String[] f = lines[i].split("\t", -1);
            int id;
            try { id = Integer.parseInt(f[0]); } catch (NumberFormatException e) { continue; }
            String[] names = new String[LANGS];
            for (int l = 0; l < LANGS; l++) names[l] = l + 1 < f.length ? f[l + 1] : "";
            rows.put(id, names);
        }
        return new DeckNames(rows);
    }

    String area(int id, int lang) {
        String[] names = rows.get(id);
        if (names == null) return "";
        String s = lang >= 0 && lang < LANGS ? names[lang] : "";
        return s.isEmpty() ? names[0] : s;
    }
}

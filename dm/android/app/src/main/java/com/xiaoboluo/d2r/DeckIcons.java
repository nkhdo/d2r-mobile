package com.xiaoboluo.d2r;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

// Item pictures for the Deck's Bag tab, from the import pack (stage_deck_assets.py): deck/items.tsv maps a key (item code, "u<row>",
// "s<row>") to a PNG in deck/items/, made from the player's own item sprites. Each picture is decoded once, on first use; a pack made
// before the Bag tab has none of this, and the Deck then draws item codes instead.
final class DeckIcons {
    private static final long MAX_FILE = 1024 * 1024;
    private final File dir;
    private Map<String, String> files;
    private final Map<String, Bitmap> cache = new HashMap<>();

    DeckIcons(Context context) { dir = new File(context.getFilesDir(), "dm/overlay/deck"); }

    private Map<String, String> files() {
        if (files != null) return files;
        files = new HashMap<>();
        File f = new File(dir, "items.tsv");
        try {
            if (f.isFile() && f.length() <= MAX_FILE)
                for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\r?\n")) {
                    int tab = line.indexOf('\t');
                    if (tab > 0 && line.indexOf('/') < 0 && line.indexOf('\\') < 0) files.put(line.substring(0, tab), line.substring(tab + 1));
                }
        } catch (IOException | SecurityException e) { files.clear(); }
        return files;
    }

    // the picture for this item: its own (set / unique) first, else its code's; null = none
    Bitmap get(BagModel.Item it) {
        Bitmap b = load(it.pictureKey());
        return b != null || it.pictureKey().equals(it.code) ? b : load(it.code);
    }

    private Bitmap load(String key) {
        if (cache.containsKey(key)) return cache.get(key);
        Bitmap b = null;
        String name = files().get(key);
        if (name != null) {
            File f = new File(new File(dir, "items"), name);
            if (f.isFile() && f.length() <= MAX_FILE) b = BitmapFactory.decodeFile(f.getAbsolutePath());
        }
        cache.put(key, b);
        return b;
    }
}

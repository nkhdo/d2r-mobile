package com.xiaoboluo.d2r;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

// The Deck's look: the game's own panel art and fonts from the import pack (deck/ui/, stage_deck_assets.py UI_ART), made from the
// player's own game files. Each picture is decoded once, on first use. A pack made before the Deck had art has no deck/ui/: every
// draw call then falls back to a flat panel in the same place, so the layout code has one path.
final class DeckArt {
    private static final long MAX_FILE = 8L * 1024 * 1024;
    static final int PANEL = 0xFF1C1712, PRESSED = 0xFF3A2E1C, GOLD = 0xFF8C7340;
    private final File dir;
    private final Map<String, Bitmap> cache = new HashMap<>();
    private Typeface exocet, formal;
    private boolean fontsTried;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG), flat = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect s = new Rect();
    private final RectF d = new RectF();

    DeckArt(Context context) { dir = new File(context.getFilesDir(), "dm/overlay/deck/ui"); }

    Bitmap get(String name) {
        if (cache.containsKey(name)) return cache.get(name);
        File f = new File(dir, name + ".png");
        Bitmap b = f.isFile() && f.length() <= MAX_FILE ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
        cache.put(name, b);
        return b;
    }

    boolean has(String name) { return get(name) != null; }

    private void fonts() {
        if (fontsTried) return;
        fontsTried = true;
        exocet = font("exocet.otf"); formal = font("formal.ttf");
    }

    private Typeface font(String name) {
        File f = new File(dir, name);
        try { return f.isFile() && f.length() <= MAX_FILE ? Typeface.createFromFile(f) : null; } catch (RuntimeException e) { return null; }
    }

    // The game's fonts have Latin and Cyrillic only: Korean, Chinese and Japanese (language numbers 5, 8, 9, 11) keep the system font
    private static boolean cjk(int lang) { return lang == 5 || lang == 8 || lang == 9 || lang == 11; }
    Typeface label(int lang) { fonts(); return cjk(lang) || exocet == null ? Typeface.DEFAULT : exocet; }
    Typeface name(int lang) { fonts(); return cjk(lang) || formal == null ? Typeface.DEFAULT : formal; }

    // the whole picture (or one frame-sized part of it, src) stretched over dst; false = no such picture
    boolean draw(Canvas c, String name, Rect src, RectF dst) {
        Bitmap b = get(name); if (b == null) return false;
        if (src == null) { s.set(0, 0, b.getWidth(), b.getHeight()); src = s; }
        c.drawBitmap(b, src, dst, paint);
        return true;
    }

    // Nine-slice: corners of `border` picture pixels drawn at `border * scale`, edges and middle stretched (frames keep their
    // thickness at any size). Without the picture: a flat rounded panel with a gold edge.
    void frame(Canvas c, String name, Rect src, RectF dst, int border, float scale, int fallback) {
        Bitmap b = get(name);
        if (b == null) {
            float r = Math.min(dst.width(), dst.height()) * 0.12f;
            flat.setStyle(Paint.Style.FILL); flat.setColor(fallback); c.drawRoundRect(dst, r, r, flat);
            flat.setStyle(Paint.Style.STROKE); flat.setStrokeWidth(Math.max(2, scale * 2)); flat.setColor(GOLD); c.drawRoundRect(dst, r, r, flat);
            return;
        }
        int l = src == null ? 0 : src.left, t = src == null ? 0 : src.top, r = src == null ? b.getWidth() : src.right, bo = src == null ? b.getHeight() : src.bottom;
        int e = Math.min(border, Math.min((r - l) / 2, (bo - t) / 2));
        float de = Math.min(e * scale, Math.min(dst.width(), dst.height()) / 2);
        int[] sx = { l, l + e, r - e, r }, sy = { t, t + e, bo - e, bo };
        float[] dx = { dst.left, dst.left + de, dst.right - de, dst.right }, dy = { dst.top, dst.top + de, dst.bottom - de, dst.bottom };
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                if (sx[i + 1] <= sx[i] || sy[j + 1] <= sy[j] || dx[i + 1] <= dx[i] || dy[j + 1] <= dy[j]) continue;
                s.set(sx[i], sy[j], sx[i + 1], sy[j + 1]); d.set(dx[i], dy[j], dx[i + 1], dy[j + 1]);
                c.drawBitmap(b, s, d, paint);
            }
    }

    // a picture scaled to cover dst, centred and cut at the edges (the stone background)
    boolean cover(Canvas c, String name, RectF dst) {
        Bitmap b = get(name); if (b == null) return false;
        float k = Math.max(dst.width() / b.getWidth(), dst.height() / b.getHeight());
        float w = dst.width() / k, h = dst.height() / k, x = (b.getWidth() - w) / 2, y = (b.getHeight() - h) / 2;
        s.set(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
        c.drawBitmap(b, s, dst, paint);
        return true;
    }
}

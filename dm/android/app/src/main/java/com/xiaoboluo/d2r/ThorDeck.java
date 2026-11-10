package com.xiaoboluo.d2r;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

// Bottom-screen Deck: status and large buttons on the second screen of dual-screen handhelds (for controller play; SecondScreen.java
// handles showing and hiding), drawn with the game's own panel art and fonts from the import pack (DeckArt.java; flat panels without it).
// A tab row on top switches between:
//   Main: the game's HUD bar (life and mana globes, area name, level and experience, portal count, badges above it; its belt boxes stay
//     empty, the belt is the row at the bottom), 8 large buttons (Inventory, Character, Skills, Quests / Portal, Map, Swap weapons,
//     Players) and a row of the four belt columns as potion buttons (tap = drink the bottom-row bottle, long press = feed the mercenary).
// The host's item list (BagModel.java) is read while the Deck shows, for the belt's own pictures. Item management stays in the
// game's own inventory (the Inventory button): the bottom screen is for glancing and one-tap actions.
// All text goes through the string table (nativeText). SecondScreen reads a snapshot every 100 ms and passes it to update, which redraws
// only on change; press feedback is drawn at once. A touch takes effect on release, and CANCEL means undo: right after the bottom screen
// appears, the first touch arrives as DOWN then CANCEL.
final class ThorDeck extends View {
    // snapshot indices, matching deck_jni.c
    static final int H_VALID = 0, H_LEVEL = 1, H_EXP = 2, H_HP = 3, H_HP_MAX = 4, H_MP = 5, H_MP_MAX = 6, H_BELT = 7, H_KIND = 11, H_PORTAL = 15,
        H_PLAYERS = 16, H_POINTS = 17, H_QUEST = 18, H_DEAD = 19, H_TOWN = 20, H_SWITCH = 21, H_MAP_FULL = 22, H_AREA = 23, H_LANG = 24, H_WSET = 25, H_COUNT = 26;
    // action numbers, matching deck_jni.c
    static final int A_DRINK = 0, A_FEED_MERC = 1, A_PORTAL = 2, A_MAP = 3, A_SWAP = 4, A_TAB = 5, A_PLAYERS = 6;
    static native int nativeHud(int[] out);
    static native void nativeAction(int action, int arg);
    static native String nativeText(String key);
    static native void nativeBagWant(boolean on);
    static native int nativeBag(int[] out);

    private static final long LONG_PRESS_MS = 500;
    // twelve cells: 0–3 the four function pages, 4 portal, 5 map, 6 swap, 7 players, 8–11 the four belt columns
    private static final int B_PORTAL = 4, B_MAP = 5, B_SWAP = 6, B_PLAYERS = 7, B_BELT = 8, B_COUNT = 12;
    private static final String[] TAB_KEY = { "dmDeckInventory", "dmDeckCharacter", "dmDeckSkills", "dmDeckQuests" };
    private static final int BG = 0xFF0E0B08, PANEL = DeckArt.PANEL, PRESSED = DeckArt.PRESSED, GOLD = DeckArt.GOLD, TEXT = 0xFFE8D9B0, DIM = 0xFF8A7F68,
        LIFE = 0xFFB0202A, MANA = 0xFF2A4FC0, XP = 0xFFC7A04A, DEAD = 0xFFE04030;
    private static final int[] KIND_COLOUR = { 0xFF2A2520, LIFE, MANA, 0xFF8A3AB0, 0xFF6B6B6B };   // empty / red / blue / purple / other
    private static final String[] KIND_PICTURE = { null, "hp4", "mp4", "rvl", null };   // the picture a belt column's kind is drawn with
    // where things sit on the game's HUD bar art (deck/ui/hud.png, 1582 x 206 pixels)
    private static final float HUD_W = 1582, HUD_H = 206, GLOBE_L = 222, GLOBE_R = 1376, GLOBE_Y = 117, GLOBE_RAD = 83, GLOBE_ART = 87;
    private static final float[] HUD_INFO = { 330, 118, 745, 196 }, HUD_GOLD = { 849, 116, 1119, 197 }, HUD_PORTAL = { 1128, 140, 1245, 170 };

    private final int[] hud = new int[H_COUNT];
    private final RectF[] cell = new RectF[B_COUNT];
    private final RectF[] pick = new RectF[8];
    private final RectF hudRect = new RectF(), full = new RectF(), tmp = new RectF(), tmp2 = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    // which cell each finger is on (-1 none; PICK_BASE + n = cell n of the players picker), and whether its long press already fired
    private static final int PICK_BASE = 100, MAX_POINTERS = 10;
    private final int[] downCell = new int[MAX_POINTERS];
    private final boolean[] longFired = new boolean[MAX_POINTERS];
    private final Runnable[] longPress = new Runnable[MAX_POINTERS];
    private boolean picking;   // the players 1–8 picker is open
    // tabs. Kept across shows of the Deck (a new view is made each time the bottom screen comes back)
    private static final int TAB_MAIN = 0, TAB_COUNT = 1, TAB_BASE = 200;
    private static final String[] TAB_LABEL = { "dmDeckTabMain" };
    private static int tab = TAB_MAIN;
    private final RectF[] tabRect = new RectF[TAB_COUNT];
    private BagModel.Item[] bag = new BagModel.Item[0];
    private final int[] bagRaw = new int[1 + 128 * BagModel.INTS];
    private int bagGen = -1;
    private DeckIcons icons;
    private DeckArt art;
    // touch target codes, in ranges that cannot overlap: Main cells 0–11, picker 99–107, tabs 200+
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG), manaPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private DeckNames names;   // area names from the import pack (deck/levels.tsv); loaded once, null until then

    // An import pack made before the Deck had area names has no deck/levels.tsv: the name line then stays empty
    private DeckNames names() {
        if (names != null) return names;
        String tsv = "";
        File f = new File(getContext().getFilesDir(), "dm/overlay/deck/levels.tsv");
        try {
            if (f.isFile() && f.length() <= 1024 * 1024) tsv = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (java.io.IOException | SecurityException e) { tsv = ""; }
        return names = DeckNames.parse(tsv);
    }

    ThorDeck(Context context) {
        super(context);
        for (int i = 0; i < B_COUNT; i++) cell[i] = new RectF();
        for (int i = 0; i < 8; i++) pick[i] = new RectF();
        for (int i = 0; i < TAB_COUNT; i++) tabRect[i] = new RectF();
        Arrays.fill(downCell, -1);
        line.setStyle(Paint.Style.STROKE);
        text.setColor(TEXT);
        text.setTextAlign(Paint.Align.CENTER);
        // the life globe's red turned blue for mana: red and blue swapped
        manaPaint.setColorFilter(new ColorMatrixColorFilter(new float[] { 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0 }));
        for (int i = 0; i < MAX_POINTERS; i++) { final int p = i; longPress[i] = () -> onLongPress(p); }
    }

    // new snapshot: redraw only on change. Returns false = same as last time
    boolean update(int[] next) {
        if (Arrays.equals(hud, next)) return false;
        System.arraycopy(next, 0, hud, 0, H_COUNT);
        if (!actionsEnabled()) { picking = false; cancelAll(); }
        invalidate();
        return true;
    }

    private boolean actionsEnabled() { return hud[H_VALID] != 0 && hud[H_DEAD] == 0; }
    private static String t(String key) { return nativeText(key); }
    private DeckArt art() { if (art == null) art = new DeckArt(getContext()); return art; }
    private DeckIcons icons() { if (icons == null) icons = new DeckIcons(getContext()); return icons; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float k = w / 1240f, gap = 14 * k, pad = 18 * k, tw = (w - 2 * pad - gap) / 2, tabH = tw * 45 / 459;
        full.set(0, 0, w, h);
        for (int i = 0; i < TAB_COUNT; i++) tabRect[i].set(pad + i * (tw + gap), pad, pad + i * (tw + gap) + tw, pad + tabH);
        float top = pad + tabH + gap;
        // Main: the HUD bar across the whole width, 8 buttons in 2 columns, then one low row of the four potion buttons
        hudRect.set(0, top, w, top + w * HUD_H / HUD_W);
        float potH = 140 * k, by = hudRect.bottom + gap, bw = (w - 2 * pad - gap) / 2, bh = (h - pad - by - potH - 4 * gap) / 4;
        for (int i = 0; i < B_BELT; i++) {
            float x = pad + (i % 2) * (bw + gap), y = by + (i / 2) * (bh + gap);
            cell[i].set(x, y, x + bw, y + bh);
        }
        float py = h - pad - potH, pw4 = (w - 2 * pad - 3 * gap) / 4;
        for (int i = 0; i < 4; i++) cell[B_BELT + i].set(pad + i * (pw4 + gap), py, pad + i * (pw4 + gap) + pw4, py + potH);
        // the players picker covers the middle two button rows: 4 x 2
        float pt = cell[2].top, ph = (cell[5].bottom - pt - gap) / 2, pw = (w - 2 * pad - 3 * gap) / 4;
        for (int i = 0; i < 8; i++) { float x = pad + (i % 4) * (pw + gap), y = pt + (i / 4) * (ph + gap); pick[i].set(x, y, x + pw, y + ph); }
        line.setStrokeWidth(Math.max(2, 3 * k));
        text.setShadowLayer(3 * k, 0, 2 * k, 0xE0000000);
    }

    @Override protected void onDraw(Canvas c) {
        if (!art().cover(c, "stone", full)) c.drawColor(BG);
        text.setTypeface(art.label(hud[H_LANG]));
        if (hud[H_VALID] == 0) {   // not in a run yet: just one line of explanation
            label(c, t("dmDeckIdle"), getWidth() / 2f, getHeight() / 2f, getHeight() * 0.045f, getWidth() * 0.9f, DIM);
            return;
        }
        drawTabs(c);
        drawHud(c);
        boolean on = actionsEnabled();
        for (int i = 0; i < B_COUNT; i++) drawCell(c, i, on);
        if (picking) drawPicker(c);
    }

    // one line of text centred on (cx, cy), shrunk to fit the width `fit`
    private void label(Canvas c, String s, float cx, float cy, float size, float fit, int colour) {
        text.setTextAlign(Paint.Align.CENTER); text.setColor(colour); text.setTextSize(size);
        float tw = text.measureText(s);
        if (tw > fit) text.setTextSize(size * fit / tw);
        c.drawText(s, cx, cy - (text.descent() + text.ascent()) / 2, text);
    }

    // a picture centred in box, as large as fits but at most maxScale times its own size
    private void picture(Canvas c, Bitmap b, RectF box, float maxScale) {
        float s = Math.min(maxScale, Math.min(box.width() / b.getWidth(), box.height() / b.getHeight())), bw = b.getWidth() * s, bh = b.getHeight() * s;
        src.set(0, 0, b.getWidth(), b.getHeight());
        dst.set(box.centerX() - bw / 2, box.centerY() - bh / 2, box.centerX() + bw / 2, box.centerY() + bh / 2);
        c.drawBitmap(b, src, dst, bitmapPaint);
    }

    private boolean pressed(int i) { for (int d : downCell) if (d == i) return true; return false; }

    private void drawTabs(Canvas c) {
        for (int i = 0; i < TAB_COUNT; i++) {
            RectF r = tabRect[i];
            if (!art.draw(c, i == tab ? "tab_on" : "tab_off", null, r)) art.frame(c, "", null, r, 0, 1, i == tab ? PRESSED : PANEL);
            if (pressed(TAB_BASE + i)) { fill.setColor(0x50000000); c.drawRect(r, fill); }
            label(c, t(TAB_LABEL[i]), r.centerX(), r.centerY() - r.height() * 0.05f, r.height() * 0.5f, r.width() * 0.9f, i == tab ? TEXT : DIM);
        }
    }

    // ---- Main tab ----
    private float hx(float x) { return hudRect.left + x * hudRect.width() / HUD_W; }
    private float hy(float y) { return hudRect.top + y * hudRect.height() / HUD_H; }

    // a globe filled from the bottom up to v / max: the game's globe picture (blue-shifted for mana) cut at the level
    private void drawGlobe(Canvas c, float cx, int v, int max, boolean mana) {
        float s = hudRect.width() / HUD_W, x = hx(cx), y = hy(GLOBE_Y), r = GLOBE_RAD * s, a = GLOBE_ART * s;
        float frac = max > 0 ? Math.min(1f, Math.max(0f, v / (float) max)) : 0;
        c.save();
        c.clipRect(x - a, y + r - 2 * r * frac, x + a, y + a);
        Bitmap g = art.get("globe");
        if (g != null) { tmp.set(x - a, y - a, x + a, y + a); c.drawBitmap(g, null, tmp, mana ? manaPaint : bitmapPaint); }
        else { fill.setColor(mana ? MANA : LIFE); c.drawCircle(x, y, r, fill); }
        c.restore();
    }

    private void drawHud(Canvas c) {
        float s = hudRect.width() / HUD_W;
        drawGlobe(c, GLOBE_L, hud[H_HP], hud[H_HP_MAX], false);
        drawGlobe(c, GLOBE_R, hud[H_MP], hud[H_MP_MAX], true);
        if (!art.draw(c, "hud", null, hudRect)) {
            tmp.set(hx(HUD_INFO[0] - 20), hy(HUD_INFO[1] - 20), hx(HUD_PORTAL[2] + 20), hy(HUD_INFO[3] + 6));
            art.frame(c, "", null, tmp, 0, 1, PANEL);
            line.setColor(GOLD);
            c.drawCircle(hx(GLOBE_L), hy(GLOBE_Y), GLOBE_RAD * s, line); c.drawCircle(hx(GLOBE_R), hy(GLOBE_Y), GLOBE_RAD * s, line);
        } else {
            // the belt lives in the potion row below: cover the HUD's four gold belt boxes (they would read as an empty belt) with a
            // copy of the HUD's own plain box, the long one the area name sits in
            src.set((int) HUD_INFO[0], (int) HUD_INFO[1], (int) HUD_INFO[2], (int) HUD_INFO[3]);
            tmp.set(hx(HUD_GOLD[0]), hy(HUD_GOLD[1]), hx(HUD_GOLD[2]), hy(HUD_GOLD[3]));
            art.frame(c, "hud", src, tmp, 10, s, PANEL);
        }
        text.setTypeface(art.name(hud[H_LANG]));   // numbers in the game's Formal font: Exocet's 1 reads as I
        label(c, hud[H_HP] + "/" + hud[H_HP_MAX], hx(GLOBE_L), hy(GLOBE_Y), 34 * s, GLOBE_RAD * 1.8f * s, TEXT);
        label(c, hud[H_MP] + "/" + hud[H_MP_MAX], hx(GLOBE_R), hy(GLOBE_Y), 34 * s, GLOBE_RAD * 1.8f * s, TEXT);
        text.setTypeface(art.label(hud[H_LANG]));
        // the long box: area name on top, level and the experience bar under it
        float l = hx(HUD_INFO[0]) + 14 * s, rr = hx(HUD_INFO[2]) - 14 * s, t0 = hy(HUD_INFO[1]), bh = hy(HUD_INFO[3]) - t0;
        text.setTextAlign(Paint.Align.LEFT); text.setTextSize(bh * 0.32f);
        String area = names().area(hud[H_AREA], hud[H_LANG]);
        if (!area.isEmpty()) {
            float room = rr - l;
            if (text.measureText(area) > room) area = area.substring(0, Math.max(0, text.breakText(area, true, room - text.measureText("…"), null))) + "…";
            text.setColor(XP); c.drawText(area, l, t0 + bh * 0.42f, text);
        }
        String lv = String.format(t("dmDeckLevel"), hud[H_LEVEL]);
        text.setColor(TEXT); c.drawText(lv, l, t0 + bh * 0.84f, text);
        float xl = l + text.measureText(lv) + 14 * s, xy = t0 + bh * 0.72f, xh = 11 * s * 1.3f;
        if (xl < rr) {
            tmp.set(xl, xy - xh / 2, rr, xy + xh / 2); fill.setColor(0xC0000000); c.drawRect(tmp, fill);
            float frac = Math.min(1000, Math.max(0, hud[H_EXP])) / 1000f;
            c.save(); c.clipRect(xl, tmp.top, xl + (rr - xl) * frac, tmp.bottom);
            if (!art.draw(c, "expbar", null, tmp)) { fill.setColor(XP); c.drawRect(tmp, fill); }
            c.restore();
        }
        // the small right box: portal count, with the town portal book's picture
        tmp.set(hx(HUD_PORTAL[0]), hy(HUD_PORTAL[1]) - 10 * s, hx(HUD_PORTAL[2]), hy(HUD_PORTAL[3]) + 10 * s);
        Bitmap tbk = icons().load("tbk");
        if (tbk != null) { tmp2.set(tmp.left + 6 * s, tmp.top, tmp.left + 6 * s + tmp.height(), tmp.bottom); picture(c, tbk, tmp2, 10); }
        text.setTypeface(art.name(hud[H_LANG]));
        label(c, "×" + hud[H_PORTAL], tmp.centerX() + tmp.height() * 0.35f, tmp.centerY(), tmp.height() * 0.7f, tmp.width() * 0.55f, TEXT);
        text.setTypeface(art.label(hud[H_LANG]));
        // above the bar, between the globes: badges (one red word on death)
        float bx = getWidth() / 2f, by = hy(55);
        if (hud[H_DEAD] != 0) label(c, t("dmDeckDead"), bx, by, 48 * s, hx(1150) - hx(430), DEAD);
        else {
            String[] word = new String[3]; int[] col = new int[3]; int n = 0;
            if (hud[H_TOWN] != 0) { word[n] = t("dmDeckTown"); col[n++] = XP; }
            if (hud[H_POINTS] != 0) { word[n] = t("dmDeckPoints"); col[n++] = DEAD; }
            if (hud[H_QUEST] != 0) { word[n] = t("dmDeckQuest"); col[n++] = DEAD; }
            text.setTextAlign(Paint.Align.LEFT); text.setTextSize(34 * s);
            float sp = 40 * s, total = sp * Math.max(0, n - 1);
            for (int i = 0; i < n; i++) total += text.measureText(word[i]);
            float x = bx - total / 2;
            for (int i = 0; i < n; i++) { text.setColor(col[i]); c.drawText(word[i], x, by - (text.descent() + text.ascent()) / 2, text); x += text.measureText(word[i]) + sp; }
        }
        text.setTextAlign(Paint.Align.CENTER);
    }

    // a belt column's bottom-row item: its own picture from the item list (scrolls and other potions too), else the picture for its
    // kind, and how many the column holds
    private void beltBottle(Canvas c, int col, RectF r, float countSize) {
        int kind = Math.max(0, Math.min(4, hud[H_KIND + col]));
        Bitmap b = null;
        if (kind != 0) for (BagModel.Item it : bag) if (it.where == BagModel.BELT && it.x == col) { b = icons().get(it); break; }
        if (b == null && KIND_PICTURE[kind] != null) b = icons().load(KIND_PICTURE[kind]);
        if (b != null) picture(c, b, r, 2.2f);
        else if (kind != 0) { fill.setColor(KIND_COLOUR[kind]); c.drawCircle(r.centerX(), r.centerY(), Math.min(r.width(), r.height()) * 0.3f, fill); }
        if (hud[H_BELT + col] > 0) {
            text.setTypeface(art.name(hud[H_LANG]));
            text.setTextAlign(Paint.Align.RIGHT); text.setColor(TEXT); text.setTextSize(countSize);
            c.drawText(String.valueOf(hud[H_BELT + col]), r.right, r.bottom - text.descent() * 0.5f, text);
            text.setTextAlign(Paint.Align.CENTER); text.setTypeface(art.label(hud[H_LANG]));
        }
    }

    private void drawCell(Canvas c, int i, boolean on) {
        RectF r = cell[i];
        if (i >= B_BELT) {   // potion button: one belt-slot frame, the bottle and its count
            int col = i - B_BELT;
            src.set(0, 0, 61, 56);
            art.frame(c, "beltrow", src, r, 12, r.height() / 56 * 0.8f, KIND_COLOUR[Math.max(0, Math.min(4, hud[H_KIND + col]))]);
            tmp.set(r); tmp.inset(r.width() * 0.12f, r.height() * 0.1f);
            beltBottle(c, col, tmp, r.height() * 0.3f);
            if (pressed(i) || !on) { fill.setColor(pressed(i) ? 0x60000000 : 0x80000000); c.drawRect(r, fill); }
            return;
        }
        boolean down = pressed(i);
        art.frame(c, down ? "button_down" : on ? "button" : "button_off", null, r, 20, Math.min(r.width() / 330, r.height() / 60), down ? PRESSED : PANEL);
        String s;
        if (i < 4) s = t(TAB_KEY[i]);
        else if (i == B_PORTAL) s = t("dmDeckPortal");
        else if (i == B_MAP) s = t("dmDeckMap");
        else if (i == B_SWAP) s = t("dmDeckSwap");
        else s = String.format(t("dmDeckPlayers"), hud[H_PLAYERS]);
        label(c, s, r.centerX(), r.centerY(), Math.min(r.height() * 0.3f, 40 * getWidth() / 1240f), r.width() * 0.72f, on ? TEXT : DIM);
        if (i == B_MAP && hud[H_MAP_FULL] != 0) {   // full map is on
            fill.setColor(XP); c.drawCircle(r.right - r.height() * 0.6f, r.centerY(), r.height() * 0.08f, fill);
        }
    }

    private void drawPicker(Canvas c) {
        fill.setColor(0xE0000000); c.drawRect(0, cell[0].top, getWidth(), cell[B_BELT - 1].bottom, fill);
        for (int i = 0; i < 8; i++) {
            RectF r = pick[i]; boolean cur = hud[H_PLAYERS] == i + 1, down = pressed(PICK_BASE + i);
            art.frame(c, down || cur ? "button_down" : "button", null, r, 20, r.height() / 60, down || cur ? PRESSED : PANEL);
            label(c, String.valueOf(i + 1), r.centerX(), r.centerY(), r.height() * 0.42f, r.width(), cur ? XP : TEXT);
        }
    }

    private void setTab(int t) {
        if (t == tab) return;
        tab = t; picking = false; cancelAll();
        invalidate();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); nativeBagWant(true); bagGen = -1; }
    @Override protected void onDetachedFromWindow() { nativeBagWant(false); super.onDetachedFromWindow(); }

    // called with every snapshot (SecondScreen, 10 Hz): take the host's item list and redraw if it changed
    void pollBag() {
        int n = nativeBag(bagRaw);
        if (n < 0 || bagRaw[0] == bagGen) return;
        bagGen = bagRaw[0];
        bag = BagModel.decode(Arrays.copyOfRange(bagRaw, 1, bagRaw.length), n);
        if (tab == TAB_MAIN) invalidate();
    }

    // ---- touch ----
    private int hit(float x, float y) {
        for (int i = 0; i < TAB_COUNT; i++) if (tabRect[i].contains(x, y)) return TAB_BASE + i;
        if (picking) { for (int i = 0; i < 8; i++) if (pick[i].contains(x, y)) return PICK_BASE + i; return PICK_BASE - 1; }   // outside the picker = close it
        for (int i = 0; i < B_COUNT; i++) if (cell[i].contains(x, y)) return i;
        return -1;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int idx = e.getActionIndex(), id = e.getPointerId(idx);
        switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_POINTER_DOWN:
            if (id >= MAX_POINTERS || !actionsEnabled()) return true;
            downCell[id] = hit(e.getX(idx), e.getY(idx)); longFired[id] = false;
            if (tab == TAB_MAIN && downCell[id] >= B_BELT && downCell[id] < B_COUNT) handler.postDelayed(longPress[id], LONG_PRESS_MS);
            invalidate(); return true;
        case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP:
            if (id >= MAX_POINTERS) return true;
            handler.removeCallbacks(longPress[id]);
            int d = downCell[id]; downCell[id] = -1;
            // it counts only if the finger is still on the cell it went down on
            boolean same = d >= 0 && hit(e.getX(idx), e.getY(idx)) == d;
            if (d == PICK_BASE - 1) { if (same) picking = false; }
            else if (same && !longFired[id]) { performClick(); commit(d); }
            invalidate(); return true;
        case MotionEvent.ACTION_CANCEL:
            cancelAll(); invalidate(); return true;
        default: return true;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }   // accessibility: every button that takes effect reports a click

    private void cancelAll() { for (int i = 0; i < MAX_POINTERS; i++) { handler.removeCallbacks(longPress[i]); downCell[i] = -1; } }

    private void onLongPress(int pointer) {
        int d = downCell[pointer];
        if (tab != TAB_MAIN || d < B_BELT || d >= B_COUNT || !actionsEnabled()) return;
        longFired[pointer] = true;
        nativeAction(A_FEED_MERC, d - B_BELT);
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    private void commit(int d) {
        // highest range first: tabs, then the players picker, then Main's cells
        if (d >= TAB_BASE) { setTab(d - TAB_BASE); return; }
        if (d >= PICK_BASE) { nativeAction(A_PLAYERS, d - PICK_BASE + 1); picking = false; return; }
        if (d < 4) nativeAction(A_TAB, d);
        else if (d == B_PORTAL) nativeAction(A_PORTAL, 0);
        else if (d == B_MAP) nativeAction(A_MAP, 0);
        else if (d == B_SWAP) nativeAction(A_SWAP, 0);
        else if (d == B_PLAYERS) picking = true;
        else nativeAction(A_DRINK, d - B_BELT);
    }
}

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
//   Main: on top, the middle of the game's HUD bar as an info bar (area name, level and experience; where its belt boxes were, run
//     stats: time in this game, experience per hour and time to the next level, RunStats.java; in its small box, the mercenary's life),
//     badges under it, 8 large buttons (Inventory, Character, Skills, Quests / Portal with the portal count, Map, Swap weapons, Players),
//     and at the bottom the HUD's two ends, the life and mana globes, with the four belt columns between them as potion buttons
//     (tap = drink the bottom-row bottle, long press = feed the mercenary).
//   Map: a goal compass for the current area (GoalCompass.java): the host's map goals (quest goals, the true tomb, waypoints, real
//     exits) on a radar turned like the game's view, and a list nearest first with an arrow, the game's name and the distance.
//   Skills: the skills bound to the controller buttons, as the game's own icons and names (SkillBar.java): the four face buttons as a
//     diamond plus R and ZR, and the same again with ZL held. Read-only: bindings are changed in the game's skill menu.
// The host's item list (BagModel.java) is read while the Deck shows, for the belt's own pictures. Item management stays in the
// game's own inventory (the Inventory button): the bottom screen is for glancing and one-tap actions.
// All text goes through the string table (nativeText). SecondScreen reads a snapshot every 100 ms and passes it to update, which redraws
// only on change; press feedback is drawn at once. A touch takes effect on release, and CANCEL means undo: right after the bottom screen
// appears, the first touch arrives as DOWN then CANCEL.
final class ThorDeck extends View {
    // snapshot indices, matching deck_jni.c
    static final int H_VALID = 0, H_LEVEL = 1, H_EXP = 2, H_HP = 3, H_HP_MAX = 4, H_MP = 5, H_MP_MAX = 6, H_BELT = 7, H_KIND = 11, H_PORTAL = 15,
        H_PLAYERS = 16, H_POINTS = 17, H_QUEST = 18, H_DEAD = 19, H_TOWN = 20, H_SWITCH = 21, H_MAP_FULL = 22, H_AREA = 23, H_LANG = 24, H_WSET = 25, H_MERC_HP = 26, H_MERC_MAX = 27, H_COUNT = 28;
    // action numbers, matching deck_jni.c
    static final int A_DRINK = 0, A_FEED_MERC = 1, A_PORTAL = 2, A_MAP = 3, A_SWAP = 4, A_TAB = 5, A_PLAYERS = 6;
    static native int nativeHud(int[] out);
    static native void nativeAction(int action, int arg);
    static native String nativeText(String key);
    static native void nativeBagWant(boolean on);
    static native int nativeBag(int[] out);
    static native int nativeGoals(int[] out);
    static native int nativeSkills(int[] out);

    private static final long LONG_PRESS_MS = 500;
    // twelve cells: 0–3 the four function pages, 4 portal, 5 map, 6 swap, 7 players, 8–11 the four belt columns
    private static final int B_PORTAL = 4, B_MAP = 5, B_SWAP = 6, B_PLAYERS = 7, B_BELT = 8, B_COUNT = 12;
    private static final String[] TAB_KEY = { "dmDeckInventory", "dmDeckCharacter", "dmDeckSkills", "dmDeckQuests" };
    private static final int BG = 0xFF0E0B08, PANEL = DeckArt.PANEL, PRESSED = DeckArt.PRESSED, GOLD = DeckArt.GOLD, TEXT = 0xFFE8D9B0, DIM = 0xFF8A7F68,
        LIFE = 0xFFB0202A, MANA = 0xFF2A4FC0, XP = 0xFFC7A04A, DEAD = 0xFFE04030;
    private static final int[] KIND_COLOUR = { 0xFF2A2520, LIFE, MANA, 0xFF8A3AB0, 0xFF6B6B6B };   // empty / red / blue / purple / other
    private static final String[] KIND_PICTURE = { null, "hp4", "mp4", "rvl", null };   // the picture a belt column's kind is drawn with
    // where things sit on the game's HUD bar art (deck/ui/hud.png, 1582 x 206 pixels): the middle of the bar (BAR) is the info bar on top,
    // its two ends (SIDE_L / SIDE_R: statue and globe) sit at the ends of the potion row
    private static final float HUD_H = 206, GLOBE_L = 222, GLOBE_R = 1376, GLOBE_Y = 117, GLOBE_RAD = 83, GLOBE_ART = 87;
    // the bar's experience channel along its top edge: ten segments (start, end) between the tick marks, and its top and bottom
    private static final int[] XP_SEG = { 329, 411, 417, 506, 513, 602, 609, 698, 704, 794, 800, 890, 897, 986, 993, 1082, 1088, 1178, 1184, 1264 }, XP_Y = { 101, 109 };
    private static final int[] BAR = { 300, 84, 1285, 206 }, SIDE_L = { 0, 0, 312, 206 }, SIDE_R = { 1270, 0, 1582, 206 };
    private static final float[] HUD_INFO = { 330, 118, 745, 196 }, HUD_GOLD = { 849, 116, 1119, 197 }, HUD_PORTAL = { 1128, 140, 1245, 170 };

    private final int[] hud = new int[H_COUNT];
    private final RectF[] cell = new RectF[B_COUNT];
    private final RectF[] pick = new RectF[8];
    private final RectF hudRect = new RectF(), sideL = new RectF(), sideR = new RectF(), full = new RectF(), tmp = new RectF(), tmp2 = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    // which cell each finger is on (-1 none; PICK_BASE + n = cell n of the players picker), and whether its long press already fired
    private static final int PICK_BASE = 100, MAX_POINTERS = 10;
    private final int[] downCell = new int[MAX_POINTERS];
    private final boolean[] longFired = new boolean[MAX_POINTERS];
    private final Runnable[] longPress = new Runnable[MAX_POINTERS];
    private boolean picking;   // the players 1–8 picker is open
    // tabs. Kept across shows of the Deck (a new view is made each time the bottom screen comes back)
    private static final int TAB_MAIN = 0, TAB_MAP = 1, TAB_SKILLS = 2, TAB_COUNT = 3, TAB_BASE = 200;
    private static final String[] TAB_LABEL = { "dmDeckTabMain", "dmDeckMap", "dmDeckSkills" };
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
    private static final RunStats run = new RunStats();   // kept across shows of the Deck, like the tab
    private String runLine = "";   // the run stats as last drawn: the clock ticks every second, the snapshot does not
    // Map tab: the goals as last read (nativeGoals) and their names (deck/goals.tsv)
    private final int[] goalRaw = new int[3 + 3 * 24], goalLast = new int[3 + 3 * 24];
    private GoalCompass.Goal[] goals = new GoalCompass.Goal[0];
    private DeckNames goalNames;
    private final RectF radar = new RectF(), list = new RectF();
    private final android.graphics.Path arrow = new android.graphics.Path();
    // Skills tab: the bindings as last read (nativeSkills), skill names (deck/skills.tsv) and the game's skill icon atlases
    private final int[] skillRaw = new int[12 * SkillBar.INTS], skillLast = new int[12 * SkillBar.INTS];
    private SkillBar.Slot[] skills = new SkillBar.Slot[0];
    private DeckNames skillNames;
    private final Bitmap[] skillAtlas = new Bitmap[9];
    private final boolean[] skillAtlasTried = new boolean[9];
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
        run.sample(android.os.SystemClock.elapsedRealtime(), next[H_VALID] != 0 && next[H_DEAD] == 0, next[H_LEVEL], next[H_EXP]);
        if (Arrays.equals(hud, next)) {
            if (tab != TAB_MAIN || runText(0).equals(runLine)) return false;
            invalidate(); return true;
        }
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
        float k = w / 1240f, gap = 14 * k, pad = 18 * k, tw = (w - 2 * pad - (TAB_COUNT - 1) * gap) / TAB_COUNT, tabH = 58 * k;
        full.set(0, 0, w, h);
        for (int i = 0; i < TAB_COUNT; i++) tabRect[i].set(pad + i * (tw + gap), pad, pad + i * (tw + gap) + tw, pad + tabH);
        float top = pad + tabH + gap;
        // Main: the info bar across the whole width, a row of badges, 8 buttons in 2 columns, then the bottom row: life globe, the four
        // potion buttons, mana globe
        hudRect.set(0, top, w, top + w * (BAR[3] - BAR[1]) / (BAR[2] - BAR[0]));
        float sideH = 190 * k, sideW = sideH * (SIDE_L[2] - SIDE_L[0]) / HUD_H, stripTop = h - sideH;
        sideL.set(0, stripTop, sideW, h);
        sideR.set(w - sideW, stripTop, w, h);
        float by = hudRect.bottom + BADGE_H * k + gap, bw = (w - 2 * pad - gap) / 2, bh = (stripTop - gap - by - 3 * gap) / 4;
        for (int i = 0; i < B_BELT; i++) {
            float x = pad + (i % 2) * (bw + gap), y = by + (i / 2) * (bh + gap);
            cell[i].set(x, y, x + bw, y + bh);
        }
        // the potions centred on the globes' height, between them
        float potH = 140 * k, gy = stripTop + GLOBE_Y * sideH / HUD_H, py = Math.min(gy - potH / 2, h - pad - potH);
        float pl = sideW + gap, pw4 = (w - 2 * sideW - 2 * gap - 3 * gap) / 4;
        for (int i = 0; i < 4; i++) cell[B_BELT + i].set(pl + i * (pw4 + gap), py, pl + i * (pw4 + gap) + pw4, py + potH);
        // the players picker covers the middle two button rows: 4 x 2
        float pt = cell[2].top, ph = (cell[5].bottom - pt - gap) / 2, pw = (w - 2 * pad - 3 * gap) / 4;
        for (int i = 0; i < 8; i++) { float x = pad + (i % 4) * (pw + gap), y = pt + (i / 4) * (ph + gap); pick[i].set(x, y, x + pw, y + ph); }
        // Map: the radar as a square on the left, the goal list beside it
        float bodyH = h - pad - top, rs = Math.min(bodyH, w * 0.5f);
        radar.set(pad, top + (bodyH - rs) / 2, pad + rs, top + (bodyH + rs) / 2);
        list.set(radar.right + 2 * gap, top, w - pad, h - pad);
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
        if (tab == TAB_MAP) { drawMap(c); return; }
        if (tab == TAB_SKILLS) { drawSkills(c); return; }
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
    // a point on the HUD art's middle (BAR) to the info bar on screen
    private float hx(float x) { return hudRect.left + (x - BAR[0]) * hudRect.width() / (BAR[2] - BAR[0]); }
    private float hy(float y) { return hudRect.top + (y - BAR[1]) * hudRect.height() / (BAR[3] - BAR[1]); }

    // one end of the bottom row: the globe filled from the bottom up to v / max (the game's globe picture, blue-shifted for mana, cut at
    // the level), the HUD's end over it (statue and glass), and the numbers
    private void drawSide(Canvas c, RectF side, int[] crop, float globeX, int v, int max, boolean mana) {
        float sc = side.height() / HUD_H, x = side.left + (globeX - crop[0]) * sc, y = side.top + GLOBE_Y * sc, r = GLOBE_RAD * sc, a = GLOBE_ART * sc;
        float frac = max > 0 ? Math.min(1f, Math.max(0f, v / (float) max)) : 0;
        c.save();
        c.clipRect(x - a, y + r - 2 * r * frac, x + a, y + a);
        Bitmap g = art.get("globe");
        if (g != null) { tmp.set(x - a, y - a, x + a, y + a); c.drawBitmap(g, null, tmp, mana ? manaPaint : bitmapPaint); }
        else { fill.setColor(mana ? MANA : LIFE); c.drawCircle(x, y, r, fill); }
        c.restore();
        src.set(crop[0], crop[1], crop[2], crop[3]);
        if (!art.draw(c, "hud", src, side)) { line.setColor(GOLD); c.drawCircle(x, y, r, line); }
        text.setTypeface(art.name(hud[H_LANG]));   // numbers in the game's Formal font: Exocet's 1 reads as I
        label(c, v + "/" + max, x, y, 40 * sc, r * 1.8f, TEXT);
        text.setTypeface(art.label(hud[H_LANG]));
    }

    private void drawHud(Canvas c) {
        float s = hudRect.width() / (BAR[2] - BAR[0]), k = getWidth() / 1240f;
        // the bottom row's ends
        drawSide(c, sideL, SIDE_L, GLOBE_L, hud[H_HP], hud[H_HP_MAX], false);
        drawSide(c, sideR, SIDE_R, GLOBE_R, hud[H_MP], hud[H_MP_MAX], true);
        // the info bar
        src.set(BAR[0], BAR[1], BAR[2], BAR[3]);
        if (!art.draw(c, "hud", src, hudRect)) art.frame(c, "", null, hudRect, 0, 1, PANEL);
        else {
            // cover the HUD's four gold belt boxes (they would read as an empty belt) with a copy of the HUD's own plain box, the long
            // one the area name sits in
            src.set((int) HUD_INFO[0], (int) HUD_INFO[1], (int) HUD_INFO[2], (int) HUD_INFO[3]);
            tmp.set(hx(HUD_GOLD[0]), hy(HUD_GOLD[1]), hx(HUD_GOLD[2]), hy(HUD_GOLD[3]));
            art.frame(c, "hud", src, tmp, 10, s, PANEL);
        }
        // in that box: time in this game, then experience per hour and time to the next level
        tmp.set(hx(HUD_GOLD[0]), hy(HUD_GOLD[1]), hx(HUD_GOLD[2]), hy(HUD_GOLD[3]));
        runLine = runText(0);
        float rh = tmp.height(), fit = tmp.width() * 0.88f;
        text.setTypeface(art.name(hud[H_LANG]));   // numbers again: Formal
        label(c, runLine, tmp.centerX(), tmp.top + rh * 0.36f, rh * 0.3f, fit, TEXT);
        label(c, runText(1), tmp.centerX(), tmp.top + rh * 0.7f, rh * 0.26f, fit, XP);
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
        int exp = Math.min(1000, Math.max(0, hud[H_EXP]));
        text.setColor(TEXT); c.drawText(lv, l, t0 + bh * 0.84f, text);
        float pl = l + text.measureText(lv) + 18 * s;
        text.setTypeface(art.name(hud[H_LANG])); text.setColor(DIM);
        c.drawText(exp / 10 + "%", pl, t0 + bh * 0.84f, text);
        text.setTypeface(art.label(hud[H_LANG]));
        // experience: in the bar's own channel along its top edge, the ten segments between its tick marks, as in the game
        float frac = exp / 1000f, xEnd = hx(XP_SEG[0] + (XP_SEG[XP_SEG.length - 1] - XP_SEG[0]) * frac);
        // the game's own fill (deck/ui/expbar.png, 938 x 11: the channel's size), cut at the level; flat segments without it
        tmp.set(hx(XP_SEG[0]), hy(XP_Y[0] - 1), hx(XP_SEG[0] + 938), hy(XP_Y[0] + 10));
        c.save(); c.clipRect(tmp.left, tmp.top, xEnd, tmp.bottom);
        boolean drawn = art.draw(c, "expbar", null, tmp);
        c.restore();
        if (!drawn) for (int i = 0; i + 1 < XP_SEG.length; i += 2) {
            float a = hx(XP_SEG[i]), b = Math.min(hx(XP_SEG[i + 1]), xEnd);
            if (b <= a) break;
            tmp.set(a, hy(XP_Y[0]), b, hy(XP_Y[1]));
            fill.setColor(XP); c.drawRect(tmp, fill);
        }
        // the small right box: the mercenary's life, a red bar with the word on it (empty without a mercenary)
        if (hud[H_MERC_MAX] > 0) {
            tmp.set(hx(HUD_PORTAL[0]) + 4 * s, hy(HUD_PORTAL[1]), hx(HUD_PORTAL[2]) - 4 * s, hy(HUD_PORTAL[3]));
            float mf = Math.min(1f, Math.max(0f, hud[H_MERC_HP] / (float) hud[H_MERC_MAX]));
            fill.setColor(0xC0000000); c.drawRect(tmp, fill);
            tmp2.set(tmp); tmp2.right = tmp.left + tmp.width() * mf; fill.setColor(mf < 0.3f ? DEAD : LIFE); c.drawRect(tmp2, fill);
            label(c, t("dmDeckMerc"), tmp.centerX(), tmp.centerY(), tmp.height() * 0.62f, tmp.width() * 0.9f, TEXT);
        }
        // under the bar: badges (one red word on death)
        float by = hudRect.bottom + BADGE_H * k / 2;
        if (hud[H_DEAD] != 0) label(c, t("dmDeckDead"), getWidth() / 2f, by, 38 * k, getWidth() * 0.8f, DEAD);
        else {
            String[] word = new String[3]; int[] col = new int[3]; int n = 0;
            if (hud[H_TOWN] != 0) { word[n] = t("dmDeckTown"); col[n++] = XP; }
            if (hud[H_POINTS] != 0) { word[n] = t("dmDeckPoints"); col[n++] = DEAD; }
            if (hud[H_QUEST] != 0) { word[n] = t("dmDeckQuest"); col[n++] = DEAD; }
            text.setTextAlign(Paint.Align.LEFT); text.setTextSize(30 * k);
            float sp = 48 * k, total = sp * Math.max(0, n - 1);
            for (int i = 0; i < n; i++) total += text.measureText(word[i]);
            float x = getWidth() / 2f - total / 2;
            for (int i = 0; i < n; i++) { text.setColor(col[i]); c.drawText(word[i], x, by - (text.descent() + text.ascent()) / 2, text); x += text.measureText(word[i]) + sp; }
        }
        text.setTextAlign(Paint.Align.CENTER);
    }

    // run stats line 0: time in this game; line 1: experience per hour and time to the next level ("XP -" until known)
    private String runText(int line) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (line == 0) return String.format(t("dmDeckRun"), RunStats.clock(Math.max(0, run.elapsed(now))));
        double p = run.percentPerHour(now);
        if (p < 0) return String.format(t("dmDeckXpRate"), "-");
        String rate = String.format(t("dmDeckXpRate"), "+" + (p >= 10 ? Math.round(p) : Math.round(p * 10) / 10.0) + "%");
        long eta = run.etaMs(now);
        return eta < 0 ? rate : rate + "  " + String.format(t("dmDeckNextLv"), RunStats.span(eta));
    }

    // ---- Map tab ----
    private static final int GOAL_QUEST = 0xFFC7A04A, GOAL_TOMB = 0xFFE04030, GOAL_WAYPOINT = 0xFF7FA8FF, GOAL_EXIT = 0xFFE8D9B0;
    private static final int GOALS_SHOWN = 6;
    private static final float BADGE_H = 46;   // the badge row under the info bar, in 1240-wide pixels

    private int goalColour(int art) {
        return art >= GoalCompass.LEVEL ? GOAL_EXIT : art == GoalCompass.WAYPOINT ? GOAL_WAYPOINT : art >= GoalCompass.SYMBOL ? GOAL_TOMB : GOAL_QUEST;
    }

    // the game's name for a goal in its language: exits by the area they lead to, the rest from deck/goals.tsv
    private String goalName(int art) {
        int lv = GoalCompass.exitLevel(art);
        if (lv >= 0) return names().area(lv, hud[H_LANG]);
        if (goalNames == null) {
            String tsv = "";
            File f = new File(getContext().getFilesDir(), "dm/overlay/deck/goals.tsv");
            try {
                if (f.isFile() && f.length() <= 1024 * 1024) tsv = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            } catch (java.io.IOException | SecurityException e) { tsv = ""; }
            goalNames = DeckNames.parse(tsv);
        }
        String n = goalNames.name(GoalCompass.nameKey(art), hud[H_LANG]);
        return n.isEmpty() ? "?" : n;
    }

    // a small arrow at (x, y) pointing along the unit direction (dx, dy)
    private void drawArrow(Canvas c, float x, float y, double dx, double dy, float size, int colour) {
        float a = (float) Math.atan2(dy, dx);
        arrow.reset();
        arrow.moveTo(size, 0); arrow.lineTo(-size * 0.6f, size * 0.6f); arrow.lineTo(-size * 0.25f, 0); arrow.lineTo(-size * 0.6f, -size * 0.6f); arrow.close();
        c.save(); c.translate(x, y); c.rotate((float) Math.toDegrees(a));
        fill.setColor(colour); c.drawPath(arrow, fill);
        c.restore();
    }

    private void drawMap(Canvas c) {
        float k = getWidth() / 1240f;
        int n = Math.min(GOALS_SHOWN, goals.length);
        float cx = radar.centerX(), cy = radar.centerY(), R = radar.width() / 2 - 12 * k;
        // radar: dark disc, three rings, the player in the middle; goals at their screen direction, distance scaled to the farthest shown
        fill.setColor(0xB0000000); c.drawCircle(cx, cy, R, fill);
        line.setColor(0x808C7340);
        for (int i = 1; i <= 3; i++) c.drawCircle(cx, cy, R * i / 3, line);
        line.setColor(GOLD); c.drawCircle(cx, cy, R, line);
        fill.setColor(TEXT); c.drawCircle(cx, cy, 8 * k, fill);
        double far = 60;
        for (int i = 0; i < n; i++) far = Math.max(far, goals[i].dist);
        text.setTypeface(art.name(hud[H_LANG]));
        for (int i = n - 1; i >= 0; i--) {
            GoalCompass.Goal g = goals[i];
            float r = (float) (R * 0.92 * Math.min(1, g.dist / far)), x = cx + (float) g.sx * r, y = cy + (float) g.sy * r;
            fill.setColor(goalColour(g.art)); c.drawCircle(x, y, 16 * k, fill);
            label(c, String.valueOf(i + 1), x, y, 22 * k, 30 * k, 0xFF000000);
        }
        // list: number, arrow, name, distance
        if (n == 0) {
            text.setTypeface(art.label(hud[H_LANG]));
            label(c, t("dmDeckNoGoals"), list.centerX(), list.centerY(), 36 * k, list.width() * 0.95f, DIM);
            return;
        }
        float rowH = Math.min(list.height() / GOALS_SHOWN, 150 * k), gap = 10 * k;
        for (int i = 0; i < n; i++) {
            GoalCompass.Goal g = goals[i];
            tmp.set(list.left, list.top + i * rowH, list.right, list.top + (i + 1) * rowH - gap);
            art.frame(c, "button_off", null, tmp, 20, Math.min(tmp.width() / 330, tmp.height() / 60), PANEL);
            float mid = tmp.centerY(), x0 = tmp.left + tmp.height() * 0.55f;
            fill.setColor(goalColour(g.art)); c.drawCircle(x0, mid, tmp.height() * 0.2f, fill);
            label(c, String.valueOf(i + 1), x0, mid, tmp.height() * 0.24f, tmp.height() * 0.3f, 0xFF000000);
            drawArrow(c, x0 + tmp.height() * 0.55f, mid, g.sx, g.sy, tmp.height() * 0.22f, TEXT);
            // name on top, distance on its own line under it, both shrunk to the room left of the frame's right edge
            float nameX = x0 + tmp.height() * 0.95f, room = tmp.right - tmp.height() * 0.4f - nameX;
            String name = goalName(g.art), d = String.format(t("dmDeckGoalAway"), Math.round(g.dist));
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(goalColour(g.art)); text.setTextSize(tmp.height() * 0.28f);
            if (text.measureText(name) > room) text.setTextSize(text.getTextSize() * room / text.measureText(name));
            c.drawText(name, nameX, mid - tmp.height() * 0.06f, text);
            text.setColor(DIM); text.setTextSize(tmp.height() * 0.21f);
            if (text.measureText(d) > room) text.setTextSize(text.getTextSize() * room / text.measureText(d));
            c.drawText(d, nameX, mid + tmp.height() * 0.06f - text.ascent(), text);
        }
        text.setTextAlign(Paint.Align.CENTER); text.setTypeface(art.label(hud[H_LANG]));
    }

    // ---- Skills tab ----
    private Bitmap skillAtlas(int a) {
        if (a < 0 || a >= skillAtlas.length) return null;
        if (!skillAtlasTried[a]) {
            skillAtlasTried[a] = true;
            File f = new File(getContext().getFilesDir(), "dm/overlay/touchart/dm_native_skill_" + a + ".png");
            if (f.isFile() && f.length() <= 8 * 1024 * 1024) skillAtlas[a] = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
        }
        return skillAtlas[a];
    }

    private String skillName(int id) {
        if (skillNames == null) {
            String tsv = "";
            File f = new File(getContext().getFilesDir(), "dm/overlay/deck/skills.tsv");
            try {
                if (f.isFile() && f.length() <= 1024 * 1024) tsv = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            } catch (java.io.IOException | SecurityException e) { tsv = ""; }
            skillNames = DeckNames.parse(tsv);
        }
        return skillNames.name(String.valueOf(id), hud[H_LANG]);
    }

    // one bound skill: its icon in a dark square (empty square when nothing is bound), the button's name in the corner, the skill's name under it
    private void drawSkill(Canvas c, SkillBar.Slot s, String face, float cx, float cy, float size, float nameW) {
        float k = getWidth() / 1240f;
        tmp.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        fill.setColor(0xC0000000); c.drawRect(tmp, fill);
        Bitmap a = s == null ? null : skillAtlas(s.atlas);
        if (a != null && s.w > 0) { src.set(s.x, s.y, s.x + s.w, s.y + s.h); c.drawBitmap(a, src, tmp, bitmapPaint); }
        line.setColor(s != null && s.id >= 0 ? GOLD : 0x608C7340); c.drawRect(tmp, line);
        text.setTypeface(art.label(hud[H_LANG]));
        text.setTextAlign(Paint.Align.LEFT); text.setColor(TEXT); text.setTextSize(26 * k);
        c.drawText(face, tmp.left + 6 * k, tmp.top - text.ascent() + 4 * k, text);
        if (s != null && s.id >= 0) {
            text.setTypeface(art.name(hud[H_LANG]));
            String n = skillName(s.id);
            label(c, n.isEmpty() ? "#" + s.id : n, cx, tmp.bottom + 22 * k, 24 * k, nameW, XP);
        }
        text.setTextAlign(Paint.Align.CENTER); text.setTypeface(art.label(hud[H_LANG]));
    }

    private void drawSkills(Canvas c) {
        float w = getWidth(), h = getHeight(), k = w / 1240f, pad = 18 * k, gap = 14 * k, top = tabRect[0].bottom + gap;
        float pw = (w - 2 * pad - gap) / 2, size = 116 * k, d = 160 * k;
        for (int set = 0; set < 2; set++) {
            float l = pad + set * (pw + gap), cx = l + pw / 2;
            tmp2.set(l, top, l + pw, h - pad - 60 * k);
            fill.setColor(0x90000000); c.drawRect(tmp2, fill); line.setColor(0x808C7340); c.drawRect(tmp2, line);
            label(c, t(set == 0 ? "dmDeckSkillsNormal" : "dmDeckSkillsZl"), cx, top + 36 * k, 34 * k, pw * 0.9f, TEXT);
            // the shoulders on top, then the four face buttons as a diamond
            float sy = top + 90 * k + size / 2;
            for (int f = 4; f < 6; f++) {
                float x = cx + (f == 4 ? -1 : 1) * d * 0.8f;
                drawSkill(c, slot(set * 6 + f), SkillBar.FACE_NAME[f], x, sy, size, d * 1.5f);
            }
            float dy = sy + size / 2 + 60 * k + d + size / 2;
            for (int f = 0; f < 4; f++)
                drawSkill(c, slot(set * 6 + f), SkillBar.FACE_NAME[f], cx + SkillBar.FACE_DX[f] * d, dy + SkillBar.FACE_DY[f] * d, size, d * 1.1f);
        }
        label(c, t("dmDeckSkillsHint"), w / 2, h - pad - 28 * k, 26 * k, w - 2 * pad, DIM);
    }

    private SkillBar.Slot slot(int i) { return i < skills.length ? skills[i] : null; }

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
        float lx = r.centerX(), fitW = r.width() * 0.72f;
        if (i == B_PORTAL) {   // the town portal book's picture and how many portals are left, right of the word
            lx = r.left + r.width() * 0.4f; fitW = r.width() * 0.4f;
            float ih = r.height() * 0.5f, ix = r.left + r.width() * 0.64f;
            Bitmap tbk = icons().load("tbk");
            if (tbk != null) { tmp2.set(ix, r.centerY() - ih / 2, ix + ih, r.centerY() + ih / 2); picture(c, tbk, tmp2, 10); }
            text.setTypeface(art.name(hud[H_LANG]));
            text.setTextAlign(Paint.Align.LEFT); text.setTextSize(r.height() * 0.3f); text.setColor(!on ? DIM : hud[H_PORTAL] > 0 ? TEXT : DEAD);
            c.drawText("×" + hud[H_PORTAL], ix + ih + r.width() * 0.02f, r.centerY() - (text.descent() + text.ascent()) / 2, text);
            text.setTextAlign(Paint.Align.CENTER); text.setTypeface(art.label(hud[H_LANG]));
        }
        label(c, s, lx, r.centerY(), Math.min(r.height() * 0.3f, 40 * getWidth() / 1240f), fitW, on ? TEXT : DIM);
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

    // called with every snapshot (SecondScreen, 10 Hz)
    void poll() { pollBag(); pollGoals(); pollSkills(); }

    // Skills tab: redraw when a binding changes
    private void pollSkills() {
        if (tab != TAB_SKILLS) return;
        int n = nativeSkills(skillRaw);
        if (n < 0) return;
        if (n == 0) java.util.Arrays.fill(skillRaw, -1);
        if (Arrays.equals(skillRaw, skillLast)) return;
        System.arraycopy(skillRaw, 0, skillLast, 0, skillRaw.length);
        skills = SkillBar.decode(skillRaw, n);
        invalidate();
    }

    // the host's item list (for the belt's pictures): redraw if it changed
    private void pollBag() {
        int n = nativeBag(bagRaw);
        if (n < 0 || bagRaw[0] == bagGen) return;
        bagGen = bagRaw[0];
        bag = BagModel.decode(Arrays.copyOfRange(bagRaw, 1, bagRaw.length), n);
        if (tab == TAB_MAIN) invalidate();
    }

    // Map tab: the goals for the current area, redrawn when they or the player's cell change
    private void pollGoals() {
        if (tab != TAB_MAP) return;
        int n = nativeGoals(goalRaw);
        if (n < 0 || Arrays.equals(goalRaw, goalLast)) return;
        System.arraycopy(goalRaw, 0, goalLast, 0, goalRaw.length);
        goals = GoalCompass.read(Arrays.copyOf(goalRaw, 3 + 3 * n));
        invalidate();
    }

    // ---- touch ----
    private int hit(float x, float y) {
        for (int i = 0; i < TAB_COUNT; i++) if (tabRect[i].contains(x, y)) return TAB_BASE + i;
        if (tab != TAB_MAIN) return -1;   // the Map tab is to look at
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

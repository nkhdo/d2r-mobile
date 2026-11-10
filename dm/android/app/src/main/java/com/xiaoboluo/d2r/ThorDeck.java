package com.xiaoboluo.d2r;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

// Bottom-screen Deck: status and large buttons on the second screen of dual-screen handhelds (for controller play; SecondScreen.java handles showing and hiding). A status strip on top (life / mana / level and experience / badges),
// 4 x 3 large buttons below: Inventory, Character, Skills, Quests / Portal, Map, Swap weapons, Players / the four belt columns (tap = drink the bottom-row bottle, long press = feed the mercenary).
// Drawn on the canvas only (no game art); all text goes through the string table (nativeText). SecondScreen reads a snapshot every 100 ms and passes it to update, which redraws only on change; press feedback is drawn at once.
// A touch takes effect on release, and CANCEL means undo: right after the bottom screen appears, the first touch arrives as DOWN then CANCEL
// A tab row on top switches between Main (the above) and Bag (read only: worn items, the bag grid and the belt, with the player's own item
// pictures; BagModel.java). The host reads the items only while Bag is showing.
final class ThorDeck extends View {
    // snapshot indices, matching deck_jni.c
    static final int H_VALID = 0, H_LEVEL = 1, H_EXP = 2, H_HP = 3, H_HP_MAX = 4, H_MP = 5, H_MP_MAX = 6, H_BELT = 7, H_KIND = 11, H_PORTAL = 15,
        H_PLAYERS = 16, H_POINTS = 17, H_QUEST = 18, H_DEAD = 19, H_TOWN = 20, H_SWITCH = 21, H_MAP_FULL = 22, H_AREA = 23, H_LANG = 24, H_COUNT = 25;
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
    private static final int BG = 0xFF0E0B08, PANEL = 0xFF1C1712, PRESSED = 0xFF3A2E1C, GOLD = 0xFF8C7340, TEXT = 0xFFE8D9B0, DIM = 0xFF6B6250,
        LIFE = 0xFFB0202A, MANA = 0xFF2A4FC0, XP = 0xFFC7A04A, DEAD = 0xFFE04030;
    // item quality colours (1 low ... 8 crafted), as the game colours item names
    private static final int[] QUALITY_COLOUR = { 0xFF6B6250, 0xFF8A8A8A, 0xFFB8B8B8, 0xFFB8B8B8, 0xFF6F6FFF, 0xFF1EC81E, 0xFFE8E85A, 0xFFC7A04A, 0xFFE89A2A };
    private static final int[] KIND_COLOUR = { 0xFF2A2520, LIFE, MANA, 0xFF8A3AB0, 0xFF6B6B6B };   // empty / red / blue / purple / other

    private final int[] hud = new int[H_COUNT];
    private final RectF[] cell = new RectF[B_COUNT];
    private final RectF[] pick = new RectF[8];
    private final RectF strip = new RectF(), tmp = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    // which cell each finger is on (-1 none; PICK_BASE + n = cell n of the players picker), and whether its long press already fired
    private static final int PICK_BASE = 100, MAX_POINTERS = 10;
    private final int[] downCell = new int[MAX_POINTERS];
    private final boolean[] longFired = new boolean[MAX_POINTERS];
    private final Runnable[] longPress = new Runnable[MAX_POINTERS];
    private boolean picking;   // the players 1–8 picker is open
    // tabs: 0 Main, 1 Bag. Kept across shows of the Deck (a new view is made each time the bottom screen comes back)
    private static final int TAB_MAIN = 0, TAB_BAG = 1, TAB_COUNT = 2, TAB_BASE = 200;
    private static final String[] TAB_LABEL = { "dmDeckTabMain", "dmDeckTabBag" };
    private static int tab = TAB_MAIN;
    private final RectF[] tabRect = new RectF[TAB_COUNT];
    private BagModel.Layout bagLayout;
    private BagModel.Item[] bag = new BagModel.Item[0];
    private final int[] bagRaw = new int[1 + 128 * BagModel.INTS];
    private int bagGen = -1;
    private DeckIcons icons;
    private final android.graphics.Rect src = new android.graphics.Rect();
    private final RectF dst = new RectF();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
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

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float k = w / 1240f, gap = 14 * k, pad = 18 * k, tabH = 76 * k, tw = (w - 2 * pad - 2 * gap) / 3;
        for (int i = 0; i < TAB_COUNT; i++) tabRect[i].set(pad + i * (tw + gap), pad, pad + i * (tw + gap) + tw, pad + tabH);
        float body = pad + tabH + gap;
        strip.set(pad, body, w - pad, body + h * 0.2f);
        bagLayout = new BagModel.Layout(pad, body, w - 2 * pad, h - body - pad, gap);
        float top = strip.bottom + gap, cw = (w - 2 * pad - 3 * gap) / 4, ch = (h - pad - top - 2 * gap) / 3;
        for (int i = 0; i < B_COUNT; i++) {
            int r = i / 4, c = i % 4; float x = pad + c * (cw + gap), y = top + r * (ch + gap);
            cell[i].set(x, y, x + cw, y + ch);
        }
        // the players picker covers the middle row: 8 cells
        float py = cell[4].top, ph = cell[4].height(), pw = (w - 2 * pad - 7 * gap) / 8;
        for (int i = 0; i < 8; i++) { float x = pad + i * (pw + gap); pick[i].set(x, py, x + pw, py + ph); }
        line.setStrokeWidth(Math.max(2, 3 * k));
    }

    @Override protected void onDraw(Canvas c) {
        c.drawColor(BG);
        float h = getHeight();
        if (hud[H_VALID] == 0) {   // not in a run yet: just one line of explanation
            text.setColor(DIM); text.setTextSize(h * 0.045f);
            c.drawText(t("dmDeckIdle"), getWidth() / 2f, h / 2f, text);
            return;
        }
        drawTabs(c);
        if (tab == TAB_BAG) { drawBag(c); return; }
        drawStrip(c);
        boolean on = actionsEnabled();
        for (int i = 0; i < B_COUNT; i++) drawCell(c, i, on);
        if (picking) drawPicker(c);
    }

    private void drawTabs(Canvas c) {
        for (int i = 0; i < TAB_COUNT; i++) {
            RectF r = tabRect[i]; float rad = r.height() * 0.2f;
            fill.setColor(pressed(TAB_BASE + i) || i == tab ? PRESSED : PANEL); c.drawRoundRect(r, rad, rad, fill);
            line.setColor(i == tab ? XP : DIM); c.drawRoundRect(r, rad, rad, line);
            text.setColor(i == tab ? TEXT : DIM); text.setTextSize(r.height() * 0.45f);
            c.drawText(t(TAB_LABEL[i]), r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2, text);
        }
    }

    // ---- Bag tab ----
    private void setTab(int t) {
        if (t == tab) return;
        tab = t; picking = false; cancelAll();
        nativeBagWant(tab == TAB_BAG); bagGen = -1;
        invalidate();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); nativeBagWant(tab == TAB_BAG); }
    @Override protected void onDetachedFromWindow() { nativeBagWant(false); super.onDetachedFromWindow(); }

    // called with every snapshot (SecondScreen, 10 Hz): while Bag shows, take the host's item list and redraw if it changed
    void pollBag() {
        if (tab != TAB_BAG) return;
        int n = nativeBag(bagRaw);
        if (n < 0 || bagRaw[0] == bagGen) return;
        bagGen = bagRaw[0];
        bag = BagModel.decode(Arrays.copyOfRange(bagRaw, 1, bagRaw.length), n);
        invalidate();
    }

    private void drawBag(Canvas c) {
        BagModel.Layout l = bagLayout; if (l == null) return;
        if (icons == null) icons = new DeckIcons(getContext());
        float rad = l.slot * 0.08f;
        fill.setColor(PANEL); line.setColor(DIM);
        for (int i = 0; i < l.slots(); i++) { float[] r = l.slotRect(i); tmp.set(r[0], r[1], r[2], r[3]); c.drawRoundRect(tmp, rad, rad, fill); c.drawRoundRect(tmp, rad, rad, line); }
        tmp.set(l.gridX, l.gridY, l.gridX + BagModel.GRID_W * l.cell, l.gridY + BagModel.GRID_H * l.cell); c.drawRect(tmp, fill);
        for (int x = 0; x <= BagModel.GRID_W; x++) c.drawLine(l.gridX + x * l.cell, l.gridY, l.gridX + x * l.cell, tmp.bottom, line);
        for (int y = 0; y <= BagModel.GRID_H; y++) c.drawLine(l.gridX, l.gridY + y * l.cell, tmp.right, l.gridY + y * l.cell, line);
        tmp.set(l.beltX, l.beltY, l.beltX + 4 * l.beltCell, l.beltY + BagModel.BELT_ROWS * l.beltCell); c.drawRect(tmp, fill);
        for (int x = 0; x <= 4; x++) c.drawLine(l.beltX + x * l.beltCell, l.beltY, l.beltX + x * l.beltCell, tmp.bottom, line);
        for (int y = 0; y <= BagModel.BELT_ROWS; y++) c.drawLine(l.beltX, l.beltY + y * l.beltCell, tmp.right, l.beltY + y * l.beltCell, line);
        text.setColor(DIM); text.setTextAlign(Paint.Align.LEFT); text.setTextSize(l.beltCell * 0.4f);
        c.drawText(t("dmDeckBelt"), tmp.right + l.gap * 2, l.beltY + l.beltCell * 0.6f, text);
        text.setTextAlign(Paint.Align.CENTER);
        for (BagModel.Item it : bag) {
            float[] r = l.rect(it); if (r == null) continue;
            tmp.set(r[0], r[1], r[2], r[3]); tmp.inset(2, 2);
            int q = QUALITY_COLOUR[Math.max(0, Math.min(8, it.quality))];
            fill.setColor(it.unid ? 0x40B0202A : (q & 0x00FFFFFF) | 0x30000000); c.drawRect(tmp, fill);
            Bitmap b = icons.get(it);
            if (b != null) {
                // keep the picture's shape: fit it inside the item's area, centred
                float s = Math.min(tmp.width() / b.getWidth(), tmp.height() / b.getHeight()), bw = b.getWidth() * s, bh = b.getHeight() * s;
                src.set(0, 0, b.getWidth(), b.getHeight());
                dst.set(tmp.centerX() - bw / 2, tmp.centerY() - bh / 2, tmp.centerX() + bw / 2, tmp.centerY() + bh / 2);
                c.drawBitmap(b, src, dst, bitmapPaint);
            } else {
                text.setColor(q); text.setTextSize(Math.min(tmp.width(), tmp.height()) * 0.3f);
                c.drawText(it.code, tmp.centerX(), tmp.centerY() - (text.descent() + text.ascent()) / 2, text);
            }
            if (it.quality >= 4) { line.setColor(it.unid ? DEAD : q); c.drawRect(tmp, line); }
        }
    }

    private void drawStrip(Canvas c) {
        float sh = strip.height(), w = strip.width(), bar = sh * 0.26f, gap = sh * 0.12f;
        text.setTextSize(sh * 0.2f);
        // left: life / mana bars
        float bw = w * 0.38f, y1 = strip.top + gap, y2 = y1 + bar + gap;
        drawBar(c, strip.left, y1, bw, bar, LIFE, hud[H_HP], hud[H_HP_MAX], t("dmDeckLife"));
        drawBar(c, strip.left, y2, bw, bar, MANA, hud[H_MP], hud[H_MP_MAX], t("dmDeckMana"));
        // middle: level and experience
        float mx = strip.left + bw + w * 0.04f, mw = w * 0.24f;
        text.setColor(TEXT); text.setTextAlign(Paint.Align.LEFT);
        c.drawText(String.format(t("dmDeckLevel"), hud[H_LEVEL]), mx, y1 + bar * 0.8f, text);
        tmp.set(mx, y2, mx + mw, y2 + bar); fill.setColor(PANEL); c.drawRoundRect(tmp, bar / 4, bar / 4, fill);
        tmp.right = mx + mw * Math.min(1000, Math.max(0, hud[H_EXP])) / 1000f; fill.setColor(XP); c.drawRoundRect(tmp, bar / 4, bar / 4, fill);
        // right: badges (replaced by one red word on death)
        float rx = mx + mw + w * 0.04f; text.setTextSize(sh * 0.17f);
        // under the bars, across the left and middle columns: the area name in the game's language, cut with … when too long
        String area = names().area(hud[H_AREA], hud[H_LANG]);
        if (!area.isEmpty()) {
            float room = rx - w * 0.04f - strip.left;
            int fit = text.breakText(area, true, room, null);
            if (fit < area.length()) area = area.substring(0, Math.max(0, text.breakText(area, true, room - text.measureText("…"), null))) + "…";
            text.setColor(GOLD); c.drawText(area, strip.left, strip.bottom - sh * 0.05f, text);
        }
        if (hud[H_DEAD] != 0) { text.setColor(DEAD); text.setTextSize(sh * 0.3f); c.drawText(t("dmDeckDead"), rx, strip.top + sh * 0.6f, text); }
        else {
            float by = y1 + bar * 0.8f;
            if (hud[H_TOWN] != 0) { text.setColor(XP); c.drawText(t("dmDeckTown"), rx, by, text); by += bar + gap; }
            if (hud[H_POINTS] != 0) { text.setColor(DEAD); c.drawText(t("dmDeckPoints"), rx, by, text); by += bar + gap; }
            if (hud[H_QUEST] != 0 && by < strip.bottom) { text.setColor(DEAD); c.drawText(t("dmDeckQuest"), rx, by, text); }
        }
        text.setTextAlign(Paint.Align.CENTER);
    }

    private void drawBar(Canvas c, float x, float y, float w, float h, int colour, int v, int max, String label) {
        tmp.set(x, y, x + w, y + h); fill.setColor(PANEL); c.drawRoundRect(tmp, h / 4, h / 4, fill);
        if (max > 0) { tmp.right = x + w * Math.min(1f, Math.max(0f, v / (float) max)); fill.setColor(colour); c.drawRoundRect(tmp, h / 4, h / 4, fill); }
        text.setColor(TEXT); text.setTextAlign(Paint.Align.LEFT); text.setTextSize(h * 0.72f);
        c.drawText(label + "  " + v + " / " + max, x + h * 0.4f, y + h * 0.76f, text);
    }

    private boolean pressed(int i) { for (int d : downCell) if (d == i) return true; return false; }

    private void drawCell(Canvas c, int i, boolean on) {
        RectF r = cell[i]; float rad = r.height() * 0.12f;
        boolean belt = i >= B_BELT; int col = i - B_BELT;
        int body = belt ? KIND_COLOUR[Math.max(0, Math.min(4, hud[H_KIND + col]))] : PANEL;
        fill.setColor(pressed(i) ? PRESSED : body); c.drawRoundRect(r, rad, rad, fill);
        line.setColor(on ? GOLD : DIM); c.drawRoundRect(r, rad, rad, line);
        String label;
        if (i < 4) label = t(TAB_KEY[i]);
        else if (i == B_PORTAL) label = t("dmDeckPortal");
        else if (i == B_MAP) label = t("dmDeckMap");
        else if (i == B_SWAP) label = t("dmDeckSwap");
        else if (i == B_PLAYERS) label = String.format(t("dmDeckPlayers"), hud[H_PLAYERS]);
        else label = "×" + hud[H_BELT + col];
        text.setColor(on ? TEXT : DIM); text.setTextSize(r.height() * (belt ? 0.34f : 0.2f));
        float fit = r.width() * 0.88f, tw = text.measureText(label);   // shrink long labels (Spanish, Russian, etc.) to fit the cell
        if (tw > fit) text.setTextSize(text.getTextSize() * fit / tw);
        float ty = r.centerY() - (text.descent() + text.ascent()) / 2;
        c.drawText(label, r.centerX(), ty, text);
        if (i == B_PORTAL || (i == B_MAP && hud[H_MAP_FULL] != 0)) {   // portal count / full map is on
            text.setTextSize(r.height() * 0.15f); text.setColor(on ? XP : DIM);
            c.drawText(i == B_PORTAL ? "×" + hud[H_PORTAL] : "●", r.centerX(), r.bottom - r.height() * 0.1f, text);
        }
    }

    private void drawPicker(Canvas c) {
        fill.setColor(0xE0000000); c.drawRect(0, cell[0].top, getWidth(), cell[B_COUNT - 1].bottom, fill);
        for (int i = 0; i < 8; i++) {
            RectF r = pick[i]; float rad = r.height() * 0.12f;
            fill.setColor(pressed(PICK_BASE + i) || hud[H_PLAYERS] == i + 1 ? PRESSED : PANEL); c.drawRoundRect(r, rad, rad, fill);
            line.setColor(hud[H_PLAYERS] == i + 1 ? XP : GOLD); c.drawRoundRect(r, rad, rad, line);
            text.setColor(TEXT); text.setTextSize(r.height() * 0.34f);
            c.drawText(String.valueOf(i + 1), r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2, text);
        }
    }

    private int hit(float x, float y) {
        for (int i = 0; i < TAB_COUNT; i++) if (tabRect[i].contains(x, y)) return TAB_BASE + i;
        if (tab == TAB_BAG) return -1;   // read only for now
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
        if (d < B_BELT || d >= B_COUNT || !actionsEnabled()) return;
        longFired[pointer] = true;
        nativeAction(A_FEED_MERC, d - B_BELT);
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    private void commit(int d) {
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

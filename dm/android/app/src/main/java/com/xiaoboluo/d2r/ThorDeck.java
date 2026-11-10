package com.xiaoboluo.d2r;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.util.Arrays;

// 底屏 Deck：双屏掌机第二块屏上的状态与大按钮（手柄玩时用；SecondScreen.java 管显隐）。上面一条状态（生命 / 法力 / 等级与经验 / 角标），
// 下面 4 × 3 大按钮：物品栏、角色、技能、任务 / 回城、地图、换武器、人数 / 腰带四列（点 = 喝底排那瓶，长按 = 喂雇佣兵）。
// 只用画布画（不用游戏图），字全走中英表（nativeText）。数据由 SecondScreen 每 100 ms 读一次快照交给 update，变了才重画；按下的反馈当场画。
// 触摸在松手时才生效，CANCEL 当作撤销：底屏刚出来时第一下会先收到 DOWN 再收到 CANCEL
// Bottom-screen Deck: status and large buttons on the second screen of dual-screen handhelds (for controller play; SecondScreen.java handles showing and hiding). A status strip on top (life / mana / level and experience / badges),
// 4 x 3 large buttons below: Inventory, Character, Skills, Quests / Portal, Map, Swap weapons, Players / the four belt columns (tap = drink the bottom-row bottle, long press = feed the mercenary).
// Drawn on the canvas only (no game art); all text goes through the string table (nativeText). SecondScreen reads a snapshot every 100 ms and passes it to update, which redraws only on change; press feedback is drawn at once.
// A touch takes effect on release, and CANCEL means undo: right after the bottom screen appears, the first touch arrives as DOWN then CANCEL
final class ThorDeck extends View {
    // 快照下标，与 deck_jni.c 一致 | snapshot indices, matching deck_jni.c
    static final int H_VALID = 0, H_LEVEL = 1, H_EXP = 2, H_HP = 3, H_HP_MAX = 4, H_MP = 5, H_MP_MAX = 6, H_BELT = 7, H_KIND = 11, H_PORTAL = 15,
        H_PLAYERS = 16, H_POINTS = 17, H_QUEST = 18, H_DEAD = 19, H_TOWN = 20, H_SWITCH = 21, H_MAP_FULL = 22, H_COUNT = 23;
    // 动作号，与 deck_jni.c 一致 | action numbers, matching deck_jni.c
    static final int A_DRINK = 0, A_FEED_MERC = 1, A_PORTAL = 2, A_MAP = 3, A_SWAP = 4, A_TAB = 5, A_PLAYERS = 6;
    static native int nativeHud(int[] out);
    static native void nativeAction(int action, int arg);
    static native String nativeText(String key);

    private static final long LONG_PRESS_MS = 500;
    // 十二格：0–3 功能页四页，4 回城，5 地图，6 换武器，7 人数，8–11 腰带四列 | twelve cells: 0–3 the four function pages, 4 portal, 5 map, 6 swap, 7 players, 8–11 the four belt columns
    private static final int B_PORTAL = 4, B_MAP = 5, B_SWAP = 6, B_PLAYERS = 7, B_BELT = 8, B_COUNT = 12;
    private static final String[] TAB_KEY = { "dmDeckInventory", "dmDeckCharacter", "dmDeckSkills", "dmDeckQuests" };
    private static final int BG = 0xFF0E0B08, PANEL = 0xFF1C1712, PRESSED = 0xFF3A2E1C, GOLD = 0xFF8C7340, TEXT = 0xFFE8D9B0, DIM = 0xFF6B6250,
        LIFE = 0xFFB0202A, MANA = 0xFF2A4FC0, XP = 0xFFC7A04A, DEAD = 0xFFE04030;
    private static final int[] KIND_COLOUR = { 0xFF2A2520, LIFE, MANA, 0xFF8A3AB0, 0xFF6B6B6B };   // 空 / 红 / 蓝 / 紫 / 别的 | empty / red / blue / purple / other

    private final int[] hud = new int[H_COUNT];
    private final RectF[] cell = new RectF[B_COUNT];
    private final RectF[] pick = new RectF[8];
    private final RectF strip = new RectF(), tmp = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    // 每根手指按在哪一格（-1 没有；PICK_BASE + n = 人数选择条第 n 格），长按是否已经触发 | which cell each finger is on (-1 none; PICK_BASE + n = cell n of the players picker), and whether its long press already fired
    private static final int PICK_BASE = 100, MAX_POINTERS = 10;
    private final int[] downCell = new int[MAX_POINTERS];
    private final boolean[] longFired = new boolean[MAX_POINTERS];
    private final Runnable[] longPress = new Runnable[MAX_POINTERS];
    private boolean picking;   // 人数 1–8 选择条开着 | the players 1–8 picker is open

    ThorDeck(Context context) {
        super(context);
        for (int i = 0; i < B_COUNT; i++) cell[i] = new RectF();
        for (int i = 0; i < 8; i++) pick[i] = new RectF();
        Arrays.fill(downCell, -1);
        line.setStyle(Paint.Style.STROKE);
        text.setColor(TEXT);
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < MAX_POINTERS; i++) { final int p = i; longPress[i] = () -> onLongPress(p); }
    }

    // 新快照：变了才重画。返回 false = 和上次一样 | new snapshot: redraw only on change. Returns false = same as last time
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
        float k = w / 1240f, gap = 14 * k, pad = 18 * k;
        strip.set(pad, pad, w - pad, h * 0.22f);
        float top = strip.bottom + gap, cw = (w - 2 * pad - 3 * gap) / 4, ch = (h - pad - top - 2 * gap) / 3;
        for (int i = 0; i < B_COUNT; i++) {
            int r = i / 4, c = i % 4; float x = pad + c * (cw + gap), y = top + r * (ch + gap);
            cell[i].set(x, y, x + cw, y + ch);
        }
        // 人数选择条盖在中间一排上：8 格 | the players picker covers the middle row: 8 cells
        float py = cell[4].top, ph = cell[4].height(), pw = (w - 2 * pad - 7 * gap) / 8;
        for (int i = 0; i < 8; i++) { float x = pad + i * (pw + gap); pick[i].set(x, py, x + pw, py + ph); }
        line.setStrokeWidth(Math.max(2, 3 * k));
    }

    @Override protected void onDraw(Canvas c) {
        c.drawColor(BG);
        float h = getHeight();
        if (hud[H_VALID] == 0) {   // 还没进局：只出一句说明 | not in a run yet: just one line of explanation
            text.setColor(DIM); text.setTextSize(h * 0.045f);
            c.drawText(t("dmDeckIdle"), getWidth() / 2f, h / 2f, text);
            return;
        }
        drawStrip(c);
        boolean on = actionsEnabled();
        for (int i = 0; i < B_COUNT; i++) drawCell(c, i, on);
        if (picking) drawPicker(c);
    }

    private void drawStrip(Canvas c) {
        float sh = strip.height(), w = strip.width(), bar = sh * 0.26f, gap = sh * 0.12f;
        text.setTextSize(sh * 0.2f);
        // 左：生命 / 法力条 | left: life / mana bars
        float bw = w * 0.38f, y1 = strip.top + gap, y2 = y1 + bar + gap;
        drawBar(c, strip.left, y1, bw, bar, LIFE, hud[H_HP], hud[H_HP_MAX], t("dmDeckLife"));
        drawBar(c, strip.left, y2, bw, bar, MANA, hud[H_MP], hud[H_MP_MAX], t("dmDeckMana"));
        // 中：等级与经验 | middle: level and experience
        float mx = strip.left + bw + w * 0.04f, mw = w * 0.24f;
        text.setColor(TEXT); text.setTextAlign(Paint.Align.LEFT);
        c.drawText(String.format(t("dmDeckLevel"), hud[H_LEVEL]), mx, y1 + bar * 0.8f, text);
        tmp.set(mx, y2, mx + mw, y2 + bar); fill.setColor(PANEL); c.drawRoundRect(tmp, bar / 4, bar / 4, fill);
        tmp.right = mx + mw * Math.min(1000, Math.max(0, hud[H_EXP])) / 1000f; fill.setColor(XP); c.drawRoundRect(tmp, bar / 4, bar / 4, fill);
        // 右：角标（死亡时换成一个红字） | right: badges (replaced by one red word on death)
        float rx = mx + mw + w * 0.04f; text.setTextSize(sh * 0.17f);
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
        float ty = r.centerY() - (text.descent() + text.ascent()) / 2;
        c.drawText(label, r.centerX(), ty, text);
        if (i == B_PORTAL || (i == B_MAP && hud[H_MAP_FULL] != 0)) {   // 回城次数 / 全屏地图开着 | portal count / full map is on
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
        if (picking) { for (int i = 0; i < 8; i++) if (pick[i].contains(x, y)) return PICK_BASE + i; return PICK_BASE - 1; }   // 选择条外 = 关掉它 | outside the picker = close it
        for (int i = 0; i < B_COUNT; i++) if (cell[i].contains(x, y)) return i;
        return -1;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int idx = e.getActionIndex(), id = e.getPointerId(idx);
        switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_POINTER_DOWN:
            if (id >= MAX_POINTERS || !actionsEnabled()) return true;
            downCell[id] = hit(e.getX(idx), e.getY(idx)); longFired[id] = false;
            if (downCell[id] >= B_BELT && downCell[id] < B_COUNT) handler.postDelayed(longPress[id], LONG_PRESS_MS);
            invalidate(); return true;
        case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP:
            if (id >= MAX_POINTERS) return true;
            handler.removeCallbacks(longPress[id]);
            int d = downCell[id]; downCell[id] = -1;
            // 松手时还在按下的那一格里才算 | it counts only if the finger is still on the cell it went down on
            boolean same = d >= 0 && hit(e.getX(idx), e.getY(idx)) == d;
            if (d == PICK_BASE - 1) { if (same) picking = false; }
            else if (same && !longFired[id]) { performClick(); commit(d); }
            invalidate(); return true;
        case MotionEvent.ACTION_CANCEL:
            cancelAll(); invalidate(); return true;
        default: return true;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }   // 无障碍：每次按钮生效都报一次点击 | accessibility: every button that takes effect reports a click

    private void cancelAll() { for (int i = 0; i < MAX_POINTERS; i++) { handler.removeCallbacks(longPress[i]); downCell[i] = -1; } }

    private void onLongPress(int pointer) {
        int d = downCell[pointer];
        if (d < B_BELT || d >= B_COUNT || !actionsEnabled()) return;
        longFired[pointer] = true;
        nativeAction(A_FEED_MERC, d - B_BELT);
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    private void commit(int d) {
        if (d >= PICK_BASE) { nativeAction(A_PLAYERS, d - PICK_BASE + 1); picking = false; return; }
        if (d < 4) nativeAction(A_TAB, d);
        else if (d == B_PORTAL) nativeAction(A_PORTAL, 0);
        else if (d == B_MAP) nativeAction(A_MAP, 0);
        else if (d == B_SWAP) nativeAction(A_SWAP, 0);
        else if (d == B_PLAYERS) picking = true;
        else nativeAction(A_DRINK, d - B_BELT);
    }
}

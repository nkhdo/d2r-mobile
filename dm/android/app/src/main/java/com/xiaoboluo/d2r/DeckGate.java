package com.xiaoboluo.d2r;

// 底屏 Deck 什么时候该显示：纯规则，不碰 Android，时间由调用方给（毫秒），可单测（tests/DeckGateTest.java）。
// 活动先 onResume 后拿焦点，只看 onResume 会在切任务后把 Deck 留在桌面上，所以要「前台且有焦点」；
// 旋转、系统对话框会短暂拿走焦点，失焦后延迟 HIDE_DELAY_MS 再收起，拿到焦点立即显示。
// When the bottom-screen Deck should be shown: pure rules, no Android, time supplied by the caller (milliseconds), unit-testable (tests/DeckGateTest.java).
// An activity resumes before it gains focus, and gating on onResume alone leaves the Deck over the launcher after a task switch, so it needs "resumed and focused";
// rotation and system dialogs take focus briefly, so a focus loss hides only after HIDE_DELAY_MS, and gaining focus shows at once.
final class DeckGate {
    static final long HIDE_DELAY_MS = 400;
    private boolean resumed, focused, display, enabled, text;
    private long hideAt;   // 失焦时还显示着：到这个时刻收起；0 = 没有待收起 | still shown when focus was lost: hide at this time; 0 = no hide pending

    void resumed(boolean on, long now) { resumed = on; if (!on) hideAt = 0; }
    void focused(boolean on, long now) {
        if (!on && focused && shown(now)) hideAt = now + HIDE_DELAY_MS;
        if (on) hideAt = 0;
        focused = on;
    }
    void display(boolean present) { display = present; }
    void enabled(boolean on) { enabled = on; }
    // 文字输入对话框开着：键盘可能开在底屏（AYN ime_show_on_second），当场让开 | text entry dialog up: the keyboard may open on the bottom screen (AYN ime_show_on_second), get out of the way at once
    void textEntry(boolean on) { text = on; }

    boolean shown(long now) {
        if (!resumed || !display || !enabled || text) return false;
        return focused || now < hideAt;
    }
    // 不再有输入时，shown() 还要过多少毫秒才会自己变；-1 = 不会变 | with no further input, milliseconds until shown() changes on its own; -1 = it will not
    long nextChange(long now) { return shown(now) && !focused ? hideAt - now : -1; }
}

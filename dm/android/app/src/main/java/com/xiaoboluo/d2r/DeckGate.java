package com.xiaoboluo.d2r;

// When the bottom-screen Deck should be shown: pure rules, no Android, time supplied by the caller (milliseconds), unit-testable (tests/DeckGateTest.java).
// An activity resumes before it gains focus, and gating on onResume alone leaves the Deck over the launcher after a task switch, so it needs "resumed and focused";
// rotation and system dialogs take focus briefly, so a focus loss hides only after HIDE_DELAY_MS, and gaining focus shows at once.
final class DeckGate {
    static final long HIDE_DELAY_MS = 400;
    private boolean resumed, focused, display, enabled, text;
    private long hideAt;   // still shown when focus was lost: hide at this time; 0 = no hide pending

    void resumed(boolean on, long now) { resumed = on; if (!on) hideAt = 0; }
    void focused(boolean on, long now) {
        if (!on && focused && shown(now)) hideAt = now + HIDE_DELAY_MS;
        if (on) hideAt = 0;
        focused = on;
    }
    void display(boolean present) { display = present; }
    void enabled(boolean on) { enabled = on; }
    // text entry dialog up: the keyboard may open on the bottom screen (AYN ime_show_on_second), get out of the way at once
    void textEntry(boolean on) { text = on; }

    boolean shown(long now) {
        if (!resumed || !display || !enabled || text) return false;
        return focused || now < hideAt;
    }
    // with no further input, milliseconds until shown() changes on its own; -1 = it will not
    long nextChange(long now) { return shown(now) && !focused ? hideAt - now : -1; }
}

package com.xiaoboluo.d2r;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

// The Deck on the bottom screen (the second screen of dual-screen handhelds; first target the AYN Thor): a system Presentation on the presentation display; nothing happens on devices without one.
// When it shows is decided by DeckGate (pure rules, unit-tested); this class only keeps the Presentation the way it says, and applies the workarounds other Thor ports needed (section 3 of docs/android-notes/2026-11-10-ayn-thor-dual-screen.md):
//   not focusable (otherwise the gamepad follows focus on the first tap on the bottom screen), not cancelable and not consuming keys (gamepad B becomes BACK at system level), system bars hidden on its own window (immersive mode is per window),
//   locked to landscape (it does not follow the activity, and the bottom panel is natively portrait), re-shown when the system dismisses it, not shown for a while when show() throws InvalidDisplayException because the AYN launcher holds the display, and the display found by category instead of hard-coding id 4.
// The host snapshot (ThorDeck.nativeHud) is read every 100 ms: the Custom page's deck switch takes effect as soon as Apply is tapped, and the Deck's numbers refresh at the same rate
final class SecondScreen implements DisplayManager.DisplayListener {
    private static final String TAG = "DmDeck";
    private static final long POLL_MS = 100, BLOCKED_RETRY_MS = 5000;
    private final Activity activity;
    private final DisplayManager displays;
    private final DeckGate gate = new DeckGate();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] hud = new int[ThorDeck.H_COUNT];
    private final Runnable poll = this::poll, reconcile = this::reconcile;
    private DeckPresentation shown;   // the one we showed; if it still points at it when the system dismisses = not a dismissal we asked for
    private long blockedUntil;        // after a failed show(), do not try again before this time
    private boolean polling;

    SecondScreen(Activity activity, boolean resumed, boolean focused) {
        this.activity = activity;
        displays = activity.getSystemService(DisplayManager.class);
        long now = SystemClock.uptimeMillis();
        gate.resumed(resumed, now); gate.focused(focused, now);
        gate.display(findDisplay() != null);
        if (displays != null) displays.registerDisplayListener(this, handler);
        if (resumed) startPolling();
    }

    void resumed(boolean on) { gate.resumed(on, SystemClock.uptimeMillis()); if (on) startPolling(); else { polling = false; handler.removeCallbacks(poll); } reconcile(); }
    void focused(boolean on) { gate.focused(on, SystemClock.uptimeMillis()); reconcile(); }
    void textEntry(boolean on) { gate.textEntry(on); reconcile(); }
    void destroy() { polling = false; handler.removeCallbacksAndMessages(null); if (displays != null) displays.unregisterDisplayListener(this); hide(); }

    private void startPolling() { if (!polling) { polling = true; handler.post(poll); } }
    private void poll() {
        if (!polling) return;
        if (ThorDeck.nativeHud(hud) == ThorDeck.H_COUNT) {
            gate.enabled(hud[ThorDeck.H_SWITCH] != 0);
            if (shown != null) shown.deck.update(hud);
        }
        reconcile();
        handler.postDelayed(poll, POLL_MS);
    }

    private void reconcile() {
        handler.removeCallbacks(reconcile);
        long now = SystemClock.uptimeMillis();
        boolean want = gate.shown(now);
        if (want && shown == null) {
            if (now < blockedUntil) { handler.postDelayed(reconcile, blockedUntil - now); return; }
            show();
        } else if (!want && shown != null) hide();
        long next = gate.nextChange(now);
        if (next >= 0) handler.postDelayed(reconcile, next + 1);
    }

    // the first presentation-category display that is not the activity's own
    private Display findDisplay() {
        if (displays == null) return null;
        Display own = activity.getDisplay();
        for (Display d : displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION))
            if (d.isValid() && (own == null || d.getDisplayId() != own.getDisplayId())) return d;
        return null;
    }

    private void show() {
        Display d = findDisplay();
        if (d == null) { gate.display(false); return; }
        DeckPresentation p = new DeckPresentation(activity, d);
        p.setCancelable(false);
        Window w = p.getWindow();
        if (w != null) {
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            w.setAttributes(lp);   // from target SDK 36 windows are always edge-to-edge, so setDecorFitsSystemWindows is no longer needed
        }
        p.setOnDismissListener(x -> {
            // When we hide it we clear the reference first; if it still points at it here = the system dismissed it (display flicker, configuration churn): clear it and re-show on the next pass as DeckGate says
            if (shown == p) { shown = null; Log.i(TAG, "dismissed by the system; showing again"); handler.post(reconcile); }
        });
        shown = p;
        try {
            p.show();
        } catch (WindowManager.InvalidDisplayException | WindowManager.BadTokenException e) {
            shown = null; blockedUntil = SystemClock.uptimeMillis() + BLOCKED_RETRY_MS;
            Log.w(TAG, "bottom screen not available (" + e.getClass().getSimpleName() + "); retrying in " + BLOCKED_RETRY_MS + " ms");
            return;
        }
        if (w != null) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) { c.hide(WindowInsets.Type.systemBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
        }
        p.deck.update(hud);
        Log.i(TAG, "Deck shown on display " + d.getDisplayId());
    }

    private void hide() {
        DeckPresentation p = shown;
        if (p == null) return;
        shown = null;   // clear the reference before dismissing: the dismiss callback uses it to know we asked for it
        p.dismiss();
        Log.i(TAG, "Deck hidden");
    }

    @Override public void onDisplayAdded(int id) { gate.display(findDisplay() != null); reconcile(); }
    @Override public void onDisplayRemoved(int id) { gate.display(findDisplay() != null); reconcile(); }
    @Override public void onDisplayChanged(int id) { gate.display(findDisplay() != null); reconcile(); }

    private static final class DeckPresentation extends Presentation {
        final ThorDeck deck;
        DeckPresentation(Context outer, Display display) { super(outer, display); deck = new ThorDeck(getContext()); }
        @Override protected void onCreate(Bundle state) { super.onCreate(state); setContentView(deck); }
        // consume no keys: the BACK that gamepad B turns into at system level does not close it
        @Override public boolean dispatchKeyEvent(KeyEvent e) { return false; }
    }
}

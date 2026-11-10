package com.xiaoboluo.d2r;

// Bottom-screen Deck visibility rules (DeckGate): shown only while resumed and focused, delayed hide on focus loss, out of the way during text entry, the switch and whether a bottom screen exists.
public class DeckGateTest {
    private static int cases;
    private static void check(String what, boolean want, boolean actual) {
        cases++;
        if (want != actual) throw new AssertionError(what + ": want=" + want + " actual=" + actual);
    }
    private static DeckGate ready(long now) {
        DeckGate g = new DeckGate();
        g.display(true); g.enabled(true); g.resumed(true, now); g.focused(true, now);
        return g;
    }
    public static void main(String[] args) {
        check("resumed + focused + display + switch on", true, ready(0).shown(0));
        DeckGate g = new DeckGate();
        g.display(true); g.enabled(true); g.resumed(true, 0);
        check("resumed but not yet focused", false, g.shown(0));

        // rotation and system dialogs take focus briefly: back within 400 ms means no flicker
        g = ready(0);
        g.focused(false, 1000);
        check("just lost focus", true, g.shown(1000));
        check("399 ms after losing focus", true, g.shown(1399));
        check("400 ms after losing focus", false, g.shown(1400));
        if (g.nextChange(1100) != 300) throw new AssertionError("nextChange while a hide is pending: " + g.nextChange(1100));
        cases++;
        if (g.nextChange(1500) != -1) throw new AssertionError("nextChange once hidden: " + g.nextChange(1500));
        cases++;
        g = ready(0);
        g.focused(false, 1000); g.focused(true, 1200);
        check("focus back within the delay", true, g.shown(1600));
        g = new DeckGate();
        g.display(true); g.enabled(true); g.resumed(true, 0); g.focused(false, 0);
        check("never shown, focus loss does not show it", false, g.shown(10));

        // Home: onPause comes before the focus loss; hide at once, no delay
        g = ready(0);
        g.resumed(false, 1000); g.focused(false, 1000);
        check("paused hides at once", false, g.shown(1000));
        g = ready(0);
        g.focused(false, 1000); g.resumed(false, 1100); g.resumed(true, 1200);
        check("a pending hide does not survive a pause: resumed again, still waiting for focus", false, g.shown(1250));

        // name dialog: AYN's ime_show_on_second opens the keyboard on the bottom screen, under the Deck
        g = ready(0);
        g.textEntry(true); g.focused(false, 1000);   // the dialog takes the activity window's focus
        check("text entry hides at once", false, g.shown(1000));
        g.textEntry(false); g.focused(true, 5000);
        check("back after the dialog closes", true, g.shown(5000));

        g = ready(0); g.enabled(false);
        check("switch off hides at once", false, g.shown(0));
        g.enabled(true);
        check("switch back on shows at once", true, g.shown(0));
        g = ready(0); g.display(false);
        check("bottom screen removed", false, g.shown(0));
        System.out.println("DeckGate OK (" + cases + " cases)");
    }
}

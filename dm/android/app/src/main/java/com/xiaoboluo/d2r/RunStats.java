package com.xiaoboluo.d2r;

// Run stats for the Deck's HUD bar, without Android types (tested by RunStatsTest): time in the current game, experience gained per
// hour (in percent of a level: the snapshot carries the level and the progress within it, in per mille) and time to the next level.
// A run starts when a game is entered; being out of the game for longer than a loading screen, or a lower level (another character),
// starts a new one. The rate is measured over the whole run and shown only after a minute with some progress.
final class RunStats {
    private static final long GAP_MS = 15_000, MIN_MS = 60_000;
    private long start = -1, outSince = -1;
    private double startLevels, levels;
    private int level;

    void sample(long now, boolean inGame, int lv, int permille) {
        if (!inGame) { if (outSince < 0) outSince = now; return; }
        double l = lv + Math.max(0, Math.min(1000, permille)) / 1000.0;
        if (start < 0 || (outSince >= 0 && now - outSince > GAP_MS) || lv < level) { start = now; startLevels = l; }
        outSince = -1; levels = l; level = lv;
    }

    long elapsed(long now) { return start < 0 ? -1 : now - start; }

    // percent of a level per hour; -1 = not known yet
    double percentPerHour(long now) {
        long t = elapsed(now);
        if (t < MIN_MS || levels <= startLevels) return -1;
        return (levels - startLevels) * 100 * 3_600_000.0 / t;
    }

    // time to the next level at the run's rate; -1 = not known
    long etaMs(long now) {
        double p = percentPerHour(now);
        if (p <= 0) return -1;
        double left = (Math.floor(levels) + 1 - levels) * 100;
        return (long) (left / p * 3_600_000.0);
    }

    // h:mm:ss or m:ss; empty before the first game
    static String clock(long ms) {
        if (ms < 0) return "";
        long s = ms / 1000, h = s / 3600, m = s / 60 % 60;
        return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s % 60) : String.format(java.util.Locale.ROOT, "%d:%02d", m, s % 60);
    }

    // a rough duration: ~45m, ~2h05m
    static String span(long ms) {
        long m = ms / 60_000;
        return m < 60 ? "~" + m + "m" : String.format(java.util.Locale.ROOT, "~%dh%02dm", m / 60, m % 60);
    }
}

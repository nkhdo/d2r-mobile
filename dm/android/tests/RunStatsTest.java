package com.xiaoboluo.d2r;

// Run stats on the Deck's HUD bar (RunStats): time in the current game, experience per hour and time to the next level.
public class RunStatsTest {
    private static int cases;
    private static void check(String what, Object want, Object actual) {
        cases++;
        if (!java.util.Objects.equals(want, actual)) throw new AssertionError(what + ": want=" + want + " actual=" + actual);
    }
    private static final long S = 1000, M = 60 * S, H = 60 * M;

    public static void main(String[] args) {
        RunStats r = new RunStats();
        check("nothing before the first game", "", RunStats.clock(r.elapsed(0)));
        r.sample(0, true, 10, 200);
        r.sample(30 * S, true, 10, 210);
        check("time in game", "0:30", RunStats.clock(r.elapsed(30 * S)));
        check("no rate in the first minute", -1L, r.etaMs(30 * S));
        r.sample(30 * M, true, 10, 450);   // a quarter of a level in half an hour
        check("rate: 50% of a level per hour", 50L, Math.round(r.percentPerHour(30 * M)));
        check("eta: the remaining 55% at 50%/h", 66L, r.etaMs(30 * M) / M);
        check("time format past an hour", "1:02:03", RunStats.clock(H + 2 * M + 3 * S));
        check("short duration", "~45m", RunStats.span(45 * M));
        check("long duration", "~2h05m", RunStats.span(2 * H + 5 * M + 20 * S));

        r.sample(60 * M, true, 11, 100);   // levelled up: from 10.2 to 11.1 in an hour = 90%/h
        check("level-ups count as progress", 90L, Math.round(r.percentPerHour(60 * M)));

        r.sample(60 * M + 5 * S, false, 0, 0);   // a loading screen: short gaps keep the run
        r.sample(60 * M + 10 * S, true, 11, 100);
        check("short gap keeps the clock", "1:00:10", RunStats.clock(r.elapsed(60 * M + 10 * S)));

        r.sample(61 * M, false, 0, 0);   // out of the game for longer: the next game starts a new run
        r.sample(62 * M, true, 11, 120);
        check("a new game restarts the clock", "0:00", RunStats.clock(r.elapsed(62 * M)));
        check("and the rate", -1L, r.etaMs(62 * M));

        RunStats s = new RunStats();
        s.sample(0, true, 30, 500); s.sample(10 * M, true, 30, 500);
        check("no progress: no eta", -1L, s.etaMs(10 * M));
        s.sample(11 * M, true, 5, 0);   // another character (level went down): new run
        check("lower level = another character", "0:00", RunStats.clock(s.elapsed(11 * M)));
        System.out.println("RunStatsTest OK (" + cases + " cases)");
    }
}

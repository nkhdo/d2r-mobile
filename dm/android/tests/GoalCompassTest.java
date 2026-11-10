package com.xiaoboluo.d2r;

// The Deck's goal compass (GoalCompass): the host's map goals (dm_map_goals) as screen directions and distances, nearest first.
public class GoalCompassTest {
    private static int cases;
    private static void check(String what, Object want, Object actual) {
        cases++;
        if (!java.util.Objects.equals(want, actual)) throw new AssertionError(what + ": want=" + want + " actual=" + actual);
    }
    private static void near(String what, double want, double actual) {
        cases++;
        if (Math.abs(want - actual) > 0.01) throw new AssertionError(what + ": want=" + want + " actual=" + actual);
    }

    public static void main(String[] args) {
        // nativeGoals layout: n, player x, player y, then x, y, art per goal
        int[] raw = { 4, 100, 100,
            130, 100, 255,          // waypoint: +30 world x
            100, 160, 256 + 3,      // exit to level 3: +60 world y
            90, 90, 1,              // Blood Raven: -10, -10
            110, 90, 64 + 2 };      // the true tomb's symbol: +10 x, -10 y
        GoalCompass.Goal[] g = GoalCompass.read(raw);
        check("count", 4, g.length);
        check("nearest first", 1, g[0].art);
        check("farthest last", 256 + 3, g[3].art);
        near("distance in cells", Math.sqrt(200), g[0].dist);
        // the game's view: world +x goes down-right on screen, +y down-left; up the screen is -x -y
        near("-x -y points straight up: x", 0, g[0].sx);
        check("-x -y points straight up: y < 0", true, g[0].sy < 0);
        GoalCompass.Goal wp = g[2];
        check("waypoint is third (30 cells)", 255, wp.art);
        check("+x points right", true, wp.sx > 0);
        check("+x points down", true, wp.sy > 0);
        near("screen direction is unit length", 1, Math.hypot(wp.sx, wp.sy));
        GoalCompass.Goal tomb = g[1];
        check("+x -y points right only", true, tomb.sx > 0.99);

        check("name key: label number", "1", GoalCompass.nameKey(1));
        check("name key: waypoint", "255", GoalCompass.nameKey(255));
        check("name key: true tomb symbol = label 0", "0", GoalCompass.nameKey(64 + 5));
        check("level exits are not in the goal table", null, GoalCompass.nameKey(256 + 3));
        check("level of an exit", 3, GoalCompass.exitLevel(256 + 3));
        check("not an exit", -1, GoalCompass.exitLevel(255));

        check("no goals", 0, GoalCompass.read(new int[] { 0, 5, 5 }).length);
        check("short buffer is cut to what it holds", 1, GoalCompass.read(new int[] { 3, 0, 0, 1, 1, 255 }).length);
        check("standing on a goal: no direction, no crash", 0.0, GoalCompass.read(new int[] { 1, 7, 7, 7, 7, 255 })[0].dist);
        System.out.println("GoalCompassTest OK (" + cases + " cases)");
    }
}

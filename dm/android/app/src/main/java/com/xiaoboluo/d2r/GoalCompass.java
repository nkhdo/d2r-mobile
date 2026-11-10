package com.xiaoboluo.d2r;

import java.util.Arrays;

// The Deck's goal compass without Android types (tested by GoalCompassTest): the host's map goals (dm_world.h dm_map_goals: quest
// goals, the true tomb, waypoints and real exits of the current area, in world cells) as directions on the game's screen and distances,
// nearest first. The game's fixed angled view draws world +x down-right and +y down-left, so a goal's screen direction is
// (dx - dy, (dx + dy) / 2), normalised.
final class GoalCompass {
    static final int WAYPOINT = 255, TOWN_PORTAL = 254, RED_PORTAL = 253, LEVEL = 256, SYMBOL = 64, SYMBOLS = 7;

    static final class Goal {
        int art;              // the host's goal number: < 64 label, 64–70 true tomb symbol, 253 red portal, 254 town portal, 255 waypoint, 256 + level = exit
        double dist, sx, sy;  // distance in world cells; unit direction on screen (x right, y down; 0, 0 when standing on it)
    }

    // raw = nativeGoals' output: n, player x, player y, then x, y, art per goal
    static Goal[] read(int[] raw) {
        if (raw.length < 3) return new Goal[0];
        int n = Math.max(0, Math.min(raw[0], (raw.length - 3) / 3));
        Goal[] out = new Goal[n];
        for (int i = 0; i < n; i++) {
            Goal g = new Goal(); int o = 3 + 3 * i;
            double dx = raw[o] - raw[1], dy = raw[o + 1] - raw[2];
            g.art = raw[o + 2]; g.dist = Math.hypot(dx, dy);
            double vx = dx - dy, vy = (dx + dy) / 2, len = Math.hypot(vx, vy);
            if (len > 0) { g.sx = vx / len; g.sy = vy / len; }
            out[i] = g;
        }
        Arrays.sort(out, (a, b) -> Double.compare(a.dist, b.dist));
        return out;
    }

    // key into the pack's deck/goals.tsv; null for level exits (named from deck/levels.tsv, exitLevel)
    static String nameKey(int art) {
        if (art >= LEVEL) return null;
        if (art >= SYMBOL && art < SYMBOL + SYMBOLS) return "0";   // the true tomb: label 0, Tal Rasha's Tomb
        return String.valueOf(art);
    }

    static int exitLevel(int art) { return art >= LEVEL ? art - LEVEL : -1; }
}

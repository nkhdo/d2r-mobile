package com.xiaoboluo.d2r;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The Build tab's logic without Android types (tested by BuildPlanTest): where the character is in a Build and what to do next.
//   Live      the host's reading of the character (ThorDeck.nativeBuild): class, level, hard points, breakpoints, skill cap.
//   Rules     class skills from the import pack (deck/skillrules.tsv): required level, highest base level, prerequisites.
//   stage     the stage for the character's level: the first whose `to` is at or above it.
//   next      the first skill steps the character's base levels have not met yet, in the Build's order, from the last respec stage up
//             to the current one; each says when its next point can go in (D2: required level + current base level) or which
//             prerequisite is missing.
//   nextStat  the first stat rule not met (or the reminders when only those are left).
//   tier      where a gear-total breakpoint value sits in a breakpoint table.
final class BuildPlan {
    private BuildPlan() {}

    static final class Live {
        static final int HEAD = 14;   // deck_jni.c B_HEAD
        boolean valid;
        int cls, level, statPoints, skillPoints, fcr, fhr, ias, cap = 20;
        final int[] base = new int[4];
        final Map<Integer, Integer> skills = new HashMap<>();

        int skill(int id) { Integer v = skills.get(id); return v == null ? 0 : v; }

        // raw = nativeBuild's ints, n = how many it wrote
        static Live decode(int[] raw, int n) {
            Live l = new Live();
            if (n < HEAD || raw[0] == 0) return l;
            l.valid = true; l.cls = raw[1]; l.level = raw[2];
            for (int i = 0; i < 4; i++) l.base[i] = raw[3 + i];
            l.statPoints = raw[7]; l.skillPoints = raw[8]; l.fcr = raw[9]; l.fhr = raw[10]; l.ias = raw[11]; l.cap = Math.max(20, raw[12]);
            int count = Math.min(raw[13], (n - HEAD) / 2);
            for (int i = 0; i < count; i++) l.skills.put(raw[HEAD + 2 * i], raw[HEAD + 2 * i + 1]);
            return l;
        }
    }

    static final class Rule {
        final String cls;
        final int req, max;
        final int[] pre;
        Rule(String cls, int req, int max, int[] pre) { this.cls = cls; this.req = req; this.max = max; this.pre = pre; }
    }

    // deck/skillrules.tsv: header, then id, class, reqlevel, maxlvl, req1..3 (-1 none)
    static Map<Integer, Rule> parseRules(String tsv) {
        Map<Integer, Rule> out = new HashMap<>();
        String[] lines = tsv.split("\r?\n");
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split("\t", -1);
            if (f.length < 7) continue;
            try {
                int[] pre = { Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]) };
                out.put(Integer.parseInt(f[0]), new Rule(f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]), pre));
            } catch (NumberFormatException e) { /* skip a bad row */ }
        }
        return out;
    }

    // ---- stage ----
    static int stage(BuildGuide g, int level) {
        for (int i = 0; i < g.stages.size(); i++) if (level <= g.stages.get(i).to) return i;
        return g.stages.size() - 1;
    }

    // the stage's first level: one after the previous stage's end
    static int stageFrom(BuildGuide g, int i) { return i == 0 ? 1 : g.stages.get(i - 1).to + 1; }

    // the steps that apply at stage i: from the last respec stage at or before it, up to and including it
    static List<BuildGuide.Step> steps(BuildGuide g, int i) {
        int from = 0;
        for (int k = i; k >= 0; k--) if (g.stages.get(k).respec) { from = k; break; }
        List<BuildGuide.Step> out = new ArrayList<>();
        for (int k = from; k <= i; k++) out.addAll(g.stages.get(k).skills);
        return out;
    }

    // ---- skills ----
    static final class Next {
        final int skill, have, to;
        final int atLevel;     // 0 = can go in now, else the character level the next point needs
        final int missing;     // -1, else a prerequisite skill with no point yet
        Next(int skill, int have, int to, int atLevel, int missing) { this.skill = skill; this.have = have; this.to = to; this.atLevel = atLevel; this.missing = missing; }
    }

    // up to `count` unmet steps, each skill once (its furthest target among the next steps in a row is not merged: the Build's order
    // is the advice). rules may be empty (pack without skillrules.tsv): then no level or prerequisite check
    static List<Next> next(BuildGuide g, int stage, Live live, Map<Integer, Rule> rules, int count) {
        List<Next> out = new ArrayList<>();
        java.util.Set<Integer> shown = new java.util.HashSet<>();
        for (BuildGuide.Step s : steps(g, stage)) {
            if (out.size() >= count) break;
            int have = live.skill(s.skill);
            if (have >= s.to || shown.contains(s.skill)) continue;
            shown.add(s.skill);
            Rule r = rules.get(s.skill);
            int at = 0, missing = -1;
            if (r != null) {
                int need = r.req + have;   // the next point's required level
                if (live.level < need) at = need;
                for (int p : r.pre) if (p >= 0 && live.skill(p) < 1) { missing = p; break; }
            }
            out.add(new Next(s.skill, have, s.to, at, missing));
        }
        return out;
    }

    // every step at this stage met
    static boolean skillsDone(BuildGuide g, int stage, Live live) {
        for (BuildGuide.Step s : steps(g, stage)) if (live.skill(s.skill) < s.to) return false;
        return true;
    }

    // skills to push past 20: only once the cap is above 20, and only those still under it
    static List<Integer> after20(BuildGuide g, Live live) {
        List<Integer> out = new ArrayList<>();
        if (live.cap <= 20) return out;
        for (int id : g.after20) if (live.skill(id) < live.cap) out.add(id);
        return out;
    }

    // ---- stats ----
    // the first "to" rule not met; null when all are (the stage's gear and rest rules are then the advice)
    static BuildGuide.StatRule nextStat(BuildGuide.Stage s, Live live) {
        for (BuildGuide.StatRule r : s.stats) if (r.kind == BuildGuide.StatRule.TO && live.base[r.stat] < r.to) return r;
        return null;
    }

    static BuildGuide.StatRule restStat(BuildGuide.Stage s) {
        for (BuildGuide.StatRule r : s.stats) if (r.kind == BuildGuide.StatRule.REST) return r;
        return null;
    }

    // ---- breakpoints ----
    // index of the highest tier reached in an ascending table (0 = the first entry, usually 0%); -1 for an empty table
    static int tier(int[] table, int have) {
        int t = -1;
        for (int i = 0; i < table.length; i++) if (have >= table[i]) t = i;
        return t;
    }

    // how much more reaches the next tier; 0 at the top
    static int toNext(int[] table, int have) {
        int t = tier(table, have);
        return t + 1 < table.length ? table[t + 1] - have : 0;
    }
}

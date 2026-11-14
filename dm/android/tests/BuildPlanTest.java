package com.xiaoboluo.d2r;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// The Deck's Build tab without Android: the guide file format (BuildGuide, MiniJson), where the character is and what comes next
// (BuildPlan), item names (ItemNames) and the per-character choice (BuildChoice).
public class BuildPlanTest {
    private static int cases;
    private static void check(String what, Object want, Object actual) {
        cases++;
        if (!java.util.Objects.equals(want, actual)) throw new AssertionError(what + ": want=" + want + " actual=" + actual);
    }
    private static void throwsOn(String what, String json) {
        cases++;
        try { BuildGuide.parse(json); } catch (IllegalArgumentException e) { return; }
        throw new AssertionError(what + ": no error");
    }

    // a small Sorceress build: Ice Bolt 39, Ice Blast 45, Glacial Spike 55, Blizzard 59, Static Field 42, Telekinesis 43, Teleport 54
    static final String GUIDE = "{ \"id\": \"t-blizz\", \"class\": \"sor\", \"name\": \"Test Blizzard\", \"summary\": \"s\",\n"
        + " \"links\": [{\"title\": \"Full guide\", \"url\": \"https://example.org/g\"}],\n"
        + " \"breakpoints\": {\"fcr\": [0, 9, 20, 37, 63, 105, 200], \"fcrTarget\": 105, \"fhr\": [0, 5, 9, 14, 20, 30, 42, 60], \"fhrTarget\": 60},\n"
        + " \"after20\": [59, 55],\n"
        + " \"merc\": {\"type\": \"Act 2 Holy Freeze\", \"gear\": [{\"slot\": \"Weapon\", \"items\": [\"Insight\"]}]},\n"
        + " \"stages\": [\n"
        + "  {\"name\": \"Early\", \"to\": 12, \"skills\": [{\"skill\": 39, \"to\": 3}, {\"skill\": 42, \"to\": 1}],\n"
        + "   \"stats\": [{\"stat\": \"vit\", \"rest\": true}], \"gear\": [{\"slot\": \"Body\", \"items\": [\"Stealth|Studded Leather\"]}], \"tips\": [\"t1\"], \"gearTips\": [\"g1\"]},\n"
        + "  {\"name\": \"Mid\", \"to\": 26, \"skills\": [{\"skill\": 43, \"to\": 1}, {\"skill\": 54, \"to\": 1}],\n"
        + "   \"stats\": [{\"stat\": \"str\", \"to\": 45}, {\"stat\": \"dex\", \"gear\": true}, {\"stat\": \"vit\", \"rest\": true}]},\n"
        + "  {\"name\": \"Respec\", \"respec\": true, \"skills\": [{\"skill\": 39, \"to\": 1}, {\"skill\": 45, \"to\": 1}, {\"skill\": 55, \"to\": 1},\n"
        + "   {\"skill\": 59, \"to\": 20}, {\"skill\": 55, \"to\": 20}],\n"
        + "   \"stats\": [{\"stat\": \"str\", \"gear\": true}, {\"stat\": \"vit\", \"rest\": true}]}\n"
        + " ] }";

    static final String RULES = "id\tclass\treqlevel\tmaxlvl\treq1\treq2\treq3\n"
        + "39\tsor\t1\t20\t-1\t-1\t-1\n42\tsor\t6\t20\t-1\t-1\t-1\n43\tsor\t6\t20\t-1\t-1\t-1\n"
        + "45\tsor\t6\t20\t39\t-1\t-1\n54\tsor\t18\t20\t43\t-1\t-1\n55\tsor\t18\t20\t45\t-1\t-1\n59\tsor\t24\t20\t44\t55\t-1\n";

    static BuildPlan.Live live(int level, int... skillPairs) {
        int[] raw = new int[BuildPlan.Live.HEAD + skillPairs.length];
        raw[0] = 1; raw[1] = 1; raw[2] = level; raw[3] = 40; raw[4] = 25; raw[5] = 50; raw[6] = 35; raw[7] = 5; raw[8] = 2;
        raw[9] = 63; raw[10] = 31; raw[11] = 0; raw[12] = 20; raw[13] = skillPairs.length / 2;
        System.arraycopy(skillPairs, 0, raw, BuildPlan.Live.HEAD, skillPairs.length);
        return BuildPlan.Live.decode(raw, raw.length);
    }

    public static void main(String[] args) {
        // MiniJson
        Object j = MiniJson.parse("{\"a\": [1, 2.5, -3e1], \"b\": \"x\\\"\\u00e9\\n\", \"c\": true, \"d\": null, \"e\": {}}");
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) j;
        check("json numbers", Arrays.asList(1.0, 2.5, -30.0), m.get("a"));
        check("json escapes", "x\"é\n", m.get("b"));
        check("json literals", true, m.get("c"));
        check("json null kept", true, m.containsKey("d") && m.get("d") == null);
        cases++;
        try { MiniJson.parse("{\"a\": 1,}"); throw new AssertionError("trailing comma accepted"); } catch (IllegalArgumentException e) { /* ok */ }

        // the guide file
        BuildGuide g = BuildGuide.parse(GUIDE);
        check("class index", 1, g.cls);
        check("three stages", 3, g.stages.size());
        check("last stage open-ended", Integer.MAX_VALUE, g.stages.get(2).to);
        check("respec flag", true, g.stages.get(2).respec);
        check("stat kinds", BuildGuide.StatRule.GEAR, g.stages.get(1).stats.get(1).kind);
        check("gear tips", Arrays.asList("g1"), g.stages.get(0).gearTips);
        check("no breakpoint tips: empty", 0, g.stages.get(1).bpTips.size());
        check("merc", "Act 2 Holy Freeze", g.mercType);
        check("link", "https://example.org/g", g.links.get(0).url);
        throwsOn("unknown class", "{\"id\": \"x\", \"name\": \"x\", \"class\": \"foo\", \"stages\": [{}]}");
        throwsOn("stage ends must rise", "{\"id\": \"x\", \"name\": \"x\", \"class\": \"sor\", \"stages\": [{\"to\": 10}, {\"to\": 5}, {}]}");
        throwsOn("unknown stat", "{\"id\": \"x\", \"name\": \"x\", \"class\": \"sor\", \"stages\": [{\"stats\": [{\"stat\": \"luck\"}]}]}");

        // stage by level
        check("level 1 -> first stage", 0, BuildPlan.stage(g, 1));
        check("level 12 -> first stage (inclusive)", 0, BuildPlan.stage(g, 12));
        check("level 13 -> second stage", 1, BuildPlan.stage(g, 13));
        check("level 90 -> last stage", 2, BuildPlan.stage(g, 90));
        check("second stage starts at 13", 13, BuildPlan.stageFrom(g, 1));

        // steps carry on until a respec
        check("mid stage has early steps too", 4, BuildPlan.steps(g, 1).size());
        check("respec starts over", 5, BuildPlan.steps(g, 2).size());

        // next skill steps
        Map<Integer, BuildPlan.Rule> rules = BuildPlan.parseRules(RULES);
        List<BuildPlan.Next> n = BuildPlan.next(g, 0, live(4, 39, 2), rules, 3);
        check("first unmet: Ice Bolt 2 -> 3", 39, n.get(0).skill);
        check("can go in now", 0, n.get(0).atLevel);
        check("Static Field needs level 6", 6, n.get(1).atLevel);
        n = BuildPlan.next(g, 1, live(15, 39, 3, 42, 1), rules, 3);
        check("met steps skipped", 43, n.get(0).skill);
        check("Teleport waits for level 18", 18, n.get(1).atLevel);
        check("Teleport misses Telekinesis", 43, n.get(1).missing);
        n = BuildPlan.next(g, 2, live(28, 39, 1, 45, 1, 55, 1, 59, 5), rules, 3);
        check("after respec: Blizzard next", 59, n.get(0).skill);
        check("next point of Blizzard 5 needs 24 + 5", 29, n.get(0).atLevel);
        check("then Glacial Spike to 20", 55, n.get(1).skill);
        check("a skill shows once", 2, n.size());
        check("no rules: no level check", 0, BuildPlan.next(g, 2, live(30, 59, 5), Collections.emptyMap(), 3).get(0).atLevel);
        check("not done", false, BuildPlan.skillsDone(g, 2, live(80, 39, 1, 45, 1, 55, 20, 59, 19)));
        check("done", true, BuildPlan.skillsDone(g, 2, live(80, 39, 1, 45, 1, 55, 20, 59, 20)));

        // past 20
        BuildPlan.Live capped = live(90, 59, 20, 55, 25);
        check("cap 20: nothing past 20", 0, BuildPlan.after20(g, capped).size());
        capped.cap = 40;
        check("raised cap: both still under it", Arrays.asList(59, 55), BuildPlan.after20(g, capped));
        capped.skills.put(55, 40);
        check("at the cap: left out", Arrays.asList(59), BuildPlan.after20(g, capped));

        // stats
        BuildGuide.StatRule r = BuildPlan.nextStat(g.stages.get(1), live(20));
        check("Strength to 45 (have 40)", 0, r.stat);
        check("rest rule", 2, BuildPlan.restStat(g.stages.get(1)).stat);
        BuildPlan.Live strong = live(20); strong.base[0] = 45;
        check("met: no next", null, BuildPlan.nextStat(g.stages.get(1), strong));

        // breakpoints
        check("63 FCR is tier 4", 4, BuildPlan.tier(g.fcr, 63));
        check("+42 to 105", 42, BuildPlan.toNext(g.fcr, 63));
        check("past the top", 0, BuildPlan.toNext(g.fcr, 250));
        check("below 0 entry", -1, BuildPlan.tier(new int[] { 5 }, 0));

        // the host snapshot
        BuildPlan.Live l = BuildPlan.Live.decode(new int[BuildPlan.Live.HEAD], BuildPlan.Live.HEAD);
        check("not in a run", false, l.valid);
        l = live(10, 39, 3);
        check("decoded skill", 3, l.skill(39));
        check("absent skill = 0", 0, l.skill(59));
        check("cap at least 20", 20, l.cap);

        // item names
        ItemNames names = ItemNames.parse("key\tref\trunes\tenUS\tdeDE\n"
            + "stealth\trw\tr03 r04\tStealth\tTarnung\nstuddedleather\tstu\t\tStudded Leather\tBeschlagenes Leder\n"
            + "talrune\tr07\t\tTal Rune\tTal-Rune\nr03x\tr03\t\tTal Rune\t\nr04x\tr04\t\tEth Rune\tEth-Rune\n"
            + "harlequincrest\tu248\t\tHarlequin Crest\t\n");
        check("runeword with base, German", "Tarnung · Beschlagenes Leder", names.display("Stealth|Studded Leather", 1));
        check("English falls back", "Harlequin Crest", names.display("Harlequin  Crest", 1));
        check("unknown as written", "Short Staff +2 Charged Bolt", names.display("Short Staff +2 Charged Bolt", 0));
        check("runes, English without 'Rune'", "Tal Eth", names.runes("Stealth", 0));
        check("runes, German (English fallback loses 'Rune' too)", "Tal Eth-Rune", names.runes("Stealth", 1));
        check("note: runeword with runes", "kept for Tarnung (Tal Eth-Rune).", names.expand("kept for [Stealth].", 1));
        check("note: item, no runes", "a Harlequin Crest or Stealth (Tal Eth)", names.expand("a [Harlequin Crest] or [Stealth]", 0));
        check("note: unknown and unclosed as written", "a Tiara and [Stealth", names.expand("a [Tiara] and [Stealth", 0));
        java.util.Map<String, Integer> held = new java.util.HashMap<>();
        held.put("r03", 1);
        check("rune line: held one checked", "\u0001Tal\u0003 \u0002✓\u0003 \u0001Eth\u0003", names.runeLine("Stealth", 0, held));
        check("note with marks", "for Stealth (\u0001Tal\u0003 \u0001Eth\u0003).", names.expand("for [Stealth].", 0, new java.util.HashMap<>()));
        check("source: highest missing rune (Eth, 4)", "Countess, Normal", names.source("Stealth|Studded Leather", held));
        held.put("r04", 1);
        check("source: all held", "", names.source("Stealth", held));
        check("source: not a runeword", "", names.source("Harlequin Crest", held));
        check("unique picture", "u248", names.picture("Harlequin Crest"));
        check("runeword: its base's picture", "stu", names.picture("Stealth|Studded Leather"));
        check("runeword alone: none", null, names.picture("Stealth"));

        // the per-character choice
        BuildGuide other = BuildGuide.parse(GUIDE.replace("t-blizz", "t-other"));
        BuildChoice c = BuildChoice.parse("Alice\tt-other\nBad line\n");
        check("saved choice", "t-other", c.pick("Alice", Arrays.asList(g, other)).id);
        check("no choice, two builds: picker", null, c.pick("Bob", Arrays.asList(g, other)));
        check("no choice, one build: that one", "t-blizz", c.pick("Bob", Arrays.asList(g)).id);
        check("saved build gone: picker", null, c.pick("Alice", Arrays.asList(g, BuildGuide.parse(GUIDE.replace("t-blizz", "t-third")))));
        c.set("Bob", "t-blizz");
        check("round trip", "Alice\tt-other\nBob\tt-blizz\n", BuildChoice.parse(c.text()).text());
        System.out.println("BuildPlanTest OK (" + cases + " cases)");
    }
}

package com.xiaoboluo.d2r;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The Deck's Build tab (drawn into ThorDeck's body; the logic is BuildPlan, the files BuildGuide): a target Build for the character's
// class and where the character is in it.
//   Header   the Build's name (tap: choose another Build for this class) and one chip per Stage, the character's own lit. Tapping a
//            chip shows that stage on the right; tapping the lit one goes back to following the character's level.
//   Now      (left, fixed) the character's next steps: the next skill points in the Build's order (with the level each needs, or the
//            missing prerequisite), past 20 once a Rift has raised the cap, the next stat rule, a respec card when the character has
//            points outside the stage's plan, and buttons that open the guides it follows (GuideWeb, on the bottom screen).
//   Sections (right, scrolls) Skills, Stats, Gear, Merc and Breakpoints of the shown stage.
// The choice is saved per character name (BuildChoice, files/deck_builds.txt). Live numbers come from nativeBuild, read while the tab
// shows (the host reads them on its tick only then).
final class BuildTab {
    private static final String TAG = "DmDeck";

    interface Host {
        Context context();
        DeckArt art();
        DeckIcons icons();
        int lang();
        String text(String key);
        String skillName(int id);
        Bitmap skillAtlas(int atlas);
        void openGuide(String title, String url);
        void redraw();
    }

    private static final int TEXT = 0xFFE8D9B0, DIM = 0xFF8A7F68, GOLD = 0xFFC7A04A, RED = 0xFFE04030, GREEN = 0xFF7FC060, BLUE = 0xFF7FA8FF,
        RUNE = 0xFFFFA530,   // rune names: the game's orange for runes
        PANEL = DeckArt.PANEL, PRESSED = DeckArt.PRESSED;
    private static final String[] SECTION_KEY = { "dmDeckSkills", "dmBuildStats", "dmBuildGear", "dmBuildMerc", "dmBuildBp" };
    private static final String[] STAT_KEY = { "dmBuildStr", "dmBuildDex", "dmBuildVit", "dmBuildEne" };
    private static final int RAW = BuildPlan.Live.HEAD + 2 * 64;

    // guides, rules and names: loaded once per process
    private static List<BuildGuide> guides;
    private static Map<Integer, BuildPlan.Rule> rules;
    private static ItemNames items;
    private Map<String, Integer> have = new HashMap<>();   // runes held (BagModel.runes), for the runeword checks

    private final Host host;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final int[] raw = new int[RAW], rawLast = new int[RAW];
    private BuildPlan.Live live = new BuildPlan.Live();
    private String name = "";
    private BuildChoice choice;
    private BuildGuide guide;
    private int viewStage = -1, section, lastAuto = -1;
    private static int sectionKept;   // kept across shows of the Deck, like ThorDeck's tab
    private boolean picking;
    private float scroll, scrollMax;
    private List<Row> rows;           // the shown section, laid out for the current width; null = lay out again
    private final Map<Integer, int[]> iconFrame = new HashMap<>();

    // layout
    private final RectF body = new RectF(), head = new RectF(), nameBox = new RectF(), now = new RectF(), sections = new RectF(), content = new RectF();
    private final List<RectF> chips = new ArrayList<>(), secTabs = new ArrayList<>(), linkBtn = new ArrayList<>(), pickRows = new ArrayList<>();
    private final RectF tmp = new RectF();
    private final Rect src = new Rect();
    private float k = 1;

    // touch: one finger; a tap if it stays within SLOP, else a drag that scrolls the content
    private int downId = -1;
    private float downX, downY, lastY;
    private boolean dragging;
    private String pressed;   // target under the finger, for press feedback ("chip3", "sec1", "link0", "pick2", "name")

    BuildTab(Host host) {
        this.host = host;
        line.setStyle(Paint.Style.STROKE);
        section = sectionKept;
    }

    // ---- data ----
    private static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String overlayFile(Context c, String rel) {
        File f = new File(c.getFilesDir(), "dm/overlay/deck/" + rel);
        try { if (f.isFile() && f.length() <= 4 * 1024 * 1024) return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
        catch (java.io.IOException | SecurityException e) { /* none */ }
        return "";
    }

    private void load() {
        Context c = host.context();
        if (guides == null) {
            List<BuildGuide> out = new ArrayList<>();
            try {
                String[] files = c.getAssets().list("builds");
                if (files != null) {
                    Arrays.sort(files);
                    for (String f : files) {
                        if (!f.endsWith(".json")) continue;
                        try (InputStream in = c.getAssets().open("builds/" + f)) { out.add(BuildGuide.parse(readAll(in))); }
                        catch (IllegalArgumentException e) { Log.w(TAG, "build guide " + f + ": " + e.getMessage()); }
                    }
                }
            } catch (java.io.IOException e) { Log.w(TAG, "build guides: " + e); }
            guides = out;
            rules = BuildPlan.parseRules(overlayFile(c, "skillrules.tsv"));
            items = ItemNames.parse(overlayFile(c, "itemnames.tsv"));
            Log.i(TAG, "Build tab: " + out.size() + " guides, " + rules.size() + " skill rules");
        }
        if (choice == null) {
            File f = new File(c.getFilesDir(), "deck_builds.txt");
            String s = "";
            try { if (f.isFile() && f.length() < 64 * 1024) s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
            catch (java.io.IOException | SecurityException e) { s = ""; }
            choice = BuildChoice.parse(s);
        }
    }

    private void save() {
        File f = new File(host.context().getFilesDir(), "deck_builds.txt"), t = new File(f.getPath() + ".tmp");
        try { Files.write(t.toPath(), choice.text().getBytes(StandardCharsets.UTF_8)); if (!t.renameTo(f)) Log.w(TAG, "deck_builds.txt: rename failed"); }
        catch (java.io.IOException | SecurityException e) { Log.w(TAG, "deck_builds.txt: " + e); }
    }

    private List<BuildGuide> forClass() {
        List<BuildGuide> out = new ArrayList<>();
        if (live.valid) for (BuildGuide g : guides) if (g.cls == live.cls) out.add(g);
        return out;
    }

    // ---- polling (ThorDeck.poll, 10 Hz while the tab shows) ----
    void shown(boolean on) { ThorDeck.nativeBuildWant(on); if (on) { Arrays.fill(rawLast, Integer.MIN_VALUE); load(); } }

    void poll() {
        int n = ThorDeck.nativeBuild(raw);
        if (n < 0) return;
        Arrays.fill(raw, Math.max(0, n), RAW, 0);
        if (Arrays.equals(raw, rawLast)) return;
        System.arraycopy(raw, 0, rawLast, 0, RAW);
        BuildPlan.Live next = BuildPlan.Live.decode(raw, n);
        String nm = next.valid ? ThorDeck.nativeBuildName() : "";
        if (!nm.equals(name) || next.cls != live.cls || next.valid != live.valid) {   // another character: its own choice
            live = next; name = nm; guide = next.valid ? choice.pick(name, forClass()) : null;
            viewStage = -1; lastAuto = -1; picking = guide == null && next.valid; scroll = 0;
        } else live = next;
        if (guide != null) {
            int auto = BuildPlan.stage(guide, live.level);
            if (auto != lastAuto) { if (viewStage == auto) viewStage = -1; lastAuto = auto; }
        }
        rows = null;
        host.redraw();
    }

    // the player's items changed: returns true when the runes held changed (then the rows are rebuilt)
    boolean bag(BagModel.Item[] all) {
        Map<String, Integer> next = BagModel.runes(all);
        if (next.equals(have)) return false;
        have = next; rows = null;
        return true;
    }

    private int shownStage() { return guide == null ? 0 : viewStage >= 0 ? viewStage : BuildPlan.stage(guide, live.level); }

    // ---- layout ----
    void layout(float left, float top, float right, float bottom, float scale) {
        k = scale;
        float pad = 18 * k, gap = 14 * k;
        body.set(left, top, right, bottom);
        head.set(left + pad, top, right - pad, top + 64 * k);
        nameBox.set(head.left, head.top, head.left + 400 * k, head.bottom);
        now.set(left + pad, head.bottom + gap, left + pad + 400 * k, bottom - pad);
        sections.set(now.right + gap, now.top, right - pad, now.top + 56 * k);
        content.set(sections.left, sections.bottom + gap * 0.6f, right - pad, bottom - pad);
        secTabs.clear();
        float sw = (sections.width() - 4 * gap * 0.5f) / 5;
        for (int i = 0; i < 5; i++) secTabs.add(new RectF(sections.left + i * (sw + gap * 0.5f), sections.top, sections.left + i * (sw + gap * 0.5f) + sw, sections.bottom));
        line.setStrokeWidth(Math.max(2, 2.5f * k));
        rows = null; chips.clear();
    }

    private void layoutChips() {
        chips.clear();
        if (guide == null) return;
        int n = guide.stages.size();
        float gap = 8 * k, l = nameBox.right + 14 * k, w = (head.right - l - (n - 1) * gap) / n;
        for (int i = 0; i < n; i++) chips.add(new RectF(l + i * (w + gap), head.top + 6 * k, l + i * (w + gap) + w, head.bottom - 6 * k));
    }

    // ---- drawing ----
    void draw(Canvas c, android.graphics.Typeface label, android.graphics.Typeface numbers) {
        text.setTypeface(label);
        text.setShadowLayer(3 * k, 0, 2 * k, 0xE0000000);
        if (guides == null) load();
        if (!live.valid) { centred(c, host.text("dmDeckIdle"), body.centerX(), body.centerY(), 34 * k, body.width() * 0.9f, DIM); return; }
        if (forClass().isEmpty()) { centred(c, host.text("dmBuildNone"), body.centerX(), body.centerY(), 34 * k, body.width() * 0.9f, DIM); return; }
        if (chips.size() != (guide == null ? 0 : guide.stages.size())) layoutChips();
        drawHeader(c);
        if (picking || guide == null) { drawPicker(c); return; }
        drawNow(c, numbers);
        drawSections(c);
    }

    private void centred(Canvas c, String s, float cx, float cy, float size, float fit, int colour) {
        text.setTextAlign(Paint.Align.CENTER); text.setColor(colour); text.setTextSize(size);
        float tw = text.measureText(s);
        if (tw > fit) text.setTextSize(size * fit / tw);
        c.drawText(s, cx, cy - (text.descent() + text.ascent()) / 2, text);
    }

    // left-aligned, one line, shrunk to fit; returns the width drawn
    private float left(Canvas c, String s, float x, float baseline, float size, float fit, int colour) {
        text.setTextAlign(Paint.Align.LEFT); text.setColor(colour); text.setTextSize(size);
        float tw = text.measureText(s);
        if (tw > fit && fit > 0) { text.setTextSize(size * fit / tw); tw = fit; }
        c.drawText(s, x, baseline, text);
        return tw;
    }

    private void button(Canvas c, RectF r, boolean on, boolean down) {
        DeckArt art = host.art();
        art.frame(c, down ? "button_down" : on ? "button" : "button_off", null, r, 20, Math.min(r.width() / 330, r.height() / 60), down || on ? PRESSED : PANEL);
    }

    private void drawHeader(Canvas c) {
        button(c, nameBox, picking, "name".equals(pressed));
        String title = guide == null ? host.text("dmBuildChoose") : guide.name;
        centred(c, title + "  ▾", nameBox.centerX(), nameBox.centerY(), 30 * k, nameBox.width() * 0.88f, guide == null ? GOLD : TEXT);
        if (guide == null) return;
        int auto = BuildPlan.stage(guide, live.level), shown = shownStage();
        for (int i = 0; i < chips.size(); i++) {
            RectF r = chips.get(i);
            boolean down = ("chip" + i).equals(pressed);
            fill.setColor(down ? 0xC0302820 : i == shown ? 0xC0403420 : 0x90000000); c.drawRect(r, fill);
            line.setColor(i == auto ? GOLD : 0x808C7340); c.drawRect(r, line);
            BuildGuide.Stage s = guide.stages.get(i);
            String band = s.to == Integer.MAX_VALUE ? BuildPlan.stageFrom(guide, i) + "+" : BuildPlan.stageFrom(guide, i) + "–" + s.to;
            centred(c, band, r.centerX(), r.top + r.height() * 0.36f, r.height() * 0.36f, r.width() * 0.9f, i == auto ? GOLD : i == shown ? TEXT : DIM);
            centred(c, s.name, r.centerX(), r.top + r.height() * 0.74f, r.height() * 0.26f, r.width() * 0.92f, s.respec ? RED : i == shown ? TEXT : DIM);   // respec stages in red
        }
    }

    private void drawPicker(Canvas c) {
        List<BuildGuide> list = forClass();
        tmp.set(now.left, now.top, content.right, content.bottom);
        fill.setColor(0xC0000000); c.drawRect(tmp, fill);
        centred(c, host.text("dmBuildChoose"), tmp.centerX(), tmp.top + 36 * k, 34 * k, tmp.width() * 0.9f, GOLD);
        pickRows.clear();
        float rh = 110 * k, y = tmp.top + 76 * k;
        for (int i = 0; i < list.size() && y + rh <= tmp.bottom; i++, y += rh + 12 * k) {
            RectF r = new RectF(tmp.left + 40 * k, y, tmp.right - 40 * k, y + rh);
            pickRows.add(r);
            BuildGuide g = list.get(i);
            button(c, r, guide == g, ("pick" + i).equals(pressed));
            left(c, g.name, r.left + 28 * k, r.top + rh * 0.42f, 34 * k, r.width() - 56 * k, guide == g ? GOLD : TEXT);
            left(c, g.summary, r.left + 28 * k, r.top + rh * 0.78f, 24 * k, r.width() - 56 * k, DIM);
        }
    }

    // a skill's icon in a square, from the game's own atlases (ThorDeck.nativeSkillIcon); an empty square without one
    private void skillIcon(Canvas c, int id, float x, float y, float size) {
        tmp.set(x, y, x + size, y + size);
        fill.setColor(0xC0000000); c.drawRect(tmp, fill);
        int[] f = iconFrame.get(id);
        if (f == null) { f = new int[5]; if (ThorDeck.nativeSkillIcon(id, f) == 0) f[0] = -1; iconFrame.put(id, f); }
        Bitmap a = f[0] >= 0 ? host.skillAtlas(f[0]) : null;
        if (a != null && f[3] > 0) { src.set(f[1], f[2], f[1] + f[3], f[2] + f[4]); c.drawBitmap(a, src, tmp, bitmapPaint); }
        line.setColor(0xA08C7340); c.drawRect(tmp, line);
    }

    private String skill(int id) { String n = host.skillName(id); return n.isEmpty() ? "#" + id : n; }

    private void drawNow(Canvas c, android.graphics.Typeface numbers) {
        fill.setColor(0x90000000); c.drawRect(now, fill); line.setColor(0x808C7340); c.drawRect(now, line);
        float x = now.left + 18 * k, w = now.width() - 36 * k, y = now.top + 42 * k;
        int cur = BuildPlan.stage(guide, live.level);
        BuildGuide.Stage st = guide.stages.get(cur);
        left(c, host.text("dmBuildNow"), x, y, 32 * k, w * 0.4f, GOLD);
        String pts = String.format(host.text("dmBuildPoints"), live.skillPoints, live.statPoints);
        text.setTextAlign(Paint.Align.RIGHT); text.setTextSize(24 * k); text.setColor(live.skillPoints + live.statPoints > 0 ? RED : DIM);
        if (text.measureText(pts) > w * 0.58f) text.setTextSize(24 * k * w * 0.58f / text.measureText(pts));
        c.drawText(pts, now.right - 18 * k, y, text);
        y += 22 * k;
        // next skill points
        List<BuildPlan.Next> next = BuildPlan.next(guide, cur, live, rules, 3);
        boolean past = false;
        if (next.isEmpty()) {
            List<Integer> more = BuildPlan.after20(guide, live);
            if (!more.isEmpty()) { past = true; for (int i = 0; i < Math.min(3, more.size()); i++) next.add(new BuildPlan.Next(more.get(i), live.skill(more.get(i)), live.cap, 0, -1)); }
        }
        if (past) { y += 30 * k; left(c, host.text("dmBuildPast20"), x, y, 24 * k, w, BLUE); }
        float icon = 56 * k;
        for (BuildPlan.Next n : next) {
            y += 14 * k;
            skillIcon(c, n.skill, x, y, icon);
            float tx = x + icon + 14 * k, tw = w - icon - 14 * k;
            left(c, skill(n.skill), tx, y + icon * 0.42f, 26 * k, tw, n.atLevel == 0 && n.missing < 0 ? TEXT : DIM);
            String sub = n.have + " → " + n.to;
            if (n.missing >= 0) sub += "   " + String.format(host.text("dmBuildNeeds"), skill(n.missing));
            else if (n.atLevel > 0) sub += "   " + String.format(host.text("dmBuildAtLevel"), n.atLevel);
            left(c, sub, tx, y + icon * 0.88f, 22 * k, tw, n.missing >= 0 ? RED : n.atLevel > 0 ? DIM : GOLD);
            y += icon;
        }
        if (next.isEmpty()) { y += 40 * k; left(c, host.text("dmBuildDone"), x, y, 26 * k, w, GREEN); }
        // stats
        y += 46 * k;
        BuildGuide.StatRule r = BuildPlan.nextStat(st, live);
        if (r != null) {
            left(c, host.text(STAT_KEY[r.stat]) + "  " + live.base[r.stat] + " → " + r.to + "  (+" + (r.to - live.base[r.stat]) + ")", x, y, 26 * k, w, TEXT);
        } else {
            String gearStat = "";
            for (BuildGuide.StatRule g : st.stats) if (g.kind == BuildGuide.StatRule.GEAR) gearStat += (gearStat.isEmpty() ? "" : ", ") + host.text(STAT_KEY[g.stat]);
            if (!gearStat.isEmpty()) { left(c, String.format(host.text("dmBuildGearStat"), gearStat), x, y, 24 * k, w, TEXT); y += 34 * k; }
            BuildGuide.StatRule rest = BuildPlan.restStat(st);
            if (rest != null) left(c, String.format(host.text("dmBuildRest"), host.text(STAT_KEY[rest.stat])), x, y, 24 * k, w, TEXT);
        }
        // respec card: the stage starts over and the character still has points outside its plan
        if (st.respec && hasLeftovers(cur)) {
            y += 24 * k;
            tmp.set(x, y, x + w, y + 92 * k);
            fill.setColor(0xC0401810); c.drawRect(tmp, fill); line.setColor(RED); c.drawRect(tmp, line);
            centred(c, host.text("dmBuildRespec"), tmp.centerX(), tmp.top + 30 * k, 28 * k, w * 0.92f, TEXT);
            centred(c, host.text("dmBuildRespecSub"), tmp.centerX(), tmp.top + 66 * k, 21 * k, w * 0.92f, DIM);
        }
        // guide buttons at the bottom, at most two
        linkBtn.clear();
        int nl = Math.min(2, guide.links.size());
        float bh = 62 * k, by = now.bottom - 14 * k - bh;
        for (int i = nl - 1; i >= 0; i--, by -= bh + 10 * k) {
            RectF b = new RectF(x, by, x + w, by + bh);
            linkBtn.add(0, b);
            button(c, b, true, ("link" + i).equals(pressed));
            centred(c, guide.links.get(i).title + "  ↗", b.centerX(), b.centerY(), 26 * k, b.width() * 0.86f, TEXT);
        }
    }

    private boolean hasLeftovers(int stage) {
        Set<Integer> plan = new HashSet<>();
        for (BuildGuide.Step s : BuildPlan.steps(guide, stage)) plan.add(s.skill);
        for (int id : guide.after20) plan.add(id);
        for (Map.Entry<Integer, Integer> e : live.skills.entrySet()) {
            BuildPlan.Rule r = rules.get(e.getKey());
            boolean classSkill = r == null || BuildGuide.indexOf(BuildGuide.CLASS_CODE, r.cls) == guide.cls;   // not item or monster skills
            if (e.getValue() > 0 && classSkill && !plan.contains(e.getKey())) return true;
        }
        return false;
    }

    private void drawSections(Canvas c) {
        for (int i = 0; i < secTabs.size(); i++) {
            RectF r = secTabs.get(i);
            DeckArt art = host.art();
            if (!art.draw(c, i == section ? "tab_on" : "tab_off", null, r)) art.frame(c, "", null, r, 0, 1, i == section ? PRESSED : PANEL);
            if (("sec" + i).equals(pressed)) { fill.setColor(0x50000000); c.drawRect(r, fill); }
            centred(c, host.text(SECTION_KEY[i]), r.centerX(), r.centerY() - r.height() * 0.05f, r.height() * 0.42f, r.width() * 0.86f, i == section ? TEXT : DIM);
        }
        fill.setColor(0x90000000); c.drawRect(content, fill); line.setColor(0x808C7340); c.drawRect(content, line);
        if (rows == null) buildRows();
        float total = 0;
        for (Row r : rows) total += r.h;
        scrollMax = Math.max(0, total + 24 * k - content.height());
        scroll = Math.max(0, Math.min(scroll, scrollMax));
        c.save();
        c.clipRect(content);
        float y = content.top + 12 * k - scroll, x = content.left + 20 * k, w = content.width() - 40 * k;
        for (Row r : rows) { if (y + r.h >= content.top && y <= content.bottom) r.draw(c, x, y, w); y += r.h; }
        c.restore();
        if (scrollMax > 0) {   // scroll bar
            float bar = content.height() * content.height() / (content.height() + scrollMax), by = content.top + (content.height() - bar) * scroll / scrollMax;
            fill.setColor(0x808C7340); c.drawRect(content.right - 6 * k, by, content.right - 2 * k, by + bar, fill);
        }
    }

    // ---- section rows ----
    private abstract static class Row {
        final float h;
        Row(float h) { this.h = h; }
        abstract void draw(Canvas c, float x, float y, float w);
    }

    private Row heading(final String s, final int colour) {
        return new Row(52 * k) { void draw(Canvas c, float x, float y, float w) { left(c, s, x, y + 38 * k, 28 * k, w, colour); } };
    }

    // wrapped text; lines measured now with the content width. ItemNames' marks colour spans (runes orange, held checks green).
    private List<Row> paragraph(String s, final float size, final int colour, float indent) {
        List<Row> out = new ArrayList<>();
        text.setTextSize(size);
        float w = content.width() - 40 * k - indent;
        StringBuilder plain = new StringBuilder();
        final int[] col = new int[s.length()];
        int cur = colour;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == ItemNames.RUNE) cur = RUNE; else if (ch == ItemNames.HAVE) cur = GREEN; else if (ch == ItemNames.END) cur = colour;
            else { col[plain.length()] = cur; plain.append(ch); }
        }
        final String p = plain.toString();
        final float ind = indent;
        int start = 0;
        while (start < p.length()) {
            int partEnd = p.indexOf('\n', start);
            if (partEnd < 0) partEnd = p.length();
            while (start < partEnd && p.charAt(start) == ' ') start++;
            if (start >= partEnd) { start = partEnd + 1; continue; }
            int end = start + Math.max(1, text.breakText(p, start, partEnd, true, w, null));
            if (end < partEnd) { int sp = p.lastIndexOf(' ', end); if (sp > start) end = sp; }
            int e = end;
            while (e > start && p.charAt(e - 1) == ' ') e--;
            final int a = start, b = e;
            out.add(new Row(size * 1.35f) {
                void draw(Canvas c, float x, float y, float ww) {
                    text.setTextAlign(Paint.Align.LEFT); text.setTextSize(size);
                    float px = x + ind;
                    for (int i = a; i < b; ) {
                        int j = i;
                        while (j < b && col[j] == col[i]) j++;
                        text.setColor(col[i]); c.drawText(p, i, j, px, y + size, text);
                        px += text.measureText(p, i, j); i = j;
                    }
                }
            });
            start = end;
        }
        return out;
    }

    // One step of the plan: "n  Skill  → target", then ✓ once reached or the current base level in brackets while open. A stage is an
    // ordered list, so a skill can come back in a later step (Holy Fire → 7, Resist Fire → 4, Holy Fire → 11); the number shows that.
    // n <= 0 draws no number (the "after 20" list).
    private Row skillRow(final int n, final int id, final int to) {
        return new Row(64 * k) {
            void draw(Canvas c, float x, float y, float w) {
                int have = live.skill(id);
                boolean met = have >= to;
                float ix = x;
                if (n > 0) { centred(c, String.valueOf(n), x + 16 * k, y + 31 * k, 22 * k, 32 * k, DIM); ix += 40 * k; }
                skillIcon(c, id, ix, y + 4 * k, 52 * k);
                left(c, skill(id), ix + 68 * k, y + 40 * k, 26 * k, w * 0.55f, met ? DIM : TEXT);
                text.setTextAlign(Paint.Align.RIGHT); text.setTextSize(26 * k);
                text.setColor(met ? GREEN : TEXT);
                c.drawText(met ? "✓" : "(" + have + ")", x + w, y + 40 * k, text);
                float stateW = text.measureText(met ? "✓" : "(" + have + ")");
                text.setColor(met ? DIM : GOLD);
                c.drawText("→ " + to, x + w - stateW - 24 * k, y + 40 * k, text);
            }
        };
    }

    private void gearRows(List<Row> out, BuildGuide.Gear g) {
        out.add(heading(g.slot, GOLD));
        final int lang = host.lang();
        for (final String it : g.items) {
            final String nm = items.display(it, lang), runes = items.runeLine(it, lang, have), from = items.source(it, have), pic = items.picture(it);
            out.add(new Row(50 * k) {
                void draw(Canvas c, float x, float y, float w) {
                    Bitmap b = pic == null ? null : host.icons().load(pic);
                    float ix = x + 10 * k;
                    if (b != null) {
                        float s = Math.min(44 * k / b.getWidth(), 44 * k / b.getHeight());
                        tmp.set(ix, y + 3 * k, ix + b.getWidth() * s, y + 3 * k + b.getHeight() * s);
                        c.drawBitmap(b, null, tmp, bitmapPaint);
                    }
                    left(c, nm, x + 64 * k, y + 34 * k, 25 * k, w - 64 * k, TEXT);
                }
            });
            // its runes (held ones checked), then where the highest missing one drops
            if (!runes.isEmpty()) out.addAll(paragraph(runes + (from.isEmpty() ? "" : "   ·   " + from), 22 * k, DIM, 64 * k));
        }
        if (!g.note.isEmpty()) out.addAll(paragraph(items.expand(g.note, lang, have), 22 * k, DIM, 64 * k));
    }

    private void tipRows(List<Row> out, List<String> tips) {
        for (String tip : tips) out.addAll(paragraph("• " + items.expand(tip, host.lang(), have), 23 * k, DIM, 0));
    }

    private Row breakpoint(final String label, final int[] table, final int have, final int target) {
        return new Row(150 * k) {
            void draw(Canvas c, float x, float y, float w) {
                int t = BuildPlan.tier(table, have), more = BuildPlan.toNext(table, have);
                String head = String.format(label, have);
                if (target > 0) head += "   " + String.format(host.text("dmBuildTarget"), target);
                left(c, head, x, y + 34 * k, 27 * k, w, have >= target && target > 0 ? GREEN : TEXT);
                // the table as cells, the reached ones lit, the target framed
                float cw = Math.min(110 * k, w / Math.max(1, table.length)), cy = y + 56 * k, ch = 50 * k;
                for (int i = 0; i < table.length; i++) {
                    tmp.set(x + i * cw + 2 * k, cy, x + (i + 1) * cw - 2 * k, cy + ch);
                    fill.setColor(i <= t ? 0xC0403420 : 0x80000000); c.drawRect(tmp, fill);
                    line.setColor(table[i] == target ? GOLD : 0x608C7340); c.drawRect(tmp, line);
                    centred(c, table[i] + "%", tmp.centerX(), tmp.centerY(), 22 * k, tmp.width() * 0.9f, i <= t ? TEXT : DIM);
                }
                if (more > 0) left(c, String.format(host.text("dmBuildToNext"), more), x, cy + ch + 32 * k, 22 * k, w, GOLD);
            }
        };
    }

    private void buildRows() {
        List<Row> out = new ArrayList<>();
        int si = shownStage();
        BuildGuide.Stage st = guide.stages.get(si);
        String band = st.to == Integer.MAX_VALUE ? BuildPlan.stageFrom(guide, si) + "+" : BuildPlan.stageFrom(guide, si) + "–" + st.to;
        switch (section) {
        case 0:   // skills
            out.add(heading(st.name + "  ·  " + String.format(host.text("dmBuildLevels"), band), TEXT));
            if (st.respec) out.addAll(paragraph(host.text("dmBuildRespecStage"), 23 * k, RED, 0));
            for (int i = 0; i < st.skills.size(); i++) out.add(skillRow(i + 1, st.skills.get(i).skill, st.skills.get(i).to));
            if (si == guide.stages.size() - 1 && guide.after20.length > 0 && live.cap > 20) {
                out.add(heading(host.text("dmBuildPast20"), BLUE));
                for (int id : guide.after20) out.add(skillRow(0, id, live.cap));
            }
            tipRows(out, st.tips);
            break;
        case 1:   // stats
            out.add(heading(st.name, TEXT));
            out.addAll(paragraph(String.format(host.text("dmBuildBase"), live.base[0], live.base[1], live.base[2], live.base[3]), 24 * k, DIM, 0));
            for (BuildGuide.StatRule r : st.stats) {
                String s = host.text(STAT_KEY[r.stat]);
                boolean met = r.kind == BuildGuide.StatRule.TO && live.base[r.stat] >= r.to;
                if (r.kind == BuildGuide.StatRule.TO) s += "  →  " + r.to + (met ? "  " + ItemNames.HAVE + "✓" + ItemNames.END :"  (+" + (r.to - live.base[r.stat]) + ")");
                else if (r.kind == BuildGuide.StatRule.GEAR) s = String.format(host.text("dmBuildGearStat"), s);
                else s = String.format(host.text("dmBuildRest"), s);
                out.addAll(paragraph(s, 27 * k, met ? DIM : TEXT, 0));
                if (!r.note.isEmpty()) out.addAll(paragraph(items.expand(r.note, host.lang(), have), 22 * k, DIM, 24 * k));
            }
            break;
        case 2:   // gear
            out.add(heading(st.name, TEXT));
            if (st.gear.isEmpty()) out.addAll(paragraph(host.text("dmBuildNoGear"), 23 * k, DIM, 0));
            for (BuildGuide.Gear g : st.gear) gearRows(out, g);
            tipRows(out, st.gearTips);
            break;
        case 3:   // mercenary
            out.add(heading(guide.mercType, TEXT));
            if (!guide.mercNote.isEmpty()) out.addAll(paragraph(items.expand(guide.mercNote, host.lang(), have), 23 * k, DIM, 0));
            for (BuildGuide.Gear g : guide.merc) gearRows(out, g);
            break;
        default:  // breakpoints
            if (guide.fcr.length > 0) out.add(breakpoint(host.text("dmBuildFcr"), guide.fcr, live.fcr, guide.fcrTarget));
            if (guide.fhr.length > 0) out.add(breakpoint(host.text("dmBuildFhr"), guide.fhr, live.fhr, guide.fhrTarget));
            out.addAll(paragraph(String.format(host.text("dmBuildIas"), live.ias), 24 * k, DIM, 0));
            tipRows(out, st.bpTips);   // the shown stage's
        }
        out.add(new Row(24 * k) { void draw(Canvas c, float x, float y, float w) { } });
        out.addAll(paragraph(host.text("dmBuildCredit"), 20 * k, DIM, 0));
        rows = out;
    }

    // ---- touch (ThorDeck forwards everything below its tab row while this tab shows) ----
    private String target(float x, float y) {
        if (nameBox.contains(x, y)) return "name";
        if (picking || guide == null) { for (int i = 0; i < pickRows.size(); i++) if (pickRows.get(i).contains(x, y)) return "pick" + i; return null; }
        for (int i = 0; i < chips.size(); i++) if (chips.get(i).contains(x, y)) return "chip" + i;
        for (int i = 0; i < secTabs.size(); i++) if (secTabs.get(i).contains(x, y)) return "sec" + i;
        for (int i = 0; i < linkBtn.size(); i++) if (linkBtn.get(i).contains(x, y)) return "link" + i;
        if (content.contains(x, y)) return "content";
        return null;
    }

    void touch(android.view.MotionEvent e) {
        int idx = e.getActionIndex(), id = e.getPointerId(idx);
        switch (e.getActionMasked()) {
        case android.view.MotionEvent.ACTION_DOWN:
            downId = id; downX = e.getX(idx); downY = lastY = e.getY(idx); dragging = false;
            pressed = target(downX, downY);
            if ("content".equals(pressed)) pressed = null;
            host.redraw(); break;
        case android.view.MotionEvent.ACTION_MOVE: {
            int p = e.findPointerIndex(downId);
            if (p < 0) break;
            float y = e.getY(p);
            if (!dragging && Math.abs(y - downY) > 16 * k && content.contains(downX, downY)) { dragging = true; pressed = null; }
            if (dragging) { scroll = Math.max(0, Math.min(scrollMax, scroll - (y - lastY))); host.redraw(); }
            lastY = y; break;
        }
        case android.view.MotionEvent.ACTION_UP: {
            if (id != downId) break;
            String t = pressed; pressed = null; downId = -1;
            if (!dragging && t != null && t.equals(target(e.getX(idx), e.getY(idx)))) tap(t);
            host.redraw(); break;
        }
        case android.view.MotionEvent.ACTION_CANCEL:
            pressed = null; downId = -1; dragging = false; host.redraw(); break;
        default: break;
        }
    }

    private void tap(String t) {
        if (t.equals("name")) { picking = !picking || guide == null; return; }
        int i = Integer.parseInt(t.replaceAll("[a-z]", ""));
        if (t.startsWith("pick")) {
            List<BuildGuide> list = forClass();
            if (i < list.size()) { guide = list.get(i); choice.set(name, guide.id); save(); picking = false; viewStage = -1; lastAuto = -1; scroll = 0; rows = null; chips.clear(); }
        } else if (t.startsWith("chip")) {
            int auto = BuildPlan.stage(guide, live.level);
            viewStage = i == auto ? -1 : i; scroll = 0; rows = null;
        } else if (t.startsWith("sec")) {
            if (section != i) { section = sectionKept = i; scroll = 0; rows = null; }
        } else if (t.startsWith("link") && i < guide.links.size()) {
            host.openGuide(guide.links.get(i).title, guide.links.get(i).url);
        }
    }
}

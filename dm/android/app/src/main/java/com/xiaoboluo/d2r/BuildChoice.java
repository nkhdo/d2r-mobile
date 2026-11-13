package com.xiaoboluo.d2r;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Which Build each character follows on the Build tab: one line per character, "<name>\t<build id>", kept in the app's own files
// (files/deck_builds.txt), not in the save. Without Android types (tested by BuildPlanTest).
final class BuildChoice {
    private final Map<String, String> byName = new LinkedHashMap<>();

    static BuildChoice parse(String text) {
        BuildChoice c = new BuildChoice();
        for (String line : text.split("\r?\n")) {
            int t = line.indexOf('\t');
            if (t > 0 && t < line.length() - 1) c.byName.put(line.substring(0, t), line.substring(t + 1).trim());
        }
        return c;
    }

    String text() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : byName.entrySet()) b.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        return b.toString();
    }

    void set(String name, String id) { if (!name.isEmpty() && name.indexOf('\t') < 0 && name.indexOf('\n') < 0) byName.put(name, id); }

    // the character's Build among those for its class: the saved one if it is still there, else the only one when the class has one
    // (nothing to choose), else null (the picker shows)
    BuildGuide pick(String name, List<BuildGuide> forClass) {
        String id = byName.get(name);
        if (id != null) for (BuildGuide g : forClass) if (g.id.equals(id)) return g;
        return forClass.size() == 1 ? forClass.get(0) : null;
    }
}

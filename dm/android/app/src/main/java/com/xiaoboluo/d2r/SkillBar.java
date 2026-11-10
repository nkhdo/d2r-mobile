package com.xiaoboluo.d2r;

// The Deck's Skills tab without Android types (tested by SkillBarTest): the skill bound to each controller face, as the host reads the
// game's own binding table (dm_actions.c skillbar_step, deck_jni.c nativeSkills), with the icon frame from the game's skill atlases
// (overlay touchart/dm_native_skill_<atlas>.png, made by native_art/generate.py from the player's files). Faces 0–5 = A, B, X, Y, R, ZR;
// 6–11 = the same with ZL held. Read-only: changing a binding stays in the game's own skill menu.
final class SkillBar {
    static final int INTS = 6, FACES = 6;
    static final String[] FACE_NAME = { "A", "B", "X", "Y", "R", "ZR" };
    // the four face buttons as a diamond, at the game's Switch positions: A right, B bottom, X top, Y left
    static final int[] FACE_DX = { 1, 0, 0, -1 }, FACE_DY = { 0, 1, -1, 0 };

    static final class Slot {
        int id = -1, atlas = -1, x, y, w, h;   // skill id (-1 = empty), icon atlas (-1 = none) and its frame in the atlas
    }

    static Slot[] decode(int[] raw, int n) {
        n = Math.max(0, Math.min(n, raw.length / INTS));
        Slot[] out = new Slot[n];
        for (int i = 0; i < n; i++) {
            int o = i * INTS; Slot s = new Slot();
            s.id = raw[o]; s.atlas = raw[o + 1]; s.x = raw[o + 2]; s.y = raw[o + 3]; s.w = raw[o + 4]; s.h = raw[o + 5];
            out[i] = s;
        }
        return out;
    }
}

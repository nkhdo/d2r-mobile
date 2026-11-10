package com.xiaoboluo.d2r;

// The Deck's Skills tab (SkillBar): the skills bound to the controller faces, decoded from nativeSkills, and where each face sits.
public class SkillBarTest {
    private static int cases;
    private static void check(String what, Object want, Object actual) {
        cases++;
        if (!java.util.Objects.equals(want, actual)) throw new AssertionError(what + ": want=" + want + " actual=" + actual);
    }

    public static void main(String[] args) {
        int[] raw = new int[12 * 6];
        java.util.Arrays.fill(raw, 0);
        for (int i = 0; i < 12; i++) raw[6 * i] = -1;
        raw[0] = 54; raw[1] = 1; raw[2] = 136; raw[3] = 0; raw[4] = 65; raw[5] = 65;   // A: Teleport, atlas 1 frame at x 136
        raw[6 * 7] = 0; raw[6 * 7 + 1] = 8;                                            // ZL + B: Attack, atlas 8
        raw[6 * 11] = 220; raw[6 * 11 + 1] = -1;                                       // ZL + ZR: a skill with no icon
        SkillBar.Slot[] s = SkillBar.decode(raw, 12);
        check("twelve slots", 12, s.length);
        check("A's skill", 54, s[0].id);
        check("A's icon atlas", 1, s[0].atlas);
        check("A's frame x", 136, s[0].x);
        check("empty face", -1, s[1].id);
        check("ZL set starts at 6: ZL + B", 0, s[7].id);
        check("no icon", -1, s[11].atlas);
        check("not in a run", 0, SkillBar.decode(raw, 0).length);

        // the four faces as a diamond (the game's Switch positions): A right, B bottom, X top, Y left; R and ZR are the shoulders
        check("A right", "1,0", SkillBar.FACE_DX[0] + "," + SkillBar.FACE_DY[0]);
        check("B bottom", "0,1", SkillBar.FACE_DX[1] + "," + SkillBar.FACE_DY[1]);
        check("X top", "0,-1", SkillBar.FACE_DX[2] + "," + SkillBar.FACE_DY[2]);
        check("Y left", "-1,0", SkillBar.FACE_DX[3] + "," + SkillBar.FACE_DY[3]);
        check("face names", "R ZR", SkillBar.FACE_NAME[4] + " " + SkillBar.FACE_NAME[5]);
        System.out.println("SkillBarTest OK (" + cases + " cases)");
    }
}

// Native interface of the bottom-screen Deck (the second screen of dual-screen handhelds, ThorDeck.java / SecondScreen.java): one call reads the whole HUD snapshot, one action entry point, and our own UI text.
// Every action goes through the host's existing "callable from any thread" entry points (they store an atomic request that a game-thread pump takes), so nothing here touches game memory.
#include <jni.h>
#include "../host/dm_hud.h"
#include "../host/dm_actions.h"
#include "../host/dm_custom.h"
#include "../host/dm_i18n.h"
#include "../host/dm_inv.h"
#include "../host/dm_world.h"
#include "../host/dm_uiscreen.h"
#include "dm_touch_native_art.h"

void dm_tp_request(void);          // dm_freetp.c
void dm_ui_map_full(int on);       // dm_cursor.c
int dm_ui_map_is_full(void);
void dm_ui_open_tab(int tab);
uint64_t dm_guest_frame(void);     // hle_os.c: frames the game has presented

// snapshot indices match H_* in ThorDeck.java
enum { H_VALID, H_LEVEL, H_EXP, H_HP, H_HP_MAX, H_MP, H_MP_MAX, H_BELT, H_KIND = H_BELT + 4, H_PORTAL = H_KIND + 4,
       H_PLAYERS, H_POINTS, H_QUEST, H_DEAD, H_TOWN, H_SWITCH, H_MAP_FULL, H_AREA, H_LANG, H_WSET, H_MERC_HP, H_MERC_MAX, H_FRAME, H_COUNT };

JNIEXPORT jint JNICALL Java_com_xiaoboluo_d2r_ThorDeck_nativeHud(JNIEnv *env, jclass cls, jintArray out) {
    (void)cls;
    if ((*env)->GetArrayLength(env, out) < H_COUNT) return -1;
    dm_hud_info h; dm_hud_get(&h); int kind[4]; dm_belt_kinds(kind);
    jint v[H_COUNT] = {0};
    // in a run: the player was read on the last game tick AND the game's HUD is up with no load screen. The tick snapshot alone is not
    // enough: ticks stop when the game ends, so after "Save and exit" the last in-run snapshot stayed published and the Deck kept its tabs
    // over empty data at the main menu. The UI flag is updated every frame, menus included
    v[H_VALID] = h.valid && dm_uiscreen_ingame(); v[H_LEVEL] = h.level; v[H_EXP] = h.exp_permille;
    v[H_HP] = h.hp; v[H_HP_MAX] = h.hp_max; v[H_MP] = h.mp; v[H_MP_MAX] = h.mp_max;
    for (int i = 0; i < 4; i++) { v[H_BELT + i] = h.belt[i]; v[H_KIND + i] = kind[i]; }
    v[H_PORTAL] = h.portal; v[H_PLAYERS] = h.players; v[H_POINTS] = h.points; v[H_QUEST] = h.quest_unread; v[H_DEAD] = h.dead;
    v[H_TOWN] = dm_hud_area_is_town(h.area); v[H_SWITCH] = dm_custom_get("deck"); v[H_MAP_FULL] = dm_ui_map_is_full();
    v[H_AREA] = h.area; v[H_LANG] = dm_lang_index();   // area name looked up in Java from the pack (deck/levels.tsv), in the game's language
    v[H_WSET] = dm_swapskill_wset();
    { int mh = 0, mm = 0; dm_merc_life(&mh, &mm); v[H_MERC_HP] = mh; v[H_MERC_MAX] = mm; }   // 0 / 0 = no mercenary or dead   // weapon set in hand: 0 = I, 1 = II, -1 = not known yet
    v[H_FRAME] = (jint)(dm_guest_frame() & 0x7fffffff);   // the Deck's FPS: frames presented, counted over a second in Java
    (*env)->SetIntArrayRegion(env, out, 0, H_COUNT, v);
    return H_COUNT;
}

// Item list (BagModel.java, for the belt's pictures): the host reads the player's items on the server tick only while the Deck asks for them
JNIEXPORT void JNICALL Java_com_xiaoboluo_d2r_ThorDeck_nativeBagWant(JNIEnv *env, jclass cls, jboolean on) { (void)env; (void)cls; dm_inv_want(on); }

// out[0] = list generation (changes whenever the list changes), then 5 ints per item (BagModel.decode). Returns the item count, -1 if out is too short
JNIEXPORT jint JNICALL Java_com_xiaoboluo_d2r_ThorDeck_nativeBag(JNIEnv *env, jclass cls, jintArray out) {
    (void)cls;
    enum { INTS = 5 };
    if ((*env)->GetArrayLength(env, out) < 1 + DM_INV_MAX * INTS) return -1;
    static dm_inv_item items[DM_INV_MAX]; static jint v[1 + DM_INV_MAX * INTS];   // only ever called from the Deck's UI thread
    int gen = 0, n = dm_inv_get(items, DM_INV_MAX, &gen);
    v[0] = gen;
    for (int i = 0; i < n; i++) {
        const dm_inv_item *it = &items[i]; jint *o = v + 1 + i * INTS;
        o[0] = (jint)it->id;
        o[1] = (unsigned char)it->code[0] | (unsigned char)it->code[1] << 8 | (unsigned char)it->code[2] << 16;
        o[2] = it->where | it->quality << 8 | it->unid << 16 | (jint)((uint32_t)it->loc << 24);
        o[3] = (uint8_t)it->x | (uint8_t)it->y << 8 | (uint8_t)it->w << 16 | (jint)((uint32_t)(uint8_t)it->h << 24);
        o[4] = (jint)((uint16_t)it->row | (uint32_t)it->flags << 16);
    }
    (*env)->SetIntArrayRegion(env, out, 0, 1 + n * INTS, v);
    return n;
}

// Map tab (GoalCompass.java): out = n, player x, player y, then x, y, art per goal (dm_map_goals: quest goals, waypoints and real exits of
// the current area, in world cells, then its town portals = 254 and red portals = 253). Returns n, -1 if out is too short
JNIEXPORT jint JNICALL Java_com_xiaoboluo_d2r_ThorDeck_nativeGoals(JNIEnv *env, jclass cls, jintArray out) {
    (void)cls;
    if ((*env)->GetArrayLength(env, out) < 3 + 3 * DM_MAP_GOAL_MAX) return -1;
    dm_map_goal_view g; int n = dm_map_goals(&g);
    if (n < 0) n = 0;
    if (n > DM_MAP_GOAL_MAX) n = DM_MAP_GOAL_MAX;
    jint v[3 + 3 * DM_MAP_GOAL_MAX];
    v[0] = n; v[1] = g.px; v[2] = g.py;
    for (int i = 0; i < n; i++) { v[3 + 3 * i] = g.x[i]; v[4 + 3 * i] = g.y[i]; v[5 + 3 * i] = g.art[i]; }
    // then the town portals (254) and red portals (253) in the area, from the server's object table; with no map goals, the player's
    // cell comes from the HUD snapshot (the server's player: the same world cells)
    dm_portal_view pv; int np = dm_portals(&pv);
    if (np > 0 && n == 0) { dm_hud_info h; dm_hud_get(&h); v[1] = h.x; v[2] = h.y; }
    for (int i = 0; i < np && n < DM_MAP_GOAL_MAX; i++, n++) { v[3 + 3 * n] = pv.x[i]; v[4 + 3 * n] = pv.y[i]; v[5 + 3 * n] = pv.cls[i] == 59 ? 254 : 253; }
    v[0] = n;
    (*env)->SetIntArrayRegion(env, out, 0, 3 + 3 * n, v);
    return n;
}

// Skills tab (SkillBar.java): for faces A, B, X, Y, R, ZR and then the same with ZL held, 6 ints each: skill id (-1 = empty), icon atlas
// (overlay touchart/dm_native_skill_<atlas>.png; -1 = none) and the frame's x, y, width, height in it. Returns 12, 0 when not in a run, -1 if out is too short
JNIEXPORT jint JNICALL Java_com_xiaoboluo_d2r_ThorDeck_nativeSkills(JNIEnv *env, jclass cls, jintArray out) {
    (void)cls;
    if ((*env)->GetArrayLength(env, out) < 12 * 6) return -1;
    int32_t ids[12]; if (!dm_skillbar_get(ids)) return 0;
    jint v[12 * 6];
    for (int i = 0; i < 12; i++) {
        jint *o = v + 6 * i; o[0] = ids[i]; o[1] = -1; o[2] = o[3] = o[4] = o[5] = 0;
        if (ids[i] < 0) continue;
        dm_ta_glyph g = dm_ta_skill((uint32_t)ids[i]); dm_ta_frame fr;
        if (g.status == DM_TA_GLYPH && dm_ta_frame_at(g.atlas, g.normal_frame, &fr)) { o[1] = g.atlas; o[2] = fr.x; o[3] = fr.y; o[4] = fr.width; o[5] = fr.height; }
    }
    (*env)->SetIntArrayRegion(env, out, 0, 12 * 6, v);
    return 12;
}

// action numbers match A_* in ThorDeck.java
enum { A_DRINK, A_FEED_MERC, A_PORTAL, A_MAP, A_SWAP, A_TAB, A_PLAYERS };

JNIEXPORT void JNICALL Java_com_xiaoboluo_d2r_ThorDeck_nativeAction(JNIEnv *env, jclass cls, jint action, jint arg) {
    (void)env; (void)cls;
    switch (action) {
    case A_DRINK: dm_belt_use(arg, 0); break;
    case A_FEED_MERC: dm_belt_use_merc(arg, 0); break;
    case A_PORTAL: dm_tp_request(); break;
    case A_MAP: dm_ui_map_full(!dm_ui_map_is_full()); break;
    case A_SWAP: dm_swap_request(); break;
    case A_TAB: dm_ui_open_tab(arg); break;
    case A_PLAYERS: dm_hud_request_players(arg); break;
    }
}

JNIEXPORT jstring JNICALL Java_com_xiaoboluo_d2r_ThorDeck_nativeText(JNIEnv *env, jclass cls, jstring key) {
    (void)cls;
    const char *k = (*env)->GetStringUTFChars(env, key, NULL);
    const char *s = k ? dm_ui(k) : NULL;
    jstring r = (*env)->NewStringUTF(env, s ? s : "");
    if (k) (*env)->ReleaseStringUTFChars(env, key, k);
    return r;
}

// 底屏 Deck（双屏掌机的第二块屏，ThorDeck.java / SecondScreen.java）的原生接口：一次读全 HUD 快照、一个动作入口、我们自己的界面文字。
// 动作全走宿主已有的「任意线程调」入口（存原子请求，游戏线程的泵取走），这里不碰游戏内存。
// Native interface of the bottom-screen Deck (the second screen of dual-screen handhelds, ThorDeck.java / SecondScreen.java): one call reads the whole HUD snapshot, one action entry point, and our own UI text.
// Every action goes through the host's existing "callable from any thread" entry points (they store an atomic request that a game-thread pump takes), so nothing here touches game memory.
#include <jni.h>
#include "../host/dm_hud.h"
#include "../host/dm_actions.h"
#include "../host/dm_custom.h"
#include "../host/dm_i18n.h"

void dm_tp_request(void);          // dm_freetp.c
void dm_ui_map_full(int on);       // dm_cursor.c
int dm_ui_map_is_full(void);
void dm_ui_open_tab(int tab);

// 快照下标与 ThorDeck.java 的 H_* 一致 | snapshot indices match H_* in ThorDeck.java
enum { H_VALID, H_LEVEL, H_EXP, H_HP, H_HP_MAX, H_MP, H_MP_MAX, H_BELT, H_KIND = H_BELT + 4, H_PORTAL = H_KIND + 4,
       H_PLAYERS, H_POINTS, H_QUEST, H_DEAD, H_TOWN, H_SWITCH, H_MAP_FULL, H_COUNT };

JNIEXPORT jint JNICALL Java_com_xiaoboluo_d2r_ThorDeck_nativeHud(JNIEnv *env, jclass cls, jintArray out) {
    (void)cls;
    if ((*env)->GetArrayLength(env, out) < H_COUNT) return -1;
    dm_hud_info h; dm_hud_get(&h); int kind[4]; dm_belt_kinds(kind);
    jint v[H_COUNT] = {0};
    v[H_VALID] = h.valid; v[H_LEVEL] = h.level; v[H_EXP] = h.exp_permille;
    v[H_HP] = h.hp; v[H_HP_MAX] = h.hp_max; v[H_MP] = h.mp; v[H_MP_MAX] = h.mp_max;
    for (int i = 0; i < 4; i++) { v[H_BELT + i] = h.belt[i]; v[H_KIND + i] = kind[i]; }
    v[H_PORTAL] = h.portal; v[H_PLAYERS] = h.players; v[H_POINTS] = h.points; v[H_QUEST] = h.quest_unread; v[H_DEAD] = h.dead;
    v[H_TOWN] = dm_hud_area_is_town(h.area); v[H_SWITCH] = dm_custom_get("deck"); v[H_MAP_FULL] = dm_ui_map_is_full();
    (*env)->SetIntArrayRegion(env, out, 0, H_COUNT, v);
    return H_COUNT;
}

// 动作号与 ThorDeck.java 的 A_* 一致 | action numbers match A_* in ThorDeck.java
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

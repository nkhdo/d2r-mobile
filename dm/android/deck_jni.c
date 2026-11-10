// Native interface of the bottom-screen Deck (the second screen of dual-screen handhelds, ThorDeck.java / SecondScreen.java): one call reads the whole HUD snapshot, one action entry point, and our own UI text.
// Every action goes through the host's existing "callable from any thread" entry points (they store an atomic request that a game-thread pump takes), so nothing here touches game memory.
#include <jni.h>
#include "../host/dm_hud.h"
#include "../host/dm_actions.h"
#include "../host/dm_custom.h"
#include "../host/dm_i18n.h"
#include "../host/dm_inv.h"

void dm_tp_request(void);          // dm_freetp.c
void dm_ui_map_full(int on);       // dm_cursor.c
int dm_ui_map_is_full(void);
void dm_ui_open_tab(int tab);

// snapshot indices match H_* in ThorDeck.java
enum { H_VALID, H_LEVEL, H_EXP, H_HP, H_HP_MAX, H_MP, H_MP_MAX, H_BELT, H_KIND = H_BELT + 4, H_PORTAL = H_KIND + 4,
       H_PLAYERS, H_POINTS, H_QUEST, H_DEAD, H_TOWN, H_SWITCH, H_MAP_FULL, H_AREA, H_LANG, H_WSET, H_COUNT };

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
    v[H_AREA] = h.area; v[H_LANG] = dm_lang_index();   // area name looked up in Java from the pack (deck/levels.tsv), in the game's language
    v[H_WSET] = dm_swapskill_wset();   // weapon set in hand: 0 = I, 1 = II, -1 = not known yet
    (*env)->SetIntArrayRegion(env, out, 0, H_COUNT, v);
    return H_COUNT;
}

// Bag tab (BagModel.java): the host reads the player's items on the server tick only while the tab asks for them
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

// action numbers match A_* in ThorDeck.java
enum { A_DRINK, A_FEED_MERC, A_PORTAL, A_MAP, A_SWAP, A_TAB, A_PLAYERS, A_USE, A_TO_BELT, A_SORT };

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
    case A_USE: dm_item_use((uint32_t)arg); break;          // Bag tab item sheet: arg = item id
    case A_TO_BELT: dm_belt_put((uint32_t)arg); break;
    case A_SORT: dm_inv_sort(0); break;                      // page 0 = the bag
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

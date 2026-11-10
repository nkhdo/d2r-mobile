# AYN Thor dual screen: research notes (2026-11-10)

Research for adding second-screen support to the Android build, targeting the AYN Thor. No code has been written yet. This note records what the hardware is, how other Thor ports use the second screen, which workarounds they needed, and how that maps onto this repo.

## 1. The hardware

| | Top screen | Bottom screen |
|---|---|---|
| Panel | 6" AMOLED, touch | 3.92" AMOLED, touch |
| Resolution | 1920 × 1080 (exactly 16:9) | 1240 × 1080 as presented (natively portrait 1080 × 1240, shown rotated) |
| Refresh | 120 Hz | 60 Hz |
| Android `Display` | `DEFAULT_DISPLAY` (0) | a real display flagged `FLAG_PRESENTATION`; **id 4** seen in two ports (thor-d2, balatro-dualscreen) |

- SoC: Snapdragon 8 Gen 2 / Adreno 740 (Base, Pro, Max), Snapdragon 865 / Adreno 650 (Lite). The 740 is already covered by `2026-10-25-adreno-740-750-rewrites.md`. **The Lite has not been tested with this port.**
- Android 13 meets the app's minimum (Android 12, Vulkan 1.1).
- The top screen is exactly 16:9, so the game fills it with no letterbox and no true-fullscreen strip.
- The physical controls are a built-in gamepad, which goes through `PadInput` as any other controller does.

Sources: [hothardware](https://hothardware.com/news/ayn-thor-dual-screen-handheld-oled-and-snapdragon), [retrocatalog](https://retrocatalog.com/retro-handhelds/thor), [heldgames](https://heldgames.com/reviews/ayn-thor), [notebookcheck](https://www.notebookcheck.net/AYN-Thor-dual-screen-handheld-begins-shipping-but-only-the-Snapdragon-8-Gen-2-version.1138344.0.html).

## 2. Reference projects

All of these use the standard `android.app.Presentation` API on the secondary display. None of them relies on an AYN SDK.

| Project | What the bottom screen shows | How it draws | Worth reading |
|---|---|---|---|
| [harmssi/thor-d2](https://github.com/harmssi/thor-d2): D2 LoD 1.13d via D2Android/Winlator | "Deck": stats, orbs, skills, effects, inventory/stash/cube/merc with touch item moves, quests | Java `Canvas` View, redrawn every 250 ms; data comes as packets from an in-game mod (`mod/d2deck.c`, read-only, 500 ms period, resent every 3 s) | `D2DeckScreen.java` (the whole Presentation lifecycle in 140 lines), `D2Deck.java` |
| [samyost1/tmc-android](https://github.com/samyost1/tmc-android): Minish Cap | map, quest status, touch inventory | `SurfaceView` whose `Surface` goes to native code, which CPU-draws into it with `ANativeWindow_lock` | `SecondScreenManager.java` (dismiss recovery, swapping screens), `SecondScreenPresentation.java` |
| [rosbean/balatro-dualscreen](https://github.com/rosbean/balatro-dualscreen) | the hand and buttons (interactive) | the game renders RGBA and Java uploads it | `CompanionDisplayManager.java` (foreground debounce), `BalatroActivity.java` (rotation, insets) |
| [kalenjohnson/chrono-duo](https://github.com/kalenjohnson/chrono-duo) | world map, party, battle menu | Java views | `SecondScreenManager.java` |
| [kitcatkode/horizon-android PR #1](https://github.com/kitcatkode/horizon-android/pull/1) | Map / Inventory / Chat tabs | | `SecondScreen.java` |
| [mightyflub/Pinball-NS-Thor PR #1](https://github.com/mightyflub/Pinball-NS-Thor/pull/1) | | | `DualScreen.java`: a minimal example |
| [theswest/DualCPY](https://github.com/theswest/DualCPY) | scrcpy for both screens | | for development: mirrors and records both panels from a PC |

The thor-d2 deck is the closest match to what this repo would build: the same game family, a lower-screen companion fed by a host that reads game memory. Its architecture translates directly. Its code does not, because it targets 32-bit D2 1.13d under Wine, not the D2R Switch binary.

## 3. Workarounds the ports converged on

Each of these was a bug players reported on the Thor in at least one port. Treat them as requirements.

1. **The Presentation must be `FLAG_NOT_FOCUSABLE`.** A Presentation is a Dialog and takes focus by default. Key and gamepad events follow focus, so the first tap on the bottom screen took the gamepad away from the game (balatro, tmc, zelda3-android). Non-focusable windows still receive touch.
2. **`setCancelable(false)`, and return `false` from `dispatchKeyEvent`.** Gamepad B falls back to `KEYCODE_BACK` at system level, and BACK cancels a focused dialog. Without these, the bottom screen disappeared on an ordinary B press (tmc, zelda3-android).
3. **Hide the system bars on the Presentation's own window.** Immersive mode is set per window. Otherwise the bottom panel reserves a nav-bar inset of about 55–70 px, and the view is 1240 × 1025 instead of 1240 × 1080 (balatro, tmc). Size the panel from the view, never from `Display.getMode()`, which reports the native portrait panel.
4. **Lock the Presentation's window to landscape** (`lp.screenOrientation`). It does not inherit the activity's orientation, and the panel is natively portrait (balatro).
5. **Show the panel only while the activity is resumed and focused, and delay the hide.** An activity resumes before it gains focus. Gating on `onResume` alone leaves the panel stranded over the launcher after a task switch. Rotation and system dialogs briefly withdraw focus, so delay the hide by about 400 ms and show at once (balatro, BanjoRecomp).
6. **Recover from a dismiss the app did not ask for.** The system can dismiss the Presentation (display flicker, config churn). In `onDismissListener`, check whether the app's own reference still points at it. If it does, the dismissal came from the system: clear the reference and re-show it in a posted message. On a deliberate teardown, clear the reference before calling `dismiss()` (tmc).
7. **`show()` can throw `InvalidDisplayException`.** AYN's own launcher ("Cocoon Shell") can hold the display. Catch the exception and keep running without the panel (tmc, thor-d2).
8. **The keyboard can open on the bottom screen.** With AYN's system settings `ime_fixed` and `ime_show_on_second` set, the IME opens on the second display under the Presentation. Hide the panel while text is being entered (thor-d2). This repo has a text dialog for character names (`DmActivity`'s input dialog), so this applies here.
9. **Presentation contexts do not inherit the activity's locale.** Build the view from `createConfigurationContext` with the activity's locales (thor-d2).
10. **The first touch after the panel appears arrives as DOWN then CANCEL.** Commit actions on UP and treat CANCEL as revert (balatro).
11. **Find the display without hard-coding id 4.** Use `getDisplays(DISPLAY_CATEGORY_PRESENTATION)`, excluding the activity's own display, and keep a `DisplayListener` for displays added and removed.
12. **Swapping screens (game on the bottom) is possible, but costs a second activity.** The framework refuses presentation windows on `DEFAULT_DISPLAY`, so the panel then needs its own Activity launched with `ActivityOptions.setLaunchDisplayId(0)`. That activity must stay focusable (or the default display ANRs with "no focused window") and must swallow BACK (tmc). Leave this out of the first version.

## 4. How this maps onto d2r-mobile

What already exists:

- **`DmActivity`** is one `SurfaceView` for Vulkan plus a `TouchOverlay`, `CamThreats` and `CamButtons` stacked in a `FrameLayout`. It already implements `DisplayManager.DisplayListener`, but only to update the refresh rate (`updateRefresh` → `nativeRefresh`). That listener reads `view.getDisplay()`, so a second display connecting is harmless: the refresh stays the top screen's 120 Hz.
- **Game state readable from Java at low cost:**
  - `dm_hud_get` (`dm/host/dm_hud.h`) publishes, once per server tick: level, XP ‰, life and mana with their maximums, belt counts per column, town portal count, area id, coordinates, the player-count setting, unspent points, unread quest, dead flag, and the held item.
  - `dm_shop_get` / `dm_points_get` (`dm/host/dm_actions.h`) cover vendor, stat and skill state.
  - `dm_map_goals` (`dm/host/dm_world.h`) gives minimap quest goals with the player's cell.
  - `CamThreats` already uses this pattern: poll a `float[]` from native once per frame and draw it on a `Canvas`. A bottom panel can do the same through a new `nativeHud(int[])` JNI call. The game itself needs no new packets, unlike thor-d2's mod.
- **Input:**
  - Touch on the top screen goes `DmActivity.dispatchTouchEvent` → `PadInput.onTouch()`, which switches to the touch layout. Touches on a Presentation window are delivered to that window, not to `DmActivity`, so taps on the bottom screen will not flip the game out of controller mode. That is the behaviour we want.
  - The physical pad goes `PadInput` → `nativePad` → `dm_pad_set`.
  - Actions that run on the game thread already exist as queue-then-pump entry points (`dm_world_tap`, `dm_shop_buy`, the point-allocation calls).
- **Rendering:**
  - `vulkan_present.cpp` owns one `ANativeWindow` and one swapchain.
  - Vulkan allows a second `VkSurfaceKHR`/swapchain on the same device, so a native-rendered bottom panel is technically possible. However, D2R draws its automap and panels inside the main frame. Showing those natively on the bottom would mean re-rendering parts of the game frame into a second target, and that is a large project.

## 5. Recommended plan

**Phase 1: companion panel, controller play (most of the value, low risk).**
- `SecondScreen.java`: owns the Presentation, applying all of section 3 (items 1–11), started and stopped from `DmActivity` with the "resumed and focused" rule. It does nothing when there is no presentation display, so every other phone is unaffected.
- `ThorDeck.java`: a `Canvas` view at 1240 × 1080 (sized from the view), redrawn at about 4–10 Hz rather than every frame, to keep the 60 fps game unaffected (the Lite's SD865 matters here). It shows:
  - life and mana orbs, XP bar, level
  - area name, belt column counts, town portal count
  - unspent-point and new-quest badges
  - the difficulty/players number
- Large touch buttons: the four belt potions, town portal, map, swap weapons, inventory, character, skills, quests and the game menu.
- One JNI read: `nativeHud(int[])` wrapping `dm_hud_get`.
- **Decided: the deck's buttons go through a host action queue.** A small "deck action" queue (any thread → game main thread at the pad pump, like `dm_world_tap`) reuses the code paths the touch layout's Portal / Potion / Map / Swap buttons already call. Synthesising pad buttons was rejected: it depends on the game's controller-mode bindings and on the player's remapping. Start by reading how `touch_android.c` fires those buttons.
- A setting on the Custom page: second screen on/off, so a player can hand the bottom screen back to the AYN launcher.

Not planned: moving the touch layout's controls to the bottom screen. The Thor has a built-in controller, so its players play in the controller layout. The touch layout on the top screen stays as it is on every other phone.

**Phase 2: rich panels.** Inventory/stash grid with item pictures, a skills tab, and a map. Item art would come from the player's own files through the import pack (the overlay folder already carries `touchart/`). Item moves would reuse `dm_cursor_item` / `dm_actions`. A real map on the bottom needs either level collision data drawn in Java or the second-swapchain route from section 4; both are expensive. Swapping screens (item 12) also belongs here.

## 6. Testing without a Thor

- A simulated presentation display on any phone: `adb shell settings put global overlay_display_devices 1240x1080/320`. Overlay displays are presentation displays, so the panel appears in a floating window. They take no touch, though, so they test the lifecycle and drawing only. Remove it with `... overlay_display_devices null`.
- `adb shell dumpsys display | grep -i "mDisplayId\|FLAG_PRESENTATION"` shows the real ids; `scrcpy --display-id=4` (or DualCPY) mirrors the bottom screen on a PC.
- Things to check on a real Thor:
  - the gamepad still reaches the game after tapping the bottom screen;
  - B never closes the panel;
  - the panel hides on Home and returns on resume;
  - the IME setting from section 3, item 8;
  - frame time on the top screen with the panel up, against the panel off, on both the Pro and the Lite.

## 7. Phase 1 status (2026-11-10)

Built on branch `thor/phase1-deck`. Player-facing description: [THOR.md](../THOR.md).

Measured on the Thor (QCS8550, Android 13), with the debug app built from this branch:

- The Deck appears on display 4 at the full 1240 × 1080: hiding the system bars on the Presentation window removes the 55 px nav-bar inset. The idle screen shows before a game starts, and the status strip and buttons show in game.
- **Frame time, town, 60 s each.** Sampled from SurfaceFlinger's per-frame present times for the game's SurfaceView (`dumpsys SurfaceFlinger --latency`, every 0.5 s):
  - Deck on: median 16.70 ms, p99 16.71 ms, 0 frames over 33 ms.
  - Deck off (`deck=0` in `files/sw_custom.txt`): median 16.71 ms, p99 16.71 ms, 1 frame over 33 ms.
  - Both runs hold the 60 fps cap, so the cap hides any small cost.
- **Frame time, fight, 120 s each** (same sampling, a fight with many monsters on screen):
  - Deck on: 7247 frames, median 16.71 ms, p99 16.71 ms, 2 frames over 33 ms, 59.8 fps.
  - Deck off: 7245 frames, median 16.71 ms, p99 16.71 ms, 1 frame over 33 ms, 59.8 fps.
  - Passes the agreed test (median within 0.5 ms, no new stalls): one or two isolated frames over 33 ms show up with the Deck off too, so they are noise rather than the Deck.
- **Checked by hand on the Thor** (controller mode, 2026-11-10): every button, potion colours and counts, the gamepad after touching the bottom screen, B never closing the Deck, Home and resume, and the name dialog (the Deck hides while it is open). All fine.
- **Opening a page.** The first version pressed `+` and then stepped with `R`, and the player could see it scroll through the pages. It now presses `+` once and jumps with the function page's own page switch (`main+0x405898`, the same call used for tapping a top tab). Page numbers measured on the Thor: Quests 1, Character 2, Inventory 3, Skill tree 4. The Deck re-learns them whenever it sees a target panel, and still falls back to `R`. The menu shows its last page for a frame or two before the jump.
- **Real pack and APK from this branch** (`b1ab75e`): import pack made with `dm/tools/d2rm_android.py` from the Switch 3.3.94012 game files (product `3.3.94012-r45`), APK built with headers generated from the same files. On the Thor the app starts, the Custom page shows **Bottom-screen Deck (dual screen)**, and Apply hides the Deck (80 ms after Apply) and shows it again (85 ms). Earlier device checks ran on a test build that kept main's two fingerprinted files.
- **Release app and debug app on one device.** Both read the import pack from `Download/S2X/D2R Mobile/android`. A pack from this branch there would make the release app re-import it and then refuse to start. So the fork's pack was copied straight into the debug app's `files/dm/` with `run-as`, and the shared folder was renamed to `android.r45` so neither app re-imports.
- An import pack made from other source is rejected by this build: the overlay fingerprint covers `dm/i18n/ui_text.tsv` and `dm/ios/tools/touch_layout.py`, and both change here.

## 8. Phase 2 plan (agreed 2026-11-10)

Branch `thor/phase2-panels`, one PR per slice, each stacked on the one before (PR #1 first). Every slice keeps the Phase 1 perf gate: frame-time median within 0.5 ms of Deck off, no new stalls.

1. **Area name** on the status strip. The import pack carries `deck/levels.tsv`: levels.txt Ids with the level names from the player's own `lng/strings/levels.json`, in all 13 game languages (`dm/android/tools/stage_deck_assets.py`). The Deck picks the column by the game's language number (`dm_lang_index`), falling back to English. Nothing of the game's text goes into the repo or the APK.
2. **Bag tab, read only.** The Deck gets a tab row: Main (today's strip and buttons, plus the goal compass), Bag, later Skills. A new host reader `dm_inv_snapshot` lists the bag, equipped items and belt once per server tick (id, code, container, cell x/y/w/h, quality, unidentified, unique/set index), read by Java only while Bag is open. Item pictures come from the pack (`deck/` icons from the player's own sprites, one cell = 64 px, with a code → file map); coloured cells are the fallback. The Main page's Inventory button keeps opening the game's own inventory. Stash and cube come later.
3. **Bag actions:** tap an item → a sheet with its icon and name (quality colour) and Use / To belt / Sort bag, through `dm_item_use`, `dm_belt_put`, `dm_inv_sort`. Equip, drop and drag come later, if at all.
4. **Skills tab:** the class's three trees with icons, skill levels and unspent points; tap + confirm spends a point through the existing allocation calls.
5. **Goal compass** on Main instead of a real map: up to three goals from `dm_map_goals`, arrow and distance, turned to match the game's view; hidden with no goals. The full map is shelved.
6. **Screen swap is dropped.**

Tests: plain-Java tests for pure logic (names, bag grid hit-testing, compass maths), Python tests for the pack exporters, and Thor log/screenshot checks for host readers of live game memory.

### Slice 1 status: area name (done)

On the Thor with a rebuilt pack: the strip shows "Blood Moor" in gold on its own line under the life and mana bars (the middle column is about 290 px wide, too narrow for names such as "Fortaleza de la Piedra Ecuménica Nivel 1", so the name sits under the bars rather than after the level). The real pack's table has 137 areas in all 13 languages. The frame-time check was waived for this slice by the user: the name is one `drawText`, redrawn only when the area or language changes.

### Slice 2 status: Bag tab, read only (done)

- Pack: `deck/items/` holds 415 pictures (4 MB) at the game's native 49 px per cell, and `deck/items.tsv` 1255 keys (item codes, `u<row>`, `s<row>`), from `hd/items/items.json`, `uniques.json`, `sets.json` and the `hd/global/ui/items/` sprites. Kept native rather than the 64 px agreed in Q13: the Deck scales them, so upscaling would only grow the pack.
- Host: `dm_inv.c` reads the server player's item chain on the server tick, only while the Bag tab shows (`dm_inv_want`), and publishes a list with a generation number; the Deck redraws only when it changes.
- On the Thor, a new character: the log line `Deck bag: 8 items: hp1/L0,0 ... tsc/B9,3 ... ssd/W4,0 1x3 ... buc/W5,0 2x2` matches the game, and the screenshot shows the short sword and buckler in the hand slots, two scrolls at the bag's right edge and four healing potions on the belt's bottom row.
- Not checked yet (the test character has none): unique and set pictures (row read from item data +0x34, the offset the vault table uses) and the unidentified tint.

### Slice 3 status: item sheet (done)

- Pack: `deck/itemnames.tsv`, 1242 names in all 13 languages from `item-names.json` / `item-runes.json` (base items by namestr, uniques and sets by index; gender markers such as `[fs]` and `[ns]` and colour codes removed). Magic and rare names are built from affixes and are not included: the sheet shows the base name.
- Host: `dm_inv.c` adds use and belt flags from the game's own item-type tests; the sheet's buttons go to `dm_item_use`, `dm_belt_put`, `dm_inv_sort(0)`, which already queue to the game thread.
- On the Thor (adb taps on display 4): a belt potion's sheet showed "Minor Healing Potion" with Use and Close; Use logged `使用：物品 id 1（hp1）已调原版使用` and the bag list went from 8 to 7 items. The identify scroll's sheet showed Use, To belt, Sort bag, Close; To belt put it in the empty first belt column. Sort bag logged `整理：页 0 已调原版整理`.
- Bug found on the device and fixed: touch codes for "outside the sheet" (399) fell inside the item range (300+), so tapping empty space read past the item list and crashed. The ranges are now apart (items 1000+).

## 9. Re-plan: Main, Map, Skills (2026-11-10)

After slice 3 the user found the Bag tab unnecessary: the game's own inventory does the same job better, and a second copy of the belt was confusing. A look at what a second screen is good for (glanceable state the game hides or puts behind a menu, and things the controller makes slow) gave three tabs:

- **Main.** The game's HUD drawn with its own art (globes, gold frame, Exocet and Formal fonts, exported from the player's files into `deck/ui/`), a compact potion row instead of a second belt, the page buttons in the escape-menu style. Where the HUD's belt boxes would be: run stats (`RunStats.java`: time in this game, % of a level per hour, time to the next level). Above the bar, the mercenary's life (host `merc_step`, every 6 frames, the same lookup the mercenary auto-feed uses).
- **Map.** The goal compass from slice 5, moved to its own tab with a radar and a list of up to six goals (`GoalCompass.java`, names from `deck/goals.tsv`). Towns are skipped by the map marker (`w_map_mark_step`, levels 1, 40, 75, 103, 109), so the tab shows its empty note there.
- **Skills.** Read-only: the skills bound to A/B/X/Y/R/ZR, plain and with ZL held, for the weapon set in hand. Host `skillbar_step` reads the controller slot table and the binding table every frame; icons are the touch layer's own skill atlases, names from `deck/skills.tsv` plus `ui-controller.json` for controller-only actions (Interact, Automap, Loot to Cube).

The Bag UI, the item sheet and its actions were removed; `BagModel` stays only to decode the item list for the belt pictures. Spending skill points from the Deck was dropped: the game's skill menu is one tap away.

### Status

On the Thor (Rogue Encampment, new character): Main shows the HUD, "Game 0:06", "XP -/h" and the potion row; Skills shows A = Attack and ZL + A = Scroll of Town Portal with icons; Map shows the empty note, as expected in town. No crashes. Not checked yet: the mercenary bar (the test character has none), the XP rate after a minute of play, and the compass outside town. The frame-time check stays waived. Buffs and their time left are not shown: nothing in the host reads state durations yet.

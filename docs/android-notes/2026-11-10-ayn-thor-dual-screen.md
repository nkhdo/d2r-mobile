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
- Still to check by hand on the Thor:
  - each button's effect in controller mode, especially the `+` / `R` tab stepping;
  - potion colours and counts with real potions;
  - the gamepad after touching the bottom screen;
  - B never closing the Deck;
  - Home and resume;
  - the name dialog.
- An import pack made from other source is rejected by this build: the overlay fingerprint covers `dm/i18n/ui_text.tsv` and `dm/ios/tools/touch_layout.py`, and both change here.

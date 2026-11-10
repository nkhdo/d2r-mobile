# Playing on the AYN Thor: the bottom-screen Deck

On a dual-screen handheld such as the AYN Thor, the game plays on the top screen and the **Deck** uses the bottom screen. The Deck shows your character's state and gives you large touch buttons for the things a controller reaches slowly. Everything else on [ANDROID.md](ANDROID.md) applies to the Thor unchanged: install, import pack, saves.

The Deck is made for controller play with the Thor's built-in gamepad. Touching the bottom screen never takes the gamepad away from the game, and it does not switch the game to the touch layout.

## What the Deck shows

| Area | Contents |
|---|---|
| Status strip (top) | Life and mana bars with numbers, level and experience bar, and the name of the area you are in (in the game's language; taken from your own game files when the import pack is made, so a pack made before this feature shows no name). Badges for **Town**, **Points to spend** and **New quest**. **Dead** replaces the badges while your character is dead |
| Row 1 | **Inventory**, **Character**, **Skills**, **Quests**: opens that page of the game's menu. Tap again to close it |
| Row 2 | **Portal** (with your town portal count), **Map** (full-screen map on and off), **Swap weapons**, **Players** (tap to pick 1–8) |
| Row 3 | The four belt columns, coloured by the potion in the bottom row (red, blue, purple for rejuvenation), with how many are left. **Tap** drinks it; **hold** gives it to your mercenary |

A button takes effect when you lift your finger, so sliding off a button cancels it. While you are not in a game (main menu, character select, loading), the Deck shows a short note instead. While your character is dead, the buttons are greyed out.

## Turning it off

In the game's options, the **Custom** page has a **Bottom-screen Deck (dual screen)** switch. It is on by default. Turn it off and tap **Apply** to hand the bottom screen back to the AYN launcher; turn it on again the same way. No restart is needed.

The switch only appears when your import pack is made from **this repository's source** (see below).

## The import pack must match this source

The Deck changes files that the import pack is checked against. An import pack made from another copy of the source, even the same version, is rejected: the App says "The touch resources are incomplete or do not match this version of the app" and stops. Make the import pack with this repository's `dm/tools/d2rm_android.py` (step 1 in [ANDROID.md](ANDROID.md)), and use the APK built from the same commit.

## When the Deck steps aside

- **Home, recent apps, or another app in front:** the Deck hides at once and comes back when you return to the game.
- **Typing a character name:** the Thor opens its keyboard on the bottom screen, so the Deck hides while the name dialog is open.
- **The AYN launcher holds the bottom screen:** the game keeps running without the Deck and tries again every few seconds.
- **Short system pop-ups** (rotation, volume, notifications) do not make the Deck flicker: it waits 0.4 s before hiding.

## Other devices

Any Android device with a second display that Android treats as a presentation display gets the Deck there, for example a phone casting to a TV or plugged into a monitor. Turn the Custom switch off if you do not want that. Phones with one screen are not affected.

## Known limits

- Opening a page presses the controller's menu button for you and then jumps to the page, so the menu's last page can flash for a moment. It can fail while another window of the game is open, such as a vendor or the stash.
- No item pictures, inventory grid or map yet.
- Tested on the Thor with the Snapdragon 8 Gen 2. The Thor Lite (Snapdragon 865) has not been tested.

Background, workarounds and test notes: [android-notes/2026-11-10-ayn-thor-dual-screen.md](android-notes/2026-11-10-ayn-thor-dual-screen.md).

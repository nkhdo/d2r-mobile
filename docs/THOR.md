# Playing on the AYN Thor: the bottom-screen Deck

On a dual-screen handheld such as the AYN Thor, the game plays on the top screen and the **Deck** uses the bottom screen. The Deck shows your character's state and gives you large touch buttons for the things a controller reaches slowly. Everything else on [ANDROID.md](ANDROID.md) applies to the Thor unchanged: install, import pack, saves.

The Deck is made for controller play with the Thor's built-in gamepad. Touching the bottom screen never takes the gamepad away from the game, and it does not switch the game to the touch layout.

## What the Deck shows

The Deck is drawn with the game's own panel art and fonts (HUD bar, globes, menu buttons, stone), taken from your own game files when the import pack is made. A pack made before this shows the same layout as plain panels.

Three tabs on top: **Main**, **Map** and **Skills**.

**Main**

| Area | Contents |
|---|---|
| Info bar | The middle of the game's HUD bar: the name of the area you are in (in the game's language), your level and how far into it, experience filling the bar's own channel along its top edge; where the game's belt boxes would be, time in this game, experience per hour (in percent of a level, level-ups included) and time to the next level at that rate (shown after a minute with progress; a new game, or more than 15 s out of one, starts over); in the small box, frames per second |
| Badges | Under the bar: **Town**, **Points to spend** and **New quest** (**Dead** replaces them while your character is dead), and your mercenary's life on the right when you have one |
| Buttons | **Inventory**, **Character**, **Skills**, **Quests**: opens that page of the game's menu (tap again to close it). **Portal** (with how many town portals you have left), **Map** (full-screen map on and off; a dot shows when it is on), **Swap weapons**, **Players** (tap to pick 1–8) |
| Bottom row | The life globe on the left and the mana globe on the right, the HUD's own ends, with your numbers on them. Between them, the four belt columns, each with the picture of the item in its bottom row and how many are left. **Tap** drinks it; **hold** gives it to your mercenary |

**Map**: a compass for the area you are in. The game's own map marks (quest goals such as Blood Raven or the Horadric Cube chest, the true Tal Rasha's tomb, waypoints and real exits) and the town portals and red portals in the area appear on a radar turned like the game's view, and in a list, nearest first, with an arrow, the game's name for it and the distance in map cells. It uses the same marks as the minimap, so it needs the Custom page's map marking on (the default). Exits and waypoints are marked at their room until you come near, then on the entrance or waypoint itself. Towns have no marks (the game marks nothing there) apart from portals, and areas without any say so.

**Skills**: the skills bound to your controller buttons, with the game's own icons and names: the four face buttons as a diamond plus R and ZR, and the same again with ZL held, for the weapon set in your hands. To change a binding, use the game's skill menu (the **Skills** button on Main).

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
- Items are managed in the game's own inventory (the **Inventory** button); the Deck only shows the belt.
- The Map tab is a compass, not a drawn map. The Skills tab shows bindings but cannot change them. Buffs and their time left are not shown.
- Tested on the Thor with the Snapdragon 8 Gen 2. The Thor Lite (Snapdragon 865) has not been tested.

Background, workarounds and test notes: [android-notes/2026-11-10-ayn-thor-dual-screen.md](android-notes/2026-11-10-ayn-thor-dual-screen.md).

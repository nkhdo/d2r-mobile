#!/usr/bin/env python3
"""The Deck's Build tab guides: writes dm/android/app/src/main/assets/builds/<id>.json (the format is BuildGuide.java's) from the data
below, so the plans are edited here and the JSON is regenerated, not hand-edited.

The plans are our own short summaries of community builds (each links the guide it follows); maxroll.gg's terms rule out scraping,
so nothing here is fetched. Skill ids are skills.txt *Id (the comments above each build name them); item names are the game's English
names, "Runeword|Base" for a runeword in a base, and anything else (shop affixes) is shown as written.

Usage: python3 dm/android/tools/gen_build_guides.py [output directory]
Then check: python3 dm/android/tests/test_build_guides.py, with DM_DECK_DIR=<import pack>/overlay/deck to test every skill step
against the game's own rules (class, highest level, prerequisites planned first, reachable by the stage's last level).
"""
import json, os, sys
from pathlib import Path

OUT = sys.argv[1] if len(sys.argv) > 1 else str(Path(__file__).resolve().parents[1] / 'app' / 'src' / 'main' / 'assets' / 'builds')
os.makedirs(OUT, exist_ok=True)

def s(*pairs):   # steps: (skill id, base level target)
    return [{"skill": a, "to": b} for a, b in pairs]

def to(stat, n, note=None):
    r = {"stat": stat, "to": n}
    if note: r["note"] = note
    return r

def gear(stat, note=None):
    r = {"stat": stat, "gear": True}
    if note: r["note"] = note
    return r

def rest(stat, note=None):
    r = {"stat": stat, "rest": True}
    if note: r["note"] = note
    return r

def g(slot, items, note=None):
    r = {"slot": slot, "items": items}
    if note: r["note"] = note
    return r

def stage(name, until, skills, stats, gear_=(), tips=(), respec=False):
    r = {"name": name}
    if until: r["to"] = until
    if respec: r["respec"] = True
    r.update({"skills": skills, "stats": stats, "gear": list(gear_), "tips": list(tips)})
    return r

BP = {
    "ama": ([0, 7, 14, 22, 32, 48, 68, 99, 152], [0, 6, 13, 20, 32, 52, 86, 174, 600]),
    "sor": ([0, 9, 20, 37, 63, 105, 200], [0, 5, 9, 14, 20, 30, 42, 60, 86, 142, 280]),
    "nec": ([0, 9, 18, 30, 48, 75, 125], [0, 5, 10, 16, 26, 39, 56, 86, 152, 377]),
    "pal": ([0, 9, 18, 30, 48, 75, 125], [0, 7, 15, 27, 48, 86, 200]),
    "bar": ([0, 9, 20, 37, 63, 105, 200], [0, 7, 15, 27, 48, 86, 200]),
    "dru": ([0, 4, 10, 19, 30, 46, 68, 99, 163], [0, 3, 7, 13, 19, 29, 42, 63, 99, 174, 456]),
    "ass": ([0, 8, 16, 27, 42, 65, 102, 174], [0, 7, 15, 27, 48, 86, 200]),
    # Warlock: no table published with the guides; its targets (75 / 125 FCR, 26 FHR) match the Necromancer's tables, used here
    "war": ([0, 9, 18, 30, 48, 75, 125], [0, 5, 10, 16, 26, 39, 56, 86, 152, 377]),
}

def bp(cls, fcr_target, fhr_target, fcr_table=None):
    """A target of None leaves that table out (the build does not need it); fcr_table overrides the class's own (skills with their
    own cast animation, such as Lightning and Chain Lightning)."""
    f, h = fcr_table or BP[cls][0], BP[cls][1]
    out = {}
    if fcr_target is not None:
        assert fcr_target in f, (cls, fcr_target)
        out.update({"fcr": f, "fcrTarget": fcr_target})
    if fhr_target is not None:
        assert fhr_target in h, (cls, fhr_target)
        out.update({"fhr": h, "fhrTarget": fhr_target})
    return out

# common leveling gear lines
RUNES_A1 = "Farm the Countess (Act 1, Forgotten Tower) for Tal + Eth (Stealth) and Ral runes."
HELMS = g("Helmets", ["Cap"], "Two 2-socket helmets from Charsi or drops: one for Ral runes (fire resistance), one kept for Lore. At least one must not be magic.")
STEALTH = g("Body", ["Stealth|Studded Leather"], "Any 2-socket armor that is not magic; Tal + Eth. Wear it from level 17.")
AP = g("Shield", ["Ancients' Pledge|Kite Shield"], "3-socket shield from Farah (not magic); runes from the Act 5 quests.")
SPIRIT_BASE = g("Weapon base", ["Crystal Sword"], "From the Cow Level; add 4 sockets with Larzuk's quest (Act 5). Keep it for Spirit.")
SPIRIT_NM = g("Weapon", ["Spirit|Crystal Sword"], "Tal Thul Ort Amn from the Countess in Nightmare. Equip as soon as you have the runes.")
LORE = g("Helmet", ["Lore|Cap"], "Ort Sol into the second 2-socket helmet.")
NM_SHOP = g("Shop (Nightmare)", ["Coral Belt of the Mammoth", "Russet Heavy Boots of Haste", "Coral Heavy Gloves"],
            "Charsi or Gheed at the start of Nightmare: resistances first, lightning and fire.")
TELE_STAFF = g("Swap", ["Staff of Teleportation"], "Ormus (Act 3), levels 20-23. Check the red-background staves only.")
LOWER_RES = g("Swap", ["Wand of Lower Resistance"], "Akara or Drognan in Nightmare, levels 30-35. Cast it on act bosses.")

CASTER_END = [
    g("Helmet", ["Harlequin Crest", "Lore|Cap"]),
    g("Body", ["Enigma|Mage Plate", "Skin of the Vipermagi", "Smoke"]),
    g("Gloves", ["Magefist", "Trang-Oul's Claws"]),
    g("Belt", ["Arachnid Mesh", "Verdungo's Hearty Cord"]),
    g("Boots", ["Sandstorm Trek", "Waterwalk", "War Traveler"]),
    g("Amulet", ["Mara's Kaleidoscope"]),
    g("Rings", ["The Stone of Jordan", "Bul-Kathos' Wedding Band"]),
    g("Swap", ["Call to Arms|Crystal Sword"], "Battle Orders before a fight."),
    g("Charms", ["Annihilus", "Hellfire Torch", "Gheed's Fortune"]),
]

MERC_A2 = lambda weapon_end, note: [
    g("Weapon", ["Insight|Giant Thresher"] + weapon_end, note),
    g("Body", ["Smoke", "Treachery", "Fortitude"]),
    g("Helmet", ["Bulwark", "Tal Rasha's Horadric Crest", "Andariel's Visage"]),
]

def write(b):
    with open(os.path.join(OUT, b["id"] + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(b, f, ensure_ascii=False, indent=1)
        f.write("\n")
    return b

# ---------------------------------------------------------------- Sorceress
# 38 Charged Bolt, 42 Static Field, 43 Telekinesis, 44 Frost Nova, 40 Frozen Armor, 48 Nova, 54 Teleport,
# 39 Ice Bolt, 45 Ice Blast, 55 Glacial Spike, 59 Blizzard, 65 Cold Mastery
SOR = write({
    "id": "sor-blizzard", "class": "sor", "name": "Blizzard Sorceress",
    "summary": "Nova to 26, respec into Blizzard; Glacial Spike, Ice Blast and Ice Bolt as synergies.",
    "links": [{"title": "Leveling guide", "url": "https://maxroll.gg/d2/guides/sorceress-leveling"},
              {"title": "Blizzard guide", "url": "https://maxroll.gg/d2/guides/blizzard-sorceress"}],
    "breakpoints": bp("sor", 105, 60), "after20": [59, 55, 45],
    "merc": {"type": "Act 2 Holy Freeze (Greiz, Defensive)", "note": "Infinity lets the mercenary's Conviction break cold immunity.",
             "gear": MERC_A2(["Infinity|Giant Thresher"], "Insight for mana; Infinity in the end.")},
    "stages": [
        stage("Act 1 Normal", 13, s((38, 4), (44, 1), (42, 1), (42, 4), (43, 1), (40, 1), (48, 2)),
              [to("vit", 55), to("str", 25), rest("vit")],
              [g("Weapon", ["Short Staff"], "Akara after the Den of Evil: look for +2 or +3 to Charged Bolt."), HELMS,
               g("Body", ["Studded Leather"], "Any 2-socket armor that is not magic, kept for Stealth.")],
              ["Den of Evil point: Charged Bolt.", "Save the level 5 point: Frost Nova and Static Field both open at 6.", RUNES_A1]),
        stage("Act 2–5 Normal", 26, s((48, 7), (54, 1), (48, 9), (42, 5), (48, 15)),
              [to("vit", 70), to("str", 47), rest("vit")],
              [STEALTH, AP, g("Weapon", ["Wand"], "Wand of the Magus from Ormus at 23+ if it reaches the 63% cast rate breakpoint."),
               g("Belt", ["Belt"], "Farah: more potion rows."), SPIRIT_BASE],
              ["Save Radament's point for Teleport at 18.", "Izual's points: Static Field.", "Respec after 26, before Nightmare."]),
        stage("Respec · Act 1 Nightmare", 35, s((43, 1), (54, 1), (42, 4), (40, 1), (39, 1), (44, 1), (45, 7), (55, 8), (59, 3),
                                                   (59, 6), (65, 1), (59, 12)),
              [gear("str"), rest("vit")],
              [SPIRIT_NM, LORE, NM_SHOP, LOWER_RES],
              ["Keep one point after the respec for Cold Mastery at 30.", "Spirit above 28% cast rate plus one 10% ring reaches 63%."],
              respec=True),
        stage("Nightmare – Hell", None, s((65, 2), (59, 20), (65, 3), (55, 20), (45, 20), (39, 20), (65, 20)),
              [gear("str"), rest("vit")],
              [g("Weapon", ["Heart of the Oak|Flail", "Spirit|Crystal Sword", "Death's Fathom"]),
               g("Shield", ["Spirit|Monarch", "Lidless Wall"])] + CASTER_END,
              ["Static Field brings bosses down fast; Teleport everywhere.", "Cold-immune monsters: leave them to the mercenary with Infinity."]),
    ]})

# ---------------------------------------------------------------- Amazon
# 6 Magic Arrow, 7 Fire Arrow, 12 Multiple Shot, 15 Poison Javelin, 16 Exploding Arrow, 10 Jab, 14 Power Strike, 20 Lightning Bolt,
# 24 Charged Strike, 25 Plague Javelin, 35 Lightning Fury, 34 Lightning Strike, 9 Critical Strike, 23 Penetrate, 33 Pierce,
# 13 Dodge, 18 Avoid, 29 Evade, 8 Inner Sight, 17 Slow Missiles, 28 Decoy, 32 Valkyrie
AMA = write({
    "id": "ama-lightning-fury", "class": "ama", "name": "Lightning Fury Amazon",
    "summary": "Exploding Arrow bow to 38, respec into javelins: Lightning Fury and Charged Strike.",
    "links": [{"title": "Leveling guide", "url": "https://maxroll.gg/d2/guides/amazon-leveling-guide"},
              {"title": "Lightning Fury guide", "url": "https://maxroll.gg/d2/guides/lightning-fury-amazon-guide"}],
    "breakpoints": bp("ama", 68, 32), "after20": [35, 24, 34],
    "merc": {"type": "Act 2 Might (Greiz, Offensive)", "note": "Infinity's Conviction lowers lightning resistance.",
             "gear": MERC_A2(["Infinity|Giant Thresher"], "Insight early; Infinity in the end.")},
    "stages": [
        stage("Act 1 Normal", 13, s((7, 4), (6, 1), (15, 5), (12, 1), (16, 2)),
              [to("vit", 70), to("dex", 28), to("str", 27), rest("vit")],
              [g("Weapon", ["Hunter's Bow"], "Put a Chipped Ruby in it for Exploding Arrow."), HELMS,
               g("Body", ["Studded Leather"], "2 sockets, not magic: kept for Stealth.")],
              ["Den of Evil point: Magic Arrow.", RUNES_A1]),
        stage("Act 2–5 Normal", 26, s((16, 3), (7, 5), (16, 9), (7, 6), (16, 15)),
              [to("ene", 30), rest("vit")],
              [STEALTH, g("Shield", ["Rhyme|Bone Shield"], "Bone Shield from Drognan; Shael Eth in Nightmare."),
               g("Belt", ["Belt"], "Farah."), g("Swap", ["Battle Staff"], "Battle Staff of Teleportation from Ormus, levels 20-23.")],
              ["Radament and Izual points: Fire Arrow."]),
        stage("Act 1–3 Nightmare", 38, s((16, 16), (7, 7), (16, 20), (7, 15)),
              [rest("vit")],
              [LORE, g("Shield", ["Rhyme|Bone Shield"]), NM_SHOP, LOWER_RES,
               g("Javelins", ["Javelin"], "At 29, back in Act 5 Normal: buy 2-3 stacks of Javelins of Swiftness (Malah/Larzuk) for the respec.")],
              ["Respec when you enter Act 4 Nightmare, at 38 or later."]),
        stage("Respec · Nightmare – Hell", None,
              s((10, 1), (14, 1), (15, 1), (20, 1), (24, 1), (25, 1), (35, 9), (24, 19), (9, 1), (23, 1), (33, 5), (13, 1), (18, 1), (29, 1),
                (35, 20), (24, 20), (34, 20), (14, 20), (20, 20), (8, 1), (17, 1), (28, 1), (32, 1)),
              [gear("str"), gear("dex", "Enough for 50% chance to block (75% with Rhyme or Spirit later)."), rest("vit")],
              [g("Weapon", ["Titan's Revenge", "Thunderstroke"], "Ethereal Titan's Revenge repairs itself."),
               g("Shield", ["Spirit|Monarch", "Stormshield", "Rhyme|Bone Shield"]),
               g("Helmet", ["Griffon's Eye", "Lore|Cap"]),
               g("Body", ["Enigma|Mage Plate", "Chains of Honor", "Treachery", "Smoke"]),
               g("Gloves", ["Laying of Hands"], "Or rare gloves with attack speed and javelin skills."),
               g("Belt", ["Razortail", "Arachnid Mesh", "Thundergod's Vigor"]),
               g("Boots", ["War Traveler", "Aldur's Advance"]),
               g("Amulet", ["Highlord's Wrath", "Mara's Kaleidoscope"]),
               g("Rings", ["Raven Frost", "The Stone of Jordan", "Bul-Kathos' Wedding Band"]),
               g("Swap", ["Call to Arms|Crystal Sword"]),
               g("Charms", ["Annihilus", "Hellfire Torch", "Gheed's Fortune"])],
              ["52% attack speed is the Lightning Fury breakpoint to aim for.", "Pierce makes every bolt count; Razortail gives 100%."],
              respec=True),
    ]})

# ---------------------------------------------------------------- Paladin
# 96 Sacrifice, 97 Smite, 98 Might, 99 Prayer, 100 Resist Fire, 101 Holy Bolt, 102 Holy Fire, 104 Defiance, 106 Zeal, 107 Charge,
# 108 Blessed Aim, 109 Cleansing, 110 Resist Lightning, 112 Blessed Hammer, 113 Concentration, 115 Vigor, 117 Holy Shield,
# 122 Fanaticism, 124 Redemption
PAL = write({
    "id": "pal-blessed-hammer", "class": "pal", "name": "Blessed Hammer Paladin",
    "summary": "Holy Fire Zeal to 19, respec into Hammers with Concentration, Vigor and Blessed Aim.",
    "links": [{"title": "Leveling guide", "url": "https://maxroll.gg/d2/guides/paladin-leveling"},
              {"title": "Blessed Hammer guide", "url": "https://maxroll.gg/d2/guides/blessed-hammer-paladin"}],
    "breakpoints": bp("pal", 125, 86), "after20": [112, 113, 115],
    "merc": {"type": "Act 2 Holy Freeze (Greiz, Defensive)", "note": "Slows everything around you while hammers spin.",
             "gear": MERC_A2([], "Insight keeps your mana up.")},
    "stages": [
        stage("Act 1 Normal", 13, s((98, 1), (96, 1), (100, 3), (102, 4), (97, 1), (107, 1), (106, 1), (102, 5)),
              [to("vit", 45), to("dex", 21), to("vit", 74), to("ene", 25), rest("vit")],
              [g("Weapon", ["Scepter", "Steel|Scimitar"], "Scepter from Akara after the Den of Evil; Steel if the Countess gives Tir El."),
               HELMS, g("Body", ["Studded Leather"], "2 sockets, not magic: kept for Stealth.")],
              ["Den of Evil point: Resist Fire.", "Save the level 11 point: Charge and Zeal at 12.", RUNES_A1]),
        stage("Act 2–3 Normal", 19, s((102, 7), (100, 4), (102, 11)),
              [rest("vit")],
              [STEALTH, g("Weapon", ["Scepter"], "Reroll magic scepters in the Cube (scepter + 3 chipped gems) for +Blessed Hammer.")],
              ["Radament point: Resist Fire.", "Respec at 19, before Mephisto."]),
        stage("Respec · Act 4 Normal – Nightmare", 35,
              s((101, 1), (112, 1), (97, 1), (107, 1), (98, 1), (108, 8), (113, 1), (99, 1), (109, 1), (104, 1), (115, 2), (112, 2),
                (112, 18), (115, 3), (108, 9)),
              [to("str", 43, "18 points, more if your Spirit base needs it."), rest("vit")],
              [SPIRIT_NM, g("Shield", ["Spirit|Targe"], "Paladin shield with 4 sockets (Larzuk)."), LORE, TELE_STAFF, NM_SHOP],
              ["Izual points: Vigor and Blessed Aim."], respec=True),
        stage("Nightmare – Hell", None, s((112, 20), (115, 4), (115, 11), (113, 2), (117, 1), (115, 20), (113, 20), (108, 20), (124, 1),
                                          (122, 1), (110, 20)),
              [gear("str"), gear("dex", "Enough for 75% chance to block with Holy Shield."), rest("vit")],
              [g("Weapon", ["Heart of the Oak|Flail", "Spirit|Crystal Sword", "Wizardspike"]),
               g("Shield", ["Spirit|Sacred Targe", "Herald of Zakarum"])] + CASTER_END,
              ["Once Blessed Hammer is maxed, keep synergies about 12 levels ahead of Concentration.",
               "Hammers spiral out: stand next to the target, not on it."]),
    ]})

# ---------------------------------------------------------------- Assassin
# 251 Fire Blast, 252 Claw Mastery, 253 Psychic Hammer, 256 Shock Web, 258 Burst of Speed, 261 Charged Bolt Sentry, 262 Wake of Fire,
# 263 Weapon Block, 264 Cloak of Shadows, 267 Fade, 271 Lightning Sentry, 273 Mind Blast, 276 Death Sentry
ASS = write({
    "id": "ass-lightning-sentry", "class": "ass", "name": "Lightning Sentry Assassin",
    "summary": "Fire traps to 38, respec into Lightning Sentry with Death Sentry and Charged Bolt Sentry.",
    "links": [{"title": "Leveling guide", "url": "https://maxroll.gg/d2/guides/assassin-leveling"},
              {"title": "Lightning Sentry guide", "url": "https://maxroll.gg/d2/guides/lightning-sentry-assassin"}],
    "breakpoints": bp("ass", 102, 48), "after20": [271, 256, 261],
    "merc": {"type": "Act 2 Holy Freeze (Greiz, Defensive)", "note": "Infinity's Conviction is the trap build's answer to lightning immunes.",
             "gear": MERC_A2(["Infinity|Giant Thresher"], "Insight early; Infinity in the end.")},
    "stages": [
        stage("Act 1 Normal", 13, s((251, 4), (252, 1), (258, 2), (251, 5), (258, 5), (262, 2)),
              [to("ene", 35), to("vit", 55), to("str", 25), rest("vit")],
              [g("Weapon", ["Leaf|Short Staff"], "Short Staff from Akara after the Den of Evil (not a Long Staff); Tir Ral."), HELMS,
               g("Body", ["Studded Leather"], "2 sockets, not magic: kept for Stealth.")],
              ["Den of Evil point: Claw Mastery.", RUNES_A1 + " Leaf needs Tir + Ral."]),
        stage("Act 2–5 Normal", 26, s((262, 3), (251, 6), (262, 9), (251, 7), (262, 15)),
              [to("vit", 105), to("str", 43), to("str", 48, "Only if your Spirit base is a Broad Sword."), rest("vit")],
              [STEALTH, g("Weapon", ["Leaf|Short Staff"], "Make Leaf at 19."), AP, g("Belt", ["Belt"], "Farah."),
               g("Swap", ["Battle Staff"], "Battle Staff of Teleportation from Ormus, levels 20-23."), SPIRIT_BASE],
              ["Radament and Izual points: Fire Blast."]),
        stage("Act 1–3 Nightmare", 38, s((262, 20), (251, 8), (251, 15)),
              [rest("vit")],
              [SPIRIT_NM, LORE, NM_SHOP, LOWER_RES],
              ["Respec at the start of Act 4 Nightmare, at 38 at the earliest."]),
        stage("Respec · Nightmare – Hell", None,
              s((253, 1), (264, 1), (273, 1), (252, 1), (258, 3), (251, 1), (256, 1), (261, 1), (271, 15), (276, 9), (261, 10),
                (271, 20), (256, 20), (267, 1), (263, 1), (261, 20), (276, 20), (251, 20)),
              [gear("str"), gear("dex"), rest("vit")],
              [g("Weapon", ["Heart of the Oak|Flail", "Spirit|Crystal Sword", "Wizardspike"],
                 "Claws with +6 Lightning Sentry from Anya are the upgrade later."),
               g("Shield", ["Spirit|Monarch", "Stormshield"]),
               g("Helmet", ["Griffon's Eye", "Harlequin Crest", "Lore|Cap"]),
               g("Body", ["Enigma|Mage Plate", "Treachery", "Skin of the Vipermagi", "Smoke"]),
               g("Gloves", ["Magefist", "Trang-Oul's Claws"]),
               g("Belt", ["Arachnid Mesh", "Goldwrap"]),
               g("Boots", ["Aldur's Advance", "War Traveler", "Waterwalk"]),
               g("Amulet", ["Mara's Kaleidoscope"]),
               g("Rings", ["The Stone of Jordan", "Bul-Kathos' Wedding Band"]),
               g("Swap", ["Call to Arms|Crystal Sword"]),
               g("Charms", ["Annihilus", "Hellfire Torch", "Gheed's Fortune"])],
              ["Five Lightning Sentries on one spot, then one or two Death Sentries once corpses drop.", "Keep Burst of Speed up."],
              respec=True),
    ]})

# ---------------------------------------------------------------- Druid
# 225 Firestorm, 229 Molten Boulder, 234 Fissure, 244 Volcano, 230 Arctic Blast, 235 Cyclone Armor, 240 Twister, 245 Tornado,
# 250 Hurricane, 221 Raven, 227 Summon Spirit Wolf, 226 Oak Sage, 237 Summon Dire Wolf, 247 Summon Grizzly
DRU = write({
    "id": "dru-wind", "class": "dru", "name": "Wind Druid",
    "summary": "Fire (Firestorm and Fissure) to 38, respec into Tornado and Hurricane.",
    "links": [{"title": "Leveling guide", "url": "https://maxroll.gg/d2/guides/druid-leveling"},
              {"title": "Tornado / Hurricane guide", "url": "https://maxroll.gg/d2/guides/tornado-hurricane-druid"}],
    "breakpoints": bp("dru", 163, 42), "after20": [245, 250, 235],
    "merc": {"type": "Act 2 Might (Greiz, Offensive)", "note": "The Reaper's Toll casts Decrepify on hit.",
             "gear": MERC_A2(["The Reaper's Toll"], "Insight early; The Reaper's Toll in the end.")},
    "stages": [
        stage("Act 1 Normal", 13, s((225, 5), (229, 2), (225, 9), (234, 2)),
              [to("vit", 75), to("str", 25), rest("vit")],
              [g("Weapon", ["Leaf|Short Staff"], "Short Staff from Akara after the Den of Evil; Tir Ral."), HELMS,
               g("Body", ["Studded Leather"], "2 sockets, not magic: kept for Stealth.")],
              ["Den of Evil point: Firestorm.", RUNES_A1]),
        stage("Act 2–5 Normal", 26, s((234, 3), (225, 10), (234, 9), (225, 11), (234, 15)),
              [to("vit", 115), to("str", 43), to("str", 48, "Only if your Spirit base is a Broad Sword."), rest("vit")],
              [STEALTH, g("Weapon", ["Leaf|Short Staff"]), AP, g("Belt", ["Belt"], "Farah."), TELE_STAFF, SPIRIT_BASE],
              ["Radament and Izual points: Firestorm."]),
        stage("Act 1–3 Nightmare", 38, s((225, 12), (225, 13), (234, 19), (225, 14), (225, 20), (244, 1)),
              [rest("vit")],
              [SPIRIT_NM, LORE, NM_SHOP],
              ["Wait for Anya's reward until 30 for a chance at +1 to skills.", "Respec before Mephisto in Nightmare at the latest."]),
        stage("Respec · Nightmare – Hell", None,
              s((230, 1), (235, 1), (240, 1), (245, 15), (250, 9), (235, 14), (245, 20), (250, 20), (235, 20), (221, 1), (227, 1), (226, 1),
                (237, 1), (247, 1), (240, 20), (226, 20), (237, 20)),
              [gear("str"), gear("dex"), rest("vit")],
              [g("Weapon", ["Heart of the Oak|Flail", "Spirit|Crystal Sword", "Wizardspike", "Suicide Branch"]),
               g("Shield", ["Spirit|Monarch", "Stormshield"]),
               g("Helmet", ["Jalal's Mane", "Harlequin Crest", "Lore|Cap"]),
               g("Body", ["Enigma|Mage Plate", "Skin of the Vipermagi", "Smoke"]),
               g("Gloves", ["Magefist", "Trang-Oul's Claws"]),
               g("Belt", ["Arachnid Mesh", "Verdungo's Hearty Cord"]),
               g("Boots", ["War Traveler", "Aldur's Advance", "Sandstorm Trek"]),
               g("Amulet", ["Mara's Kaleidoscope"]),
               g("Rings", ["The Stone of Jordan", "Bul-Kathos' Wedding Band"]),
               g("Swap", ["Call to Arms|Crystal Sword"]),
               g("Charms", ["Annihilus", "Hellfire Torch", "Gheed's Fortune"])],
              ["The respec leaves a few points over: spend them on the summon line (Raven to Grizzly).",
               "Tornado hits physical; Hurricane adds cold around you."],
              respec=True),
    ]})

# ---------------------------------------------------------------- Necromancer (our own: the guide pages could not be read)
# 70 Raise Skeleton, 69 Skeleton Mastery, 80 Raise Skeletal Mage, 66 Amplify Damage, 67 Teeth, 74 Corpse Explosion, 75 Clay Golem,
# 79 Golem Mastery, 72 Weaken, 77 Terror, 87 Decrepify, 89 Summon Resist, 85 Blood Golem, 90 Iron Golem, 95 Revive
NEC = write({
    "id": "nec-summoner", "class": "nec", "name": "Summoner Necromancer",
    "summary": "An army of skeletons from level 1 to the end; no respec needed.",
    "notes": "Written from the game's own skill data and the long-standing summoner plan; the maxroll pages could not be read while it was made.",
    "links": [{"title": "Necromancer guides", "url": "https://maxroll.gg/d2/category/guides/necromancer"}],
    "breakpoints": bp("nec", 75, 39), "after20": [69, 70, 80],
    "merc": {"type": "Act 2 Might (Greiz, Offensive)", "note": "Might helps the skeletons hit harder.",
             "gear": MERC_A2(["The Reaper's Toll"], "Insight early; The Reaper's Toll in the end.")},
    "stages": [
        stage("Act 1 Normal", 13, s((70, 1), (69, 1), (70, 3), (66, 1), (67, 1), (74, 1), (75, 1), (70, 6)),
              [rest("vit")],
              [g("Weapon", ["Wand"], "Any wand with +Raise Skeleton from Akara or drops."), HELMS,
               g("Body", ["Studded Leather"], "2 sockets, not magic: kept for Stealth.")],
              ["Corpse Explosion clears packs once the skeletons drop the first kill.", RUNES_A1]),
        stage("Act 2 Normal – Act 1 Nightmare", 30, s((80, 1), (79, 1), (72, 1), (77, 1), (70, 12), (69, 6), (87, 1), (89, 1), (70, 20)),
              [gear("str"), rest("vit")],
              [STEALTH, AP, LORE, g("Belt", ["Belt"], "Farah."), TELE_STAFF],
              ["Amplify Damage or Decrepify on bosses; let the army do the rest."]),
        stage("Nightmare – Hell", None, s((69, 20), (80, 20), (85, 1), (90, 1), (95, 1), (89, 20), (74, 20)),
              [gear("str"), rest("vit")],
              [g("Weapon", ["Heart of the Oak|Flail", "Arm of King Leoric", "Wizardspike"]),
               g("Shield", ["Homunculus", "Spirit|Monarch", "Rhyme|Bone Shield"])] + CASTER_END,
              ["Revive the strongest monsters you meet: they last a few minutes.",
               "Summon Resist keeps the army alive in Hell."]),
    ]})

# ---------------------------------------------------------------- Barbarian
# 126 Bash, 129 Mace Mastery, 132 Leap, 133 Double Swing, 130 Howl, 137 Taunt, 143 Leap Attack, 146 Battle Cry, 141 Increased Stamina,
# 138 Shout, 140 Double Throw, 147 Frenzy, 149 Battle Orders, 148 Increased Speed, 154 War Cry, 155 Battle Command, 139 Stun,
# 144 Concentrate, 151 Whirlwind, 145 Iron Skin, 153 Natural Resistance, 127 Blade Mastery, 131 Find Potion, 142 Find Item,
# 150 Grim Ward, 152 Berserk
BAR = write({
    "id": "bar-whirlwind", "class": "bar", "name": "Whirlwind Barbarian",
    "summary": "Bash and Double Swing, War Cry from 31, then a second respec into Whirlwind at 75.",
    "links": [{"title": "Leveling guide", "url": "https://maxroll.gg/d2/guides/barbarian-leveling"},
              {"title": "Whirlwind guide", "url": "https://maxroll.gg/d2/guides/whirlwind-barbarian-guide"}],
    "breakpoints": bp("bar", 63, 27), "after20": [151, 127, 149],
    "merc": {"type": "Act 2 Might (Greiz, Offensive)", "note": "The Reaper's Toll casts Decrepify on hit.",
             "gear": MERC_A2(["The Reaper's Toll"], "Insight early; The Reaper's Toll in the end.")},
    "stages": [
        stage("Act 1 Normal", 15, s((126, 1), (129, 2), (126, 2), (133, 1), (132, 1), (133, 6), (126, 6)),
              [to("vit", 55), to("dex", 35), to("str", 41), rest("vit")],
              [g("Weapon", ["Scepter"], "Two scepters from Akara after the Den of Evil."),
               g("Helmet", ["Nadir|Cap"], "2-socket helmet, not magic: Nef Tir."),
               g("Body", ["Studded Leather"], "2 sockets, not magic: kept for Stealth.")],
              ["Den of Evil point: Bash.", "Save the level 5 point: Double Swing and Leap at 6.", RUNES_A1]),
        stage("Act 2–4 Normal", 25, s((130, 1), (137, 1), (143, 1), (146, 1), (129, 3), (141, 1), (138, 1), (140, 1), (147, 1), (149, 1),
                                      (148, 1), (149, 2), (147, 2)),
              [rest("vit")],
              [g("Weapon", ["Malice|Flail"], "Two Flails from Farah (level 14): Malice in one, 3 Tal gems in the other."),
               STEALTH, g("Shield", ["Bone Shield"], "Drognan: kept for Rhyme."), TELE_STAFF],
              ["Radament point: Howl.", "Save points at 17 and 23 for the two-skill levels after."]),
        stage("Act 5 Normal", 30, s((126, 9), (154, 1), (155, 1)),
              [rest("vit")],
              [g("Weapon", ["Wand"], "Wyrm's Wand of the Magus from Malah (mana first)."),
               g("Swap", ["Javelin"], "A throwing weapon with +1 to Warcries from Malah.")],
              ["Save the level 29 point: War Cry and Battle Command at 30.", "Respec in the Cow Level at 31."]),
        stage("Respec · Act 1 Nightmare", 40,
              s((126, 1), (139, 1), (144, 1), (132, 1), (143, 1), (151, 1), (129, 1), (141, 1), (148, 1), (145, 1), (153, 1), (130, 1),
                (137, 1), (138, 1), (146, 9), (149, 8), (155, 1), (154, 2), (146, 10), (154, 11)),
              [to("str", 43), to("dex", 60), to("dex", 70, "50% chance to block."), rest("vit")],
              [g("Weapon", ["Spirit|Crystal Sword"], "Two of them in the end: one now, one after Larzuk in Nightmare."),
               LORE, g("Shield", ["Rhyme|Bone Shield"]), g("Weapon", ["Black|Flail"], "On the Malice flail's base, or a new one."),
               g("Shop (Nightmare)", ["Coral Demonhide Sash", "Garnet Heavy Boots of Haste", "Garnet Heavy Gloves"])],
              ["War Cry stuns and hurts everything around you.", "Den of Evil point: Battle Cry."], respec=True),
        stage("Nightmare – Hell", 75, s((154, 20), (146, 20), (137, 20), (130, 20)),
              [gear("dex", "Keep 50% chance to block."), rest("vit")],
              [g("Weapon", ["Spirit|Crystal Sword"], "The second Spirit from Larzuk's quest in Nightmare.")],
              ["At 75, respec again into Whirlwind (next stage)."]),
        stage("Respec · Whirlwind", None,
              s((126, 1), (139, 1), (144, 1), (132, 1), (143, 1), (151, 1), (130, 1), (137, 1), (146, 1), (138, 1), (149, 1), (155, 1),
                (131, 1), (142, 1), (150, 1), (141, 1), (148, 1), (145, 1), (153, 1),
                (151, 20), (127, 20), (149, 20), (131, 20), (152, 20)),
              [gear("str"), gear("dex"), rest("vit")],
              [g("Weapons", ["Grief|Phase Blade", "Beast|Berserker Axe", "Breath of the Dying|War Pike", "Obedience|Thresher"],
                 "Two Grief Phase Blades in the end; Obedience is the cheap start (then Polearm Mastery instead of Blade Mastery)."),
               g("Helmet", ["Arreat's Face", "Guillaume's Face", "Crown of Ages"]),
               g("Body", ["Chains of Honor", "Enigma", "Fortitude"]),
               g("Gloves", ["Chance Guards", "Laying of Hands"]),
               g("Belt", ["String of Ears", "Verdungo's Hearty Cord", "Thundergod's Vigor"]),
               g("Boots", ["Gore Rider", "War Traveler", "Goblin Toe"]),
               g("Amulet", ["Highlord's Wrath", "Mara's Kaleidoscope", "Metalgrid"]),
               g("Rings", ["Raven Frost", "Bul-Kathos' Wedding Band"]),
               g("Swap", ["Call to Arms|Crystal Sword", "Heart of the Oak|Flail"]),
               g("Charms", ["Annihilus", "Hellfire Torch", "Gheed's Fortune"])],
              ["The mastery follows the weapon: Blade Mastery for Phase Blades.", "Berserk for physical-immune monsters."],
              respec=True),
    ]})

# ---------------------------------------------------------------- Warlock
# 395 Miasma Bolt, 399 Miasma Chains, 393 Sigil: Lethargy, 383 Levitation Mastery, 388 Echoing Strike, 390 Blade Warp,
# 397 Enhanced Entropy, 402 Abyss, 373 Summon Goatman, 376 Summon Tainted, 377 Summon Defiler, 374 Demonic Mastery, 378 Blood Oath,
# 381 Consume, 396 Sigil: Rancor, 400 Sigil: Death, 391 Cleave, 387 Psychic Ward, 384 Eldritch Blast, 385 Hex Bane, 389 Hex Purge,
# 392 Mirrored Blades
WAR = write({
    "id": "war-echoing-strike", "class": "war", "name": "Echoing Strike Warlock",
    "summary": "Miasma and Abyss to 75, then a respec into Echoing Strike with Hex Purge and Mirrored Blades.",
    "notes": "Warlock breakpoint tables are not published with the guides; the guide's targets (75 / 125 FCR, 26 FHR) match the Necromancer's tables, used here.",
    "links": [{"title": "Leveling guide", "url": "https://maxroll.gg/d2/guides/abyss-warlock-leveling-build-guide"},
              {"title": "Echoing Strike guide", "url": "https://maxroll.gg/d2/guides/echoing-strike-warlock-guide"}],
    "breakpoints": bp("war", 125, 26), "after20": [389, 392, 385],
    "merc": {"type": "Act 2 Prayer (Greiz, Combat)", "note": "Prayer heals you and the mercenary.",
             "gear": [g("Weapon", ["Insight|Giant Thresher"]), g("Body", ["Smoke", "Treachery", "Chains of Honor"]),
                      g("Helmet", ["Bulwark", "Tal Rasha's Horadric Crest", "Cure|Grand Crown"])]},
    "stages": [
        stage("Act 1–2 Normal", 18, s((395, 11), (399, 4), (393, 1), (383, 1), (388, 1), (390, 1)),
              [to("vit", 45), to("dex", 25), to("str", 25), rest("vit")],
              [HELMS, g("Body", ["Hard Leather Armor"], "2 sockets, not magic: kept for Stealth."),
               g("Boots", ["Boots"], "Imbue boots at Charsi at 19 for a chance at faster run/walk.")],
              ["Den of Evil point: Miasma Bolt.", "Radament point: Sigil: Lethargy.", RUNES_A1]),
        stage("Act 3–5 Normal", 27, s((399, 9), (399, 11), (397, 1), (399, 14)),
              [to("str", 48, "Includes Lam Esen's Tome (Act 3)."), rest("vit")],
              [g("Body", ["Stealth|Hard Leather Armor"]), AP, g("Swap", ["Battle Staff"], "Battle Staff of Teleportation from Ormus, levels 20-23 (no more than 19,000 gold).")],
              ["Izual points: Miasma Chains."]),
        stage("Nightmare", 40, s((399, 17), (402, 1), (399, 20), (402, 7), (402, 8), (402, 10)),
              [rest("vit")],
              [LORE, SPIRIT_NM, NM_SHOP],
              ["Respec at 40, after all Nightmare quests: not before."]),
        stage("Respec · Hell", 75,
              s((373, 1), (376, 1), (377, 1), (374, 1), (378, 1), (381, 1), (383, 1), (388, 1), (390, 1), (393, 1), (396, 1), (400, 1),
                (395, 3), (399, 20), (397, 2), (402, 10), (402, 20), (395, 20), (400, 8), (397, 8)),
              [to("str", 48), to("dex", 35), rest("vit")],
              [g("Weapon", ["Spirit|Crystal Sword"]), g("Shield", ["Spirit|Monarch", "Ancients' Pledge|Kite Shield"])],
              ["Abyss and Miasma carry you to 75.", "At 75, respec into Echoing Strike (next stage)."], respec=True),
        stage("Respec · Echoing Strike", None,
              s((383, 1), (388, 1), (390, 1), (391, 1), (387, 1), (384, 1), (385, 1), (393, 1), (373, 1), (376, 1), (377, 1), (374, 1),
                (389, 20), (392, 20), (385, 20), (374, 10), (384, 20), (378, 20)),
              [gear("str"), to("dex", 120, "About 100 points for attack rating."), rest("vit")],
              [g("Weapon", ["Void", "Heart of the Oak|Flail", "Spirit|Crystal Sword"], "Spirit to start; reach 75% cast rate first."),
               g("Off-hand", ["Spirit|Monarch", "Lidless Wall"])] + CASTER_END,
              ["Keep 125% cast rate once you can.", "Starter: Demonic Mastery only to 5 and Consume after the main skills."],
              respec=True),
    ]})


# ================================================================ second builds
# The endgame guides all start from a level 75 character made with the class's leveling guide, so a second build shares the first
# build's stages up to 75 (its open last stage now ends there) and adds one respec stage of its own.
import copy

def then(base, id_, name, summary, title, url, final, breakpoints, after20, merc, cut=None):
    stages = copy.deepcopy(base["stages"][:cut])
    last = stages[-1]
    if "to" not in last:
        last["to"] = 75
    last["tips"] = [t for t in last["tips"] if "next stage" not in t] + ["At 75, respec into %s (next stage)." % name]
    return write({"id": id_, "class": base["class"], "name": name, "summary": summary,
                  "links": [base["links"][0], {"title": title, "url": url}],
                  "breakpoints": breakpoints, "after20": after20, "merc": merc, "stages": stages + [final]})

def merc_a2(type_, note, weapons, body, helmet):
    return {"type": type_, "note": note, "gear": [g("Weapon", weapons), g("Body", body), g("Helmet", helmet)]}

MIGHT = "Act 2 Might (Greiz, Offensive)"
HOLY_FREEZE = "Act 2 Holy Freeze (Greiz, Defensive)"
CHARMS_PHYS = g("Charms", ["Annihilus", "Hellfire Torch", "Bone Break"], "Bone Break lowers physical immunity.")

# ---------------------------------------------------------------- Sorceress: Lightning
# 37 Warmth, 49 Lightning, 53 Chain Lightning, 57 Thunder Storm, 63 Lightning Mastery (others above)
then(SOR, "sor-lightning", "Lightning Sorceress",
     "Levels as the Blizzard Sorceress, then Lightning and Chain Lightning with Lightning Mastery at 75.",
     "Lightning guide", "https://maxroll.gg/d2/guides/lightning-sorceress",
     stage("Respec · Lightning", None,
           s((43, 1), (54, 1), (42, 1), (40, 1), (37, 1), (38, 1), (49, 1), (53, 1), (48, 1), (57, 1), (63, 1),
             (49, 20), (63, 20), (53, 20), (38, 20), (48, 20), (37, 20)),
           [gear("str"), gear("dex"), rest("vit")],
           [g("Weapon", ["Heart of the Oak|Flail", "Eschuta's Temper", "The Oculus", "Spirit|Crystal Sword"]),
            g("Shield", ["Spirit|Monarch", "Stormshield", "Ancients' Pledge|Kite Shield"]),
            g("Helmet", ["Griffon's Eye", "Harlequin Crest", "Lore|Cap"]),
            g("Body", ["Enigma|Mage Plate", "Skin of the Vipermagi", "Smoke"]),
            g("Gloves", ["Chance Guards", "Magefist", "Frostburn"]),
            g("Belt", ["Arachnid Mesh", "Verdungo's Hearty Cord", "Goldwrap"]),
            g("Boots", ["War Traveler", "Sandstorm Trek", "Silkweave"]),
            g("Amulet", ["Mara's Kaleidoscope"]),
            g("Rings", ["The Stone of Jordan", "Bul-Kathos' Wedding Band", "Nagelring"]),
            g("Swap", ["Call to Arms|Crystal Sword", "Wand of Lower Resistance"], "Lower Resist charges help against lightning immunes."),
            g("Charms", ["Crack of the Heavens", "Annihilus", "Hellfire Torch", "Gheed's Fortune"], "Crack of the Heavens lowers lightning immunity.")],
           ["Lightning and Chain Lightning have their own cast speed table: 117% is the target.",
            "Warmth takes the points left over at the very end."],
           respec=True),
     bp("sor", 117, 60, [0, 7, 15, 23, 35, 52, 78, 117, 194]), [49, 53, 63],
     merc_a2(MIGHT, "Infinity's Conviction lowers lightning resistance.", ["Insight|Giant Thresher", "Infinity|Giant Thresher"],
             ["Smoke", "Fortitude"], ["Bulwark", "Andariel's Visage"]))

# ---------------------------------------------------------------- Amazon: Multiple Shot bow
# 11 Cold Arrow, 22 Guided Arrow, 26 Strafe (others above)
then(AMA, "ama-multishot", "Multiple Shot Amazon",
     "Levels as the Lightning Fury Amazon, then a bow build at 75: Multiple Shot with Pierce, Guided Arrow and Strafe.",
     "Multiple Shot guide", "https://maxroll.gg/d2/guides/multiple-shot-amazon",
     stage("Respec · Multiple Shot", None,
           s((6, 1), (12, 1), (9, 1), (23, 1), (33, 1), (13, 1), (18, 1), (29, 1), (8, 1), (17, 1), (28, 1), (32, 1), (11, 1), (22, 1),
             (26, 1), (33, 12), (12, 20), (9, 20), (23, 20), (22, 20), (6, 20), (26, 20)),
           [gear("str", "As little as the gear allows."), rest("dex")],
           [g("Weapon", ["Eternity|Grand Matron Bow", "Windforce", "Buriza-Do Kyanon"], "Windforce or Buriza first; arrows or bolts in the other hand."),
            g("Helmet", ["Andariel's Visage", "Crown of Ages", "Guillaume's Face"]),
            g("Body", ["Treachery", "Fortitude", "Enigma"]),
            g("Gloves", ["Death's Hand"]),
            g("Belt", ["Death's Guard", "Razortail"]),
            g("Boots", ["War Traveler", "Sandstorm Trek", "Aldur's Advance"]),
            g("Amulet", ["Angelic Wings"]),
            g("Rings", ["Angelic Halo", "Raven Frost"]),
            CHARMS_PHYS],
           ["Stop Pierce at 69% counting +skills; Multiple Shot stops gaining arrows at 24.",
            "Guided Arrow for bosses; Valkyrie and Decoy draw the hits."],
           respec=True),
     bp("ama", None, 32), [12, 33, 9],
     merc_a2(MIGHT, "Pride's aura raises your attack rating and damage.", ["Insight|Giant Thresher", "Pride|Giant Thresher"],
             ["Treachery", "Shaftstop"], ["Bulwark", "Andariel's Visage"]))

# ---------------------------------------------------------------- Necromancer: Bone Spear
# 68 Bone Armor, 76 Iron Maiden, 78 Bone Wall, 82 Life Tap, 84 Bone Spear, 88 Bone Prison, 93 Bone Spirit (others above)
then(NEC, "nec-bone-spear", "Bone Spear Necromancer",
     "Levels as the Summoner, then Bone Spear and Bone Spirit at 75; Bone Wall and Bone Prison as synergies.",
     "Bone Spear guide", "https://maxroll.gg/d2/guides/bone-spear-necromancer",
     stage("Respec · Bone Spear", None,
           s((66, 1), (72, 1), (77, 1), (87, 1), (76, 1), (82, 1), (75, 1), (67, 1), (74, 1), (84, 1), (68, 1), (78, 1), (88, 1), (93, 1),
             (84, 20), (88, 20), (78, 20), (93, 20), (67, 20)),
           [gear("str"), rest("vit")],
           [g("Weapon", ["Heart of the Oak|Flail", "Spirit|Crystal Sword", "Wizardspike"]),
            g("Shield", ["Homunculus", "Spirit|Monarch", "Trang-Oul's Wing"]),
            g("Helmet", ["Harlequin Crest", "Trang-Oul's Guise", "Lore|Cap"]),
            g("Body", ["Enigma|Mage Plate", "Skin of the Vipermagi", "Bone|Mage Plate"]),
            g("Gloves", ["Trang-Oul's Claws", "Magefist"]),
            g("Belt", ["Arachnid Mesh", "Goldwrap"]),
            g("Boots", ["Marrowwalk", "Sandstorm Trek", "Silkweave"]),
            g("Amulet", ["Mara's Kaleidoscope"]),
            g("Rings", ["The Stone of Jordan", "Bul-Kathos' Wedding Band", "Nagelring"]),
            g("Swap", ["Call to Arms|Crystal Sword", "Staff of Teleportation"]),
            g("Charms", ["Annihilus", "Hellfire Torch", "Gheed's Fortune"])],
           ["Bone damage is magic: hardly anything resists it.", "Clay Golem slows; Decrepify on bosses; Bone Prison walls them in."],
           respec=True),
     bp("nec", 125, 56), [84, 93, 88],
     merc_a2(HOLY_FREEZE, "Holy Freeze slows everything that comes close.", ["Insight|Giant Thresher"],
             ["Smoke", "Treachery", "Fortitude"], ["Bulwark", "Tal Rasha's Horadric Crest", "Andariel's Visage"]))

# ---------------------------------------------------------------- Paladin: Smiter
# 125 Salvation (others above)
then(PAL, "pal-smiter", "Smite Paladin",
     "Levels as the Blessed Hammer Paladin, then Smite with Fanaticism and Holy Shield at 75: the boss killer.",
     "Smite guide", "https://maxroll.gg/d2/guides/smite-paladin",
     stage("Respec · Smite", None,
           s((99, 1), (109, 1), (104, 1), (115, 1), (125, 1), (97, 1), (107, 1), (101, 1), (112, 1), (117, 1), (98, 1), (108, 1), (113, 1),
             (122, 1), (96, 1), (106, 1), (110, 1), (117, 20), (122, 20), (97, 20), (110, 20), (106, 20)),
           [gear("str"), gear("dex", "Enough for 75% chance to block with Holy Shield."), rest("vit")],
           [g("Weapon", ["Grief|Phase Blade", "Black|Flail"]),
            g("Shield", ["Exile|Vortex Shield", "Herald of Zakarum", "Rhyme|Targe"]),
            g("Helmet", ["Guillaume's Face", "Crown of Ages", "Vampire Gaze"]),
            g("Body", ["Chains of Honor", "Enigma", "Treachery", "Duress"]),
            g("Gloves", ["Dracul's Grasp", "Laying of Hands", "Bloodfist"]),
            g("Belt", ["Thundergod's Vigor", "String of Ears", "Verdungo's Hearty Cord"]),
            g("Boots", ["Goblin Toe", "Gore Rider", "Sandstorm Trek"]),
            g("Amulet", ["Mara's Kaleidoscope", "Metalgrid"]),
            g("Rings", ["Raven Frost", "Bul-Kathos' Wedding Band", "Wisp Projector"]),
            g("Charms", ["Annihilus", "Hellfire Torch"])],
           ["Smite always hits and stuns.", "Crushing blow and life tap (Dracul's Grasp) melt bosses."],
           respec=True),
     bp("pal", None, 48), [117, 122, 97],
     merc_a2(MIGHT, "The Reaper's Toll casts Decrepify on hit.", ["Insight|Giant Thresher", "The Reaper's Toll"],
             ["Smoke", "Treachery"], ["Bulwark", "Vampire Gaze"]))

# ---------------------------------------------------------------- Barbarian: Frenzy (from the War Cry stage at 75, like Whirlwind)
then(BAR, "bar-frenzy", "Frenzy Barbarian",
     "Levels as the Whirlwind Barbarian with War Cry, then Frenzy with two weapons at 75.",
     "Frenzy guide", "https://maxroll.gg/d2/guides/frenzy-barbarian",
     stage("Respec · Frenzy", None,
           s((130, 1), (137, 1), (146, 1), (138, 1), (149, 1), (155, 1), (131, 1), (142, 1), (150, 1), (141, 1), (148, 1), (145, 1),
             (153, 1), (132, 1), (143, 1), (126, 1), (133, 1), (140, 1), (147, 1), (127, 1), (139, 1), (144, 1), (152, 1),
             (147, 20), (133, 20), (127, 20), (149, 20), (152, 20)),
           [gear("str"), gear("dex"), rest("vit")],
           [g("Weapons", ["Grief|Phase Blade", "Oath|Balrog Blade", "Beast|Berserker Axe"], "Two Grief Phase Blades in the end; the mastery follows the weapon."),
            g("Helmet", ["Arreat's Face", "Immortal King's Will", "Crown of Ages"]),
            g("Body", ["Fortitude", "Enigma", "Treachery"]),
            g("Gloves", ["Dracul's Grasp", "Laying of Hands"]),
            g("Belt", ["Thundergod's Vigor", "String of Ears"]),
            g("Boots", ["Gore Rider", "War Traveler", "Goblin Toe"]),
            g("Amulet", ["Highlord's Wrath"]),
            g("Rings", ["Raven Frost", "Bul-Kathos' Wedding Band"]),
            g("Swap", ["Call to Arms|Crystal Sword"], "Battle Orders before a fight."),
            CHARMS_PHYS],
           ["Frenzy gets faster with every hit: keep swinging.", "Berserk for physical-immune monsters."],
           respec=True),
     bp("bar", 63, 27), [147, 133, 149],
     merc_a2(MIGHT, "The Reaper's Toll casts Decrepify on hit.", ["Insight|Giant Thresher", "The Reaper's Toll"],
             ["Treachery", "Fortitude"], ["Tal Rasha's Horadric Crest", "Andariel's Visage"]),
     cut=5)

# ---------------------------------------------------------------- Druid: Fury Werewolf
# 223 Werewolf, 224 Lycanthropy, 228 Werebear, 232 Feral Rage, 233 Maul, 236 Heart of Wolverine, 238 Rabies, 248 Fury (others above)
then(DRU, "dru-fury", "Fury Druid",
     "Levels as the Wind Druid, then a Werewolf with Fury and Feral Rage at 75.",
     "Fury guide", "https://maxroll.gg/d2/guides/werewolf-fury-druid",
     stage("Respec · Fury", None,
           s((221, 1), (227, 1), (226, 1), (237, 1), (247, 1), (236, 1), (228, 1), (233, 1), (223, 1), (224, 1), (232, 1), (238, 1), (248, 1),
             (223, 20), (224, 20), (248, 20), (232, 20), (236, 20)),
           [to("str", 200, "Fury hits harder with Strength; up to 300."), gear("dex"), rest("vit")],
           [g("Weapon", ["Eternity|Archon Staff", "Breath of the Dying|Archon Staff", "Tomb Reaver", "Oath|Feral Axe"]),
            g("Helmet", ["Metamorphosis", "Jalal's Mane", "Harlequin Crest"]),
            g("Body", ["Fortitude", "Treachery", "Shaftstop"]),
            g("Gloves", ["Dracul's Grasp", "Laying of Hands"]),
            g("Belt", ["Thundergod's Vigor", "String of Ears", "Verdungo's Hearty Cord"]),
            g("Boots", ["Gore Rider", "War Traveler", "Goblin Toe"]),
            g("Rings", ["Raven Frost", "Bul-Kathos' Wedding Band"]),
            g("Swap", ["Call to Arms|Crystal Sword"]),
            CHARMS_PHYS],
           ["With Metamorphosis: Werebear and one Maul for its mark, then back to Werewolf (about every 3 minutes).",
            "Points past 97 go to Summon Dire Wolf."],
           respec=True),
     bp("dru", None, 42), [248, 223, 224],
     merc_a2(MIGHT, "Pride's aura raises your attack rating and damage.", ["Insight|Giant Thresher", "Pride|Giant Thresher"],
             ["Smoke", "Shaftstop"], ["Bulwark", "Vampire Gaze"]))

# ---------------------------------------------------------------- Assassin: Phoenix Strike (Mosaic)
# 254 Tiger Strike, 255 Dragon Talon, 259 Fists of Fire, 265 Cobra Strike, 268 Shadow Warrior, 269 Claws of Thunder, 274 Blades of Ice,
# 280 Phoenix Strike (others above)
then(ASS, "ass-phoenix-strike", "Phoenix Strike Assassin",
     "Levels as the Lightning Sentry Assassin, then martial arts at 75: Phoenix Strike charges with Mosaic claws.",
     "Phoenix Strike guide", "https://maxroll.gg/d2/guides/phoenix-strike-assassin",
     stage("Respec · Phoenix Strike", None,
           s((253, 1), (264, 1), (273, 1), (252, 1), (258, 1), (267, 1), (263, 1), (254, 1), (265, 1), (259, 1), (269, 1), (274, 1),
             (280, 1), (255, 1), (268, 1), (280, 20), (255, 20), (269, 20), (254, 20), (274, 20)),
           [gear("str"), gear("dex"), rest("vit")],
           [g("Claws", ["Mosaic|Greater Talons", "Bartuc's Cut-Throat", "Plague|Greater Talons"], "Two claws; Greater Talons need little Strength."),
            g("Helmet", ["Griffon's Eye", "Harlequin Crest", "Guillaume's Face"]),
            g("Body", ["Enigma|Mage Plate", "Chains of Honor", "Treachery"]),
            g("Gloves", ["Magefist", "Dracul's Grasp"]),
            g("Belt", ["Arachnid Mesh", "String of Ears"]),
            g("Boots", ["Aldur's Advance", "War Traveler", "Waterwalk"]),
            g("Amulet", ["Mara's Kaleidoscope", "Highlord's Wrath"]),
            g("Rings", ["The Stone of Jordan", "Bul-Kathos' Wedding Band", "Raven Frost"]),
            g("Charms", ["Annihilus", "Hellfire Torch", "Gheed's Fortune"])],
           ["Hit with Phoenix Strike to build charges, then release them with a finisher; Mosaic keeps the charges after release."],
           respec=True),
     bp("ass", None, 48), [280, 255, 269],
     merc_a2(MIGHT, "Infinity's Conviction lowers elemental resistances.", ["Insight|Giant Thresher", "Infinity|Giant Thresher"],
             ["Treachery", "Fortitude"], ["Bulwark", "Andariel's Visage"]))

# ---------------------------------------------------------------- Warlock: Mirrored Blades (from the Abyss stage at 75, like Echoing Strike)
# 375 Death Mark, 379 Engorge, 380 Blood Boil, 382 Bind Demon (others above)
then(WAR, "war-mirrored-blades", "Mirrored Blades Warlock",
     "Levels with Miasma and Abyss like the Echoing Strike Warlock, then Mirrored Blades with Cleave and Hex: Purge at 75.",
     "Mirrored Blades guide", "https://maxroll.gg/d2/guides/mirrored-blades-warlock-guide",
     stage("Respec · Mirrored Blades", None,
           s((385, 1), (393, 1), (396, 1), (400, 1), (373, 1), (374, 1), (378, 1), (383, 1), (388, 1), (390, 1), (391, 1), (387, 1),
             (384, 1), (392, 1), (389, 1), (375, 1), (380, 1), (379, 1), (376, 1), (377, 1), (382, 1),
             (392, 20), (391, 20), (389, 20), (374, 10), (382, 10), (383, 20)),
           [gear("str"), rest("vit")],
           [g("Weapon", ["Tomb Reaver"]),
            g("Helmet", ["Guillaume's Face", "Harlequin Crest", "Crown of Ages"]),
            g("Body", ["Enigma|Mage Plate", "Chains of Honor", "Stealth|Studded Leather"]),
            g("Gloves", ["Chance Guards"]),
            g("Belt", ["Arachnid Mesh", "Goldwrap"]),
            g("Boots", ["Waterwalk", "Sandstorm Trek", "War Traveler"]),
            g("Amulet", ["Mara's Kaleidoscope"]),
            g("Rings", ["The Stone of Jordan", "Bul-Kathos' Wedding Band"]),
            g("Swap", ["Call to Arms|Crystal Sword", "Staff of Teleportation"]),
            g("Charms", ["Annihilus", "Hellfire Torch", "Gheed's Fortune"])],
           ["Bind Demon: stop at 20 counting +skills; the rest goes to Levitation Mastery.",
            "Aim for at least 48% faster hit recovery (no Warlock table is published yet)."],
           respec=True),
     {}, [392, 391, 389],
     merc_a2(MIGHT, "Pride's aura raises your attack rating and damage.", ["Insight|Giant Thresher", "Pride|Giant Thresher"],
             ["Smoke", "Treachery", "Guardian Angel"], ["Bulwark", "Tal Rasha's Horadric Crest", "Andariel's Visage"]),
     cut=4)

print('wrote %d build guides to %s' % (len(os.listdir(OUT)), OUT))

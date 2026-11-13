#!/usr/bin/env python3
"""Assets for the bottom-screen Deck (dual-screen handhelds such as the AYN Thor), made from the player's own game files.

Writes into the import pack's overlay folder, before its manifest is written:
  deck/levels.tsv   area names per game language: header "id" + the 13 language columns in the game's
                    language-number order (dm_i18n.h g_dm_lang), one row per levels.txt Id.
  deck/items/*.png  inventory pictures from the player's own item sprites, at the game's native 49 px per cell
  deck/items.tsv    key -> picture file: key = item code ("hax"), "u<UniqueItems *ID>" or "s<SetItems *ID>"
                    (the row number the host reads at item data +0x34)
  deck/goals.tsv    map goal names per game language (the goal compass): key = the host's goal label number (dm_world.c, labels from
                    gen_map_goal_art.py), 255 = waypoint
  deck/skills.tsv   skill names per game language by skills.txt *Id (the Skills tab; icons come from overlay touchart/, native_art)
  deck/skillrules.tsv  class skills by *Id: class, required level, highest base level, prerequisites (the Build tab's next steps)
  deck/itemnames.tsv   item, unique, set and runeword names per game language by English name, with runewords' runes (the Build tab)
  deck/ui/*.png     the game's own panel art the Deck is drawn with (UI_ART below), and deck/ui/exocet.otf / formal.ttf, the game's fonts
These files hold game text, so they only ever live in the player's own import pack.
"""
import json
import os
import re
import struct
import sys
from pathlib import Path

# Column i = game language number i (dm_i18n.h: 0 enUS 1 deDE 2 esES ... 12 ptBR)
LANGS = ('enUS', 'deDE', 'esES', 'frFR', 'itIT', 'koKR', 'plPL', 'ruRU', 'zhCN', 'zhTW', 'esMX', 'jaJP', 'ptBR')
GENDER_TAG = re.compile(r'^(?:\[[a-z]{2}\])+')   # grammatical-gender marker at the start of a name ([fs] Spanish, [ns] German, ...)


def _clean(text):
    return re.sub(r'\s+', ' ', GENDER_TAG.sub('', text or '')).strip()


def level_table(levels, strings):
    """levels: rows of levels.txt (dicts); strings: entries of lng/strings/levels.json. Returns the TSV text."""
    by_key = {}
    for entry in strings:
        by_key.setdefault(entry.get('Key'), entry)   # first entry wins, as in the game's own lookup order
    out = ['\t'.join(('id',) + LANGS)]
    for row in levels:
        ident = (row.get('Id') or '').strip()
        if not ident.isdigit():
            continue
        key = row.get('LevelName') or ''
        entry = by_key.get(key)
        names = [_clean(entry.get(lang)) for lang in LANGS] if entry else [_clean(key)] + [''] * (len(LANGS) - 1)
        out.append('\t'.join([ident] + names))
    return '\n'.join(out) + '\n'


def skill_table(skills, skilldesc, strings):
    """Skill names per game language by skills.txt *Id (the ids in the game's controller binding table): skills.txt skilldesc ->
    skilldesc.txt 'str name' -> lng/strings/skills.json. Skills without a description or string are left out."""
    by_key, desc = {}, {r.get('skilldesc'): r.get('str name') for r in skilldesc}
    for e in strings:
        by_key.setdefault(e.get('Key'), e)
    out = ['\t'.join(('id',) + LANGS)]
    for r in skills:
        ident = (r.get('*Id') or '').strip()
        e = by_key.get(desc.get(r.get('skilldesc') or ''))
        if ident.isdigit() and e:
            out.append('\t'.join([ident] + [_clean(e.get(lang)) for lang in LANGS]))
    return '\n'.join(out) + '\n'


def skill_rules(skills):
    """The Build tab's skill rules by skills.txt *Id (BuildPlan.java): class code, required level, highest base level, the three
    prerequisite skill ids (-1 none). Skills of no class (monster skills, item skills) are left out."""
    ids = {r.get('skill'): (r.get('*Id') or '').strip() for r in skills}
    out = ['\t'.join(('id', 'class', 'reqlevel', 'maxlvl', 'req1', 'req2', 'req3'))]
    for r in skills:
        ident, cls = (r.get('*Id') or '').strip(), (r.get('charclass') or '').strip()
        if not ident.isdigit() or not cls:
            continue
        req = [ids.get(r.get('reqskill%d' % i) or '', '') or '-1' for i in (1, 2, 3)]
        out.append('\t'.join([ident, cls, (r.get('reqlevel') or '0').strip() or '0', (r.get('maxlvl') or '0').strip() or '0'] + req))
    return '\n'.join(out) + '\n'


def name_key(text):
    """How the Build tab's guide files name an item: its English name, lowercase, letters and digits only ("Harlequin Crest" ->
    "harlequincrest"), so apostrophes and spacing in the files do not matter."""
    return re.sub(r'[^a-z0-9]+', '', (text or '').lower())


def item_names(base, unique_rows, set_rows, rune_rows, strings):
    """Item names per game language for the Build tab, keyed by name_key(English name). Columns: key, ref (base code, "u<ID>",
    "s<ID>" as in items.tsv for the picture, or "rw" for a runeword), runes (a runeword's rune codes, space separated), then the 13
    languages. base: rows of armor / weapons / misc .txt (code, namestr); strings: entries of item-names.json and item-runes.json.
    First name wins: runewords, then uniques, sets, base items."""
    by_key = {}
    for e in strings:
        by_key.setdefault(e.get('Key'), e)
    out, seen = ['\t'.join(('key', 'ref', 'runes') + LANGS)], set()

    def add(string_key, ref, runes=''):
        e = by_key.get(string_key)
        key = name_key(e.get('enUS')) if e else ''
        if not key or key in seen:
            return
        seen.add(key)
        out.append('\t'.join([key, ref, runes] + [_clean(e.get(lang)) for lang in LANGS]))

    for r in rune_rows:
        if (r.get('complete') or '').strip() == '1':
            add(r.get('Name'), 'rw', ' '.join(c for c in (r.get('Rune%d' % i, '').strip() for i in range(1, 7)) if c))
    for prefix, rows in (('u', unique_rows), ('s', set_rows)):
        for r in rows:
            ident = (r.get('*ID') or '').strip()
            if ident.isdigit():
                add(r.get('index'), prefix + ident)
    for rows in base.values():
        for r in rows:
            if r.get('code'):
                add(r.get('namestr') or r.get('code'), r['code'])
    return '\n'.join(out) + '\n'


WAYPOINT_GOAL = 255   # dm_world.h DM_MG_WP
TOWN_PORTAL_GOAL, RED_PORTAL_GOAL = 254, 253   # deck_jni.c: portals the Deck adds to the goals (GoalCompass.java)


def goal_labels():
    """The map goal label table (number -> (strings file, key)) from dm/ios/tools/gen_map_goal_art.py LABELS, read without importing it
    (it renders pictures on import paths); the host's goal numbers index it (dm_world.c)."""
    import ast
    src = (Path(__file__).resolve().parents[2] / 'ios' / 'tools' / 'gen_map_goal_art.py').read_text(encoding='utf-8')
    for node in ast.parse(src).body:
        if isinstance(node, ast.Assign) and any(getattr(t, 'id', '') == 'LABELS' for t in node.targets):
            return [(e.elts[0].value, e.elts[1].value) for e in node.value.elts]
    raise SystemExit('stage_deck_assets: no LABELS in gen_map_goal_art.py')


def goal_table(labels, strings):
    """Names of the map goals (dm_map_goals) per game language: key = goal label number, plus 255 = waypoint, 254 = town portal (the
    skill's name) and 253 = red portal.
    labels: [(strings file, key)]; strings: {file name: entries of lng/strings/<file>.json}."""
    by_file = {}
    for name, entries in strings.items():
        m = by_file.setdefault(name, {})
        for e in entries:
            m.setdefault(e.get('Key'), e)
    want = list(enumerate(labels)) + [(WAYPOINT_GOAL, ('objects', 'Waypoint')), (TOWN_PORTAL_GOAL, ('skills', 'skillan219')),
                                      (RED_PORTAL_GOAL, ('objects', 'portal'))]
    out = ['\t'.join(('key',) + LANGS)]
    for n, (name, key) in want:
        e = by_file.get(name, {}).get(key)
        if e:
            out.append('\t'.join([str(n)] + [_clean(e.get(lang)) for lang in LANGS]))
    return '\n'.join(out) + '\n'


def _json_map(entries):
    out = {}
    for entry in entries:
        out.update(entry)
    return out


def _name_key(index):
    """UniqueItems / SetItems index -> the key used in hd/items/uniques.json and sets.json (as gen_vault_table.py)."""
    return re.sub(r'[^a-z0-9]+', '_', index.lower().replace("'", '')).strip('_')


def icon_map(base, items, uniques, sets, unique_rows, set_rows, sprites):
    """base: {category: rows of armor / weapons / misc .txt}; items / uniques / sets: hd/items/*.json entries;
    unique_rows / set_rows: UniqueItems / SetItems rows; sprites: 'category/asset' paths that exist (lowercase).
    Returns {key: 'category/asset'} for every key whose picture exists."""
    cat = {r['code']: c for c, rows in base.items() for r in rows if r.get('code')}
    item_asset = {code: (v or {}).get('asset') for code, v in _json_map(items).items()}

    def find(code, own):
        if code not in cat:
            return None
        for asset in (own, item_asset.get(code)):
            if asset and '%s/%s' % (cat[code], asset.lower()) in sprites:
                return '%s/%s' % (cat[code], asset.lower())
        return None

    out = {}
    for code in cat:
        path = find(code, None)
        if path:
            out[code] = path
    for prefix, rows, field, j in (('u', unique_rows, 'code', _json_map(uniques)), ('s', set_rows, 'item', _json_map(sets))):
        for r in rows:
            ident = (r.get('*ID') or '').strip()
            if not ident.isdigit():
                continue
            path = find(r.get(field, ''), (j.get(_name_key(r.get('index', ''))) or {}).get('normal'))
            if path:
                out[prefix + ident] = path
    return out


def icon_file(path):
    return re.sub(r'[^a-z0-9]+', '_', path.lower()).strip('_') + '.png'


# The Deck's look, from the game's own UI sprites (hd/global/ui/...): output name -> (sprite, frame or None, crop box or None).
# Frames are the sprite's side-by-side cells (frame width in the sprite header).
UI_ART = {
    'tab_off': ('controller/panel/controller_top_tab_3tabs', 0, None),
    'tab_on': ('controller/panel/controller_top_tab_3tabs', 1, None),
    'button': ('controller/panel/escapemenu/escapeoptions_button', 0, None),
    'button_down': ('controller/panel/escapemenu/escapeoptions_button', 1, None),
    'button_off': ('controller/panel/escapemenu/escapeoptions_button', 2, None),
    'hud': ('controller/hud/hudbase', None, None),
    'globe': ('panel/hud_02/healthmanaanimation/healthidle/4k/globe_health_man_idle', 0, None),
    'expbar': ('controller/hud/expbar', None, None),
    'beltrow': ('panel/hud_02/belt_popped_row', None, None),
    'stone': ('controller/panel/mainpanelbg', None, (2225, 120, 2815, 755)),   # plain stone inside the right-hand frame
}
UI_FONTS = {'exocet.otf': 'exocetblizzardot-medium.otf', 'formal.ttf': 'formal436bt.ttf'}


def _decode_sprite(raw, path):
    """A .sprite file -> (RGBA image of the whole sheet, frame width)."""
    import romfs_art
    from PIL import Image
    if raw[:4] != b'SpA1':
        raise SystemExit('stage_deck_assets: %s is not a sprite' % path)
    ver, fw, w, h = struct.unpack_from('<HHII', raw, 4)
    if ver == 61:
        return romfs_art.dxt5(raw, w, h), fw
    if ver == 31:
        return Image.frombytes('RGBA', (w, h), raw[40:40 + w * h * 4]), fw
    raise SystemExit('stage_deck_assets: %s has unknown sprite version %d' % (path, ver))


def _stage_ui(read, target):
    out = target / 'ui'
    out.mkdir(parents=True, exist_ok=True)
    for name, (sprite, frame, box) in sorted(UI_ART.items()):
        im, fw = _decode_sprite(read('data/hd/global/ui/%s.sprite' % sprite), sprite)
        if frame is not None and 0 < fw <= im.width:
            im = im.crop((frame * fw, 0, (frame + 1) * fw, im.height))
        if box:
            im = im.crop(box)
        im.save(out / (name + '.png'), optimize=True)
    for name, font in UI_FONTS.items():
        (out / name).write_bytes(read('data/hd/ui/fonts/' + font))
    return len(UI_ART) + len(UI_FONTS)


def _stage_icons(romfs, target):
    import annot_strings, touch_layout
    index = {}   # one pass over the CASC manifest (romfs_art.read scans it per file, too slow for hundreds of pictures)
    for line in open(os.path.join(romfs, 'data', 'build_manifest.psv'), encoding='utf-8'):
        f = line.rstrip('\n').split('|')
        if len(f) >= 11 and not line.startswith('#'):
            index[f[9].lower().split(':', 1)[-1]] = f
    read = lambda p: touch_layout._read_entry(romfs, index[p])
    root = 'data/hd/global/ui/items/'
    sprites = {p[len(root):-len('.sprite')] for p in index if p.startswith(root) and p.endswith('.sprite')}
    base = {c: annot_strings.excel(romfs, f + '.txt') for c, f in (('armor', 'armor'), ('weapon', 'weapons'), ('misc', 'misc'))}
    js = lambda n: json.loads(read('data/hd/items/%s.json' % n).decode('utf-8-sig'))
    unique_rows, set_rows = annot_strings.excel(romfs, 'uniqueitems.txt'), annot_strings.excel(romfs, 'setitems.txt')
    icons = icon_map(base, js('items'), js('uniques'), js('sets'), unique_rows, set_rows, sprites)
    out = target / 'items'
    out.mkdir(parents=True, exist_ok=True)
    for path in sorted(set(icons.values())):
        im, fw = _decode_sprite(read(root + path + '.sprite'), path)
        if 0 < fw < im.width:
            im = im.crop((0, 0, fw, im.height))   # several frames side by side: keep the first
        im.save(out / icon_file(path), optimize=True)
    lines = ['%s\t%s' % (k, icon_file(v)) for k, v in sorted(icons.items())]
    (target / 'items.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    labels, strings = goal_labels(), {}
    for name in sorted({f for f, _ in labels} | {'objects', 'skills'}):
        path = [p for p in index if p.endswith('/lng/strings/%s.json' % name)]
        if path:
            strings[name] = json.loads(read(path[0]).decode('utf-8-sig'))
    (target / 'goals.tsv').write_text(goal_table(labels, strings), encoding='utf-8', newline='\n')
    skill_strings = []   # class skills in skills.json; the controller-only actions (Interact, Loot, Automap ...) in ui-controller.json
    for name in ('skills.json', 'ui-controller.json'):
        path = [p for p in index if p.endswith('/lng/strings/' + name)]
        if path:
            skill_strings += json.loads(read(path[0]).decode('utf-8-sig'))
    (target / 'skills.tsv').write_text(skill_table(annot_strings.excel(romfs, 'skills.txt'), annot_strings.excel(romfs, 'skilldesc.txt'),
                                                   skill_strings), encoding='utf-8', newline='\n')
    (target / 'skillrules.tsv').write_text(skill_rules(annot_strings.excel(romfs, 'skills.txt')), encoding='utf-8', newline='\n')
    item_strings = []
    for name in ('item-names.json', 'item-runes.json'):
        path = [p for p in index if p.endswith('/lng/strings/' + name)]
        if path:
            item_strings += json.loads(read(path[0]).decode('utf-8-sig'))
    (target / 'itemnames.tsv').write_text(item_names(base, unique_rows, set_rows, annot_strings.excel(romfs, 'runes.txt'), item_strings),
                                          encoding='utf-8', newline='\n')
    _stage_ui(read, target)
    return len(icons), len(set(icons.values()))


def stage(romfs, overlay):
    """Read levels.txt and the level strings from the player's RomFS and write deck/ into the overlay."""
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'ios' / 'tools'))
    import annot_strings, touch_layout
    levels = annot_strings.excel(romfs, 'levels.txt')
    _, text = touch_layout.read_layout(romfs, None, suffix='/lng/strings/levels.json')
    target = Path(overlay) / 'deck'
    target.mkdir(parents=True, exist_ok=True)
    table = level_table(levels, json.loads(text))
    (target / 'levels.tsv').write_text(table, encoding='utf-8', newline='\n')
    keys, pictures = _stage_icons(romfs, target)
    return {'deck_levels': table.count('\n') - 1, 'deck_item_keys': keys, 'deck_item_pictures': pictures}


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit('usage: stage_deck_assets.py <RomFS> <overlay directory>')
    print(json.dumps(stage(sys.argv[1], sys.argv[2])))

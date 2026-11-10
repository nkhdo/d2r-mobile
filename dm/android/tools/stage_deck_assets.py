#!/usr/bin/env python3
"""Assets for the bottom-screen Deck (dual-screen handhelds such as the AYN Thor), made from the player's own game files.

Writes into the import pack's overlay folder, before its manifest is written:
  deck/levels.tsv   area names per game language: header "id" + the 13 language columns in the game's
                    language-number order (dm_i18n.h g_dm_lang), one row per levels.txt Id.
  deck/items/*.png  inventory pictures from the player's own item sprites, at the game's native 49 px per cell
  deck/items.tsv    key -> picture file: key = item code ("hax"), "u<UniqueItems *ID>" or "s<SetItems *ID>"
                    (the row number the host reads at item data +0x34)
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

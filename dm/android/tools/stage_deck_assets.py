#!/usr/bin/env python3
"""Assets for the bottom-screen Deck (dual-screen handhelds such as the AYN Thor), made from the player's own game files.

Writes into the import pack's overlay folder, before its manifest is written:
  deck/levels.tsv   area names per game language: header "id" + the 13 language columns in the game's
                    language-number order (dm_i18n.h g_dm_lang), one row per levels.txt Id.
These files hold game text, so they only ever live in the player's own import pack.
"""
import json
import os
import re
import sys
from pathlib import Path

# Column i = game language number i (dm_i18n.h: 0 enUS 1 deDE 2 esES ... 12 ptBR)
LANGS = ('enUS', 'deDE', 'esES', 'frFR', 'itIT', 'koKR', 'plPL', 'ruRU', 'zhCN', 'zhTW', 'esMX', 'jaJP', 'ptBR')
GENDER_TAG = re.compile(r'^(?:\[[fm][sp]\])+')   # Spanish grammatical-gender marker at the start of a name


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
    return {'deck_levels': table.count('\n') - 1}


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit('usage: stage_deck_assets.py <RomFS> <overlay directory>')
    print(json.dumps(stage(sys.argv[1], sys.argv[2])))

"""Bottom-screen Deck assets made from the player's game files, checked with synthetic tiny inputs (no game content).
"""
import importlib.util
from pathlib import Path
import unittest

TOOLS = Path(__file__).resolve().parents[1] / 'tools'
spec = importlib.util.spec_from_file_location('stage_deck_assets', TOOLS / 'stage_deck_assets.py')
deck = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deck)


def rows(text):
    return [line.split('\t') for line in text.rstrip('\n').split('\n')]


class LevelNames(unittest.TestCase):
    LEVELS = [
        {'Id': '1', 'Name': 'Act 1 - Town', 'LevelName': 'Rogue Encampment'},
        {'Id': '2', 'Name': 'Act 1 - Wilderness 1', 'LevelName': 'Blood Moor'},
        {'Id': '', 'Name': 'Expansion', 'LevelName': ''},           # separator row, no Id
        {'Id': '3', 'Name': 'Act 1 - Wilderness 2', 'LevelName': 'NoSuchKey'},
    ]
    STRINGS = [
        {'id': 1, 'Key': 'Rogue Encampment', 'enUS': 'Rogue Encampment', 'esES': '[ms]Campamento rogue', 'zhCN': '罗格营地'},
        {'id': 2, 'Key': 'Blood Moor', 'enUS': 'Blood\tMoor', 'esMX': '[fs]Páramo\nsangriento'},
    ]

    def test_header_lists_the_game_language_numbers_in_order(self):
        head = rows(deck.level_table(self.LEVELS, self.STRINGS))[0]
        self.assertEqual(head, ['id', 'enUS', 'deDE', 'esES', 'frFR', 'itIT', 'koKR', 'plPL', 'ruRU', 'zhCN', 'zhTW', 'esMX', 'jaJP', 'ptBR'])

    def test_one_row_per_level_with_names_per_language(self):
        body = rows(deck.level_table(self.LEVELS, self.STRINGS))[1:]
        self.assertEqual([r[0] for r in body], ['1', '2', '3'])
        town = body[0]
        self.assertEqual(town[1], 'Rogue Encampment')
        self.assertEqual(town[3], 'Campamento rogue')      # Spanish gender tag dropped
        self.assertEqual(town[9], '罗格营地')
        self.assertEqual(town[2], '')                      # missing language left empty (the Deck falls back to enUS)

    def test_tabs_and_newlines_inside_names_become_spaces(self):
        moor = rows(deck.level_table(self.LEVELS, self.STRINGS))[2]
        self.assertEqual(moor[1], 'Blood Moor')
        self.assertEqual(moor[11], 'Páramo sangriento')

    def test_level_without_a_string_falls_back_to_its_key(self):
        third = rows(deck.level_table(self.LEVELS, self.STRINGS))[3]
        self.assertEqual(third[1], 'NoSuchKey')
        self.assertEqual(third[2:], [''] * 12)


if __name__ == '__main__':
    unittest.main()

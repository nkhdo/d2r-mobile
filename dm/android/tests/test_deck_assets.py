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


class ItemIcons(unittest.TestCase):
    BASE = {'weapon': [{'code': 'hax'}, {'code': '9ha'}], 'armor': [{'code': 'cap'}], 'misc': [{'code': 'rin'}, {'code': 'zzz'}]}
    ITEMS = [{'hax': {'asset': 'axe/hand_axe'}}, {'9ha': {'asset': 'axe/hatchet'}}, {'cap': {'asset': 'helmet/cap'}},
             {'rin': {'asset': 'ring/ring'}}, {'zzz': {'asset': 'nothing/here'}}]
    UNIQUES = [{'the_gnasher': {'normal': 'axe/the_gnasher'}}, {'nagelring': {'normal': 'ring/nagelring_missing'}}]
    SETS = [{'civerbs_icon': {'normal': 'ring/ring'}}]
    UROWS = [{'index': 'The Gnasher', 'code': 'hax', '*ID': '0'}, {'index': 'Nagelring', 'code': 'rin', '*ID': '1'},
             {'index': 'Not An Item', 'code': 'qqq', '*ID': '2'}]
    SROWS = [{'index': "Civerb's Icon", 'item': 'rin', '*ID': '7'}]
    SPRITES = {'weapon/axe/hand_axe', 'weapon/axe/the_gnasher', 'armor/helmet/cap', 'misc/ring/ring'}

    def icons(self):
        return deck.icon_map(self.BASE, self.ITEMS, self.UNIQUES, self.SETS, self.UROWS, self.SROWS, self.SPRITES)

    def test_base_codes_map_to_their_category_sprite(self):
        m = self.icons()
        self.assertEqual(m['hax'], 'weapon/axe/hand_axe')
        self.assertEqual(m['cap'], 'armor/helmet/cap')
        self.assertEqual(m['rin'], 'misc/ring/ring')

    def test_codes_without_a_sprite_are_left_out(self):
        m = self.icons()
        self.assertNotIn('9ha', m)
        self.assertNotIn('zzz', m)

    def test_unique_and_set_rows_use_their_own_picture_or_the_base_one(self):
        m = self.icons()
        self.assertEqual(m['u0'], 'weapon/axe/the_gnasher')   # own picture, name key normalised like the game's json
        self.assertEqual(m['u1'], 'misc/ring/ring')           # own picture missing: base picture
        self.assertNotIn('u2', m)                             # base code unknown
        self.assertEqual(m['s7'], 'misc/ring/ring')           # apostrophe dropped from "Civerb's Icon"

    def test_file_names_are_flat_and_safe(self):
        self.assertEqual(deck.icon_file('weapon/axe/hand_axe'), 'weapon_axe_hand_axe.png')


class GoalNames(unittest.TestCase):
    # label number -> (strings file, key), as gen_map_goal_art.py LABELS
    LABELS = [('levels', "Tal Rasha's Tomb"), ('monsters', 'Bloodraven'), ('objects', 'NoSuchKey')]
    STRINGS = {
        'levels': [{'Key': "Tal Rasha's Tomb", 'enUS': "Tal Rasha's Tomb", 'deDE': '[ms]Tal Rashas Grab'}],
        'monsters': [{'Key': 'Bloodraven', 'enUS': 'Blood Raven', 'zhCN': '血鸦'}],
        'objects': [{'Key': 'Waypoint', 'enUS': 'Waypoint', 'frFR': 'Point de passage'}],
    }

    def table(self):
        return {r[0]: r[1:] for r in rows(deck.goal_table(self.LABELS, self.STRINGS))[1:]}

    def test_labels_by_number_in_every_language(self):
        t = self.table()
        self.assertEqual(t['0'][0], "Tal Rasha's Tomb")
        self.assertEqual(t['0'][1], 'Tal Rashas Grab')   # gender marker removed
        self.assertEqual(t['1'][deck.LANGS.index('zhCN')], '血鸦')

    def test_waypoint_is_goal_255(self):
        self.assertEqual(self.table()['255'][deck.LANGS.index('frFR')], 'Point de passage')

    def test_labels_without_a_string_are_left_out(self):
        self.assertNotIn('2', self.table())

    def test_labels_are_read_from_the_map_goal_tool(self):
        labels = deck.goal_labels()
        self.assertEqual(labels[0], ('levels', "Tal Rasha's Tomb"))
        self.assertEqual(labels[2], ('objects', 'StoneAlpha'))


if __name__ == '__main__':
    unittest.main()

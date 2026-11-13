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


class SkillNames(unittest.TestCase):
    SKILLS = [{'*Id': '0', 'skilldesc': 'attack'}, {'*Id': '54', 'skilldesc': 'teleport'}, {'*Id': '99', 'skilldesc': ''},
              {'*Id': '', 'skilldesc': 'x'}]
    DESC = [{'skilldesc': 'attack', 'str name': 'StrSkill0'}, {'skilldesc': 'teleport', 'str name': 'skillname54'}]
    STRINGS = [{'Key': 'StrSkill0', 'enUS': 'Attack', 'zhCN': '攻击'}, {'Key': 'skillname54', 'enUS': 'Teleport', 'deDE': '[ms]Teleportieren'}]

    def test_skill_names_by_id(self):
        t = {r[0]: r[1:] for r in rows(deck.skill_table(self.SKILLS, self.DESC, self.STRINGS))[1:]}
        self.assertEqual(t['0'][deck.LANGS.index('zhCN')], '攻击')
        self.assertEqual(t['54'][1], 'Teleportieren')
        self.assertNotIn('99', t)
        self.assertEqual(rows(deck.skill_table(self.SKILLS, self.DESC, self.STRINGS))[0], ['id'] + list(deck.LANGS))


class SkillRules(unittest.TestCase):
    SKILLS = [{'skill': 'Attack', '*Id': '0', 'charclass': '', 'reqlevel': '1', 'maxlvl': '1'},
              {'skill': 'Ice Bolt', '*Id': '39', 'charclass': 'sor', 'reqlevel': '1', 'maxlvl': '20'},
              {'skill': 'Ice Blast', '*Id': '45', 'charclass': 'sor', 'reqlevel': '6', 'maxlvl': '20', 'reqskill1': 'Ice Bolt'},
              {'skill': 'Blizzard', '*Id': '59', 'charclass': 'sor', 'reqlevel': '24', 'maxlvl': '20', 'reqskill1': 'Frost Nova', 'reqskill2': 'Glacial Spike'}]

    def test_class_skills_with_requirements(self):
        t = rows(deck.skill_rules(self.SKILLS))
        self.assertEqual(t[0], ['id', 'class', 'reqlevel', 'maxlvl', 'req1', 'req2', 'req3'])
        body = {r[0]: r[1:] for r in t[1:]}
        self.assertNotIn('0', body)                                  # no class: left out
        self.assertEqual(body['45'], ['sor', '6', '20', '39', '-1', '-1'])
        self.assertEqual(body['59'][4:], ['-1', '-1'])               # prerequisites not in the table: -1


class ItemNames(unittest.TestCase):
    BASE = {'armor': [{'code': 'cap', 'namestr': 'cap'}], 'misc': [{'code': 'r30', 'namestr': 'r30'}, {'code': 'r31', 'namestr': 'r31'}]}
    UROWS = [{'index': 'Harlequin Crest', '*ID': '248'}]
    SROWS = [{'index': "Tal Rasha's Guardianship", '*ID': '60'}]
    RUNES = [{'Name': 'Runeword130', 'complete': '1', 'Rune1': 'r07', 'Rune2': 'r10', 'Rune3': 'r09', 'Rune4': 'r11'},
             {'Name': 'Runeword999', 'complete': '0', 'Rune1': 'r01'}]
    STRINGS = [{'Key': 'cap', 'enUS': 'Cap', 'deDE': '[fs]Kappe'}, {'Key': 'r30', 'enUS': 'Ber Rune'}, {'Key': 'r31', 'enUS': 'Jah Rune'},
               {'Key': 'Harlequin Crest', 'enUS': 'Harlequin Crest', 'zhCN': '丑角冠'},
               {'Key': "Tal Rasha's Guardianship", 'enUS': "Tal Rasha's Guardianship"},
               {'Key': 'Runeword130', 'enUS': 'Spirit', 'frFR': 'Esprit'}, {'Key': 'Runeword999', 'enUS': 'Unfinished'}]

    def table(self):
        t = rows(deck.item_names(self.BASE, self.UROWS, self.SROWS, self.RUNES, self.STRINGS))
        return t[0], {r[0]: r[1:] for r in t[1:]}

    def test_keys_are_english_names_without_spacing_or_punctuation(self):
        head, t = self.table()
        self.assertEqual(head[:3], ['key', 'ref', 'runes'])
        self.assertEqual(deck.name_key("Tal Rasha's  Guardianship"), 'talrashasguardianship')
        self.assertIn('talrashasguardianship', t)

    def test_refs_point_at_the_picture_keys(self):
        _, t = self.table()
        self.assertEqual(t['harlequincrest'][0], 'u248')
        self.assertEqual(t['talrashasguardianship'][0], 's60')
        self.assertEqual(t['cap'][0], 'cap')
        self.assertEqual(t['berrune'][0], 'r30')

    def test_runewords_carry_their_runes_and_unfinished_ones_are_left_out(self):
        _, t = self.table()
        self.assertEqual(t['spirit'][:2], ['rw', 'r07 r10 r09 r11'])
        self.assertEqual(t['spirit'][2 + deck.LANGS.index('frFR')], 'Esprit')
        self.assertNotIn('unfinished', t)

    def test_names_per_language_without_gender_tags(self):
        _, t = self.table()
        self.assertEqual(t['cap'][2 + 1], 'Kappe')
        self.assertEqual(t['harlequincrest'][2 + deck.LANGS.index('zhCN')], '丑角冠')


class GoalNames(unittest.TestCase):
    # label number -> (strings file, key), as gen_map_goal_art.py LABELS
    LABELS = [('levels', "Tal Rasha's Tomb"), ('monsters', 'Bloodraven'), ('objects', 'NoSuchKey')]
    STRINGS = {
        'levels': [{'Key': "Tal Rasha's Tomb", 'enUS': "Tal Rasha's Tomb", 'deDE': '[ms]Tal Rashas Grab'}],
        'monsters': [{'Key': 'Bloodraven', 'enUS': 'Blood Raven', 'zhCN': '血鸦'}],
        'objects': [{'Key': 'Waypoint', 'enUS': 'Waypoint', 'frFR': 'Point de passage'}, {'Key': 'portal', 'enUS': 'Portal'}],
        'skills': [{'Key': 'skillan219', 'enUS': 'Town Portal', 'ruRU': 'Портал в город'}],
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

    def test_portals_are_goals_254_and_253(self):
        t = self.table()
        self.assertEqual(t['254'][deck.LANGS.index('ruRU')], 'Портал в город')
        self.assertEqual(t['253'][0], 'Portal')

    def test_labels_without_a_string_are_left_out(self):
        self.assertNotIn('2', self.table())

    def test_labels_are_read_from_the_map_goal_tool(self):
        labels = deck.goal_labels()
        self.assertEqual(labels[0], ('levels', "Tal Rasha's Tomb"))
        self.assertEqual(labels[2], ('objects', 'StoneAlpha'))


if __name__ == '__main__':
    unittest.main()

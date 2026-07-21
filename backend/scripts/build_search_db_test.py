#!/usr/bin/env python3
"""
Unit tests for build_search_db helper functions.

Run: python3 -m unittest build_search_db_test
     (from this directory, or add it to PYTHONPATH)

These tests don't touch the PBF — they exercise the pure helpers (transliteration,
kind classification, population parsing). The full pipeline gets integration-tested
via bin/build_region.sh --smoke.
"""

import os
import sys
import unittest

# Make build_search_db importable when running from any cwd.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Stub osmium before importing build_search_db, so we can test the pure helpers
# (transliterate, kind classification, population parsing) without the C++ binding.
# We need the stub to expose SimpleHandler so the PlaceHandler class definition at
# import time doesn't fail.
_osmium_stub = type(sys)("osmium")


class _SimpleHandlerStub:  # pragma: no cover - stub only
    def __init__(self, *args, **kwargs):
        pass

    def apply_file(self, *args, **kwargs):
        pass


_osmium_stub.SimpleHandler = _SimpleHandlerStub
sys.modules["osmium"] = _osmium_stub

import build_search_db as bs  # noqa: E402


class TestTransliterate(unittest.TestCase):
    def test_ascii_passthrough(self):
        self.assertEqual(bs.transliterate("Yalta"), "yalta")
        self.assertEqual(bs.transliterate("Saint-Etienne"), "saintetienne")

    def test_cyrillic_to_latin(self):
        self.assertEqual(bs.transliterate("Ялта"), "yalta")
        self.assertEqual(bs.transliterate("Киев"), "kiev")
        self.assertEqual(bs.transliterate("Москва"), "moskva")

    def test_diacritics_stripped(self):
        # unicode61 with remove_diacritics handles this on the FTS side too, but
        # we pre-normalise so the name_ascii column is genuinely ASCII.
        self.assertEqual(bs.transliterate("España"), "espana")
        self.assertEqual(bs.transliterate("München"), "munchen")
        self.assertEqual(bs.transliterate("Östersund"), "ostersund")

    def test_mixed_script(self):
        # "Ялта-Yalta" — the latin half passes through unchanged.
        self.assertEqual(bs.transliterate("Ялта-Yalta"), "yalta yalta".replace(" ", "")
                         if False else "yaltayalta")  # hyphen stripped

    def test_punctuation_dropped(self):
        self.assertEqual(bs.transliterate("St. Petersburg"), "st petersburg")


class _FakeTags(dict):
    """Stand-in for osmium's tag list — get() works the same."""


class TestKindFromTags(unittest.TestCase):
    def test_place_tags(self):
        self.assertEqual(bs.kind_from_tags(_FakeTags(place="city")), "city")
        self.assertEqual(bs.kind_from_tags(_FakeTags(place="town")), "town")
        self.assertEqual(bs.kind_from_tags(_FakeTags(place="village")), "village")
        self.assertEqual(bs.kind_from_tags(_FakeTags(place="hamlet")), "hamlet")
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(place="isolated_dwelling")), "hamlet",
        )

    def test_poi_tags(self):
        # Original curated set — backcountry / cycling POIs.
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(natural="spring")), "spring",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(natural="mountain_pass")), "mountain_pass",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(tourism="camp_site")), "campsite",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(tourism="viewpoint")), "viewpoint",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(shop="bicycle")), "bicycle_service",
        )
        # Daily on-tour needs — added in v2.
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(amenity="pharmacy")), "pharmacy",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(amenity="fuel")), "fuel",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(amenity="cafe")), "cafe",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(amenity="restaurant")), "restaurant",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(amenity="hospital")), "hospital",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(amenity="clinic")), "hospital",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(amenity="atm")), "atm",
        )
        # bank → atm alias (cash source either way).
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(amenity="bank")), "atm",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(amenity="drinking_water")), "water",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(amenity="bicycle_rental")), "bicycle_rental",
        )
        # Lodging.
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(tourism="hotel")), "hotel",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(tourism="hostel")), "hotel",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(tourism="guest_house")), "hotel",
        )
        # Resupply shopping.
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(shop="supermarket")), "shop",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(shop="convenience")), "shop",
        )
        self.assertEqual(
            bs.kind_from_tags(_FakeTags(shop="bakery")), "shop",
        )

    def test_irrelevant_returns_none(self):
        # Anything we deliberately don't surface as a POI should fall through.
        self.assertIsNone(bs.kind_from_tags(_FakeTags(place="country")))
        self.assertIsNone(bs.kind_from_tags(_FakeTags(highway="residential")))
        self.assertIsNone(bs.kind_from_tags(_FakeTags(building="yes")))
        self.assertIsNone(bs.kind_from_tags(_FakeTags(landuse="residential")))
        self.assertIsNone(bs.kind_from_tags(_FakeTags(amenity="parking")))
        self.assertIsNone(bs.kind_from_tags(_FakeTags()))


class TestPopulationParsing(unittest.TestCase):
    def test_plain_int(self):
        self.assertEqual(bs.population_from_tags(_FakeTags(population="1234")), 1234)

    def test_commas(self):
        self.assertEqual(bs.population_from_tags(_FakeTags(population="1,200,000")), 1200000)

    def test_approximate(self):
        self.assertEqual(bs.population_from_tags(_FakeTags(population="~500")), 500)

    def test_missing(self):
        self.assertEqual(bs.population_from_tags(_FakeTags()), 0)

    def test_garbage(self):
        # Non-numeric content is silently dropped to zero rather than crashing the build.
        self.assertEqual(bs.population_from_tags(_FakeTags(population="many")), 0)


class TestParseBounds(unittest.TestCase):
    def test_valid(self):
        self.assertEqual(bs.parse_bounds("47.5,22.0,50.0,26.5"),
                         (47.5, 22.0, 50.0, 26.5))

    def test_negative(self):
        self.assertEqual(bs.parse_bounds("-50.0,-70.0,-10.0,-30.0"),
                         (-50.0, -70.0, -10.0, -30.0))

    def test_none(self):
        self.assertIsNone(bs.parse_bounds(None))
        self.assertIsNone(bs.parse_bounds(""))

    def test_wrong_arity(self):
        with self.assertRaises(ValueError):
            bs.parse_bounds("1,2,3")

    def test_min_gt_max(self):
        with self.assertRaises(ValueError):
            bs.parse_bounds("50.0,22.0,40.0,26.5")


if __name__ == "__main__":
    unittest.main()

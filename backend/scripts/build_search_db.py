#!/usr/bin/env python3
"""
build_search_db.py — produce search.db from an OSM PBF.

The output is a single SQLite file with one FTS5 virtual table. The Android client
opens it read-only via SQLiteDatabase.openDatabase() and issues MATCH queries — no
schema management happens on-device.

Schema (must stay in lockstep with the client's SearchEngine):
    CREATE VIRTUAL TABLE places USING fts5(
        name, name_ascii, kind, lat UNINDEXED, lon UNINDEXED,
        population UNINDEXED,
        tokenize = "unicode61 remove_diacritics 2"
    );

Filtering: we keep what a bicycle tourist will look up by name — settlements, natural
features (springs, passes), and a curated set of tourism POIs. Each row carries its OSM
kind so the client can filter / icon.

Why a Python script rather than tilemaker + SQL: PBF parsing in Python is one-liner
territory (osmium), and the filter logic is the kind of business rule that's painful to
express in SQL but trivial in Python. The result is ~150 lines, debuggable, and easy
to unit-test.

Requires:
    pip install osmium
"""

from __future__ import annotations

import argparse
import math
import re
import sqlite3
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Optional

try:
    import osmium  # type: ignore
except ImportError:
    sys.stderr.write(
        "osmium not installed. Run: pip install osmium\n"
    )
    raise


# ─── data ──────────────────────────────────────────────────────────────────────

@dataclass
class Place:
    name: str
    name_ascii: str
    kind: str
    lat: float
    lon: float
    population: int


# Map OSM tags → our kind enum (matches client's PlaceKind).
PLACE_KINDS = {
    "city": "city",
    "town": "town",
    "village": "village",
    "hamlet": "hamlet",
    "isolated_dwelling": "hamlet",
}
POI_KINDS = {
    # Natural / tourism (the original curated set — useful in the backcountry).
    ("natural", "spring"): "spring",
    ("natural", "mountain_pass"): "mountain_pass",
    ("tourism", "camp_site"): "campsite",
    ("tourism", "viewpoint"): "viewpoint",
    ("tourism", "alpine_hut"): "campsite",
    ("tourism", "wilderness_hut"): "campsite",
    # Bicycle-specific.
    ("amenity", "bicycle_repair_station"): "bicycle_service",
    ("shop", "bicycle"): "bicycle_service",
    ("amenity", "bicycle_rental"): "bicycle_rental",
    # Daily on-tour needs — pharmacies, food, fuel, lodging.
    ("amenity", "pharmacy"): "pharmacy",
    ("amenity", "fuel"): "fuel",
    ("amenity", "cafe"): "cafe",
    ("amenity", "restaurant"): "restaurant",
    ("amenity", "fast_food"): "restaurant",
    ("amenity", "hospital"): "hospital",
    ("amenity", "clinic"): "hospital",
    ("amenity", "doctors"): "hospital",
    ("amenity", "atm"): "atm",
    ("amenity", "bank"): "atm",
    ("amenity", "drinking_water"): "water",
    # Lodging.
    ("tourism", "hotel"): "hotel",
    ("tourism", "hostel"): "hotel",
    ("tourism", "motel"): "hotel",
    ("tourism", "guest_house"): "hotel",
    # Food shopping (supermarkets & convenience — where riders resupply).
    ("shop", "supermarket"): "shop",
    ("shop", "convenience"): "shop",
    ("shop", "bakery"): "shop",
    ("shop", "kiosk"): "shop",
}


# ─── helpers ───────────────────────────────────────────────────────────────────

# Cyrillic → Latin transliteration (a subset; good enough for search-fuzzy matching).
# The FTS5 unicode61 tokenizer handles diacritics, so we only transliterate scripts
# outside its scope — primarily Cyrillic, which riders will type in either layout.
_CYRILLIC = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e",
    "ж": "zh", "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m",
    "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ф": "f", "х": "h", "ц": "ts", "ч": "ch", "ш": "sh", "щ": "sch",
    "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya",
}


def transliterate(text: str) -> str:
    """Transliterate to ASCII Latin for layout-agnostic search.

    The client's SearchEngine queries both `name` (original script) and `name_ascii`
    (this), so a rider typing "Yalta" finds "Ялта" and vice versa.
    """
    out = []
    for ch in text.lower():
        if ch in _CYRILLIC:
            out.append(_CYRILLIC[ch])
        else:
            # Normalize decomposed form + drop combining marks → ASCII-friendly.
            decomposed = unicodedata.normalize("NFKD", ch)
            out.append("".join(c for c in decomposed if not unicodedata.combining(c)))
    ascii_str = "".join(out)
    # Final pass: drop anything that's not ASCII letter/digit/space.
    return re.sub(r"[^a-z0-9 ]", "", ascii_str)


def kind_from_tags(tags) -> Optional[str]:
    """Extract our `kind` from an OSM object's tags, or None to skip."""
    place = tags.get("place")
    if place is not None and place in PLACE_KINDS:
        return PLACE_KINDS[place]
    for (ns, key), kind in POI_KINDS.items():
        if tags.get(ns) == key:
            return kind
    return None


def population_from_tags(tags) -> int:
    raw = tags.get("population")
    if raw is None:
        return 0
    # OSM population values are messy ("123", "~500", "1,200"); be lenient.
    digits = re.sub(r"[^0-9]", "", raw)
    return int(digits) if digits else 0


# ─── PBF scan ──────────────────────────────────────────────────────────────────

class PlaceHandler(osmium.SimpleHandler):
    """Walks the PBF and collects Places that match our criteria."""

    def __init__(self, bounds: Optional[tuple[float, float, float, float]]):
        super().__init__()
        self.bounds = bounds  # (min_lat, min_lon, max_lat, max_lon) or None
        self.places: list[Place] = []

    def _in_bounds(self, lat: float, lon: float) -> bool:
        if self.bounds is None:
            return True
        min_lat, min_lon, max_lat, max_lon = self.bounds
        return min_lat <= lat <= max_lat and min_lon <= lon <= max_lon

    def node(self, n) -> None:
        tags = {t.k: t.v for t in n.tags}
        name = tags.get("name")
        if not name:
            return
        kind = kind_from_tags(tags)
        if kind is None:
            return
        if not self._in_bounds(n.location.lat, n.location.lon):
            return
        self._emit(name, kind, n.location.lat, n.location.lon, tags)

    def way(self, w) -> None:
        """Capture polygon POIs (hotels, hospitals, shops as buildings/areas).

        Many OSM POIs are mapped as closed ways rather than nodes — a shop polygon, a
        hospital building, a tourism=hotel area. Without this handler we'd miss them.
        We use the way's centroid (average of node coords) as the place coordinate,
        which is close enough for search-and-fly-to use.

        osmium with `locations=True` (set in main) loads node coordinates for ways,
        so w.nodes carries lat/lon without a separate join pass.
        """
        tags = {t.k: t.v for t in w.tags}
        name = tags.get("name")
        if not name:
            return
        kind = kind_from_tags(tags)
        if kind is None:
            return
        nodes = list(w.nodes)
        if len(nodes) < 3:
            # A 2-node way is a line (road/waterway) — not a POI polygon; skip.
            return
        # Centroid = mean of node coordinates. Not the true geometric centroid of the
        # polygon, but for small features (a shop footprint) the difference is sub-meter.
        try:
            lats = [nd.location.lat for nd in nodes]
            lons = [nd.location.lon for nd in nodes]
        except AttributeError:
            # Some ways may have unresolved node refs despite locations=True; skip them.
            return
        if not lats:
            return
        lat = sum(lats) / len(lats)
        lon = sum(lons) / len(lons)
        if not self._in_bounds(lat, lon):
            return
        self._emit(name, kind, lat, lon, tags)

    def _emit(
        self,
        name: str,
        kind: str,
        lat: float,
        lon: float,
        tags: dict,
    ) -> None:
        self.places.append(
            Place(
                name=name,
                name_ascii=transliterate(name),
                kind=kind,
                lat=lat,
                lon=lon,
                population=population_from_tags(tags),
            )
        )


# ─── schema + writer ───────────────────────────────────────────────────────────

SCHEMA = """
-- Plain SQLite table (NOT FTS5). Works on every Android device, including AVD
-- images and OEM ROMs that ship SQLite without the FTS5 extension compiled in.
-- The client's SearchEngine uses LIKE with a leading wildcard; the indexes below
-- make those queries fast enough for typical region sizes (tens of thousands of rows).
--
-- To recover the FTS5 experience on devices that support it, a future pipeline mode
-- could emit a `places_fts` virtual table alongside this one and let the client pick.
CREATE TABLE places (
    rowid INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    name_ascii TEXT NOT NULL,
    kind TEXT NOT NULL,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    population INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_places_name ON places(name);
CREATE INDEX idx_places_name_ascii ON places(name_ascii);
"""


SCHEMA_FTS5 = """
-- Alternative schema using FTS5 (better tokenisation, bm25 ranking). Use only when
-- the target devices are known to ship SQLite with ENABLE_FTS5 — otherwise the table
-- itself becomes unreadable on those devices. Kept here as the path forward.
CREATE VIRTUAL TABLE places USING fts5(
    name, name_ascii, kind, lat UNINDEXED, lon UNINDEXED,
    population UNINDEXED,
    tokenize = "unicode61 remove_diacritics 2"
);
"""


def write_sqlite(places: Iterable[Place], out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if out_path.exists():
        out_path.unlink()
    conn = sqlite3.connect(str(out_path))
    try:
        conn.executescript(SCHEMA)
        conn.executemany(
            "INSERT INTO places (name, name_ascii, kind, lat, lon, population) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            [
                (p.name, p.name_ascii, p.kind, p.lat, p.lon, p.population)
                for p in places
            ],
        )
        conn.commit()
    finally:
        conn.close()


# ─── CLI ───────────────────────────────────────────────────────────────────────

def parse_bounds(s: Optional[str]) -> Optional[tuple[float, float, float, float]]:
    if not s:
        return None
    parts = s.split(",")
    if len(parts) != 4:
        raise ValueError(f"bounds must be minLat,minLon,maxLat,maxLon; got: {s}")
    min_lat, min_lon, max_lat, max_lon = (float(p) for p in parts)
    if min_lat > max_lat or min_lon > max_lon:
        raise ValueError("bounds: min > max")
    return min_lat, min_lon, max_lat, max_lon


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description="Build search.db from an OSM PBF.")
    ap.add_argument("--pbf", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--bounds", type=str, default=None,
                    help="minLat,minLon,maxLat,maxLon (optional; filters to bbox)")
    args = ap.parse_args(argv)

    if not args.pbf.is_file():
        sys.stderr.write(f"PBF not found: {args.pbf}\n")
        return 3

    bounds = parse_bounds(args.bounds)

    handler = PlaceHandler(bounds)
    print(f"[build_search_db] scanning {args.pbf}…", file=sys.stderr)
    handler.apply_file(str(args.pbf), locations=True)
    print(f"[build_search_db] collected {len(handler.places)} places", file=sys.stderr)

    write_sqlite(handler.places, args.out)
    size = args.out.stat().st_size
    print(f"[build_search_db] wrote {args.out} ({size} bytes)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

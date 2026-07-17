# 2circle backend — region package pipeline

Turns raw OpenStreetMap data into the offline bundles the Android app consumes.

For each region the pipeline produces a single `.zip` package containing four artefacts
that map 1:1 to what the client expects (see `:feature:regions` and the design doc):

```
region-<id>-v<version>.zip
├── tiles.mbtiles     ← vector tiles, produced by tilemaker
├── routing.rd5       ← BRouter routing graph, produced by BRouter's prepare subcommand
├── search.db         ← FTS5 place index, produced by the Python script in scripts/
└── manifest.json     ← per-package metadata (size, sha256, version, bounds)
```

A separate top-level `manifest.json` (an index of all available packages) lives at the
static-host root and is what `RegionCatalog.fetch()` reads. This pipeline emits both the
per-region entry and a roll-up you can append to the published index.

## Architecture

```
Geofabrik PBF
      │
      ├──> tilemaker ────────> tiles.mbtiles
      │      (with Lua layer that keeps surface/highway tags,
      │       drops buildings and house numbers)
      │
      ├──> BRouter prepare ──> routing.rd5
      │      (built once per BRouter profile — trekking, fastbike, MTB)
      │
      └──> scripts/build_search_db.py ──> search.db
             (FTS5 virtual table; place=*, POI, named ways)
```

Why these three tools specifically (rather than rolling our own):

- **tilemaker** is OSS and battle-tested for MBTiles-from-PBF; its Lua hooks give us
  exact control over which OSM tags survive into the vector tile attributes — that's
  what powers the road-surface colour scheme on the client.
- **BRouter** is the de-facto standard for offline bike routing; its `rd5` segments are
  tiny (tens of MB per region) and its weight model encodes the honest-ETA physics.
- **FTS5** is built into SQLite; the client opens the file read-only with zero
  additional dependencies, and `unicode61 remove_diacritics 2` gives us
  keyboard-layout-agnostic search out of the box.

## Prerequisites (host machine, not the phone)

These must be on `PATH`:

- `tilemaker` ≥ 2.3.0  — https://github.com/systemed/tilemaker
- `java` ≥ 17          — for BRouter
- `python3` ≥ 3.10     — for the search-DB builder
- `sqlite3` ≥ 3.34     — for FTS5 (bundled with most Python installs)
- `zip`, `sha256sum`, `jq`, `curl`

`scripts/check_prerequisites.sh` verifies all of these are present.

## Quickstart

```bash
# Build one region from a Geofabrik PBF (local file):
./bin/build_region.sh \
    --id carpathians-ua \
    --name "Carpathians (Ukraine)" \
    --pbf /data/geofabrik/ukraine-latest.osm.pbf \
    --bounds 47.5,22.0,50.0,26.5 \
    --profile touring

# Output lands in regions/:
ls regions/carpathians-ua/
#   tiles.mbtiles  routing.rd5  search.db  manifest.json
#   carpathians-ua-v1.zip

# Roll up into the published index:
./bin/publish_index.sh --output regions/manifest.json
```

The same script runs in CI; see `.github/workflows/build-regions.yml` (added in a later
iteration) for the per-region job matrix.

## Why the pipeline is bash + Python, not Kotlin/Go/Rust

- `tilemaker` and `BRouter` are CLI tools — the pipeline's job is to orchestrate them,
  not to re-implement them. Bash is the right level of abstraction for that.
- The search-DB builder is Python because its inputs (`.osm.pbf` parsing, FTS5 via
  `sqlite3`) are Python's bread and butter, and the script is small enough (~150 lines)
  that a build tool would be overkill.
- If a step grows complex enough to warrant its own binary, it graduates to `bin/` as a
  compiled tool — but we don't pre-pay that cost.

## Tests

The Python builder has unit tests for tag filtering and transliteration
(`scripts/build_search_db_test.py`). The bash scripts themselves are integration-tested
by `bin/build_region.sh --smoke` which builds a tiny synthetic PBF end-to-end.

## Stability contract

The client and the pipeline agree on:

- **Schema version** of the manifest (currently `1`) — bumped only on incompatible change.
- **FTS5 schema** in `search.db` (see `scripts/build_search_db.py`, `CREATE VIRTUAL TABLE`).
- **MBTiles layer names** — must match what `MapStyleProvider` in `:feature:map` reads:
  `water`, `landcover`, `transportation`, `boundary`, `place`. The Lua script enforces this.
- **rd5 segment format** — whatever BRouter emits; we don't transform it.

Any breaking change to the above requires a `schema` bump in the manifest and a
client-side migration.

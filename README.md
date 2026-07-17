# 2circle

**Offline-first bicycle-touring app for Android.** Plan routes by place name, record
rides with crash-safe GPS logging, and export to GPX — all without a network once you've
downloaded a region.

> ⚠️ Work in progress. v1 milestone reached: all 8 client modules shipped, 138 unit +
> 7 instrumentation tests green, backend pipeline drafted. See `docs/superpowers/specs/`
> for the design doc.

## Why

Existing bike apps each do one thing well — Strava for stats, Komoot for routing, OsmAnd
for offline detail — but none combine **deep road-surface detail** (gravel vs asphalt vs
sand), **offline routing**, and **crash-proof recording** in one place. 2circle is built
around the kind of multi-day touring where the phone has no signal for hours and losing
the day's track to a service kill is unacceptable.

## Features (v1)

- **Map** — MapLibre Native with offline vector tiles; roads coloured by OSM `surface`
  tag (asphalt green, gravel orange, sand red) so you read surface at a glance
- **Offline search** — FTS5 by place name; type "Yalta" or "Ялта" interchangeably;
  results ranked by relevance × population × proximity
- **Route Builder** — compose a route from named waypoints (start / via / end), pick a
  profile (Touring / Road / MTB), get honest ETA from BRouter's physics-based cost model
- **Tracking** — Foreground Service with adaptive GPS polling (3 s moving, 30 s
  stationary), crash-safe pipeline via Room WAL + monotonic-seq watermark, live HUD
- **Tracks** — GPX 1.1 export with XXE hardening; import from any source; Canvas-drawn
  track preview
- **Regions** — download / verify (SHA-256) / install offline bundles from a static-host
  catalog

## Architecture

Clean Architecture + MVI, Gradle multi-module (12 modules):

```
:app                            single-activity shell, nav, permission UX
├── :core:common                pure Kotlin — Outcome/Failure, geo, formatting
├── :core:domain                pure Kotlin — domain models, UseCase contracts
├── :core:data                  Room (WAL), repositories, OkHttp
├── :core:designsystem          Compose theme, primitives
├── :feature:map                MapLibre wrapper + offline .mbtiles style
├── :feature:search             FTS5 client, ranking, screen
├── :feature:routing            Route Builder, BRouter cloud-fallback
├── :feature:tracking           Foreground Service, crash-safe pipeline
├── :feature:tracks             GPX import/export, history
└── :feature:regions            region download/install
```

The Android SDK never leaks past `:feature:*` and `:core:data`; `:core:common` and
`:core:domain` are pure Kotlin/JVM and run in plain unit tests on the JVM.

## Stack

- **Kotlin 2.1**, Coroutines/Flow, Jetpack Compose (Material 3)
- **Hilt 2.56** (DI), **Room 2.7** (storage, KSP), **Retrofit + OkHttp** (network)
- **MapLibre Native Android 11.11** (vector tile rendering)
- **FusedLocationProvider** (GPS), **Foreground Service** (background tracking)
- minSdk 26 / targetSdk 35

## Build & run

Prerequisites: JDK 17+ (toolchain 21 fine), Android SDK with platforms 34+35, build-tools
35.0.0, Kotlin/Gradle plugins fetched automatically.

```bash
# Debug APK
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# All unit tests
./gradlew test

# Instrumentation tests (needs a device or emulator)
./gradlew :app:connectedDebugAndroidTest
```

Install the APK via adb or by copying to the device. On first launch you'll see the
"No offline region downloaded yet" prompt — to actually use the map you need a region
package (see `backend/`).

## Backend pipeline

`backend/` produces the offline bundles the app downloads via `:feature:regions`. Each
region is a `.zip` of `tiles.mbtiles` + `routing.rd5` + `search.db` + `manifest.json`,
built from a Geofabrik PBF by tilemaker + BRouter + a Python FTS5 indexer. See
[`backend/README.md`](backend/README.md) for the full pipeline.

## Roadmap

Done for v1 — listed above.

In progress / next:
- **BRouter-jar offline engine** — replace the cloud-routing fallback with an in-process
  router driven by `routing.rd5`. Architecture ready (`OfflineRoutingEngine` stub).
- **CI for backend** — GitHub Actions matrix that rebuilds regions on OSM updates.
- **Track overlay on the main map** — currently only the Canvas preview in track detail.
- **More instrumentation coverage** — RegionDownloader happy path, search→routing flow.

## License

TBD. Source-available for now; license decision before any public release.

## Acknowledgements

- **OpenStreetMap** — © OSM contributors; all map data originates here
- **MapLibre Native** — the open fork of Mapbox GL that powers the renderer
- **BRouter** — offline routing graph + profiles
- **tilemaker** — PBF → vector tiles
- **Copernicus DEM GLO-30** — elevation data

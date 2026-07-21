package com.twocircle.bike.feature.map.style

import java.util.Locale

/**
 * Builds the MapLibre style JSON for an offline region.
 *
 * The style is generated dynamically rather than shipped as a fixed asset because the
 * vector source URL must point at the per-region `.mbtiles` file on disk, and we want
 * the road-colouring scheme to live in code (testable, version-controlled) rather than
 * in a JSON blob that's easy to drift from the legend UI.
 *
 * Layers (in draw order, bottom-to-top):
 *   1. background (solid dark)
 *   2. water (fill)
 *   3. landuse / green areas (subtle fill)
 *   4. transportation line (ROADS — the centrepiece, coloured by surface)
 *   5. transportation name (labels) — added in a later step
 *   6. place-label (settlement names — locale-aware)
 *
 * The transportation layer uses a MapLibre `match` expression on the `surface` property
 * produced by tilemaker's Lua layer. Unknown surfaces fall back to [MapColors.UNKNOWN].
 *
 * The place-label layer uses a `coalesce` expression to pick the localised name:
 * the locale-specific OSM name (`name:ru`, `name:en`, …), then the English fallback
 * (`name:en`), then the default `name`. The backend pipeline must write the relevant
 * `name:<lang>` attributes into the vector tiles — see `backend/lua/2circle-process.lua`.
 *
 * Source-layer names ("water", "transportation", "landcover") follow the OpenMapTiles
 * vector schema — what tilemaker produces by default. If a future pipeline uses a
 * different schema, only these constants change.
 */
object MapStyleProvider {

    /** Vector source-layer names — match the OpenMapTiles schema that tilemaker emits. */
    object Layers {
        const val WATER = "water"
        const val LANDCOVER = "landcover"
        const val TRANSPORTATION = "transportation"
        const val TRANSPORTATION_NAME = "transportation_name"
        const val BOUNDARY = "boundary"
        const val PLACE = "place"
    }

    /**
     * Produce a complete style JSON for the given [mbtilesPath] (absolute filesystem path).
     *
     * The path is interpolated into the `mbtiles://` scheme exactly as MapLibre expects:
     * three slashes, then the absolute path. File existence is the caller's responsibility.
     *
     * [locale] drives the name-field priority in the place-label layer. A null locale
     * falls back to the bare `{name}` field (legacy behaviour); a real locale emits
     * `["coalesce", ["get","name:<lang>"], ["get","name:en"], ["get","name"]]`, so a
     * Russian user sees Russian labels when the tile has `name:ru`, otherwise English,
     * otherwise the default OSM name.
     */
    fun buildStyleJson(
        mbtilesPath: String,
        sourceName: String = "region",
        locale: Locale? = null,
        isDark: Boolean = true,
    ): String = buildString {
        val bg = if (isDark) DarkMapColors.BACKGROUND else LightMapColors.BACKGROUND
        val water = if (isDark) DarkMapColors.WATER else LightMapColors.WATER
        val land = if (isDark) DarkMapColors.LAND else LightMapColors.LAND
        val textColor = if (isDark) DarkMapColors.TEXT_COLOR else LightMapColors.TEXT_COLOR
        val textHalo = if (isDark) DarkMapColors.TEXT_HALO else LightMapColors.TEXT_HALO

        append("{")
        append("\"version\": 8,")
        append("\"name\": \"2circle-region\",")
        append("\"sources\": {")
        append("\"").append(sourceName).append("\": {")
        append("\"type\": \"vector\",")
        append("\"url\": \"mbtiles://").append(escapePath(mbtilesPath)).append("\"")
        append("}")
        append("},")
        append("\"glyphs\": \"asset://fonts/{fontstack}/{range}.pbf\",")

        append("\"layers\": [")
        appendBackgroundLayer(bg)
        append(",")
        appendWaterLayer(sourceName, water)
        append(",")
        appendLandcoverLayer(sourceName, land)
        append(",")
        appendBoundaryLayer(sourceName)
        append(",")
        appendRoadLayer(sourceName)
        append(",")
        appendTransportationNameLayer(sourceName, locale, textColor, textHalo)
        append(",")
        appendPlaceLayer(sourceName, locale, textColor, textHalo)
        append("]")

        append("}")
    }

    /**
     * The road layer — coloured by OSM `surface` tag via a `match` expression.
     *
     * `line-width` scales with zoom so roads are visible at overview zoom but don't
     * dominate at street level. `line-opacity` is slightly under 1 so overlapping
     * tunnel/bridge segments blend rather than flicker.
     */
    private fun StringBuilder.appendRoadLayer(source: String) {
        append("{")
        append("\"id\": \"road\",")
        append("\"type\": \"line\",")
        append("\"source\": \"").append(source).append("\",")
        append("\"source-layer\": \"").append(Layers.TRANSPORTATION).append("\",")
        append("\"filter\": [\"in\", \"\$type\", \"LineString\"],")
        append("\"layout\": {")
        append("\"line-cap\": \"round\",")
        append("\"line-join\": \"round\"")
        append("},")
        append("\"paint\": {")
        // match expression: get the surface property, fall through colour cases, default grey.
        append("\"line-color\": [")
        append("\"match\",")
        append("[\"get\", \"surface\"],")
        appendSurfaceCase("asphalt", MapColors.ASPHALT)
        appendSurfaceCase("concrete", MapColors.ASPHALT)
        appendSurfaceCase("chipseal", MapColors.ASPHALT)
        appendSurfaceCase("paving_stones", MapColors.ASPHALT)
        appendSurfaceCase("sett", MapColors.ASPHALT)
        appendSurfaceCase("compacted", MapColors.COMPACTED)
        appendSurfaceCase("gravel", MapColors.COMPACTED)
        appendSurfaceCase("fine_gravel", MapColors.COMPACTED)
        appendSurfaceCase("dirt", MapColors.DIRT)
        appendSurfaceCase("ground", MapColors.DIRT)
        appendSurfaceCase("earth", MapColors.DIRT)
        appendSurfaceCase("sand", MapColors.SAND)
        appendSurfaceCase("grass", MapColors.GRASS)
        appendSurfaceCase("rock", MapColors.ROCK)
        append("\"").append(MapColors.UNKNOWN).append("\"") // default
        append("],")
        // line-width scales with zoom: visible at overview (z8) and properly thick at street level.
        // Previous values (0.5 at z10) drew sub-pixel lines that were effectively invisible.
        append("\"line-width\": {")
        append("\"base\": 1.2,")
        append("\"stops\": [[4, 0.3], [8, 0.8], [10, 1.5], [13, 2.5], [15, 4.0], [17, 5.5], [20, 8.0]]")
        append("},")
        append("\"line-opacity\": 0.95")
        append("}")
        append("}")
    }

    private fun StringBuilder.appendSurfaceCase(tag: String, color: String) {
        append("\"").append(tag).append("\", \"").append(color).append("\",")
    }

    private fun StringBuilder.appendBackgroundLayer(bgColor: String) {
        append("{")
        append("\"id\": \"background\",")
        append("\"type\": \"background\",")
        append("\"paint\": { \"background-color\": \"").append(bgColor).append("\" }")
        append("}")
    }

    private fun StringBuilder.appendWaterLayer(source: String, waterColor: String) {
        append("{")
        append("\"id\": \"water\",")
        append("\"type\": \"fill\",")
        append("\"source\": \"").append(source).append("\",")
        append("\"source-layer\": \"").append(Layers.WATER).append("\",")
        append("\"paint\": { \"fill-color\": \"").append(waterColor).append("\" }")
        append("}")
    }

    private fun StringBuilder.appendLandcoverLayer(source: String, landColor: String) {
        append("{")
        append("\"id\": \"landcover\",")
        append("\"type\": \"fill\",")
        append("\"source\": \"").append(source).append("\",")
        append("\"source-layer\": \"").append(Layers.LANDCOVER).append("\",")
        append("\"paint\": { \"fill-color\": \"").append(landColor).append("\", \"fill-opacity\": 0.6 }")
        append("}")
    }

    private fun StringBuilder.appendBoundaryLayer(source: String) {
        append("{")
        append("\"id\": \"boundary\",")
        append("\"type\": \"line\",")
        append("\"source\": \"").append(source).append("\",")
        append("\"source-layer\": \"").append(Layers.BOUNDARY).append("\",")
        append("\"paint\": { \"line-color\": \"#3A3F45\", \"line-width\": 0.8, \"line-opacity\": 0.7 }")
        append("}")
    }

    private fun StringBuilder.appendTransportationNameLayer(
        source: String,
        locale: Locale?,
        textColor: String,
        textHalo: String,
    ) {
        append("{")
        append("\"id\": \"road-name\",")
        append("\"type\": \"symbol\",")
        append("\"source\": \"").append(source).append("\",")
        append("\"source-layer\": \"").append(Layers.TRANSPORTATION_NAME).append("\",")
        append("\"minzoom\": 12,")
        append("\"layout\": {")
        append("\"text-field\": ").append(roadNameTextField(locale)).append(",")
        append("\"text-size\": 10,")
        append("\"text-font\": [\"Open Sans Semibold\"],")
        append("\"text-anchor\": \"center\",")
        append("\"text-rotation-alignment\": \"map\",")
        append("\"text-max-angle\": 45,")
        append("\"symbol-placement\": \"line\"")
        append("},")
        append("\"paint\": {")
        append("\"text-color\": \"").append(textColor).append("\",")
        append("\"text-halo-color\": \"").append(textHalo).append("\",")
        append("\"text-halo-width\": 1.0")
        append("}")
        append("}")
    }

    private fun roadNameTextField(locale: Locale?): String {
        val lang = locale?.language?.takeIf { it.isNotEmpty() }
        return buildString {
            append("[\"coalesce\",")
            if (lang != null) {
                append("[\"get\", \"name:").append(lang).append("\"],")
            }
            append("[\"get\", \"name:en\"],")
            append("[\"get\", \"name:latin\"],")
            append("[\"get\", \"ref\"]")
            append("]")
        }
    }

    private fun StringBuilder.appendPlaceLayer(
        source: String,
        locale: Locale?,
        textColor: String,
        textHalo: String,
    ) {
        append("{")
        append("\"id\": \"place-label\",")
        append("\"type\": \"symbol\",")
        append("\"source\": \"").append(source).append("\",")
        append("\"source-layer\": \"").append(Layers.PLACE).append("\",")
        append("\"layout\": {")
        append("\"text-field\": ").append(placeTextFieldExpression(locale)).append(",")
        append("\"text-size\": 12,")
        append("\"text-font\": [\"Open Sans Semibold\"],")
        append("\"text-anchor\": \"center\",")
        append("\"text-allow-overlap\": false,")
        append("\"text-ignore-placement\": false")
        append("},")
        append("\"paint\": {")
        append("\"text-color\": \"").append(textColor).append("\",")
        append("\"text-halo-color\": \"").append(textHalo).append("\",")
        append("\"text-halo-width\": 1.5")
        append("}")
        append("}")
    }

    /**
     * Build the MapLibre `text-field` expression for place labels.
     *
     * - No locale → `"{name}"` (plain token, legacy behaviour).
     * - Locale set → `["coalesce", ["get","name:<lang>"], ["get","name:en"], ["get","name"]]`.
     *
     * `languageTag` lowercases the language code to match OSM tag conventions
     * (`name:ru`, not `name:RU`). Region variants (`ru-RU`, `es-MX`) are stripped —
     * OSM doesn't tag region variants, so `es-MX` → `name:es`.
     */
    private fun placeTextFieldExpression(locale: Locale?): String {
        if (locale == null) return "\"{name}\""
        val lang = locale.language.takeIf { it.isNotEmpty() } ?: return "\"{name}\""
        return buildString {
            append("[\"coalesce\",")
            append("[\"get\", \"name:").append(lang).append("\"],")
            // Skip the duplicate `name:en` if the locale is already English.
            if (lang != "en") append("[\"get\", \"name:en\"],")
            append("[\"get\", \"name\"]")
            append("]")
        }
    }

    /**
     * Escape backslashes in a Windows path so the JSON string is valid.
     * `mbtiles://E:\regions\foo.mbtiles` becomes `mbtiles://E:\\regions\\foo.mbtiles`.
     */
    private fun escapePath(path: String): String = path.replace("\\", "\\\\")
}

package com.twocircle.bike.feature.map.style

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
 *
 * The transportation layer uses a MapLibre `match` expression on the `surface` property
 * produced by tilemaker's Lua layer. Unknown surfaces fall back to [MapColors.UNKNOWN].
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
        const val BOUNDARY = "boundary"
        const val PLACE = "place"
    }

    /**
     * Produce a complete style JSON for the given [mbtilesPath] (absolute filesystem path).
     *
     * The path is interpolated into the `mbtiles://` scheme exactly as MapLibre expects:
     * three slashes, then the absolute path. File existence is the caller's responsibility.
     */
    fun buildStyleJson(mbtilesPath: String, sourceName: String = "region"): String = buildString {
        append("{")
        append("\"version\": 8,")
        append("\"name\": \"2circle-region\",")
        append("\"sources\": {")
        append("\"").append(sourceName).append("\": {")
        append("\"type\": \"vector\",")
        append("\"url\": \"mbtiles://").append(escapePath(mbtilesPath)).append("\"")
        append("}")
        append("},")

        append("\"layers\": [")
        appendBackgroundLayer()
        append(",")
        appendWaterLayer(sourceName)
        append(",")
        appendLandcoverLayer(sourceName)
        append(",")
        appendBoundaryLayer(sourceName)
        append(",")
        appendRoadLayer(sourceName)
        append(",")
        appendPlaceLayer(sourceName)
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
        // line-width scales with zoom: thin at z10, ~3px at z15, ~6px at z20.
        append("\"line-width\": {")
        append("\"base\": 1.2,")
        append("\"stops\": [[10, 0.5], [13, 1.0], [15, 2.5], [17, 4.0], [20, 6.0]]")
        append("},")
        append("\"line-opacity\": 0.92")
        append("}")
        append("}")
    }

    private fun StringBuilder.appendSurfaceCase(tag: String, color: String) {
        append("\"").append(tag).append("\", \"").append(color).append("\",")
    }

    private fun StringBuilder.appendBackgroundLayer() {
        append("{")
        append("\"id\": \"background\",")
        append("\"type\": \"background\",")
        append("\"paint\": { \"background-color\": \"").append(MapColors.BACKGROUND).append("\" }")
        append("}")
    }

    private fun StringBuilder.appendWaterLayer(source: String) {
        append("{")
        append("\"id\": \"water\",")
        append("\"type\": \"fill\",")
        append("\"source\": \"").append(source).append("\",")
        append("\"source-layer\": \"").append(Layers.WATER).append("\",")
        append("\"paint\": { \"fill-color\": \"").append(MapColors.WATER).append("\" }")
        append("}")
    }

    private fun StringBuilder.appendLandcoverLayer(source: String) {
        append("{")
        append("\"id\": \"landcover\",")
        append("\"type\": \"fill\",")
        append("\"source\": \"").append(source).append("\",")
        append("\"source-layer\": \"").append(Layers.LANDCOVER).append("\",")
        append("\"paint\": { \"fill-color\": \"").append(MapColors.LAND).append("\", \"fill-opacity\": 0.6 }")
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

    private fun StringBuilder.appendPlaceLayer(source: String) {
        // Minimal text layer for settlements. Colour is muted so the surface scheme
        // stays the dominant visual signal.
        append("{")
        append("\"id\": \"place-label\",")
        append("\"type\": \"symbol\",")
        append("\"source\": \"").append(source).append("\",")
        append("\"source-layer\": \"").append(Layers.PLACE).append("\",")
        append("\"layout\": {")
        append("\"text-field\": \"{name}\",")
        append("\"text-size\": 11")
        append("},")
        append("\"paint\": {")
        append("\"text-color\": \"#C8CCD2\",")
        append("\"text-halo-color\": \"#000000\",")
        append("\"text-halo-width\": 1.2")
        append("}")
        append("}")
    }

    /**
     * Escape backslashes in a Windows path so the JSON string is valid.
     * `mbtiles://E:\regions\foo.mbtiles` becomes `mbtiles://E:\\regions\\foo.mbtiles`.
     */
    private fun escapePath(path: String): String = path.replace("\\", "\\\\")
}

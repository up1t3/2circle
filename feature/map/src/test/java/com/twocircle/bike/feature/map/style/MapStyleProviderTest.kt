package com.twocircle.bike.feature.map.style

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

/**
 * Style JSON correctness — these tests guard the JSON contract MapLibre parses.
 *
 * We deliberately don't load MapLibre here (it needs Android). Instead we parse the
 * generated JSON with org.json (JVM-only) and assert structural correctness: required
 * keys, layer count, source scheme, surface-case wiring. If MapLibre's style schema
 * changes, this is where it surfaces first.
 */
class MapStyleProviderTest {

    private val samplePath = "/data/user/0/com.twocircle.bike/files/regions/foo.mbtiles"

    private fun style(): JSONObject = JSONObject(MapStyleProvider.buildStyleJson(samplePath))

    @Test
    fun `style is valid JSON with version 8`() {
        assertThat(style().getInt("version")).isEqualTo(8)
    }

    @Test
    fun `single vector source named region with mbtiles scheme`() {
        val sources = style().getJSONObject("sources")
        val source = sources.getJSONObject("region")
        assertThat(source.getString("type")).isEqualTo("vector")
        // mbtiles scheme: "mbtiles://" + absolute path, no extra slashes.
        assertThat(source.getString("url")).isEqualTo("mbtiles://$samplePath")
    }

    @Test
    fun `windows path backslashes are escaped`() {
        val winPath = "E:\\regions\\foo.mbtiles"
        val json = MapStyleProvider.buildStyleJson(winPath)
        // The unescaped backslash would break JSON parsing; the test passes if JSONObject
        // accepts the output AND the URL preserves the original path after unescaping.
        val url = JSONObject(json).getJSONObject("sources").getJSONObject("region").getString("url")
        assertThat(url).isEqualTo("mbtiles://$winPath")
    }

    @Test
    fun `style contains all expected layers in draw order`() {
        val layers = style().getJSONArray("layers")
        val ids = (0 until layers.length()).map { layers.getJSONObject(it).getString("id") }
        // Draw order matters: background → water → landcover → boundary → road → label.
        assertThat(ids).containsAtLeast(
            "background", "water", "landcover", "boundary", "road", "place-label",
        ).inOrder()
    }

    @Test
    fun `road layer is a line layer reading transportation source-layer`() {
        val road = layerById("road")
        assertThat(road.getString("type")).isEqualTo("line")
        assertThat(road.getString("source-layer")).isEqualTo("transportation")
        assertThat(road.getString("source")).isEqualTo("region")
    }

    @Test
    fun `road colour match expression contains every surface case`() {
        val road = layerById("road")
        val paint = road.getJSONObject("paint")
        val colorExpr = paint.getJSONArray("line-color")

        // Flatten the match expression to a single string, recursing into nested arrays
        // (the "get" expression ["get", "surface"] is itself a JSON array, not a string).
        val flat = flattenToString(colorExpr)

        // Every surface case must be present; if one is missing the colour silently
        // falls through to grey, defeating the entire visual scheme.
        listOf(
            "asphalt", "concrete", "paving_stones", "sett",
            "compacted", "gravel",
            "dirt", "ground",
            "sand", "grass", "rock",
        ).forEach { tag ->
            assertThat(flat).contains(tag)
        }

        // Each surface case must pair the tag with its colour.
        assertThat(flat).contains(MapColors.ASPHALT)
        assertThat(flat).contains(MapColors.COMPACTED)
        assertThat(flat).contains(MapColors.SAND)
        assertThat(flat).contains(MapColors.DIRT)
        assertThat(flat).contains(MapColors.GRASS)
        assertThat(flat).contains(MapColors.ROCK)
    }

    @Test
    fun `road line-width has zoom stops`() {
        val road = layerById("road")
        val width = road.getJSONObject("paint").getJSONObject("line-width")
        // base is a JSON number; compare as double.
        assertThat(width.getDouble("base")).isEqualTo(1.2)
        val stops = width.getJSONArray("stops")
        assertThat(stops.length()).isAtLeast(3)
    }

    /** Recursively flatten any mix of strings, numbers, nested arrays into one string. */
    private fun flattenToString(arr: org.json.JSONArray): String =
        (0 until arr.length()).joinToString("") { i ->
            when (val v = arr.get(i)) {
                is org.json.JSONArray -> flattenToString(v)
                is org.json.JSONObject -> v.toString()
                else -> v.toString()
            }
        }

    @Test
    fun `background and water layers have hardcoded colours`() {
        val bg = layerById("background").getJSONObject("paint").getString("background-color")
        assertThat(bg).isEqualTo(MapColors.BACKGROUND)

        val water = layerById("water").getJSONObject("paint").getString("fill-color")
        assertThat(water).isEqualTo(MapColors.WATER)
    }

    @Test
    fun `place label layer shows name field`() {
        val label = layerById("place-label")
        val textField = label.getJSONObject("layout").getString("text-field")
        assertThat(textField).contains("{name}")
    }

    private fun layerById(id: String): JSONObject {
        val layers = style().getJSONArray("layers")
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            if (layer.getString("id") == id) return layer
        }
        error("Layer $id not found in style")
    }
}

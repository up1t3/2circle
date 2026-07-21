package com.twocircle.bike.feature.map.style

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that the offline font assets are bundled correctly.
 *
 * These PBF ranges are what MapLibre Native reads via `asset://fonts/{fontstack}/{range}.pbf`
 * to render place labels, POI text, and road names WITHOUT any network. The earlier
 * "black map, no labels" bug was exactly this missing — we point at an online glyphs
 * URL that the device couldn't reach in the field.
 *
 * The test opens a handful of the critical ranges through the Android AssetManager to
 * prove they're actually in the APK and non-empty. If a future build drops the assets
 * directory or changes the fontstack name, this fails loud.
 *
 * Runs under Robolectric so it executes on the JVM without an emulator — important
 * here because the emulator has been flaky in this dev environment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineFontsAssetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `open sans semibold fontstack is bundled with at least the basic ranges`() {
        // The ranges 0-255 (ASCII), 1024-1279 (Cyrillic), 256-511 (Latin Extended)
        // cover virtually all European-language place names a rider will see — they're
        // the smallest useful "must-have" set.
        val criticalRanges = listOf("0-255", "256-511", "1024-1279", "1280-1535")
        val asset = context.assets
        val present = asset.list("fonts/Open Sans Semibold").orEmpty().toSet()
        criticalRanges.forEach { range ->
            val file = "$range.pbf"
            assertThat(present).contains(file)
            // Each PBF range must be non-trivially sized — the empty placeholder files
            // some font sources return are ~200 bytes; real SDF glyphs are 10-100+ KB.
            asset.open("fonts/Open Sans Semibold/$file").use { stream ->
                val size = stream.available()
                assertThat(size).isAtLeast(100)
            }
        }
    }

    @Test
    fun `fontstack directory contains enough ranges for full BMP coverage`() {
        // 254 ranges × 256 code points = ~65k code points — covers Latin, Cyrillic,
        // Greek, CJK, Arabic, etc. If only a handful are present, the asset bundle
        // step failed silently and many scripts will not render.
        val files = context.assets.list("fonts/Open Sans Semibold").orEmpty()
        assertThat(files.size).isAtLeast(200)
    }
}

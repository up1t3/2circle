package com.twocircle.bike.feature.search.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchEngineQueryTest {

    @Test
    fun `blank query returns null`() {
        assertThat(SearchEngine.escapeForFts("")).isNull()
        assertThat(SearchEngine.escapeForFts("   ")).isNull()
        assertThat(SearchEngine.escapeForFts("\t\n")).isNull()
    }

    @Test
    fun `single token gets prefix wildcard`() {
        // Prefix matching is the bicycle-touring default: typing "Yal" should find Yalta.
        assertThat(SearchEngine.escapeForFts("Yalta")).isEqualTo("\"Yalta\"*")
    }

    @Test
    fun `multi-token phrase is space-joined`() {
        assertThat(SearchEngine.escapeForFts("saint etienne")).isEqualTo("\"saint\"* \"etienne\"*")
    }

    @Test
    fun `embedded double quotes are stripped to prevent FTS syntax injection`() {
        // A malicious or accidental `"` would close our wrapping quote and let the user
        // inject FTS5 operators. Stripping neutralises that.
        assertThat(SearchEngine.escapeForFts("foo\"bar")).isEqualTo("\"foobar\"*")
        assertThat(SearchEngine.escapeForFts("\"foo\"")).isEqualTo("\"foo\"*")
    }

    @Test
    fun `fts operators are rendered inert`() {
        // FTS5 boolean operators AND/OR/NEAR are case-insensitive keywords; quoted they
        // become literal tokens instead of operators.
        assertThat(SearchEngine.escapeForFts("foo OR bar")).isEqualTo("\"foo\"* \"OR\"* \"bar\"*")
    }

    @Test
    fun `parentheses are kept literal`() {
        // Parens inside quotes are literals, not grouping operators.
        assertThat(SearchEngine.escapeForFts("foo(bar)")).isEqualTo("\"foo(bar)\"*")
    }

    @Test
    fun `whitespace collapses`() {
        assertThat(SearchEngine.escapeForFts("a   b\tc")).isEqualTo("\"a\"* \"b\"* \"c\"*")
    }

    @Test
    fun `all-whitespace tokens drop out`() {
        assertThat(SearchEngine.escapeForFts("a \"\" b")).isEqualTo("\"a\"* \"b\"*")
    }

    @Test
    fun `unicode passes through`() {
        // Cyrillic, accented Latin — we rely on FTS5 unicode61 tokenizer, not on escaping.
        assertThat(SearchEngine.escapeForFts("Ялта")).isEqualTo("\"Ялта\"*")
        assertThat(SearchEngine.escapeForFts("España")).isEqualTo("\"España\"*")
    }
}

class PlaceKindTest {

    @Test
    fun `round-trip osm values`() {
        PlaceKind.values().forEach { kind ->
            // Other maps to "other" but fromOsm("other") also returns Other.
            val back = PlaceKind.fromOsm(kind.osmValue)
            assertThat(back).isEqualTo(kind)
        }
    }

    @Test
    fun `unknown osm value maps to Other`() {
        assertThat(PlaceKind.fromOsm("spaceship")).isEqualTo(PlaceKind.Other)
        assertThat(PlaceKind.fromOsm(null)).isEqualTo(PlaceKind.Other)
    }
}

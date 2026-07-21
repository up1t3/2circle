package com.twocircle.bike

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twocircle.bike.designsystem.theme.BikeTheme
import com.twocircle.bike.nav.TwoCircleNavHost
import com.twocircle.bike.onboarding.OnboardingStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Critical-path instrumentation tests — the regression we just hit (PRAGMA crash on
 * Tracks/Regions) made the need obvious.
 *
 * Strategy: launch the real [MainActivity] with the real nav graph. We don't drive
 * individual ViewModels directly — we treat the app as a black box and assert that
 * navigation transitions don't kill it. This is the highest-leverage check: it exercises
 * Hilt wiring, Room opening, Compose navigation, and feature-screen composition in one
 * pass.
 *
 * Each test method is independent (Compose rule recreates the activity per test).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        // Mark onboarding as completed so the activity launches straight into the nav
        // host. Without this the first-run onboarding screen would intercept every test
        // (no "No offline region" prompt would ever be visible).
        runBlocking { OnboardingStore(composeRule.activity).setCompleted() }
    }

    @Test
    fun app_launches_and_shows_map_tab() {
        // The map screen with no installed region renders the NoRegion prompt — that's
        // the visible proof that Hilt + Compose + BikeDatabase opened cleanly. Using
        // the NoRegion prompt text (rather than the "Map" tab label) avoids ambiguity
        // with anything else that might say "Map" on the screen.
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No offline region downloaded yet").assertIsDisplayed()
    }

    @Test
    fun all_bottom_nav_tabs_survive_navigation() {
        // Reproduces the regression we just fixed: tapping Tracks/Regions used to crash
        // because Room's onOpen callback ran PRAGMA journal_mode via execSQL. If this
        // test passes, every feature screen's dependencies (Room, Hilt-injected VMs)
        // resolve without throwing.
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tracks").performClick()
        composeRule.waitForIdle()
        // Tracks list with no rides renders the empty prompt — a screen-unique marker.
        composeRule.onNodeWithText("No rides yet. Start one from the map screen.").assertIsDisplayed()

        composeRule.onNodeWithText("Regions").performClick()
        composeRule.waitForIdle()
        // "Refresh" is unique to the Regions screen header — tab label collisions make
        // onNodeWithText("Regions") ambiguous (it matches both tab + header).
        composeRule.onNodeWithText("Refresh").assertIsDisplayed()

        composeRule.onNodeWithText("Routes").performClick()
        composeRule.waitForIdle()
        // "Route Builder" only appears as the Routes-screen title.
        composeRule.onNodeWithText("Route Builder").assertIsDisplayed()

        // And back to Map — full round-trip. "No offline region" is unique to Map's
        // NoRegion prompt.
        composeRule.onNodeWithText("Map").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No offline region downloaded yet").assertIsDisplayed()
    }

    @Test
    fun tracks_tab_shows_empty_state_when_no_rides() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tracks").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No rides yet. Start one from the map screen.").assertIsDisplayed()
    }

    @Test
    fun routes_tab_shows_profile_chips() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Routes").performClick()
        composeRule.waitForIdle()
        // Profile chips render via RoutingProfile.displayNameRes() — localised labels
        // for Touring / Road / MTB. Default emulator locale is en-US, so the chips read
        // "Touring / Road / MTB"; switch the assertion target together with the locale
        // when this test is run under ru/es.
        composeRule.onNodeWithText("Touring").assertIsDisplayed()
        composeRule.onNodeWithText("Road").assertIsDisplayed()
        composeRule.onNodeWithText("MTB").assertIsDisplayed()
    }
}

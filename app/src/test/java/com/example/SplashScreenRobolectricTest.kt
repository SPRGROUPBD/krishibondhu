package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.KrishiBondhuTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplashScreenRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashScreenDisplaysLogoAppNameAppVersionAndBottomRightBuildVersionInCorrectOrder() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            KrishiBondhuTheme(darkTheme = false) {
                SplashScreen(
                    isAppLoading = false,
                    onSplashComplete = {},
                    appVersion = BuildConfig.VERSION_NAME,
                    buildVersion = BuildConfig.VERSION_CODE
                )
            }
        }

        // Advance clock past staggered animations (before completion delay finishes)
        composeTestRule.mainClock.advanceTimeBy(700L)
        composeTestRule.waitForIdle()

        val logoNode = composeTestRule.onNodeWithTag("splash_logo")
        val nameNode = composeTestRule.onNodeWithTag("splash_app_name")
        val versionNode = composeTestRule.onNodeWithTag("splash_app_version")
        val buildNode = composeTestRule.onNodeWithTag("splash_build_version")

        logoNode.assertIsDisplayed()
        nameNode.assertIsDisplayed().assertTextEquals("KrishiBondhu")
        versionNode.assertIsDisplayed()
        buildNode.assertIsDisplayed()

        composeTestRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Build ${BuildConfig.VERSION_CODE}").assertIsDisplayed()

        val rootBounds = composeTestRule.onNodeWithTag("splash_screen")
            .fetchSemanticsNode().boundsInRoot
        val logoBounds = logoNode.fetchSemanticsNode().boundsInRoot
        val nameBounds = nameNode.fetchSemanticsNode().boundsInRoot
        val versionBounds = versionNode.fetchSemanticsNode().boundsInRoot
        val buildBounds = buildNode.fetchSemanticsNode().boundsInRoot

        // Vertical order: Logo -> App Name -> App Version
        assertTrue("App name should be below logo", nameBounds.top >= logoBounds.bottom)
        assertTrue("App version should be below app name", versionBounds.top >= nameBounds.bottom)

        // Bottom-right placement for build version
        assertTrue(
            "Build version should be in bottom half",
            buildBounds.top >= rootBounds.center.y
        )
        assertTrue(
            "Build version should be in right half",
            buildBounds.left >= rootBounds.center.x
        )
    }
}

package org.onionshare.android

import android.app.UiModeManager
import android.app.UiModeManager.MODE_NIGHT_CUSTOM
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.onionshare.android.ui.MainActivity
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.ScreenshotCallback
import tools.fastlane.screengrab.ScreenshotStrategy
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy

class ScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    private val screenshotStrategy = ComposeScreenshotStrategy(composeTestRule)

    init {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
    }

    private fun screenshot(name: String) {
        Screengrab.screenshot(name, screenshotStrategy)
    }

    @Test
    fun shareScreenshots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        uiModeManager.setApplicationNightMode(MODE_NIGHT_NO)
        screenshot("1_en-US")

        val fab = context.getString(R.string.share_files_add)
        composeTestRule.onNode(hasContentDescription(fab)).performClick()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.activityRule.scenario.state == Lifecycle.State.RESUMED
        }

        val startSharing = context.getString(R.string.share_button_start)
        composeTestRule.waitUntilAsserted {
            onNode(hasText(startSharing)).assertIsDisplayed()
        }
        screenshot("2_en-US")

        composeTestRule.onNode(hasText(startSharing)).performClick()

        val starting = context.getString(R.string.share_button_starting)
        composeTestRule.waitUntilAsserted(5_000) {
            onNodeWithText(starting).assertIsDisplayed()
        }
        screenshot("3_en-US")

        val stopSharing = context.getString(R.string.share_button_stop)
        composeTestRule.waitUntilAsserted(30_000) {
            onNodeWithText(stopSharing).assertIsDisplayed()
        }
        screenshot("4_en-US")

        val about = context.getString(R.string.about_title)
        composeTestRule.onNodeWithContentDescription(about).performClick()

        screenshot("5_en-US")

        val back = context.getString(R.string.back)
        composeTestRule.onNodeWithContentDescription(back).performClick()

        uiModeManager.setApplicationNightMode(MODE_NIGHT_YES)

        composeTestRule.onNodeWithText(stopSharing).performClick()

        composeTestRule.activityRule.scenario.recreate()

        val clearAll = context.getString(R.string.clear_all)
        composeTestRule.waitUntilAsserted {
            onNodeWithText(clearAll).assertIsDisplayed()
        }
        screenshot("7_en-US")

        composeTestRule.onNodeWithText(clearAll).performClick()

        screenshot("6_en-US")

        // need to reset this, otherwise the app gets stuck in this mode
        uiModeManager.setApplicationNightMode(MODE_NIGHT_CUSTOM)
    }

    private fun <T : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<T>, T>.waitUntilAsserted(
        timeout: Long = 1_000,
        block: AndroidComposeTestRule<ActivityScenarioRule<T>, T>.() -> Unit,
    ) = waitUntil(timeout) {
        try {
            block()
            true
        } catch (e: AssertionError) {
            false
        }
    }

}

class ComposeScreenshotStrategy<T : ComponentActivity>(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<T>, T>,
) : ScreenshotStrategy {
    override fun takeScreenshot(screenshotName: String, screenshotCallback: ScreenshotCallback) {
        val imageBitmap = composeTestRule.onRoot().captureToImage()
        screenshotCallback.screenshotCaptured(screenshotName, imageBitmap.asAndroidBitmap())
    }
}

package com.lanu.yalan

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanu.yalan.domain.DevicePolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a Google-services-free AOSP image. Does not download or benchmark the AI model. */
@RunWith(AndroidJUnit4::class)
class AndroidCompatibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitHome() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("BAHANEN HAZIR MI?").fetchSemanticsNodes().isNotEmpty()
        }
    }
    @Test fun opensAndNavigatesToLockedLevelMapWithoutGoogleServices() {
        awaitHome()
        compose.onNodeWithText("Seviyeler").performClick()
        compose.onNodeWithText("Hikâyen büyüsün.").assertIsDisplayed()
        compose.onNodeWithText("01").assertIsEnabled()
        compose.onNodeWithText("02").assertIsNotEnabled()
    }
    @Test fun displaysDeviceDetailsAndSettingsInLargeFont() {
        awaitHome()
        compose.onNodeWithText("Ayarlar").performClick()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("device-summary"))
        compose.onNodeWithTag("device-summary").assertIsDisplayed()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasText("Cihaz ve performans"))
        compose.onNodeWithText("Cihaz ve performans").assertIsDisplayed()
        assertFalse(BuildConfig.FIREBASE_ENABLED)
    }
}

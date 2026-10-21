package com.lanu.yalan

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanu.yalan.domain.DevicePolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.io.File

/** AOSP: real fast game, encrypted persistence and UI. Does not benchmark the optional LLM. */
@RunWith(AndroidJUnit4::class)
class AndroidCompatibilityTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            listOf("progress.enc","progress.enc.bak","progress.enc.new").forEach { File(context.filesDir,it).delete() }
        }
    }).around(compose)

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
    @Test fun completesFastStoryWithoutAModelAndRepliesWithinThreeSeconds() {
        awaitHome()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("topic-input"))
        compose.onNodeWithTag("topic-input").performTextInput("İşe geç kaldım")
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("start-game"))
        val start=SystemClock.elapsedRealtime()
        compose.onNodeWithTag("start-game").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("answer-input").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("Opening exceeded 3s",SystemClock.elapsedRealtime()-start<3_000)
        compose.onNodeWithText("Hızlı oyun · Kural tabanlı puanlama").assertIsDisplayed()
        val answers=listOf("Saat 8'de otobüse bindim, lastik patladı.","Durakta 15 dakika yedek otobüs bekledim.",
            "Şoför arızadan dolayı durakta beklememizi istedi.","Telefonla müdüre mesaj attım.","Otobüs biletim ve mesaj saatim beklediğimi gösterir.")
        answers.forEachIndexed { index, answer ->
            compose.onNodeWithTag("answer-input").performTextInput(answer)
            compose.onNodeWithTag("send-answer").assertIsEnabled().assertIsDisplayed()
            val sent=SystemClock.elapsedRealtime()
            if(index%2==0) compose.onNodeWithTag("answer-input").performImeAction()
            else compose.onNodeWithTag("send-answer").performClick()
            try {
                compose.waitUntil(3_000) {
                    compose.onAllNodesWithText(if(index<4) "SORU ${index+2} / 5" else "İNANDIRICILIK").fetchSemanticsNodes().isNotEmpty()
                }
            } catch(e: Throwable) {
                println("LANU_FAST_FAILED_TURN=${index+1}")
                println(compose.onRoot(useUnmergedTree=true).printToString())
                throw e
            }
            if(index<4) compose.onNodeWithTag("current-question").assertIsDisplayed()
            else compose.onNodeWithText("İNANDIRICILIK").assertIsDisplayed()
            val elapsed=SystemClock.elapsedRealtime()-sent
            println("LANU_FAST_TURN_${index+1}_MS=$elapsed")
            assertTrue("Turn ${index+1} exceeded 3s: $elapsed ms",elapsed<3_000)
        }
        compose.onNodeWithText("Bu sefer yedik!").assertIsDisplayed()
        compose.onNodeWithText("Çık").performClick()
        compose.onNodeWithText("Seviyeler").performClick()
        compose.onNodeWithText("02").assertIsEnabled()
    }
    private fun startSpecificStory() {
        awaitHome()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("topic-input"))
        compose.onNodeWithTag("topic-input").performTextInput("İşe geç kaldım")
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("start-game"))
        compose.onNodeWithTag("start-game").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("answer-input").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun sendSpecific(answer: String, next: Int) {
        compose.onNodeWithTag("answer-input").performTextInput(answer)
        compose.onNodeWithTag("answer-input").performImeAction()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("SORU $next / 5").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("current-question").assertIsDisplayed()
    }
    @Test fun phoneAnswersRenderDifferentSpecificQuestionsInsteadOfGenericPrompts() {
        startSpecificStory()
        sendSpecific("Telefonumun şarjı bitti, o yüzden arayamadım.",2)
        compose.onNodeWithText("Telefon tam hangi anda kapandı; o sırada neredeydin?").assertIsDisplayed()
        sendSpecific("Şarjım bittiğinde duraktaydım ve telefon kapandı.",3)
        compose.onNodeWithText("Telefon kapandıktan sonra hangi seçeneğin vardı; başka cihaz veya şarj imkânı varsa neden işlemedi?").assertIsDisplayed()
    }
    @Test fun contradictoryClocksAreRenderedAsACrossCheckOfBothPlayerTimes() {
        startSpecificStory()
        sendSpecific("Sabah 09:00'da evden çıktım.",2)
        sendSpecific("Sabah 08:30'da ofisteydim.",3)
        compose.onNodeWithText("09:00 çıkış ile 08:30 varış aynı gün mü; hangi saati düzeltiyorsun?").assertIsDisplayed()
    }
}

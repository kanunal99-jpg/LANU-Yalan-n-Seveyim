package com.lanu.yalan

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanu.yalan.domain.DevicePolicy
import com.lanu.yalan.domain.PerformanceStats
import com.lanu.yalan.domain.PlayMode
import com.lanu.yalan.domain.TimingSample
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
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
            assumeTrue("UI smoke requires UI_SMOKE", BuildConfig.UI_SMOKE)
            assumeTrue("Never reset a physical phone", Build.HARDWARE in setOf("ranchu", "goldfish"))
            assumeTrue("CI progress reset requires opt-in", InstrumentationRegistry.getArguments().getString("lanu.allowProgressReset") == "true")
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
    @Test fun settingsExposeUpdatesAndRequireMatchingLongBackupPasswords() {
        awaitHome()
        compose.onNodeWithText("Ayarlar").performClick()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("check-update"))
        compose.onNodeWithTag("check-update").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("download-update").assertDoesNotExist()
        compose.onNodeWithTag("install-update").assertDoesNotExist()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("export-backup"))
        compose.onNodeWithTag("export-backup").assertIsDisplayed().assertIsEnabled().performClick()
        awaitBackupDialog()
        compose.onNodeWithText("Yedeğine parola belirle").assertIsDisplayed()
        compose.onNodeWithTag("confirm-backup").assertIsNotEnabled()
        compose.onNodeWithTag("backup-passphrase").performTextInput("kisa")
        compose.onNodeWithTag("backup-passphrase-confirm").performTextInput("kisa")
        compose.onNodeWithTag("confirm-backup").assertIsNotEnabled()
        val password="CI parola eşleşmesi 42"
        compose.onNodeWithTag("backup-passphrase").performTextReplacement(password)
        compose.onNodeWithTag("backup-passphrase-confirm").performTextReplacement("Farklı bir parola 42")
        compose.onNodeWithText("Parolalar aynı olmalı.").assertExists()
        compose.onNodeWithTag("confirm-backup").assertIsNotEnabled()
        compose.onNodeWithTag("backup-passphrase-confirm").performTextReplacement(password)
        compose.onNodeWithTag("confirm-backup").assertIsEnabled()
        compose.onNodeWithText("Parolalar aynı olmalı.").assertDoesNotExist()
        compose.onNodeWithText("Vazgeç").performClick()
        compose.onNodeWithTag("confirm-backup").assertDoesNotExist()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("export-backup"))
        compose.onNodeWithTag("export-backup").assertIsDisplayed().performClick()
        awaitBackupDialog()
        compose.onNodeWithTag("confirm-backup").assertIsNotEnabled()
        compose.onNodeWithText("Vazgeç").performClick()
    }
    private fun awaitBackupDialog() {
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("confirm-backup").fetchSemanticsNodes().isNotEmpty() }
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
        val renderedTimings=mutableListOf<TimingSample>()
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
            renderedTimings+=TimingSample(PlayMode.FAST,elapsed)
            println("LANU_FAST_TURN_${index+1}_MS=$elapsed")
            assertTrue("Turn ${index+1} exceeded 3s: $elapsed ms",elapsed<3_000)
        }
        val summary=PerformanceStats.summarize(renderedTimings)
        println("LANU_UI_RENDERED_SUMMARY count=${summary.successCount} p50_ms=${summary.p50Ms} p95_ms=${summary.p95Ms}")
        compose.onNodeWithText("Bu sefer yedik!").assertIsDisplayed()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasText("PUANIN HESABI · YEREL KURALLAR"))
        compose.onNodeWithText("PUANIN HESABI · YEREL KURALLAR").assertIsDisplayed()
        compose.onNodeWithTag("score-explanations").assertExists()
        listOf("Tutarlılık", "Makullük", "Somut ayrıntı", "Sorulara karşı savunma").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNode(hasText("Hikâyede", substring=true)).assertExists()
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
    @Test fun absurdAnswerShowsItsComicObjectionAndSpecificQuestionTogetherWithoutManualScrolling() {
        startSpecificStory()
        sendSpecific("100 km yolu 2 dakikada yürüdüm.",2)
        compose.onNodeWithTag("current-reply").assertIsDisplayed()
        compose.onNode(hasText("ayakkabı değil hızlı tren",substring=true)).assertIsDisplayed()
        compose.onNodeWithTag("current-question").assertIsDisplayed()
        compose.onNode(hasText("mesafe, süre veya yürüyüş",substring=true)).assertIsDisplayed()
        captureObjection("lanu-comic-objection.png")
        sendSpecific("Müdüre mesaj attım.",3)
        compose.onNode(hasText("itirazı hâlâ açık",substring=true)).assertIsDisplayed()
        compose.onNodeWithTag("current-question").assertIsDisplayed()
        compose.onNode(hasText("iddianı geri mi alıyorsun",substring=true)).assertIsDisplayed()
        captureObjection("lanu-persistent-objection.png")
        println("LANU_COMIC_OBJECTION_VISIBLE_AND_PERSISTENT")
    }
    private fun captureObjection(name: String) {
        require(Regex("lanu-[a-z-]+\\.png").matches(name))
        compose.waitForIdle()
        val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/$name")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}

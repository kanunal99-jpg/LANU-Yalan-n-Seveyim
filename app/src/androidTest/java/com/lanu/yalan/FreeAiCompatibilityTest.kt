package com.lanu.yalan

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanu.yalan.data.OnlineAiCredentials
import com.lanu.yalan.data.SaveStore
import com.lanu.yalan.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File

/** Real Keystore and UI tests. Never sends a real API request or resets a physical phone. */
@RunWith(AndroidJUnit4::class)
class FreeAiCompatibilityTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture=GameSave(unlocked=2,best=mapOf(1 to 80),mode=PlayMode.FREE_ONLINE_AI,onboarding=true,
        session=Session("online-fallback",2,"İşe geç kaldım","Seni geciktiren olay neydi?",turns=listOf(
            Turn("Seni geciktiren olay neydi?","Telefonumun şarjı bitti.",Verdict("Telefon mesaiyi bırakmış.","Telefon kapandığında neredeydin?",17,18,14,12))
        ),mode=PlayMode.FREE_ONLINE_AI))
    private fun allowed()=BuildConfig.UI_SMOKE && Build.HARDWARE in setOf("ranchu","goldfish") &&
        InstrumentationRegistry.getArguments().getString("lanu.allowProgressReset")=="true"
    @get:Rule val rules: TestRule=RuleChain.outerRule(object: ExternalResource() {
        override fun before() {
            assumeTrue(allowed())
            runBlocking {OnlineAiCredentials(context).delete();SaveStore(context).write(fixture)}
        }
        override fun after() {if(allowed()) runBlocking {OnlineAiCredentials(context).delete();SaveStore(context).reset()}}
    }).around(compose)
    private fun home()=compose.waitUntil(20_000) {compose.onAllNodesWithText("BAHANEN HAZIR MI?").fetchSemanticsNodes().isNotEmpty()}

    @Test fun connectionRequiresAPersonalKeyAndExplicitDataConsent() {
        home()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("open-online-setup"))
        compose.onNodeWithTag("open-online-setup").assertIsDisplayed().performClick()
        compose.onNodeWithTag("connect-online").assertIsNotEnabled()
        compose.onNodeWithTag("online-key").performTextInput("test-personal-key-"+"x".repeat(32))
        compose.onNodeWithTag("connect-online").assertIsNotEnabled()
        compose.onNodeWithTag("online-consent").performScrollTo().assertIsDisplayed().performClick().assertIsOn()
        compose.onNodeWithTag("connect-online").assertIsDisplayed().assertIsEnabled()
        // Do NOT press: this is a fake key, not a live provider or quality test.
        println("LANU_ONLINE_KEY_AND_CONSENT_REQUIRED")
    }
    @Test fun aMissingOnlineKeyCanContinueTheSameStoryWithExplicitFastChoice() {
        home()
        val resume="Hikâyeye devam et · AI · 1/5"
        compose.onNodeWithTag("screen-content").performScrollToNode(hasText(resume))
        compose.onNodeWithText(resume).performClick()
        compose.onNodeWithTag("answer-input").assertIsNotEnabled()
        compose.onNodeWithText("Bu hikâyeye hızlı devam et").performClick()
        compose.waitUntil(3_000) {compose.onAllNodesWithText(PlayMode.FAST.label()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("answer-input").performTextInput("Durakta saat 08:15'te telefon kapandı.")
        compose.onNodeWithTag("answer-input").performImeAction()
        compose.waitUntil(3_000) {compose.onAllNodesWithText("SORU 3 / 5").fetchSemanticsNodes().isNotEmpty()}
        val saved=runBlocking {SaveStore(context).read()}
        assertEquals(fixture.session!!.id,saved.session!!.id)
        assertEquals(fixture.session.turns.first(),saved.session.turns.first())
        assertEquals(fixture.unlocked,saved.unlocked);assertEquals(fixture.best,saved.best)
        assertEquals(PlayMode.FAST,saved.session.mode)
        println("LANU_ONLINE_TO_FAST_KEPT_HISTORY_AND_PROGRESS")
    }
    @Test fun thePersonalKeyIsEncryptedSeparateFromProgressAndPortableBackup()=runBlocking {
        home()
        val secret="test-personal-key-"+"x".repeat(32)
        val credentials=OnlineAiCredentials(context)
        credentials.write(secret)
        assertEquals(secret,OnlineAiCredentials(context).read())
        val file=File(context.noBackupFilesDir,"online-ai.enc")
        val bytes=file.readBytes()
        assertFalse(bytes.decodeToString().contains(secret))
        val password="CI portable backup password".toCharArray()
        val backup=SaveStore(context).exportBackup(password)
        val restored=com.lanu.yalan.data.PortableBackup.decode(backup,"CI portable backup password".toCharArray())
        assertEquals(fixture,restored)
        file.writeBytes(bytes.also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()})
        assertTrue(runCatching {credentials.read()}.isFailure)
        assertEquals(fixture,SaveStore(context).read())
        credentials.delete();assertNull(credentials.read())
        println("LANU_ONLINE_KEY_ENCRYPTED_TAMPER_REJECTED_PROGRESS_KEPT")
    }
}

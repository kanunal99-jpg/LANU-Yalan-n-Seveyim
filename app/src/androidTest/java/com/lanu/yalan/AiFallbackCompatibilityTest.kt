package com.lanu.yalan

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

/** Real UI and encrypted save fallback, with no model download and no physical-device mutation. */
@RunWith(AndroidJUnit4::class)
class AiFallbackCompatibilityTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture=GameSave(unlocked=2,best=mapOf(1 to 80),mode=PlayMode.LOCAL_AI,onboarding=true,
        session=Session("preserve-ai-fallback",2,"İşe geç kaldım","Seni geciktiren olay neydi?",
            turns=listOf(Turn("Seni geciktiren olay neydi?","Telefonumun şarjı bitti.",
                Verdict("Telefon mesaiyi bırakmış.","Telefon kapandığında neredeydin?",17,18,14,12))),mode=PlayMode.LOCAL_AI))
    @get:Rule val rules: TestRule=RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            assumeTrue(BuildConfig.UI_SMOKE)
            assumeTrue(Build.HARDWARE in setOf("ranchu","goldfish"))
            assumeTrue(InstrumentationRegistry.getArguments().getString("lanu.allowProgressReset")=="true")
            runBlocking { SaveStore(context).write(fixture) }
        }
        override fun after() { if(BuildConfig.UI_SMOKE && Build.HARDWARE in setOf("ranchu","goldfish") && InstrumentationRegistry.getArguments().getString("lanu.allowProgressReset")=="true") runBlocking { SaveStore(context).reset() } }
    }).around(compose)

    @Test fun missingModelCanContinueTheSameStoryWithExplicitFastChoice() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("BAHANEN HAZIR MI?").fetchSemanticsNodes().isNotEmpty() }
        val resume="Hikâyeye devam et · AI · 1/5"
        compose.onNodeWithTag("screen-content").performScrollToNode(hasText(resume))
        compose.onNodeWithText(resume).performClick()
        compose.onNodeWithTag("answer-input").assertIsNotEnabled()
        compose.onNodeWithText("Bu hikâyeye hızlı devam et").assertIsDisplayed().performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Hızlı oyun · Kural tabanlı puanlama").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("answer-input").assertIsEnabled()
        val answer="Durakta saat 08:15'te telefon kapandı."
        compose.onNodeWithTag("answer-input").performTextInput(answer)
        compose.onNodeWithTag("answer-input").performImeAction()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("SORU 3 / 5").fetchSemanticsNodes().isNotEmpty() }
        val saved=runBlocking { SaveStore(context).read() }
        assertEquals(fixture.session!!.id,saved.session!!.id)
        assertEquals(fixture.session.turns.first(),saved.session.turns.first())
        assertEquals(answer,saved.session.turns.last().answer)
        assertEquals(PlayMode.FAST,saved.mode)
        assertEquals(PlayMode.FAST,saved.session.mode)
        assertEquals(fixture.unlocked,saved.unlocked)
        assertEquals(fixture.best,saved.best)
        println("LANU_EXPLICIT_FAST_FALLBACK_PRESERVED turns=2 unlocked=2 previous_answer=true")
    }
}

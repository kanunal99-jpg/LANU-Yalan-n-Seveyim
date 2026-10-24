package com.lanu.yalan

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanu.yalan.data.PortableBackup
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

/** Five real encrypted stories on an opted-in emulator. No network or model calls. */
@RunWith(AndroidJUnit4::class)
class MultiStoryCompatibilityTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun story(n: Int)=Session("shelf-$n",2,"Bağımsız konu $n","Konu $n nerede başladı?",
        turns=listOf(Turn("Konu $n nerede başladı?","Cevap yalnız konu $n için.",Verdict("Konu $n dosyada.","Konu $n için sonra ne oldu?",17,18,16,15))),
        mode=if(n==3) PlayMode.LOCAL_AI else PlayMode.FAST,draftAnswer="Ayrı taslak $n")
    private val fixture=GameSave(unlocked=2,best=mapOf(1 to 80),mode=PlayMode.FAST,onboarding=true,
        session=story(1),parkedSessions=(2..5).map {story(it)})
    private fun allowed()=BuildConfig.UI_SMOKE && Build.HARDWARE in setOf("ranchu","goldfish") &&
        InstrumentationRegistry.getArguments().getString("lanu.allowProgressReset")=="true"
    @get:Rule val rules: TestRule=RuleChain.outerRule(object: ExternalResource() {
        override fun before() {assumeTrue(allowed());runBlocking {SaveStore(context).write(fixture)}}
        override fun after() {if(allowed()) {File(context.filesDir,"progress.enc.new").takeIf {it.isDirectory}?.deleteRecursively();runBlocking {SaveStore(context).reset()}}}
    }).around(compose)
    private fun home()=compose.waitUntil(20_000) {compose.onAllNodesWithText("BAHANEN HAZIR MI?").fetchSemanticsNodes().isNotEmpty()}
    private fun manager(fromHome: Boolean=false) {
        if(fromHome) compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("manage-stories"))
        compose.onNodeWithTag("manage-stories").assertIsDisplayed().performClick()
        compose.onNodeWithTag("story-list").assertExists()
    }
    private fun choose(n: Int, succeeds: Boolean=true) {
        compose.onNodeWithTag("story-list").performScrollToNode(hasTestTag("resume-story-shelf-$n"))
        compose.onNodeWithTag("resume-story-shelf-$n").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {compose.onAllNodesWithTag("answer-input").fetchSemanticsNodes().isNotEmpty()}
        if(succeeds) compose.waitUntil(5_000) {saved().session?.id=="shelf-$n"}
        else compose.waitUntil(5_000) {compose.onAllNodes(hasText("[SAVE]",substring=true)).fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
    }
    private fun saved()=runBlocking {SaveStore(context).read()}

    @Test fun fiveStorySwitchKeepsEveryHistoryModeAndDraftThroughRecreationAndBackup() {
        home();manager(true);choose(1)
        compose.onNodeWithTag("answer-input").assertTextContains("Ayrı taslak 1")
        compose.onNodeWithTag("answer-input").performTextReplacement("Birinci hikâyeye yeni taslak")
        // Switch immediately: the synchronous flush must work before the debounce timer.
        manager();choose(5)
        compose.onNodeWithTag("answer-input").assertTextContains("Ayrı taslak 5")
        val first=saved()
        assertEquals(fixture.session!!.turns,first.parkedSessions.single {it.id=="shelf-1"}.turns)
        assertEquals("Birinci hikâyeye yeni taslak",first.parkedSessions.single {it.id=="shelf-1"}.draftAnswer)
        compose.onNodeWithTag("answer-input").performTextReplacement("Beşinci hikâyede gönderilmemiş cevap")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {saved().session?.draftAnswer=="Beşinci hikâyede gönderilmemiş cevap"}
        compose.onNodeWithTag("answer-input").assertTextContains("Beşinci hikâyede gönderilmemiş cevap")
        val all=saved()
        assertEquals(5,StoryShelf.activeCount(all))
        for(n in 1..5) {
            val actual=StoryShelf.stories(all).single {it.id=="shelf-$n"}
            assertEquals(story(n).turns,actual.turns);assertEquals(story(n).mode,actual.mode)
        }
        val bytes=runBlocking {SaveStore(context).exportBackup("CI five story password".toCharArray())}
        assertEquals(all,PortableBackup.decode(bytes,"CI five story password".toCharArray()))
        println("LANU_FIVE_STORIES_DRAFTS_HISTORY_MODES_RECREATION_BACKUP_PRESERVED")
    }

    @Test fun sixthStoryIsBlockedAndClosingRequiresConfirmationBeforeANewStoryCanStart() {
        home()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("topic-input"))
        compose.onNodeWithTag("topic-input").performTextInput("Altıncı ayrı konu")
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("start-game"))
        compose.onNodeWithTag("start-game").assertIsNotEnabled()
        assertEquals(fixture,saved())
        manager(true)
        compose.onNodeWithTag("story-list").performScrollToNode(hasTestTag("close-story-shelf-3"))
        compose.onNodeWithTag("close-story-shelf-3").performClick()
        compose.onNodeWithText("Vazgeç").performClick()
        assertEquals(fixture,saved())
        compose.onNodeWithTag("story-list").performScrollToNode(hasTestTag("close-story-shelf-3"))
        compose.onNodeWithTag("close-story-shelf-3").performClick()
        compose.onNodeWithTag("confirm-close-story").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {StoryShelf.activeCount(saved())==4}
        assertEquals(fixture.best,saved().best)
        compose.onNodeWithTag("new-story").assertIsEnabled().performClick()
        compose.onNodeWithTag("screen-content").performScrollToNode(hasTestTag("start-game"))
        compose.onNodeWithTag("start-game").assertIsEnabled().performClick()
        compose.waitUntil(5_000) {saved().session?.topic=="Altıncı ayrı konu"}
        val next=saved()
        assertEquals(5,StoryShelf.activeCount(next));assertEquals(fixture.best,next.best)
        assertEquals(setOf("shelf-1","shelf-2","shelf-4","shelf-5"),next.parkedSessions.map {it.id}.toSet())
        println("LANU_SIXTH_STORY_BLOCKED_EXPLICIT_CLOSE_REQUIRED_NO_EVICTION")
    }

    @Test fun aFailedDraftWriteCannotSwitchStoriesOrLoseTheTypedAnswer() {
        home();manager(true);choose(1)
        val blocker=File(context.filesDir,"progress.enc.new")
        check(blocker.mkdir());File(blocker,"block-atomic-write").writeText("controlled smoke fixture")
        try {
            compose.onNodeWithTag("answer-input").performTextReplacement("Disk doluyken korunacak cevap")
            manager();choose(2,succeeds=false)
            compose.onNodeWithTag("answer-input").assertTextContains("Disk doluyken korunacak cevap")
            assertEquals("shelf-1",saved().session!!.id)
            assertEquals(fixture.session!!.turns,saved().session!!.turns)
        } finally {blocker.deleteRecursively()}
        manager();choose(2)
        compose.waitUntil(5_000) {saved().session?.id=="shelf-2"}
        assertEquals("Disk doluyken korunacak cevap",saved().parkedSessions.single {it.id=="shelf-1"}.draftAnswer)
        println("LANU_FAILED_DRAFT_WRITE_KEPT_SELECTION_AND_ANSWER_RETRY_RECOVERED")
    }
    @Test fun aFailedAtomicRenameCannotBeAcknowledgedAsASavedDraft() {
        home();manager(true);choose(1)
        val progress=File(context.filesDir,"progress.enc")
        val original=progress.readBytes()
        check(progress.delete());check(progress.mkdir())
        File(progress,"block-atomic-rename").writeText("controlled smoke fixture")
        try {
            compose.onNodeWithTag("answer-input").performTextReplacement("Yeniden adlandırılamayan taslak")
            manager();choose(2,succeeds=false)
            compose.onNodeWithTag("answer-input").assertTextContains("Yeniden adlandırılamayan taslak")
            compose.onNodeWithTag("current-question").assertTextContains("Konu 1 için sonra ne oldu?")
        } finally {
            progress.deleteRecursively();progress.writeBytes(original)
        }
        assertEquals(fixture,saved())
        manager();choose(2)
        assertEquals("Yeniden adlandırılamayan taslak",saved().parkedSessions.single {it.id=="shelf-1"}.draftAnswer)
        println("LANU_ATOMIC_RENAME_FAILURE_NOT_ACKNOWLEDGED_RETRY_PRESERVED_DRAFT")
    }
}

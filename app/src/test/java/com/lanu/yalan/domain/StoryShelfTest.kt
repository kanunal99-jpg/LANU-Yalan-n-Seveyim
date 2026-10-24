package com.lanu.yalan.domain

import com.lanu.yalan.data.SaveValidation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StoryShelfTest {
    private fun story(n: Int, turns: Int=1) = Session("story-$n",1,"Bağımsız konu $n","Soru $n/0?",
        turns=List(turns) { i -> Turn("Soru $n/$i?","Cevap $n/$i",Verdict("Yorum $n/$i","Soru $n/${i+1}?",20,20,20,20)) },
        mode=PlayMode.entries[(n-1)%3],draftAnswer=if(turns==5) "" else "Taslak $n")
    private fun five()=GameSave(unlocked=2,best=mapOf(1 to 75),session=story(1),parkedSessions=(2..5).map {story(it)})

    @Test fun newStoryParksTheWholePreviousStoryWithItsDraftAndMode() {
        val old=GameSave(session=story(1))
        val next=StoryShelf.start(old,story(2,0).copy(draftAnswer=""))
        assertEquals(listOf(old.session),next.parkedSessions)
        assertEquals("story-2",next.session!!.id)
        assertEquals(2,StoryShelf.activeCount(next))
        SaveValidation.validate(next)
    }
    @Test fun fiveStoriesCanSwitchBackWithoutMixingHistoryDraftOrMode() {
        val original=five()
        var next=original
        for(n in listOf(5,3,2,4,1)) {
            next=StoryShelf.select(next,"story-$n")
            assertEquals(story(n),next.session)
            assertEquals(StoryShelf.stories(original).toSet(),StoryShelf.stories(next).toSet())
            SaveValidation.validate(next)
        }
    }
    @Test fun sixthUnfinishedStoryIsRejectedWithoutEvictingAnyStory() {
        val original=five()
        assertFalse(StoryShelf.canStart(original))
        assertThrows(IllegalArgumentException::class.java) {StoryShelf.start(original,story(6,0).copy(draftAnswer=""))}
        assertEquals((1..5).map {story(it)}.toSet(),StoryShelf.stories(original).toSet())
    }
    @Test fun editingSelectedDraftCannotChangeAnInactiveStory() {
        val old=five()
        val next=StoryShelf.draft(old,"  yeni cevap  ")
        assertEquals("  yeni cevap  ",next.session!!.draftAnswer)
        assertEquals(old.session!!.turns,next.session.turns)
        assertEquals(old.parkedSessions,next.parkedSessions)
        assertThrows(IllegalArgumentException::class.java) {StoryShelf.draft(old,"x".repeat(281))}
    }
    @Test fun closingAnInactiveStoryKeepsTheSelectedStoryAndEarnedProgress() {
        val old=five();val next=StoryShelf.close(old,"story-3")
        assertEquals(old.session,next.session);assertEquals(old.best,next.best);assertEquals(old.unlocked,next.unlocked)
        assertEquals(setOf("story-1","story-2","story-4","story-5"),StoryShelf.stories(next).map {it.id}.toSet())
        assertTrue(StoryShelf.canStart(next))
    }
    @Test fun closingSelectedStoryRestoresTheNextStoryDraftThenLastCloseClearsSelection() {
        val next=StoryShelf.close(five(),"story-1")
        assertEquals(story(2),next.session)
        val empty=StoryShelf.close(GameSave(session=story(1)),"story-1")
        assertNull(empty.session);assertTrue(empty.parkedSessions.isEmpty())
    }
    @Test fun finishedResultCanBeReplacedWithoutBlockingTheNextLevel() {
        val done=Rules.credit(GameSave(session=story(1,5),parkedSessions=(2..5).map {story(it)}))
        assertTrue(StoryShelf.canStart(done))
        val next=StoryShelf.start(done,story(6,0).copy(draftAnswer=""))
        assertEquals(5,StoryShelf.activeCount(next));assertEquals(done.best,next.best)
        assertFalse(StoryShelf.stories(next).any {it.id=="story-1"})
        SaveValidation.validate(next)
    }
    @Test fun finishingTwoConcurrentStoriesAtSameLevelCannotSkipAnotherLevel() {
        val first=Rules.credit(GameSave(session=story(1,5),parkedSessions=listOf(story(2))))
        assertEquals(2,first.unlocked)
        val selected=StoryShelf.select(first,"story-2")
        val second=Rules.credit(StoryShelf.replaceSelected(selected,story(2,5)))
        assertEquals(2,second.unlocked);assertEquals(second,Rules.credit(second))
        assertTrue(second.parkedSessions.isEmpty())
    }
    @Test fun staleAnswerCannotReplaceAnotherSelectedStory() {
        assertThrows(IllegalArgumentException::class.java) {StoryShelf.replaceSelected(five(),story(3))}
        assertThrows(IllegalArgumentException::class.java) {StoryShelf.select(five(),"missing")}
        assertThrows(IllegalArgumentException::class.java) {StoryShelf.close(five(),"missing")}
        assertThrows(IllegalArgumentException::class.java) {StoryShelf.start(GameSave(session=story(1)),story(1,0).copy(draftAnswer=""))}
    }
    @Test fun everyStoredStoryIsValidatedIncludingDuplicatesAndInvalidInactiveDrafts() {
        val base=five()
        val invalid=listOf(base.copy(parkedSessions=base.parkedSessions+story(6)),
            base.copy(parkedSessions=listOf(story(1))),base.copy(session=null),
            base.copy(parkedSessions=listOf(story(2).copy(draftAnswer="x".repeat(281)))),
            base.copy(parkedSessions=listOf(story(2).copy(turns=listOf(story(2).turns.first().copy(question="Başka soru?"))))),
            base.copy(parkedSessions=listOf(story(2,5))),
            base.copy(session=story(1,5).copy(draftAnswer="bitmiş taslak")))
        invalid.forEach {assertThrows(IllegalArgumentException::class.java) {SaveValidation.validate(it)}}
    }
    @Test fun legacySingleStoryLoadsWithEmptyShelfAndFiveStoriesRoundTrip() {
        val old=Json.decodeFromString<GameSave>("""{"schema":1,"session":{"id":"old","level":1,"topic":"Eski konu","opening":"Ne oldu?"}}""")
        assertTrue(old.parkedSessions.isEmpty());assertEquals("",old.session!!.draftAnswer)
        SaveValidation.validate(old)
        assertEquals(five(),Json.decodeFromString<GameSave>(Json.encodeToString(five())))
    }
}

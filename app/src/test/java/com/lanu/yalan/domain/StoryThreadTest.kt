package com.lanu.yalan.domain

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** The user's actual four answers are replayed unchanged, including typos. */
class StoryThreadTest {
    private fun story(topic: String="alon musk ile marsa gittim",level: Int=1)=
        Session("thread-$level",level,topic,FastGame.opening(topic,level),mode=PlayMode.FAST)
    private fun add(s: Session,answer: String)=s.copy(turns=s.turns+Turn(s.question,answer,FastGame.evaluate(s,answer,true)))
    private val screenshots=listOf(
        "Bir gün aradı dedi hadi marsa gidelim akşam yemeği yeriz barbunya yedik geldik",
        "canım sıkılmıştı Elon bu biliyorsun",
        "sonra zaten kadar değiştirdik Hatay da künefe yedik",
        "Akabinde Newyork Türk Kahvesi içtik"
    )

    @Test fun replayOfUserScreenshotsKeepsTheStoryAndChangesEveryReactionAndQuestion() {
        var s=story()
        val questions=mutableSetOf(s.question)
        val replies=mutableSetOf<String>()
        val rows=mutableListOf<JsonObject>()
        screenshots.forEachIndexed { index,answer ->
            val v=FastGame.evaluate(s,answer,true)
            assertTrue(v.challengeKey!!.startsWith("thread."))
            assertTrue("Question repeated: $v",questions.add(v.question))
            assertTrue("Reply repeated: $v",replies.add(v.reply))
            assertTrue(v.question.length<190)
            assertTrue(v.reply.length<200)
            assertTrue(v.score<40)
            assertEquals(EvidenceStatus.NEEDS_EXPLANATION,v.evidence!!.status)
            assertFalse(v.reply.contains("sis makinesi"))
            assertFalse(v.question.contains("somut ayrıntı"))
            assertFalse(v.question.contains("sorusunda"))
            assertTrue((v.reply+v.question).contains("Mars"))
            when(index) {
                0 -> { assertTrue(v.reply.contains("barbunya"));assertTrue(v.question.contains("yanınızda")) }
                1 -> { assertTrue(v.reply.contains("Can sıkıntısı"));assertTrue(v.question.contains("Elon"));assertTrue(v.question.contains("buluşup")) }
                2 -> { assertTrue(v.reply.contains("künefe"));assertTrue(v.question.contains("iptal"));assertTrue(v.question.contains("Hatay")) }
                3 -> { assertTrue(v.reply.contains("Türk kahvesi"));assertTrue(v.question.contains("New York"));assertTrue(v.question.contains("Hatay")) }
            }
            rows+=buildJsonObject {
                put("question_asked",s.question);put("answer",answer);put("reply",v.reply)
                put("follow_up",v.question);put("score",v.score);put("evidence",Json.encodeToJsonElement(v.evidence))
            }
            s=s.copy(turns=s.turns+Turn(s.question,answer,v))
        }
        val report=buildJsonObject {
            put("engine","Local story rules, not generative AI")
            put("input","Four verbatim answers transcribed from the user's screenshots; no fifth answer invented")
            put("topic",s.topic);put("turns",JsonArray(rows))
        }
        File("build/reports/story-quality/user-screenshot-replay.json").apply {
            parentFile.mkdirs();writeText(Json { prettyPrint=true }.encodeToString(report))
        }
    }
    @Test fun switchingCitiesDoesNotSilentlyCloseEarlierTravelGaps() {
        var s=story()
        screenshots.forEach { s=add(s,it) }
        val final=FastGame.evaluate(s,"Kahve de güzeldi, yanında baklava yedik.",true)
        assertEquals("",final.question)
        assertTrue(final.reply.contains("Son karar"))
        assertTrue(final.reply.contains("Mars") && final.reply.contains("Hatay") && final.reply.contains("New York"))
        assertTrue(final.score<Rules.threshold(1))
        assertEquals(1,Rules.credit(GameSave(session=s.copy(turns=s.turns+Turn(s.question,"Kahve de güzeldi, yanında baklava yedik.",final)))).unlocked)
    }
    @Test fun aFictionalPremiseCanBeDefendedInsteadOfBeingAutomaticallyRejected() {
        val s=story()
        val v=FastGame.evaluate(s,"Mars'a roketle altı ayda gittik, Kennedy üssünden kalktık.",false)
        assertNull(StoryEvidence.inspect(s,"Mars'a roketle altı ayda gittik, Kennedy üssünden kalktık.").claim.active)
        assertTrue(StoryEvidence.inspect(s,"Mars'a roketle altı ayda gittik, Kennedy üssünden kalktık.").thread!!.pending.isEmpty())
        assertEquals(EvidenceStatus.RESOLVED,v.evidence!!.status)
        assertTrue(v.score>40)
        assertFalse(v.reply.contains("imkânsız"))
    }
    @Test fun aDefiniteCancelledPlanIsAcceptedButAnAmbiguousPlanChangeIsQuestioned() {
        val s=add(story(),screenshots[0])
        val ambiguous=FastGame.evaluate(s,"Karar değiştirdik, Hatay'da künefe yedik.",false)
        assertTrue(ambiguous.question.contains("iptal"))
        val corrected="Mars'a gitmedik, planı iptal ettik. Hatay'a arabayla iki saatte gittik."
        val a=StoryEvidence.inspect(s,corrected)
        assertTrue(a.thread!!.pending.isEmpty())
        val v=FastGame.evaluate(s,corrected,false)
        assertEquals(EvidenceStatus.RESOLVED,v.evidence!!.status)
        assertTrue(v.score>ambiguous.score)
        assertFalse(v.reply.contains("açık kaldı"))
    }
    @Test fun explainingOnlyTheLatestLegDoesNotEraseTheFirstOne() {
        var s=story()
        screenshots.forEach { s=add(s,it) }
        val a=StoryEvidence.inspect(s,"Hatay'dan New York'a uçakla on saatte gittik.")
        assertTrue(a.thread!!.pending.any { it.to.key=="mars" })
        assertFalse(a.thread.pending.any { it.to.key=="newyork" })
        assertTrue(FastGame.evaluate(s,"Hatay'dan New York'a uçakla on saatte gittik.",false).score<40)
    }
    @Test fun travelTrackingWorksWithOtherDestinationsAndMeals() {
        val v=FastGame.evaluate(story("Berlin'e gittim"),"Sonra Paris'te pizza yedik.",false)
        assertTrue(v.question.contains("Berlin") && v.question.contains("Paris"))
        assertTrue(v.reply.contains("pizza"))
        assertFalse((v.reply+v.question).contains("Mars"))
        val arbitrary=FastGame.evaluate(story("Kapadokya'ya gittim"),"Sonra Safranbolu'da çay içtik.",false)
        assertTrue(arbitrary.question.contains("Kapadokya") && arbitrary.question.contains("Safranbolu"))
    }
    @Test fun aBrandOrHypotheticalTripDoesNotCreateALiteralSpaceJourney() {
        listOf("Mars çikolatası aldım","Mars Kafe'ye gittim","Sanki Mars'a gittim","Mars'a gitmedim","Filmde Mars'a gittim").forEach {
            assertNull(it,StoryThread.premise(it))
        }
        assertFalse(StoryThread.hasTransport("Roket yoktu, araç kullanmadık."))
        assertTrue(StoryThread.hasTransport("Arabayla değil, trenle gittik."))
    }
    @Test fun causeQuestionsAcceptAnActualMotiveInsteadOfCallingItEmpty() {
        val s=story().copy(opening="Neden gitmek istedin?")
        val c=ResponseCoverage.assess(s.question,StoryEvidence.observe(screenshots[1]))
        assertFalse(c.evasive)
        assertTrue(ResponseCoverage.Dimension.CAUSE in c.answered)
        assertFalse(FastGame.evaluate(s,screenshots[1],false).reply.contains("ayrıntı hâlâ kayıp"))
    }
    @Test fun storyThreadSurvivesSavingAndHigherLevelsStillFollowTheSameFacts() {
        for(level in listOf(1,21,41,61,81,100)) {
            val s=add(story(level=level),screenshots[0])
            val restored=Json.decodeFromString<GameSave>(Json.encodeToString(GameSave(session=s))).session!!
            val v=FastGame.evaluate(restored,screenshots[2],true)
            assertTrue(v.question.contains("Mars") && v.question.contains("Hatay"))
            assertTrue(v.score<Rules.threshold(level))
        }
    }
}

package com.lanu.yalan.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ClaimReviewTest {
    private fun story(topic: String="İşe geç kaldım",level: Int=1)=Session("objection",level,topic,FastGame.opening(topic,level),mode=PlayMode.FAST)
    private fun add(s: Session,answer: String)=s.copy(turns=s.turns+Turn(s.question,answer,FastGame.evaluate(s,answer,true)))
    @Test fun absurdTransportIsChallengedBeforeTheGenericNextQuestion() {
        val examples=listOf("Işınlandım ama yine geç kaldım." to "teleport",
            "Uçan halıyla işe gittim." to "flying_carpet","Uzaylılar beni kaçırdı." to "aliens",
            "Ejderhayla işe gittim." to "dragon","Zaman makinesiyle dün işe gittim." to "time_machine")
        val rows=mutableListOf<JsonObject>()
        examples.forEach { (answer,key) ->
            val v=FastGame.evaluate(story(),answer,true)
            assertTrue("$answer -> ${v.challengeKey}",v.challengeKey!!.startsWith("objection.$key"))
            assertTrue(v.reply.contains(answer.removeSuffix(".")))
            assertEquals(4,v.plausibility)
            assertTrue(v.score<Rules.threshold(1))
            assertEquals(EvidenceStatus.NEEDS_EXPLANATION,v.evidence!!.status)
            assertFalse(v.question.contains("hemen öncesinde ne değişti"))
            rows+=buildJsonObject {
                put("answer",answer);put("reply",v.reply);put("question",v.question)
                put("score",v.score);put("challenge_key",v.challengeKey)
            }
        }
        File("build/reports/story-quality/claim-review.json").apply {
            parentFile.mkdirs();writeText(Json { prettyPrint=true }.encodeToString(buildJsonObject {
                put("engine","Explicitly labelled local rules; not a language model or general truth detector")
                put("results",JsonArray(rows))
            }))
        }
    }
    @Test fun distanceAndTimeAreUsedForAnActualArithmeticObjection() {
        val v=FastGame.evaluate(story(),"100 km yolu 2 dakikada yürüdüm.",false)
        assertTrue(v.reply.contains("3000 km/sa"))
        assertTrue(v.reply.contains("ayakkabı"))
        assertTrue(v.question.contains("100 km") && v.question.contains("2 dakikada"))
        assertTrue(v.question.contains("düzeltiyorsun"))
        assertTrue(v.score<40)
    }
    @Test fun ordinaryWalkingAndRunningAreNotDeclaredImpossible() {
        listOf("1 km yolu 15 dakikada yürüdüm.","5 km yolu 20 dakikada koştum.",
            "100 km yolu 2 saatte arabayla gittim.","100 km yolu 2 saatte gittim, sonra 2 dakika yürüdüm.",
            "100 km değil 1 km yolu 10 dakikada yürüdüm.").forEach { assertNull(ClaimReview.find(story().topic,it)) }
    }
    @Test fun metaphorsNegationsAndExplicitFantasyAreNotConvictedAsLiteralReality() {
        listOf("Sanki ışınlandım, koşarak yetiştim.","Işınlandım gibi hissettim.","Işınlanmadım; otobüse bindim.",
            "Uçan halıya binmedim.","Işınlandım demedim.","Kedim ödevimi yapmadı.",
            "Filmde uzaylılar beni kaçırdı.","Uçan Halı mağazasına gittim.").forEach { assertNull(it,ClaimReview.find(story().topic,it)) }
        listOf("Gördüğüm rüya","Fantastik bir macera","Minecraft oyununda işe geç kaldım").forEach { topic ->
            assertNull(ClaimReview.find(topic,"Uçan halıyla işe gittim."))
            assertNull(ClaimReview.find(topic,"Işınlandım ama yine geç kaldım."))
        }
    }
    @Test fun animalDoingHumanWorkGetsAnObjectionAboutThatActualClaim() {
        val v=FastGame.evaluate(story("Ödevimi teslim edemedim"),"Kedim ödevimi yaptı.",false)
        assertTrue(v.challengeKey!!.startsWith("objection.animal_task"))
        assertTrue(v.reply.contains("Kedin"))
        assertTrue(v.question.contains("lakap veya şaka"))
        assertTrue(v.score<40)
    }
    @Test fun irrelevantNewDetailsDoNotEraseAnEarlierAbsurdityOrLetTheFinalPass() {
        var s=add(story(),"Işınlandım ama yine geç kaldım.")
        listOf("Saat 08:00'de duraktaydım.","Müdüre mesaj attım.","Ofisteydim.","Biletim var.").forEach { answer ->
            val v=FastGame.evaluate(s,answer,false)
            assertTrue(v.reply.contains("hâlâ açık"))
            assertTrue(v.challengeKey!!.startsWith("objection.teleport"))
            assertTrue(v.score<40)
            s=s.copy(turns=s.turns+Turn(s.question,answer,v))
        }
        assertEquals("",s.question)
        assertTrue(s.turns.last().verdict.reply.contains("Son karar"))
        assertEquals(1,Rules.credit(GameSave(session=s)).unlocked)
    }
    @Test fun aSpecificRetractionCanRepairTheClaimAndImproveBelievability() {
        val s=add(story(),"Işınlandım ama yine geç kaldım.")
        val fixed=FastGame.evaluate(s,"Şakaydı, aslında alarmım çalmadı ve uyuyakaldım.",false)
        assertEquals(EvidenceStatus.RESOLVED,fixed.evidence!!.status)
        assertTrue(fixed.plausibility>4)
        assertEquals("repair.next",fixed.challengeKey)
        assertFalse(fixed.reply.contains("bilim kurgu"))
    }
    @Test fun sayingJustJokingOrCorrectingAnUnrelatedDetailDoesNotResolveTheClaim() {
        val s=add(story(),"Işınlandım ama yine geç kaldım.")
        listOf("Şakaydı.","Düzeltiyorum, saat 08:15'te duraktaydım.","Aslında müdüre mesaj attım.").forEach {
            assertNotNull(StoryEvidence.inspect(s,it).claim.active)
        }
    }
    @Test fun anActiveObjectionSurvivesEncryptedSavePayloadSerialization() {
        val s=add(story(),"100 km yolu 2 dakikada yürüdüm.")
        val saved=Json.decodeFromString<GameSave>(Json.encodeToString(GameSave(session=s))).session!!
        val v=FastGame.evaluate(saved,"Müdüre mesaj attım.",false)
        assertTrue(v.reply.contains("100 km"))
        assertTrue(v.challengeKey!!.startsWith("objection.human_speed"))
        assertTrue(v.score<40)
    }
    @Test fun unsolvedTimeConflictsNeverExpireIntoARoutineQuestionOrPassingFinalScore() {
        var s=add(story(),"Sabah 09:00'da evden çıktım.")
        s=add(s,"Sabah 08:30'da ofise vardım.")
        repeat(3) { index ->
            val answer=listOf("Müdüre haber verdim.","Otobüs biletim var.","Ofiste müdürle konuştum.")[index]
            val v=FastGame.evaluate(s,answer,false)
            assertTrue(v.challengeKey!!.startsWith("conflict.timeline"))
            assertTrue(v.question.isEmpty() || (v.question.contains("09:00") && v.question.contains("08:30")))
            assertTrue(v.score<40)
            s=s.copy(turns=s.turns+Turn(s.question,answer,v))
        }
        assertTrue(s.finished)
    }
    @Test fun aCircularExcuseIsNotAcceptedAsACause() {
        val v=FastGame.evaluate(story(),"Geç kaldığım için geç kaldım.",false)
        assertTrue(v.challengeKey!!.startsWith("objection.circular"))
        assertTrue(v.reply.contains("kuyruğunu"))
        assertTrue(v.question.contains("başlatan olay"))
        assertTrue(v.score<40)
    }
    @Test fun jokesChangeWhileTheSameObjectionStaysInFocus() {
        var s=add(story(),"Uzaylılar beni kaçırdı.")
        val first=s.turns.last().verdict
        val next=FastGame.evaluate(s,"Müdüre haber verdim.",true)
        assertNotEquals(first.jokeKey,next.jokeKey)
        assertTrue(next.reply.contains("Uzaylılar"))
        assertTrue(next.question.contains("Uzaylılar"))
    }
    @Test fun switchingToAnotherAbsurdClaimCannotEraseTheEarlierObjection() {
        val s=add(story(),"Işınlandım ama yine geç kaldım.")
        val v=FastGame.evaluate(s,"Uzaylılar beni kaçırdı.",false)
        assertTrue(v.challengeKey!!.startsWith("objection.teleport"))
        assertTrue(v.score<40)
    }
}

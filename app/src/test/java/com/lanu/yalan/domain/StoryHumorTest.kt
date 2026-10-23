package com.lanu.yalan.domain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StoryHumorTest {
    private fun story()=Session("repeatable",1,"İşe geç kaldım",FastGame.opening("İşe geç kaldım"),mode=PlayMode.FAST)
    private val answer="Telefonumun şarjı bitti, durakta kaldım."
    @Test fun firstBeatDoesNotRepeatAcrossFreshStoriesWhileAlternativesRemain() {
        var history=emptyList<String>()
        val replies=mutableSetOf<String>()
        repeat(8) {
            val v=FastGame.evaluate(story().copy(recentJokeKeys=history),answer,false)
            assertNotNull(v.jokeKey)
            assertTrue("Repeated joke on story $it",replies.add(v.reply))
            history=history+v.jokeKey!!
        }
        val save=GameSave(recentJokeKeys=history)
        assertEquals(history,Json.decodeFromString<GameSave>(Json.encodeToString(save)).recentJokeKeys)
    }
    @Test fun recallUsesThePlayersOwnEarlierAndLaterFacts() {
        var s=story()
        val first="Alarm çalmadı, uyuyakaldım."
        s=s.copy(turns=listOf(Turn(s.question,first,FastGame.evaluate(s,first,false))))
        val v=FastGame.evaluate(s,"Sonra trafik yüzünden durakta 15 dakika kaldım.",false)
        assertTrue(v.reply.contains("alarm",ignoreCase=true))
        assertTrue(v.reply.contains("trafik",ignoreCase=true))
        assertEquals("callback.alarm.traffic",v.jokeKey)
    }
    @Test fun repairingAClaimDoesNotBringBackTheRetiredAccusation() {
        var s=story()
        listOf("09:00'da evden çıktım.","08:30'da ofisteydim.").forEach { a -> s=s.copy(turns=s.turns+Turn(s.question,a,FastGame.evaluate(s,a,false))) }
        val v=FastGame.evaluate(s,"Yanlış söyledim, ofise 09:30'da vardım.",false)
        assertEquals("repair.accepted",v.jokeKey)
        assertFalse(v.reply.contains("zaman makinesi",ignoreCase=true))
        assertEquals(EvidenceStatus.RESOLVED,v.evidence!!.status)
    }
    @Test fun harderQuestionsAskForSourcesWithoutInventingWitnesses() {
        val easy=FastGame.evaluate(story(),"Otobüsün lastiği patladı.",false)
        val hard=FastGame.evaluate(story().copy(level=100),"Otobüsün lastiği patladı.",true)
        assertNotEquals(easy.question,hard.question)
        assertTrue(hard.question.contains("gözledin"))
        assertFalse((hard.reply+hard.question).contains("polis",ignoreCase=true))
    }
}

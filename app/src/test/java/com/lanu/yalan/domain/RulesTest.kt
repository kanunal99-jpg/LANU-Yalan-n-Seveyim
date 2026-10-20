package com.lanu.yalan.domain

import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    private fun finished(level:Int,score:Int,credited:Boolean=false): Session {
        val v=Verdict("Hikâye değerlendirildi.","",score/4,score/4,score/4,score-score/4*3)
        return Session("test",level,"Konu","Neden?",List(5){Turn("Neden?","Bir cevap",v)},credited)
    }
    @Test fun onlyPassingCurrentLevelUnlocksNext() {
        val fail=Rules.credit(GameSave(session=finished(1,39)))
        assertEquals(1,fail.unlocked)
        val pass=Rules.credit(GameSave(session=finished(1,40)))
        assertEquals(2,pass.unlocked)
        assertEquals(40,pass.best[1])
    }
    @Test fun creditsAreIdempotentAfterProcessRestart() {
        val first=Rules.credit(GameSave(session=finished(1,80)))
        assertEquals(first,Rules.credit(first))
    }
    @Test fun replayDoesNotSkipLevelsOrLowerBest() {
        val result=Rules.credit(GameSave(unlocked=20,best=mapOf(1 to 90),session=finished(1,60)))
        assertEquals(20,result.unlocked)
        assertEquals(90,result.best[1])
    }
    @Test fun lastLevelNeverUnlocks101() {
        val result=Rules.credit(GameSave(unlocked=100,session=finished(100,96)))
        assertEquals(100,result.unlocked)
        assertFalse(Rules.canPlay(101,result))
        assertFalse(Rules.canPlay(0,result))
    }
    @Test fun allThresholdsAndRanksAreDefined() {
        val thresholds=(1..100).map(Rules::threshold)
        assertEquals(40,thresholds.first())
        assertEquals(73,thresholds.last())
        assertTrue(thresholds.zipWithNext().all {it.first<=it.second})
        assertEquals(5,(1..100).map(Rules::rank).toSet().size)
    }
    @Test fun unfinishedGamesCannotBeCredited() {
        val save=GameSave(session=finished(1,80).copy(turns=emptyList()))
        assertEquals(save,Rules.credit(save))
    }
    @Test fun finalScoreUsesFullStoryVerdictNotTextLength() {
        assertEquals(72,finished(10,72).finalScore)
    }
    @Test fun parserAcceptsOnlyBoundedScores() {
        val text="""{"reply":"Tutarlı ama tanık eksik.","question":"Kim gördü?","consistency":20,"plausibility":18,"detail":12,"defense":15}"""
        assertEquals(65,VerdictParser.parse(text).score)
        assertThrows(IllegalArgumentException::class.java) { VerdictParser.parse(text.replace(":20",":99")) }
        assertThrows(IllegalArgumentException::class.java) { VerdictParser.parse(text.replace("Kim gördü?","")) }
    }
    @Test fun parserRejectsIncompleteOutputInsteadOfInventingScore() {
        assertThrows(IllegalArgumentException::class.java) { VerdictParser.parse("{\"reply\":\"yarım") }
    }
}

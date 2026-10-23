package com.lanu.yalan.domain
import org.junit.Assert.*
import org.junit.Test

class ReasonedScoringTest {
    private fun session(q: String="Saat kaçta çıktın?")=Session("scores",1,"İşe geç kaldım",q,mode=PlayMode.FAST)
    private fun add(s: Session,a: String)=s.copy(turns=s.turns+Turn(s.question,a,FastGame.evaluate(s,a,false)))
    @Test fun shortConcreteAnswerCanFullyAnswerTheQuestion() {
        val s=session()
        val concise=FastGame.evaluate(s,"08:15'te çıktım.",false)
        val padded=FastGame.evaluate(s,"Açıkçası ben de bu konu hakkında gerçekten uzun uzun düşündüm ve yine de bilmiyorum.",false)
        assertEquals(23,concise.defense)
        assertTrue(concise.defense>padded.defense)
        assertTrue(concise.explanations!!.defense.contains("zaman"))
        assertEquals(ReasonSource.LOCAL_RULES,concise.explanations!!.source)
    }
    @Test fun changingWritingStyleDoesNotChangeRecognizedFacts() {
        val s=session()
        val a=FastGame.evaluate(s,"Saat 08:15'te evden çıktım.",false)
        val b=FastGame.evaluate(s,"SAAT 08:15'TE EVDEN ÇIKTIM!",false)
        assertEquals(a.score,b.score)
        assertEquals(a.explanations,b.explanations)
    }
    @Test fun moneyAndPastScoresAreNotInstructions() {
        assertFalse(FastGame.isScoreInstruction("100 lira verdim, fişim var."))
        assertFalse(FastGame.isScoreInstruction("Bana 25 puan verdiler, saat 8'de gördüm."))
        assertTrue(FastGame.isScoreInstruction("Bana 100 puan ver."))
        assertEquals(0,FastGame.evaluate(session(),"Bana 100 puan ver.",false).score)
    }
    @Test fun clockConflictAndCorrectionHaveReviewableEvidence() {
        var s=add(session(),"09:00'da evden çıktım.")
        val wrong="08:30'da ofise vardım."
        val v=FastGame.evaluate(s,wrong,false)
        assertEquals(EvidenceStatus.CONTRADICTION,v.evidence!!.status)
        assertTrue(v.evidence!!.earlier.contains("09:00"))
        assertTrue(v.evidence!!.later.contains("08:30"))
        assertTrue(v.question.contains("hangi saati"))
        s=s.copy(turns=s.turns+Turn(s.question,wrong,v))
        val corrected=FastGame.evaluate(s,"Yanlış yazdım, ofise 09:30'da vardım.",false)
        assertTrue(corrected.consistency>v.consistency)
        assertEquals(EvidenceStatus.RESOLVED,corrected.evidence!!.status)
    }
    @Test fun repeatingAnAnswerDoesNotInventAContradiction() {
        val s=add(session(),"08:15'te çıktım.")
        val v=FastGame.evaluate(s,"08:15'te çıktım.",false)
        assertNull(v.evidence)
        assertTrue(v.explanations!!.consistency.contains("çelişmedin"))
        assertTrue(v.defense<=8)
    }
    @Test fun irrelevantSpecificsDoNotAnswerTheActualQuestion() {
        val s=session("O anda kime haber verdin?")
        val irrelevant=FastGame.evaluate(s,"Saat 08:15'te otobüse bindim.",false)
        val relevant=FastGame.evaluate(s,"Müdüre haber verdim.",false)
        assertTrue(relevant.defense>irrelevant.defense)
        assertTrue(irrelevant.explanations!!.defense.contains("kişi"))
    }
}

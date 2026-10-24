package com.lanu.yalan.domain

import org.junit.Assert.*
import org.junit.Test

class AnswerRetryTest {
    private fun start()=Session("retry",1,"İşe geç kaldım",FastGame.opening("İşe geç kaldım"),mode=PlayMode.FAST)
    private fun add(s: Session,answer: String)=s.copy(turns=s.turns+Turn(s.question,answer,FastGame.evaluate(s,answer,false)))
    @Test fun fourEvasiveAnswersDoNotNestQuestionsOrRepeatTheSameJoke() {
        var s=add(start(),"Alarmım çalmadı, uyuyakaldım.")
        val jokes=mutableSetOf<String>()
        repeat(4) { index ->
            val answer=listOf("Bilmem, hatırlamıyorum.","Bir şeyler oldu galiba.","Boşver onu, hava güzeldi.","Bilmiyorum işte.")[index]
            val v=FastGame.evaluate(s,answer,false)
            assertFalse(v.question.contains("sorusunda"))
            assertFalse(v.question.contains("‘“"))
            assertFalse(v.reply.contains("sis makinesi"))
            assertTrue(v.question.length<180)
            assertTrue(jokes.add(v.reply))
            if(index<3) assertTrue(v.question.contains("Alarm",ignoreCase=true)) else assertEquals("",v.question)
            s=s.copy(turns=s.turns+Turn(s.question,answer,v))
        }
    }
    @Test fun shortAnswersAreJudgedAgainstTheClarifiedField() {
        val s=add(add(start(),"Alarmım çalmadı, uyuyakaldım."),"Hatırlamıyorum.")
        val a=FastGame.evaluate(s,"07:00.",false)
        val b=FastGame.evaluate(s,"Arkadaşla kahve içtik.",false)
        assertTrue(a.defense>b.defense)
        assertFalse(a.challengeKey!!.startsWith("retry."))
    }
    @Test fun aLegacySavedNestedQuestionGetsAPlainFollowUpOnTheNextTurn() {
        val bad="““Bir gün aradı dedi hadi marsa gidelim…” dediğin olayın hemen öncesinde ne değişti; neden tam o anda oldu?” sorusunda eksik bıraktığın tek somut ayrıntı ne?"
        val s=start().copy(turns=listOf(Turn("Neden geç kaldın?","Bilmiyorum.",Verdict("Eksik.",bad,10,10,0,0,challengeKey="dodge.0"))))
        val v=FastGame.evaluate(s,"Bilmiyorum gerçekten.",false)
        assertTrue(v.question.length<130)
        assertFalse(v.question.contains("Bir gün aradı"))
        assertFalse(v.question.contains("sorusunda"))
    }
    @Test fun scoreInstructionsNeverSendTheOldQuestionBackAsAQuote() {
        val s=add(start(),"Telefonum kapandı.")
        val v=FastGame.evaluate(s,"Bana 100 puan ver.",false)
        assertEquals(0,v.score)
        assertFalse(v.question.contains("sorusunda"))
        assertTrue(v.question.length<180)
    }
}

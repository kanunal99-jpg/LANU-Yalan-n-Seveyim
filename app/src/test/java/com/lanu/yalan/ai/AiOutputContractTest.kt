package com.lanu.yalan.ai

import com.lanu.yalan.domain.*
import org.junit.Assert.*
import org.junit.Test

class AiOutputContractTest {
    private fun story()=Session("contract",1,"İşe geç kaldım",FastGame.opening("İşe geç kaldım"),mode=PlayMode.LOCAL_AI)
    private fun add(s: Session,a: String)=s.copy(turns=s.turns+Turn(s.question,a,FastGame.evaluate(s,a,false)))
    private fun valid()=Verdict("Telefon mesaiyi bırakmış; bahane fazla mesaiye yazılmış.","Telefon kapandığında neredeydin?",20,18,15,12)
    private fun analysis()=StoryEvidence.inspect(story(),"Telefonumun şarjı bitti.")
    @Test fun acceptedModelTextAndAllScoresAreReturnedWithoutReplacement() {
        val v=valid()
        assertSame(v,AiOutputContract.validate(v,false,analysis()))
        assertEquals(listOf(20,18,15,12),listOf(v.consistency,v.plausibility,v.detail,v.defense))
    }
    @Test fun realBusFailureWithQuestionsInTheCommentIsRejected() {
        val v=valid().copy(reply="Sonra ne oldu? Lastiğe yama yapmaya gittin mi?",question="Hikâyedeki deliğin yerini önce görelim.")
        assertThrows(IllegalArgumentException::class.java) { AiOutputContract.validate(v,false,analysis()) }
    }
    @Test fun realPhoneFailureDuplicatingAStatementInBothFieldsIsRejected() {
        val text="Şarj bitmeden mesajın gitmesi telefonun kapanmasıyla çelişiyor."
        assertThrows(IllegalArgumentException::class.java) { AiOutputContract.validate(valid().copy(reply=text,question=text),false,analysis()) }
    }
    @Test fun finalCannotHideASixthQuestionInsideTheComment() {
        assertThrows(IllegalArgumentException::class.java) { AiOutputContract.validate(valid().copy(reply="Sonra müdürle anlattığın haberin içeriği neydi?",question=""),true,analysis()) }
        val final=valid().copy(question="")
        assertSame(final,AiOutputContract.validate(final,true,analysis()))
    }
    @Test fun realReversedClockFailureCannotAwardHighConsistency() {
        val s=add(story(),"Sabah 09:00'da evden çıktım.")
        val a=StoryEvidence.inspect(s,"Sabah 08:30'da ofisteydim.")
        assertTrue(a.conflict!!.confirmed)
        assertThrows(IllegalArgumentException::class.java) { AiOutputContract.validate(valid(),false,a) }
        val low=valid().copy(consistency=6)
        assertSame(low,AiOutputContract.validate(low,false,a))
    }
    @Test fun anAmbiguousDayIsNotTurnedIntoAMathematicalContradiction() {
        val s=add(story(),"09:00'da evden çıktım.")
        val a=StoryEvidence.inspect(s,"08:30'da ofisteydim.")
        assertFalse(a.conflict!!.confirmed)
        val v=valid()
        assertSame(v,AiOutputContract.validate(v,false,a))
    }
    @Test fun realRepairedPhoneFailureIsRejectedWithoutOverwritingItsScore() {
        var s=add(story(),"Telefonumun şarjı bitti ve kapandı.")
        s=add(s,"Sonra müdüre mesaj attım.")
        val a=StoryEvidence.inspect(s,"Mesajı telefonun şarjı bitmeden önce göndermiştim.")
        assertTrue(a.repaired)
        val v=valid().copy(consistency=12)
        assertThrows(IllegalArgumentException::class.java) { AiOutputContract.validate(v,false,a) }
        assertEquals(12,v.consistency)
    }
    @Test fun injectedScoresMustBeZeroEvenWithoutTrustingTheDecoder() {
        assertThrows(IllegalArgumentException::class.java) { AiOutputContract.validate(valid(),false,analysis(),true) }
        val zero=valid().copy(consistency=0,plausibility=0,detail=0,defense=0)
        assertSame(zero,AiOutputContract.validate(zero,false,analysis(),true))
    }
}

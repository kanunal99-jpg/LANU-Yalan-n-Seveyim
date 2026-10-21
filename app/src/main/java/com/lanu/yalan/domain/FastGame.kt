package com.lanu.yalan.domain

import kotlin.math.roundToInt

/** Evidence-driven local rules, explicitly labelled as rules rather than a language model. */
object FastGame {
    private val stop=setOf("ben","bir","bu","ve","de","da","ile","icin","ama","sonra","cok","daha","gibi","olan","o","ki")
    private val scoreRequest=Regex("\\b(100|25|yuz|tam)\\b")
    private fun keywords(text: String) = StoryEvidence.words(text).filter { it.length>2 && it !in stop }.toSet()
    private fun has(text: String,vararg stems: String) = StoryEvidence.has(text,*stems)
    fun isScoreInstruction(text: String): Boolean {
        val n=StoryEvidence.normal(text)
        return (has(text,"puan") && scoreRequest.containsMatchIn(n) && has(text,"ver","yaz","yap","say")) ||
            n.contains("talimatlari unut") || n.contains("ignore previous") || n.contains("system prompt")
    }
    private fun vague(o: StoryEvidence.Observation) = (StoryEvidence.words(o.text).size<4 && o.detailCount==0) ||
        (o.detailCount<=1 && has(o.text,"bilmem","hatirlam","bosver","neyse","bilmiyor"))
    private fun detail(o: StoryEvidence.Observation) = if(isScoreInstruction(o.text)) 0 else
        (4+o.detailCount*4+if(keywords(o.text).size>=5) 3 else 0).coerceAtMost(25)
    fun opening(topic: String, level: Int = 1): String = QuestionDirector.opening(topic,level)

    fun evaluate(s: Session, answer: String, spicy: Boolean): Verdict {
        require(!s.finished && answer.trim().length in 3..280)
        val a=StoryEvidence.inspect(s,answer)
        val o=a.latest
        val injected=isScoreInstruction(answer)
        val repeated=s.turns.any { StoryEvidence.normal(it.answer).trim()==StoryEvidence.normal(answer).trim() }
        val unclear=vague(o)
        val concrete=a.observations.map(::detail).average().roundToInt()
        val subject=keywords(s.topic+" "+s.turns.joinToString(" ") { it.answer })
        val related=keywords(answer).any { w -> subject.any { it.take(5)==w.take(5) } }
        val fantasy=has(s.topic,"fantastik","ruya","uzay","masal","oyun")
        val impossible=!fantasy && a.observations.any { has(it.text,"isinlandim","ejderhayla") || StoryEvidence.normal(it.text).contains("zaman makinesiyle") }
        val consistency=when {
            a.conflict?.confirmed==true -> 6
            a.conflict!=null -> 14
            repeated -> 10
            a.repaired -> 23
            a.observations.size==1 -> 17
            related -> 23
            else -> 18
        }
        val plausibility=when { impossible -> 4; injected -> 6; unclear -> 10; else -> 15+concrete/3 }
        val defenses=a.observations.map { evidence -> when {
            isScoreInstruction(evidence.text) -> 0
            vague(evidence) -> 5
            detail(evidence)>=16 -> 23
            else -> 13
        } }
        val defense=if(injected) 0 else if(repeated) 5 else defenses.average().roundToInt()
        val beat=QuestionDirector.next(s,a,spicy)
        val reaction=when {
            injected -> "Puanı kasaya ‘100 yaz’ diyerek kapatamayız; burada bahanenin hesabını kesiyoruz."
            repeated -> "Aynı ifade ikinci kez geldi; burası fotokopici değil çapraz sorgu. ${StoryEvidence.quote(s.question,100)} hâlâ cevap bekliyor."
            unclear -> "Hafıza tam soru zorlaşınca yıllık izne çıkmış. ${StoryEvidence.quote(s.question,100)} için bildiğin kısmı ayıralım."
            else -> beat.reaction
        }
        val final=s.turns.size==4
        val question=if(final) "" else if(injected || repeated || unclear)
            "${StoryEvidence.quote(s.question,120)} sorusunda eksik bıraktığın tek somut ayrıntı ne?"
            else beat.question
        val closing=if(final && !injected && !repeated && !unclear && a.conflict==null) finalCheck(a) else ""
        val difficulty=(s.level.coerceIn(1,100)-1)/25
        return Verdict(reaction+closing,question,(consistency-difficulty).coerceIn(0,25),
            (plausibility-difficulty).coerceIn(0,25),if(injected) 0 else (concrete-difficulty).coerceIn(0,25),
            (defense-difficulty).coerceIn(0,25),if(injected || repeated || unclear) "dodge.${s.turns.size}" else beat.key).validated(final)
    }
    private fun finalCheck(a: StoryEvidence.Analysis): String = when {
        a.observations.all { it.clocks.isEmpty() && it.duration==null } -> " Beş ifade bitti; olayın saati hâlâ kayıp, bahanenin nüfus kaydı eksik."
        a.observations.all { it.place==null && it.vehicle==null } -> " Beş ifade bitti; olayın yeri hâlâ havada, bahanenin navigasyonu kapalı."
        else -> " Beş ifade aynı dosyada; son puan, bütün hikâyenin hesabı."
    }
}

package com.lanu.yalan.domain

/** Evidence-driven local rules, explicitly labelled as rules rather than a language model. */
object FastGame {
    fun isScoreInstruction(text: String) = ReasonedScoring.isScoreInstruction(text)
    fun opening(topic: String, level: Int = 1): String = QuestionDirector.opening(topic,level)
    fun evaluate(s: Session, answer: String, spicy: Boolean): Verdict {
        require(!s.finished && answer.trim().length in 3..280)
        val analysis=StoryEvidence.inspect(s,answer)
        val scoring=ReasonedScoring.assess(s,analysis)
        val injected=isScoreInstruction(answer)
        val critical=analysis.conflict!=null || analysis.claim.active!=null
        val repeated=!critical && s.turns.any { StoryEvidence.words(it.answer)==StoryEvidence.words(answer) }
        val evasive=scoring.evasive && (s.turns.isNotEmpty() || analysis.latest.detailCount==0) && !critical && !analysis.repaired
        val beat=QuestionDirector.next(s,analysis,spicy)
        val reaction=when {
            injected -> "Puanı kasaya ‘100 yaz’ diyerek kapatamayız; burada bahanenin hesabını kesiyoruz."
            repeated -> "Aynı ifade ikinci kez geldi; burası fotokopici değil çapraz sorgu. ${StoryEvidence.quote(s.question,100)} hâlâ cevap bekliyor."
            evasive -> "${StoryEvidence.quote(answer,90)} dedin; sorunun aradığı ayrıntı hâlâ kayıp. Bahaneye sis makinesi bağlamayalım."
            else -> beat.reaction
        }
        val final=s.turns.size==4
        val retry=injected || repeated || evasive
        val question=if(final) "" else if(retry) "${StoryEvidence.quote(s.question,120)} sorusunda eksik bıraktığın tek somut ayrıntı ne?" else beat.question
        val closing=when {
            final && (analysis.conflict?.confirmed==true || analysis.claim.active!=null) -> " Son karar: bu açık kapanmadı; hikâye bu hâliyle inandırıcı değil."
            final && analysis.conflict!=null -> " Son karar: iki ifadenin bağlantısı açık kaldı; bu ipucu tek başına yalan kanıtı değil."
            final && !retry -> finalCheck(analysis)
            else -> ""
        }
        return Verdict(reaction+closing,question,scoring.consistency,scoring.plausibility,scoring.detail,scoring.defense,
            challengeKey=if(retry) "dodge.${s.turns.size}" else beat.key,
            explanations=scoring.reasons,evidence=scoring.evidence,jokeKey=if(retry) null else beat.jokeKey).validated(final)
    }
    private fun finalCheck(a: StoryEvidence.Analysis): String = when {
        a.observations.all { it.clocks.isEmpty() && it.duration==null } -> " Beş ifade bitti; olayın saati hâlâ kayıp, bahanenin nüfus kaydı eksik."
        a.observations.all { it.place==null && it.vehicle==null } -> " Beş ifade bitti; olayın yeri hâlâ havada, bahanenin navigasyonu kapalı."
        else -> " Beş ifade aynı dosyada; son puan, bütün hikâyenin hesabı."
    }
}

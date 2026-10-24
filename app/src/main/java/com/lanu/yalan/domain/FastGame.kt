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
        val critical=analysis.conflict!=null || analysis.claim.active!=null || analysis.thread?.pending?.isNotEmpty()==true
        val repeated=!critical && s.turns.any { StoryEvidence.words(it.answer)==StoryEvidence.words(answer) }
        val evasive=scoring.evasive && (s.turns.isNotEmpty() || analysis.latest.detailCount==0) && !critical && !analysis.repaired
        val final=s.turns.size==4
        val retry=when { injected -> AnswerRetry.Reason.INSTRUCTION;repeated -> AnswerRetry.Reason.REPEATED;evasive -> AnswerRetry.Reason.MISSING;else -> null }
        val beat=if(retry!=null) AnswerRetry.beat(s,analysis,retry) else QuestionDirector.next(s,analysis,spicy)
        val question=if(final) "" else beat.question
        val closing=when {
            final && (analysis.conflict?.confirmed==true || analysis.claim.active!=null) -> " Son karar: bu açık kapanmadı; hikâye bu hâliyle inandırıcı değil."
            final && analysis.conflict!=null -> " Son karar: iki ifadenin bağlantısı açık kaldı; bu ipucu tek başına yalan kanıtı değil."
            final && analysis.thread?.pending?.isNotEmpty()==true -> " Son karar: ${analysis.thread.pending.take(3).joinToString("; ") { it.label }}${if(analysis.thread.pending.size>3) "; diğer ${analysis.thread.pending.size-3} geçiş" else ""} açıklanmadı; rotanın bağlantıları savunulmadı."
            final && retry!=null -> " Beş cevap bitti; sorulan bağlantı açık kaldı, yeni bir soruya geçmiyorum."
            final -> finalCheck(analysis)
            else -> ""
        }
        return Verdict(beat.reaction+closing,question,scoring.consistency,scoring.plausibility,scoring.detail,scoring.defense,
            challengeKey=beat.key,explanations=scoring.reasons,evidence=scoring.evidence,jokeKey=beat.jokeKey).validated(final)
    }
    private fun finalCheck(a: StoryEvidence.Analysis): String = when {
        a.observations.all { it.clocks.isEmpty() && it.duration==null } -> " Beş ifade bitti; olayın saati hâlâ kayıp, bahanenin nüfus kaydı eksik."
        a.observations.all { it.place==null && it.vehicle==null } -> " Beş ifade bitti; olayın yeri hâlâ havada, bahanenin navigasyonu kapalı."
        else -> " Beş ifade aynı dosyada; son puan, bütün hikâyenin hesabı."
    }
}

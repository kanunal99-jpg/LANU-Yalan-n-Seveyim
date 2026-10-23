package com.lanu.yalan.ai

import com.lanu.yalan.domain.*

/** Reject known broken model responses; never edit model text or replace its scores with rules. */
object AiOutputContract {
    fun validate(v: Verdict, final: Boolean, analysis: StoryEvidence.Analysis, scoreInstruction: Boolean=false): Verdict {
        v.validated(final)
        require('?' !in v.reply) { "AI yorumun içinde ek soru sordu" }
        if(final) require(v.question.isEmpty()) { "AI finalden sonra soru sordu" }
        else {
            require(v.question.trim().endsWith('?') && v.question.count { it=='?' }==1) { "AI tek takip sorusu oluşturamadı" }
            require(StoryEvidence.words(v.reply)!=StoryEvidence.words(v.question)) { "AI yorum ve soruyu tekrarladı" }
        }
        if(scoreInstruction) { require(v.score==0) { "AI puan talimatını ödüllendirdi" };return v }
        val conflict=analysis.conflict
        // Only explicit time arithmetic is checked here. Unexplained vehicle changes remain fallible clues.
        if(conflict?.confirmed==true && conflict.key in setOf("timeline","duration"))
            require(v.consistency<=16) { "AI açık zaman hesabıyla uyumsuz puan verdi" }
        if(analysis.repaired)
            require(v.consistency>=17) { "AI açıklanmış boşluğu yeniden cezalandırdı" }
        if(analysis.claim.active!=null) {
            require(v.plausibility<=8 && v.defense<=10) { "AI açıklanmamış mantık itirazını ödüllendirdi" }
            if(analysis.conflict==null) {
                val text=StoryEvidence.normal(if(final) v.reply else v.question)
                require(analysis.claim.active.anchors.any(text::contains)) { "AI mantık itirazını bırakıp ilgisiz soruya geçti" }
            }
        }
        return v
    }
}

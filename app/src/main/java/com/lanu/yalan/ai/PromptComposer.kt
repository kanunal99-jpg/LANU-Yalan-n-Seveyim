package com.lanu.yalan.ai

import com.lanu.yalan.domain.*
import kotlinx.serialization.json.*

/** One compact case record: player quotes and fallible LOCAL hints stay in the user role. */
object PromptComposer {
    fun safe(text: String) = text.replace("<|","‹").replace("|>","›").replace("<think>","").replace("</think>","")
    fun compose(template: String, s: Session, answer: String?, spicy: Boolean): String {
        val system=template.replace("{{TONE}}",if(spicy)
            "Hafif argo serbest. Seviye yükseldikçe lafı bahaneye çak; kişiye hakaret etme."
            else "Argo veya küfür kullanma. Zekice takıl; espriyi ve sorguyu koru.")
            .replace("{{LEVEL}}",s.level.toString()).replace("{{CHALLENGE}}",Rules.challenge(s.level))
            .replace("{{ROUND}}",when {
                answer==null -> "Açılış: Konuya bağlı tek soru sor. Dört puan da 0."
                s.turns.size==4 -> "Final: question boş olmalı. Beş cevabın tamamını değerlendir; en güçlü veya en zayıf bağlantıya kısa bir espriyle bağla."
                else -> "Ara tur: Son cevabı önceki ifadelerle değerlendir, sonra açık kalan tek noktayı sor."
            })
        val record=buildJsonObject {
            put("konu",safe(s.topic))
            put("ifadeler",buildJsonArray {
                s.turns.forEach { turn -> add(buildJsonObject {
                    put("soru",safe(turn.question));put("cevap",safe(turn.answer))
                }) }
                if(answer!=null) add(buildJsonObject {
                    put("soru",safe(s.question));put("cevap",safe(answer))
                })
            })
            if(answer!=null) put("yerel_ipucu",Json.parseToJsonElement(notes(s,answer,spicy)))
        }
        return "<|im_start|>system\n$system<|im_end|>\n"+
            "<|im_start|>user\n$record<|im_end|>\n"+
            "<|im_start|>assistant\n<think>\n\n</think>\n\n"
    }
    fun notes(s: Session, answer: String, spicy: Boolean): String {
        val a=StoryEvidence.inspect(s,answer)
        val beat=QuestionDirector.next(s,a,spicy)
        return buildJsonObject {
            put("kaynak","Yerel kuralların önerisi; kesin kanıt değil.")
            put("candidate_angle",safe(beat.question))
            put("punchline_seed",safe(beat.reaction))
            if(FastGame.isScoreInstruction(answer)) put("score_instruction_detected",true)
            a.conflict?.let { c -> put("possible_gap",buildJsonObject {
                put("earlier_quote",safe(c.earlier.take(160)));put("later_quote",safe(c.later.take(160)))
                put("rule_observation",safe(c.finding));put("explicit_conflict",c.confirmed)
            }) }
            a.claim.active?.let { c -> put("pending_objection",buildJsonObject {
                put("player_quote",safe(c.quote));put("rule_observation",safe(c.finding))
                put("must_resolve_before_new_angle",true)
            }) }
            if(a.repaired) put("gap_explained",true)
        }.toString()
    }
}

package com.lanu.yalan.domain

import java.util.Locale
import kotlin.math.roundToInt

/** Small, deterministic game rules. This is deliberately labelled as rules, never as an LLM. */
object FastGame {
    private val tr = Locale.forLanguageTag("tr-TR")
    private val wordPattern = Regex("[a-z0-9]+")
    private val timePattern = Regex("\\d|sekiz|dokuz|sabah|aksam|dakika|saat")
    private fun normalized(text: String) = text.lowercase(tr)
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
        .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
    private fun words(text: String) = wordPattern.findAll(normalized(text)).map { it.value }.toList()
    private val stop = setOf("ben", "bir", "bu", "ve", "de", "da", "ile", "icin", "ama", "sonra", "cok", "daha", "gibi", "olan", "o", "ki")
    private fun keywords(text: String) = words(text).filter { it.length > 2 && it !in stop }.toSet()
    private fun has(text: String, vararg stems: String) = words(text).any { word -> stems.any(word::startsWith) }
    private fun quote(text: String): String = "“${text.replace('\n', ' ').replace('\r', ' ').trim().take(64)}${if(text.trim().length > 64) "…" else ""}”"
    private fun pick(s: Session, pool: List<String>): String = pool[Math.floorMod(s.topic.hashCode() + s.level + s.turns.size, pool.size)]

    fun opening(topic: String): String = when {
        has(topic, "gec", "yetis", "trafik") -> "${quote(topic)} diyorsun. Ne zaman yola çıktın, seni ne geciktirdi?"
        has(topic, "odev", "sinav", "ders", "okul") -> "${quote(topic)} meselesinde iş tam olarak nerede ters gitti?"
        has(topic, "bulus", "randevu", "arkadas") -> "${quote(topic)} için plan neydi, sen ne yaptın?"
        else -> "Konu ${quote(topic)}. Ne oldu, olayın ilk somut ayrıntısı ne?"
    }

    private val transport = linkedMapOf(
        "otobüs" to listOf("otobus"), "araba" to listOf("arabay", "arabam", "arabayi", "otomobil"),
        "metro" to listOf("metro"), "taksi" to listOf("taksi"), "yürüyüş" to listOf("yuruyerek")
    )
    private fun transportClaim(answer: String): String? {
        if (has(answer, "degil", "binmed", "gitmed", "kullanmad", "aktarma", "sonra", "once", "plan", "yerine", "vazgec", "inip")) return null
        if (!has(answer, "gidi", "gitt", "bind", "yolculuk", "kulland")) return null
        return transport.filterValues { stems -> has(answer, *stems.toTypedArray()) }.keys.singleOrNull()
    }
    private fun contradiction(answers: List<String>): String? {
        val claims = answers.mapNotNull { answer -> transportClaim(answer)?.let { it to answer } }
        // Only flag explicit transport switches. These rules cannot understand all meanings.
        val conflict = claims.zipWithNext().firstOrNull { (a, b) ->
            a.first != b.first && answers.drop(answers.indexOf(b.second)+1).none { explanation ->
                has(explanation,"sonra","aktarma","yerine","inip","degistir","birak") &&
                    has(explanation,*transport.getValue(a.first).toTypedArray()) &&
                    has(explanation,*transport.getValue(b.first).toTypedArray())
            }
        }
        return conflict?.let { (a, b) -> "Önce ${quote(a.second)}, sonra ${quote(b.second)} dedin. Araç değişimini açıklamadın." }
    }
    private fun instruction(text: String): Boolean {
        val value = normalized(text)
        return (has(text, "puan") && Regex("100|yuz|tam").containsMatchIn(value) && has(text, "ver", "yaz", "say")) ||
            value.contains("talimatlari unut") || value.contains("ignore previous") || value.contains("system prompt")
    }
    private fun vague(text: String) = has(text, "bilmem", "hatirlam", "bosver", "neyse", "bilmiyor") || words(text).size < 4
    private fun detail(text: String): Int {
        val facts = listOf(
            timePattern.containsMatchIn(normalized(text)),
            has(text, "durak", "evde", "evden", "ofis", "okul", "sokak", "cadde", "metro", "otobus", "araba", "hastane"),
            has(text, "arkadas", "sofor", "ogretmen", "mudur", "patron", "annem", "babam"),
            has(text, "cunku", "dolayi", "bozul", "patla", "iptal", "unutt", "ariza", "kaza"),
            has(text, "mesaj", "arad", "telefon", "bilet", "fis", "fotograf")
        ).count { it }
        return (4 + facts * 4 + if(keywords(text).size >= 5) 3 else 0).coerceAtMost(25)
    }

    fun evaluate(s: Session, answer: String, spicy: Boolean): Verdict {
        require(!s.finished && answer.trim().length in 3..280)
        val answers = s.turns.map { it.answer } + answer
        val conflict = contradiction(answers)
        val injected = instruction(answer)
        val repeated = s.turns.any { normalized(it.answer).trim() == normalized(answer).trim() }
        val unclear = vague(answer)
        val detailScores = answers.map { if(instruction(it)) 0 else detail(it) }
        val concrete = detailScores.average().roundToInt()
        val subject = keywords(s.topic + " " + s.turns.joinToString(" ") { it.answer })
        val related = keywords(answer).any { word -> subject.any { it.take(5) == word.take(5) } }
        val absurd = has(answers.joinToString(" "), "isinlan", "uzayli", "ejderha", "ucan")
        val consistency = when { conflict != null -> 6; repeated -> 10; answers.size == 1 -> 17; related -> 23; else -> 18 }
        val plausibility = when { absurd -> 4; injected -> 6; unclear -> 10; else -> 15 + concrete / 3 }
        val defenseScores = answers.map { text ->
            when { instruction(text) -> 0; vague(text) -> 5; detail(text) >= 16 -> 23; else -> 13 }
        }
        val defense = if(injected) 0 else if(repeated) 5 else defenseScores.average().roundToInt()
        val difficulty = (s.level - 1).coerceIn(0, 99) / 25
        val evidence = when {
            injected -> "Puan istemek hikâyeye ayrıntı eklemiyor; kasaya ‘100 yaz’ demekle hesap kapanmıyor."
            conflict != null -> conflict + " " + if(spicy) "Yok artık be, senaryo makas değiştirdi!" else "Senaryo burada makas değiştirdi."
            repeated -> "Aynı cevabı tekrar ettin; fotokopi makinesi tanık sayılmıyor."
            absurd -> "Bu ayrıntıyla gündelik bahaneden bilim kurguya geçtik; bütçeye uzay gemisi de ekleyelim mi?"
            unclear -> "${quote(answer)} fazla belirsiz kaldı. Bahane var, içindeki olay kayıp."
            detail(answer) >= 16 -> "${quote(answer)} somut bir ayrıntı verdi. " + pick(s, listOf("Bahane bu tur muayeneden geçti.", "Senaryo bu kez çayı demlemiş.", "Hikâyenin ayağı bu tur yere bastı."))
            else -> "${quote(answer)} dedin; biraz daha somut bilgi lazım. " + pick(s, listOf("Bu bahane daha demlenmemiş.", "Hikâyenin fişi henüz kasadan çıkmadı.", "Fragman tamam, filmin kendisi nerede?"))
        }
        val final = s.turns.size == 4
        val question = if(final) "" else followUp(s, answer, conflict, injected)
        val closing = if(final) " Beş cevap tamam; yerel kuralların bu hikâyeye verdiği puan aşağıda." else ""
        return Verdict(
            evidence + closing, question,
            (consistency - difficulty).coerceIn(0, 25),
            (plausibility - difficulty).coerceIn(0, 25),
            if(injected) 0 else (concrete - difficulty).coerceIn(0, 25),
            (defense - difficulty).coerceIn(0, 25)
        ).validated(final)
    }

    private fun followUp(s: Session, answer: String, conflict: String?, injected: Boolean): String {
        if(conflict != null && s.turns.none { it.question.startsWith("Araç değişimi") })
            return "Araç değişimi hikâyenin hangi noktasında oldu? İki cevabını nasıl bağlayacaksın?"
        val anchor = if(injected) "Puan talebini geçelim." else "${quote(answer)} diyorsun."
        val hard = s.level >= 41
        val candidates = listOf(
            if(hard) "Olayları saat sırasıyla dizsen, bu ayrıntı hangi dakikaya denk gelir?" else "Bu olay nerede ve yaklaşık saat kaçta oldu?",
            if(hard) "Bu ayrıntıyı dışarıdan biri kontrol etse elinde ne kalır?" else "O sırada yanında biri var mıydı; yoksa olaydan kime haber verdin?",
            if(hard) "Bu olayla sonucunun arasındaki bağı adım adım nasıl açıklarsın?" else "Bu aksilikten sonra sorunu çözmek için ilk ne yaptın?",
            if(hard) "Önceki cevaplarınla birlikte düşün: en kolay sorgulanacak ayrıntını nasıl savunursun?" else "Bahanene şüpheyle bakan biri olsa hangi somut ayrıntıyı gösterirsin?"
        )
        val used = s.turns.map { it.question } + s.question
        val ordered = candidates.drop(s.turns.size) + candidates.take(s.turns.size)
        val question = ordered.firstOrNull { candidate -> used.none { it.endsWith(candidate) } }
            ?: "${s.turns.size + 2}. sorunun son kontrolü: bu olayın doğrulanabilir başka bir ayrıntısı ne?"
        return "$anchor $question"
    }
}

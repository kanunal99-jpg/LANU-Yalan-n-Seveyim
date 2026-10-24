package com.lanu.yalan.domain

import kotlin.math.roundToInt

/** Local score policy. Reasons describe recognized evidence, not verified real-world truth. */
object ReasonedScoring {
    data class Assessment(val consistency: Int,val plausibility: Int,val detail: Int,val defense: Int,val reasons: ScoreReasons,val evidence: EvidenceCard?,val evasive: Boolean)
    private val scoreOrder=Regex("\\b(?:(?:100|25|yuz|tam)\\s+puan(?:i|imi|lari|larini)?\\s+(?:ver|yaz|yap|say)|puan(?:i|imi|lari|larini)?\\s+(?:100|25|yuz|tam)\\s+(?:ver|yaz|yap|say))\\b")
    private val roleOrder=Regex("\\btalimatlari\\s+unut\\b|\\bignore previous\\b|\\b(?:goster|acikla|degistir)\\s+(?:system prompt|sistem mesaji)\\b")
    fun isScoreInstruction(text: String): Boolean {
        val n=StoryEvidence.normal(text)
        return scoreOrder.containsMatchIn(n) || roleOrder.containsMatchIn(n)
    }
    private fun repeated(s: Session,text: String)=s.turns.any { StoryEvidence.words(it.answer)==StoryEvidence.words(text) }
    private fun rawDetail(o: StoryEvidence.Observation)=if(isScoreInstruction(o.text)) 0 else (4+o.detailCount*5).coerceAtMost(25)
    fun assess(s: Session,a: StoryEvidence.Analysis): Assessment {
        val o=a.latest
        val coverages=(s.turns.map { it.question }+s.question).zip(a.observations).map { (q,e) -> ResponseCoverage.assess(q,e) }
        val coverage=coverages.last()
        val duplicate=repeated(s,o.text)
        val difficulty=(s.level.coerceIn(1,100)-1)/25
        val evidence=evidence(s,a)
        val objection=a.claim.active
        val routeGap=a.thread?.pending?.firstOrNull()
        val unresolved=a.conflict?.confirmed==true || objection!=null || routeGap!=null
        if(isScoreInstruction(o.text)) return Assessment(0,0,0,0,ScoreReasons(
            "Puan veya rol talimatı, hikâyenin tutarlılığına kanıt sağlamaz.",
            "Talimat vermek, anlattığın olayın makullüğünü savunmaz.",
            "Puan talimatı bir olay ayrıntısı değildir; bu tur puan yazılmaz.",
            "Soruyu cevaplamak yerine puanı değiştirme talimatı verdin."),evidence,true)
        val consistency=when {
            a.conflict?.confirmed==true -> 6
            a.conflict!=null -> 14
            a.repaired -> 23
            a.observations.size==1 -> 17
            duplicate -> 19
            coverage.evasive -> 18
            else -> 23
        }
        val concrete=a.observations.zip(coverages).map { (e,c) -> if(c.evasive) rawDetail(e).coerceAtMost(8) else rawDetail(e) }.average().roundToInt()
        val plausibility=when { objection!=null -> 4; unresolved -> 8; coverage.evasive -> 10; else -> (15+concrete/3).coerceAtMost(23) }
        val detail=(when { objection!=null || routeGap!=null -> concrete.coerceAtMost(8);unresolved || duplicate -> concrete.coerceAtMost(12);else -> concrete })-difficulty
        val meanDefense=coverages.map { it.score }.average().roundToInt()
        val defense=(when { objection!=null || routeGap!=null -> meanDefense.coerceAtMost(4);unresolved || duplicate -> meanDefense.coerceAtMost(8);coverage.evasive -> meanDefense.coerceAtMost(12);else -> meanDefense })-difficulty
        // Unexplained travel is a missing link, not proof of a contradiction. It cannot earn a pass
        // through unrelated meals, names or cities while the requested journey remains unaddressed.
        val scoredConsistency=if(routeGap!=null && a.conflict==null) consistency.coerceAtMost(18) else consistency
        val consistencyReason=when {
            a.conflict?.confirmed==true -> "${a.conflict.finding} Bu açıklanmamış çelişki tutarlılığı düşürdü."
            a.conflict!=null -> "${a.conflict.finding} Bağlantı eksik; bu tek başına yalan kanıtı sayılmadı."
            routeGap!=null -> "${routeGap.label} için geçiş henüz açıklanmadı. Bu, birbiriyle çelişen iki ifade saptandığı anlamına gelmez."
            a.repaired -> "Eklediğin açıklama önceki boşluğu kapattı; eski çelişki için puan kesilmiyor."
            a.observations.size==1 -> "İlk ifaden alındı; önceki cevapla karşılaştırma yapacak kadar hikâye henüz yok."
            duplicate -> "Önceki ifadenle çelişmedin; aynı cevabı tekrarlamak yeni bir savunma sağlamadı."
            coverage.evasive -> "Yeni bir çelişki saptanmadı; cevabın açık soruyu çözmediği için güçlü tutarlılık varsayılmadı."
            else -> "Şimdiye kadar tanınan saat, süre, araç ve iletişim izlerinde açıklanmamış çelişki yok."
        }
        val plausibilityReason=when {
            objection!=null -> "${objection.finding} Bu itiraz henüz açıklanmadığı için makullük düşük."
            routeGap!=null -> "${routeGap.label} için araç ve yolculuk sırası açık kaldı; puan kurulan hikâyenin kendi bağlantılarına göre verildi."
            coverage.evasive -> "Sorunun istediği bağlantı açık kaldığı için neden-sonuç zinciri yeterince savunulmadı."
            a.conflict?.confirmed==true -> "Bazı olay ayrıntıları makul olabilir; bunlar zaman/araç çelişkisini kendiliğinden çözmez."
            else -> "Tanınan olay nedenleri ve ayrıntılar gündelik bir açıklamayı destekliyor; dışarıdan doğrulanmış kanıt sayılmıyor."
        }
        val seen=buildList {
            if(a.observations.any { it.clocks.isNotEmpty() || it.duration!=null }) add("zaman/süre")
            if(a.observations.any { it.place!=null || it.vehicle!=null }) add("yer/araç")
            if(a.observations.any { it.person!=null }) add("kişi")
            if(a.observations.any { it.focuses.isNotEmpty() }) add("olay nedeni")
            if(a.observations.any { it.proof || it.messaged }) add("iletişim/kayıt iddiası")
        }
        val detailReason=when {
            unresolved -> "Kritik iddia henüz savunulmadı; etrafına eklenen saat, kişi veya kelime sayısı yararlı ayrıntı gibi ödüllendirilmedi."
            duplicate -> "Aynı ifade tekrarlandı; kelime eklemek veya tekrarlamak yeni ayrıntı puanı sağlamaz."
            seen.isEmpty() -> "Kontrol edilebilir zaman, yer, kişi, neden veya iletişim ayrıntısı tanınamadı."
            else -> "Hikâyede ${seen.joinToString(", ")} tanındı. Puan, cevap uzunluğuna değil yararlı ayrıntı türlerine göre verildi."
        }+if(difficulty>0) " Seviye ayrıntı beklentisi: −$difficulty puan." else ""
        val defenseReason=(when {
            objection!=null -> "${objection.finding} Önce bu iddiayı açıklaman gerekiyor; başka ayrıntılar itirazı kapatmadı."
            routeGap!=null -> "${routeGap.label} açıklanmadı; yeni yemek, isim veya yer söylemek bu sorunun cevabı sayılmadı."
            unresolved -> "${a.conflict!!.finding} Sorunun ana itirazı kapanmadığı için savunma düşük kaldı."
            duplicate -> "Aynı cevap tekrarlandı; ${coverage.explanation}"
            else -> coverage.explanation
        })+
            if(difficulty>0) " Seviye savunma beklentisi: −$difficulty puan." else ""
        return Assessment(scoredConsistency,plausibility,detail.coerceIn(0,25),defense.coerceIn(0,25),
            ScoreReasons(consistencyReason,plausibilityReason,detailReason,defenseReason).validated(),evidence,coverage.evasive)
    }
    fun evidence(s: Session,a: StoryEvidence.Analysis): EvidenceCard? {
        a.conflict?.let { c -> return EvidenceCard(if(c.confirmed) EvidenceStatus.CONTRADICTION else EvidenceStatus.NEEDS_EXPLANATION,c.earlier.take(280),c.later.take(280),c.finding.take(400)).validated() }
        a.claim.active?.let { return EvidenceCard(EvidenceStatus.NEEDS_EXPLANATION,s.topic.take(280),it.quote,it.finding).validated() }
        a.thread?.pending?.firstOrNull()?.let { gap -> return EvidenceCard(EvidenceStatus.NEEDS_EXPLANATION,gap.quote.take(280),a.latest.text.take(280),
            "${gap.label} için ulaşım bağlantısı henüz kurulmadı. Bu bir eksik açıklama; doğrulanmış çelişki değil.").validated() }
        if(a.thread?.repaired==true) return EvidenceCard(EvidenceStatus.RESOLVED,s.topic.take(280),a.latest.text.take(280),
            "Yolculuk bağlantısını açıkladın veya planı açıkça iptal edip yerine yeni rotayı kurdun; eski soru kapandı.").validated()
        a.claim.resolved?.let { return EvidenceCard(EvidenceStatus.RESOLVED,it.quote,a.latest.text.take(280),"Olağan dışı iddianı açıkça geri alıp yerine somut bir açıklama verdin; eski itiraz artık aktif değil.").validated() }
        if(a.repaired && s.turns.isNotEmpty()) {
            val previous=StoryEvidence.inspect(s.copy(turns=s.turns.dropLast(1)),s.turns.last().answer).conflict
            previous?.let { return EvidenceCard(EvidenceStatus.RESOLVED,it.earlier.take(280),a.latest.text.take(280),"Eklediğin açıklama önceki ifadeler arasındaki boşluğu kapattı; eski açık yeniden cezalandırılmadı.").validated() }
        }
        return null
    }
}

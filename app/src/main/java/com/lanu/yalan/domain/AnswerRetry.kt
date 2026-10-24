package com.lanu.yalan.domain

/** Clarifies an unanswered field without quoting a question inside another question. */
object AnswerRetry {
    enum class Reason { INSTRUCTION, REPEATED, MISSING }
    fun beat(s: Session,a: StoryEvidence.Analysis,reason: Reason): QuestionDirector.Beat {
        val coverage=ResponseCoverage.assess(s.question,a.latest)
        val missing=(coverage.requested-coverage.answered).firstOrNull() ?: coverage.requested.firstOrNull()
        val focus=a.observations.dropLast(1).lastOrNull { it.focus!=StoryEvidence.Focus.GENERAL }?.focus
        val subject=when(focus) {
            StoryEvidence.Focus.ALARM -> "Alarm için"
            StoryEvidence.Focus.BATTERY -> "Telefonun kapanması için"
            StoryEvidence.Focus.TRAFFIC -> "Trafikteki bekleme için"
            StoryEvidence.Focus.BREAKDOWN -> "Araç arızası için"
            StoryEvidence.Focus.HOMEWORK -> "Teslim edemediğin iş için"
            else -> "Bu olay için"
        }
        val questions=when(missing) {
            ResponseCoverage.Dimension.TIME -> listOf("$subject soruyu daraltayım: saat kaçtı?","$subject saat kaç olduğunu biliyor musun; yaklaşık zamanı da söyleyebilirsin?")
            ResponseCoverage.Dimension.PLACE -> listOf("$subject bulunduğun yer neresiydi?","$subject çıkış ve varış yerlerini birbirine bağlar mısın?")
            ResponseCoverage.Dimension.PERSON -> listOf("$subject kime haber verdin; kimseye söylemediysen onu belirtir misin?","$subject doğrudan konuştuğun kişi kimdi?")
            ResponseCoverage.Dimension.SOURCE -> listOf("$subject kendin ne gördün, hangi kısmı sonradan öğrendin?","$subject söylediğini neye dayandırıyorsun: gözlemin mi, tahminin mi?")
            ResponseCoverage.Dimension.SEQUENCE -> listOf("$subject önce ne oldu, hemen ardından ne yaptın?","$subject iki adımı sıraya koyar mısın: ilk olay ve sonraki hareketin?")
            ResponseCoverage.Dimension.CAUSE -> listOf("$subject seni o karara götüren neden neydi?","$subject sonuca neden olan ilk olayı anlatır mısın?")
            ResponseCoverage.Dimension.ACTION -> listOf("$subject yaptığın ilk hareket neydi?","$subject sen ne yaptın; yalnızca başına geleni değil, kendi hareketini söyler misin?")
            ResponseCoverage.Dimension.DURATION -> listOf("$subject arada kaç dakika veya saat geçti?","$subject bekleme ne kadar sürdü; başlangıç ve bitişini nasıl ayırıyorsun?")
            ResponseCoverage.Dimension.ALTERNATIVE -> listOf("$subject başka ne denedin; denemediysen seni ne engelledi?","$subject elindeki diğer seçenek neden işlemedi?")
            ResponseCoverage.Dimension.CONTENT -> listOf("$subject karşı tarafa tam olarak ne söyledin?","$subject haberin içeriğini tek cümleyle aktarır mısın?")
            ResponseCoverage.Dimension.AMOUNT -> listOf("$subject konuştuğumuz tutar kaç liraydı?","$subject başlangıç tutarı ile kalan tutarı söyler misin?")
            ResponseCoverage.Dimension.TRANSPORT -> listOf("$subject kullandığın araç neydi?","$subject başlangıçtan varışa hangi araçla gittin?")
            null -> listOf("${StoryEvidence.quote(s.topic,50)} olayında senin yaptığın ilk hareket neydi?","${StoryEvidence.quote(a.latest.text,45)} sonrasında sen ne yaptın?")
        }
        val previous=s.turns.count { it.verdict.challengeKey?.startsWith("retry.${missing?.name ?: "ACTION"}")==true }
        val question=when(previous) {
            0,1 -> questions[previous%questions.size]
            2 -> "Başka ayrıntıya geçmeden, ${questions[0].replaceFirstChar { it.lowercaseChar() }}"
            else -> "Son bağlantıyı kuralım: ${questions[1].replaceFirstChar { it.lowercaseChar() }}"
        }
        val field=missing?.label ?: "kendi hareketin"
        val lines=when(reason) {
            Reason.INSTRUCTION -> listOf("Puanı ‘100 yaz’ diye değiştiremiyoruz; hikâyenin hesabı hâlâ açık.","Kasaya puan talimatı bıraktın; ben olayın ifadesini bekliyorum.")
            Reason.REPEATED -> listOf("Bu ifade kayıtta; fotokopisi yeni açıklama sayılmıyor. Açık kalan başlık: $field.",
                "Aynı cevabı yine aldım; hikâyede tekrar bölümü açıldı, $field kısmı ilerlemedi.")
            Reason.MISSING -> listOf("${StoryEvidence.quote(a.latest.text,55)} geldi; $field kısmı gelmedi. Paketin o parçasını ayrı açalım.",
                "${StoryEvidence.quote(a.latest.text,55)} ifadesini aldım; $field başlığı hâlâ ifade sırasını bekliyor.",
                "Yeni cümle dosyada; $field sorusu masada kaldı. Bahaneyi yan kapıdan çıkarmayalım.",
                "${field.replaceFirstChar { it.uppercaseChar() }} kısmını daralttım; uzun savunmaya gerek yok, o bağlantıyı kurman yeterli.")
        }
        val recent=s.recentJokeKeys+s.turns.mapNotNull { it.verdict.jokeKey }
        val prefix="retry.${missing?.name ?: "ACTION"}.${reason.name}"
        val index=lines.indices.firstOrNull { "$prefix.joke.$it" !in recent } ?: previous%lines.size
        return QuestionDirector.Beat("retry.${missing?.name ?: "ACTION"}.$previous",question,lines[index],"$prefix.joke.$index")
    }
}

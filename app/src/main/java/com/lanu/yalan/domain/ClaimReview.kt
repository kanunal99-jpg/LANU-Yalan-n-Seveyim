package com.lanu.yalan.domain

import java.util.Locale

/** Narrow, explainable objections. An unusual sentence is never sufficient proof of a lie. */
object ClaimReview {
    data class Objection(
        val key: String, val quote: String, val finding: String, val question: String,
        val jokes: List<String>, val anchors: List<String>
    )
    data class Review(val active: Objection?, val resolved: Objection?)
    private fun normal(text: String) = StoryEvidence.normal(text)
    private val fiction = Regex("\\b(?:fantastik|masal[a-z]*|ruya[a-z]*|bilim ?kurgu|super ?kahraman[a-z]*|minecraft|roblox|bilgisayar oyunu|oyununda)\\b")
    private val notLiteral = Regex("\\b(?:sanki|gibi|mecaz[a-z]*|varsayalim|diyelim|olsa|olsaydi|olsam|olsaydim|hayal ettim|filmde|dizide|ruyamda)\\b")
    private val retraction = Regex("\\b(?:saka yaptim|sakaydi|uydurdum|geri aliyorum|gercekte|aslinda|yanlis yazdim|duzeltiyorum)\\b")
    private val genericRetraction = Regex("\\b(?:saka yaptim|sakaydi|uydurdum|geri aliyorum)\\b")
    private val clauses = Regex("[;!?\\n]+|\\.(?:\\s|$)")
    private val distance = Regex("\\b(\\d{1,6}(?:[.,]\\d{1,2})?)\\s*(?:km|kilometre)[a-z]*\\b")
    private val duration = Regex("\\b(\\d{1,5}(?:[.,]\\d{1,2})?)\\s*(dakika|dk|saat|saniye|sn)[a-z]*\\b")

    fun inspect(topic: String, observations: List<StoryEvidence.Observation>): Review {
        val pending=linkedMapOf<String,Objection>()
        var resolved: Objection? = null
        observations.forEach { o ->
            resolved = null
            val next = find(topic,o.text)
            if(next != null) pending.putIfAbsent(next.key,next)
            else pending.values.toList().forEach { old ->
                val n=normal(o.text)
                val acknowledges=genericRetraction.containsMatchIn(n) || old.anchors.any(n::contains)
                // A bare "şakaydı" or an unrelated new detail cannot erase an unresolved claim.
                val concrete=o.detailCount>0 || distance.containsMatchIn(n)
                if(retraction.containsMatchIn(n) && acknowledges && concrete) {
                    resolved=old; pending.remove(old.key)
                }
            }
        }
        return Review(pending.values.firstOrNull(),if(pending.isEmpty()) resolved else null)
    }

    fun find(topic: String, text: String): Objection? {
        val fantasy=fiction.containsMatchIn(normal(topic))
        for(part in clauses.split(text)) {
            val n=normal(part)
            if(notLiteral.containsMatchIn(n)) continue
            if(Regex("\\b(?:demedim|soylemedim|sanmiyorum|binmedim|gitmedim|gelmedim|ucmadim)\\b").containsMatchIn(n)) continue
            if(!fantasy) {
                if(Regex("\\bzaman makinesi[a-z]*(?:\\s+[a-z]+){0,3}\\s+(?:git|gel|don|yolculuk)[a-z]*\\b").containsMatchIn(n))
                    return objection("time_machine",part,"Zaman makinesini gündelik olayın gerçek açıklaması olarak kullandın.",
                        "Zaman makinesi kısmı şakaysa, olayın gerçek zaman sırasını nasıl düzeltiyorsun?",
                        listOf("Bahane geçmişe dönmüş; mesaiye zamanında dönmeyi yine başaramamış.",
                            "Savunmaya zaman makinesi ekledin; tutanağın tarihi hâlâ bu yıl."),listOf("zaman makinesi"))
                if(Regex("\\bisinlan(?:dim|mistim|dik|di|arak)\\b").containsMatchIn(n))
                    return objection("teleport",part,"Işınlanmayı gündelik olayın gerçek nedeni olarak kullandın.",
                        "Işınlanma kısmını çıkarınca, ${StoryEvidence.quote(topic,55)} olayını açıklayan gerçek hareketin ne?",
                        listOf("Bahanenin ulaşımı bilim kurguya bağlandı; Akbil bu hatta geçmiyor.",
                            "Bu savunma otobüsü kaçırmamış, fizik dersinden kaçmış.",
                            "Işınlanma dosyaya girdi; mesai çizelgesi hâlâ Dünya saatinde."),listOf("isinlan"))
                if(!Regex("\\b(?:magaza[a-z]*|dukkan[a-z]*|oyuncak[a-z]*|marka[a-z]*|film[a-z]*)\\b").containsMatchIn(n) &&
                    Regex("\\bucan hali[a-z]*(?:\\s+[a-z]+){0,3}\\s+(?:bin|git|gel|uc|yola)[a-z]*\\b").containsMatchIn(n))
                    return objection("flying_carpet",part,"Uçan halıyı gerçek ulaşım aracı olarak gösterdin.",
                        "Uçan halı kısmı şakaysa, ${StoryEvidence.quote(topic,55)} için gerçekte hangi araçla gittin?",
                        listOf("Halıya ulaşım ruhsatı çıkmış; bahanenin ekspertizi hâlâ yerde sürünüyor.",
                            "Servis yerine halı kaldırmışsın; bu güzergâh belediyede görünmüyor.",
                            "Bahaneye püsküllü havayolu kuruldu; iniş izni henüz yok."),listOf("hali"))
                if(Regex("\\buzayli[a-z]*\\s+(?:beni\\s+)?(?:kacir|gotur)[a-z]*\\b").containsMatchIn(n))
                    return objection("aliens",part,"Uzaylı kaçırmasını gündelik mazeretin nedeni olarak sundun.",
                        "Uzaylı kısmını savunacak gündelik bir olayın var mı, yoksa mazeretin o bölümünü geri mi alıyorsun?",
                        listOf("Bu mazeret müdüre verilecekti, NASA'ya sevk edildi.",
                            "Trafik yetmedi, bahaneye galaksiler arası aktarma ekledin.",
                            "Servis kaçırma dosyası açmıştık; sen servis olarak UFO yazmışsın."),listOf("uzayli"))
                if(Regex("\\bejderha[a-z]*(?:\\s+[a-z]+){0,3}\\s+(?:bin|git|gel|uc)[a-z]*\\b").containsMatchIn(n))
                    return objection("dragon",part,"Ejderhayı gerçek ulaşım açıklaması olarak kullandın.",
                        "Ejderha kısmını çıkarırsak, anlattığın yere gerçekte nasıl gittin?",
                        listOf("Bahane alev aldı; itfaiye değil, makul bir ulaşım açıklaması gerekiyor.",
                            "Bu ulaşımın yakıtı masal; işe giriş saati yine gerçek."),listOf("ejderha"))
                humanSpeed(part,n)?.let { return it }
                animalTask(part,n)?.let { return it }
            }
            if(Regex("\\bgec kaldigim icin gec kaldim\\b|\\byetisemedigim icin yetisemedim\\b").containsMatchIn(n))
                return objection("circular",part,"Sonucu, kendisinin nedeni olarak tekrar ettin; gecikmeyi yaratan olay hâlâ yok.",
                    "Geç kaldığını biliyoruz; gecikmeyi başlatan olay neydi?",
                    listOf("Bahane kendi kuyruğunu kovalıyor; başlangıç noktası bir türlü işe yetişemedi.",
                        "Bu açıklama dönel kavşağa girmiş; her çıkışta yine ‘geç kaldım’ yazıyor."),listOf("gec kal","yetis"))
        }
        return null
    }

    private fun objection(key: String,text: String,finding: String,question: String,jokes: List<String>,anchors: List<String>) =
        Objection(key,text.trim().take(280),finding,question,jokes,anchors)

    private fun humanSpeed(text: String,n: String): Objection? {
        val walk=Regex("\\byuru(?:dum|mustum|duk|yerek|yusle)\\b").containsMatchIn(n)
        val run=Regex("\\bkos(?:tum|mustum|tuk|arak)\\b").containsMatchIn(n)
        if(!walk && !run) return null
        // Multiple legs, intervals, a negated distance, or another vehicle need a real semantic model.
        if(Regex("\\b(?:degil|sonra|once|otobus|araba[a-z]*|ucak[a-z]*|tren[a-z]*|bisiklet[a-z]*)\\b").containsMatchIn(n)) return null
        val d=distance.findAll(n).toList().singleOrNull() ?: return null
        val t=duration.findAll(n).toList().singleOrNull() ?: return null
        val km=d.groupValues[1].replace(',','.').toDoubleOrNull() ?: return null
        val amount=t.groupValues[1].replace(',','.').toDoubleOrNull() ?: return null
        if(amount<=0 || km<=0) return null
        val hours=amount/when(t.groupValues[2]) { "saat" -> 1.0;"saniye","sn" -> 3600.0;else -> 60.0 }
        val speed=km/hours
        // Very generous ceilings avoid declaring a brisk walk or an athletic run impossible.
        if(speed<=(if(walk) 25.0 else 70.0)) return null
        val rate=String.format(Locale.ROOT,"%.0f",speed)
        val manner=if(walk) "yürüyüş" else "koşu"
        return objection("human_speed",text,"${d.value} / ${t.value}, yaklaşık $rate km/sa $manner hızı gerektiriyor.",
            "${d.value} ile ${t.value} hesabı $rate km/sa ediyor; mesafe, süre veya $manner iddiasının hangisini düzeltiyorsun?",
            listOf("$rate km/sa $manner yazmışsın; ayakkabı değil hızlı tren ihalesi bu.",
                "Bu hesapta taban değil jet motoru aşınıyor; rakamlar bahaneyi sollamış.",
                "$rate km/sa ile mazeretin radara girmiş; ayakkabının plakası eksik."),listOf("yuru","kos","km","kilometre"))
    }

    private fun animalTask(text: String,n: String): Objection? {
        val animal=Regex("\\b(kedim|kopegim|baligim|muhabbet kusum)\\b").find(n) ?: return null
        val task=Regex("\\b(?:odevimi|raporumu|dilekcemi)\\s+(?:yazdi|hazirladi|yapti)\\b").find(n) ?: return null
        if(animal.range.first>task.range.first || task.range.first-animal.range.last>45) return null
        val subject=when(animal.value) { "kedim" -> "Kedin";"kopegim" -> "Köpeğin";"baligim" -> "Balığın";else -> "Muhabbet kuşun" }
        return objection("animal_task",text,"$subject için insanın hazırlaması gereken bir işi yaptığını söyledin.",
            "$subject bu işi gerçekten nasıl yaptı; bir lakap veya şaka değilse insanın yaptığı kısmı ne?",
            listOf("$subject işi hazırlamış; bu bahanede çalışanların SGK türü yanlış seçilmiş.",
                "$subject kadroya alınmış; ödevin değil, savunmanın karnesi tehlikede.",
                "$subject teslim işine girmiş; bahanenin insan kaynakları biraz fazla yaratıcı."),listOf(animal.value,"odev","rapor","dilekce"))
    }

    fun beat(s: Session, objection: Objection): QuestionDirector.Beat {
        val key="objection.${objection.key}"
        val previous=s.turns.count { it.verdict.challengeKey?.startsWith(key)==true }
        val recent=(s.recentJokeKeys+s.turns.mapNotNull { it.verdict.jokeKey }).toSet()
        val index=objection.jokes.indices.firstOrNull { "$key.joke.$it" !in recent } ?: previous%objection.jokes.size
        val lead=if(previous==0) "${StoryEvidence.quote(objection.quote,95)} — ${objection.finding}"
            else "${StoryEvidence.quote(objection.quote,80)} itirazı hâlâ açık; son cevabın bunu açıklamadı."
        val question=if(previous==0) objection.question else
            "${StoryEvidence.quote(objection.quote,100)} iddianı geri mi alıyorsun, yoksa işaret ettiğim açığı hangi olayla açıklıyorsun?"
        return QuestionDirector.Beat("$key.$previous",question,"$lead ${objection.jokes[index]}","$key.joke.$index")
    }
}

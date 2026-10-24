package com.lanu.yalan.domain

/** Small, explicit story-world tracker for travel stories. It is not a semantic language model. */
object StoryThread {
    data class Place(val key: String, val label: String, val space: Boolean = false)
    data class Gap(val from: Place?, val to: Place, val quote: String) {
        val key: String get() = "${from?.key ?: "start"}.${to.key}"
        val label: String get() = from?.let { "${it.label} → ${to.label}" } ?: "${to.label} yolculuğu"
    }
    data class Review(
        val root: Place, val places: List<Place>, val latestPlaces: List<Place>, val newPlace: Place?,
        val food: String?, val motive: Boolean, val changedPlan: Boolean,
        val pending: List<Gap>, val repaired: Boolean
    )
    private val aliases = linkedMapOf(
        "mars" to Place("mars","Mars",true), "jupiter" to Place("jupiter","Jüpiter",true),
        "venus" to Place("venus","Venüs",true), "saturn" to Place("saturn","Satürn",true),
        "neptun" to Place("neptun","Neptün",true), "uzay" to Place("space","uzay",true),
        "hatay" to Place("hatay","Hatay"), "new ?york" to Place("newyork","New York"),
        "istanbul" to Place("istanbul","İstanbul"), "ankara" to Place("ankara","Ankara"),
        "izmir" to Place("izmir","İzmir"), "antalya" to Place("antalya","Antalya"),
        "adana" to Place("adana","Adana"), "bursa" to Place("bursa","Bursa"),
        "gaziantep" to Place("gaziantep","Gaziantep"), "trabzon" to Place("trabzon","Trabzon"),
        "konya" to Place("konya","Konya"), "eskisehir" to Place("eskisehir","Eskişehir"),
        "paris" to Place("paris","Paris"), "londra" to Place("londra","Londra"),
        "berlin" to Place("berlin","Berlin"), "tokyo" to Place("tokyo","Tokyo"),
        "almanya" to Place("germany","Almanya"), "amerika" to Place("usa","Amerika")
    )
    private val travel = Regex("\\b(?:git(?:tim|tik|mistim|mistik)|gel(?:dim|dik|mistim)|uc(?:tum|tuk)|yolculuk|seyahat|tatil[a-z]*|gez(?:dim|dik))\\b")
    private val nonLiteral = Regex("\\b(?:gitmedim|gitmedik|gibi|sanki|ruya[a-z]*|film[a-z]*|dizi[a-z]*|oyun[a-z]*|kafe[a-z]*|magaza[a-z]*|cikolata[a-z]*|marka[a-z]*)\\b")
    private val transport = Regex("\\b(?:roket[a-z]*|uzay gemisi[a-z]*|ucak[a-z]*|jet[a-z]*|otobus[a-z]*|araba[a-z]*|otomobil[a-z]*|tren[a-z]*|metro[a-z]*|taksi[a-z]*|gemi[a-z]*|feribot[a-z]*|bisiklet[a-z]*|yuruyerek|isinlan[a-z]*)\\b")
    private val transportNegation = Regex("\\b(?:binmed[a-z]*|kullanmad[a-z]*|gitmed[a-z]*|yoktu|degil)\\b")
    private val elapsed = Regex("\\b(?:\\d+|bir|iki|uc|dort|bes|alti|yedi|sekiz|dokuz|on|yirmi|otuz|kirk|yarim)\\s*(?:saniye|dakika|dk|saat|gun|hafta|ay|yil)[a-z]*\\b")
    private val cancelled = Regex("\\b(?:gitmedim|gitmedik|vazgectim|vazgectik|iptal ettim|iptal ettik|uydurdum|saka yaptim|sakaydi)\\b")
    private val planChange = Regex("\\b(?:karar degistir[a-z]*|kadar degistir[a-z]*|plan[a-z]* degistir[a-z]*|vazgect[a-z]*|iptal ett[a-z]*)\\b")
    private val foods = linkedMapOf("barbunya" to "barbunya", "kunefe" to "künefe", "turk kahvesi" to "Türk kahvesi",
        "kahve" to "kahve", "kebap" to "kebap", "baklava" to "baklava", "pizza" to "pizza", "cay" to "çay")

    fun placesIn(text: String): List<Place> {
        val n=StoryEvidence.normal(text)
        val known=aliases.mapNotNull { (alias,p) ->
            Regex("\\b(?:$alias)(?:['’]?(?:a|e|ya|ye|da|de|ta|te|dan|den|tan|ten|in|un|nin|nun))?\\b").find(n)?.let { it.range.first to p }
        }
        // Proper names with an explicit Turkish place suffix also work outside the small alias list.
        val proper=Regex("\\b([A-ZÇĞİÖŞÜ][a-zçğıöşü]+(?: [A-ZÇĞİÖŞÜ][a-zçğıöşü]+)?)['’](?:da|de|ta|te|dan|den|tan|ten|ya|ye)\\b")
            .findAll(text).map { match ->
                val parts=match.groupValues[1].split(' ')
                val rawLabel=if(parts.size>1 && parts.first() in setOf("Sonra","Önce","Ardından","Akabinde","Dün","Bugün","Sabah","Akşam")) parts.drop(1).joinToString(" ") else parts.joinToString(" ")
                val label=if(rawLabel.length>55) rawLabel.take(54)+"…" else rawLabel
                match.range.first to Place("p${StoryEvidence.normal(rawLabel).hashCode().toUInt().toString(16)}",label)
            }
        return (known+proper).sortedBy { it.first }.map { it.second }
            .distinctBy { StoryEvidence.normal(it.label).replace(" ","") }
    }
    fun premise(topic: String): Place? {
        val n=StoryEvidence.normal(topic)
        if(!travel.containsMatchIn(n) || nonLiteral.containsMatchIn(n)) return null
        return placesIn(topic).lastOrNull()
    }
    fun spacePremise(topic: String) = premise(topic)?.space==true
    private fun placeCase(p: Place,back: String,front: String): String {
        val n=StoryEvidence.normal(p.label)
        // Decide using the original Turkish vowel; normal() folds front rounded vowels.
        val original=p.label.lowercase().lastOrNull { it in "aeıioöuü" }
        var ending=if(original in listOf('a','ı','o','u')) back else front
        if(n.lastOrNull() in listOf('f','s','t','k','c','p','h')) ending=ending.replaceFirstChar { if(it=='d') 't' else it }
        return p.label+(if(p.key=="space") "" else "'")+ending
    }
    fun hasTransport(text: String): Boolean {
        val n=StoryEvidence.normal(text)
        return n.split(Regex("[,;.!?]|\\bama\\b")).any { transport.containsMatchIn(it) && !transportNegation.containsMatchIn(it) }
    }
    fun hasElapsed(text: String) = elapsed.containsMatchIn(StoryEvidence.normal(text))
    fun hasMotive(text: String) = Regex("\\b(?:canim sikil[a-z]*|sikilmistim|keyfim istedi|merak ettim|gezmek istedim|istedigim icin)\\b")
        .containsMatchIn(StoryEvidence.normal(text))

    fun inspect(s: Session, observations: List<StoryEvidence.Observation>): Review? {
        val root=premise(s.topic) ?: return null
        val places=mutableListOf(root)
        val gaps=linkedMapOf("start.${root.key}" to Gap(null,root,s.topic))
        var latestPlaces=emptyList<Place>()
        var newPlace: Place?=null
        var repaired=false
        var cancelledRoot=false
        val questions=s.turns.map { it.question }+s.question
        observations.forEachIndexed { index,o ->
            val n=StoryEvidence.normal(o.text)
            latestPlaces=placesIn(o.text)
            newPlace=latestPlaces.lastOrNull { p -> places.none { it.key==p.key } }
            repaired=false
            val anchoredCancellation=cancelled.containsMatchIn(n) &&
                (latestPlaces.any { it.key==root.key } || n.contains("uydurdum") || n.contains("sakaydi")) &&
                (hasTransport(o.text) || (newPlace!=null && travel.containsMatchIn(n)))
            if(anchoredCancellation) {
                cancelledRoot=true
                gaps.entries.removeAll { it.value.to.key==root.key || it.value.from?.key==root.key }
                repaired=true
            }
            latestPlaces.forEach { p ->
                if(places.none { it.key==p.key }) {
                    val from=places.lastOrNull { !cancelledRoot || it.key!=root.key }
                    val gap=Gap(from,p,o.text)
                    gaps[gap.key]=gap
                    places+=p
                }
            }
            if(hasTransport(o.text)) {
                val q=StoryEvidence.normal(questions[index])
                gaps.values.toList().forEach { gap ->
                    val names=latestPlaces.map { it.key }.toSet()
                    val named=gap.to.key in names && (gap.from==null || gap.from.key in names)
                    val answering=q.contains(StoryEvidence.normal(gap.to.label)) && newPlace==null
                    val timing=hasElapsed(o.text) || (gap.from==null && (latestPlaces.size>1 || n.contains("ussunden") || n.contains("kalktik")))
                    if((named || answering) && timing) { gaps.remove(gap.key);repaired=true }
                }
            }
        }
        val latest=observations.last().text
        val normalized=StoryEvidence.normal(latest)
        return Review(root,places.toList(),latestPlaces,newPlace,
            foods.entries.firstOrNull { Regex("\\b${it.key}[a-z]*\\b").containsMatchIn(normalized) }?.value,
            hasMotive(latest),planChange.containsMatchIn(normalized),gaps.values.toList(),repaired)
    }

    fun opening(topic: String, level: Int): String? = premise(topic)?.let { p ->
        if(level>60) "${p.label} yolculuğunda kalkış, varış ve dönüş sırası neydi; ulaşımı hangi araçla sağladın?"
        else "${p.label} yolculuğuna nereden, hangi araçla çıktınız?"
    }

    fun beat(s: Session, a: StoryEvidence.Analysis): QuestionDirector.Beat? {
        val r=a.thread ?: return null
        if(r.pending.isEmpty()) return null
        val used=s.turns.mapNotNull { it.verdict.challengeKey }.toSet()
        val gap=r.pending.last()
        val root=r.root.label
        val latest=r.newPlace
        val previous=r.places.dropLast(1).lastOrNull()?.label ?: root
        val hard=s.level>60
        val key: String
        val question: String
        val lines: List<String>
        when {
            latest!=null && r.changedPlan -> {
                key="plan.${latest.key}"
                question=if(hard) "İlk $root iddianla ${latest.label} plan değişikliğini sıraya koy: hangisini geri alıyor, hangisini koruyorsun?"
                    else "$root planını iptal edip ${latest.label} rotasını mı seçtiniz, yoksa oradan mı geçtiniz?"
                lines=listOf(if(r.food=="künefe" && r.root.space) "$root planı, ${placeCase(latest,"da","de")} künefe… Uzay programı şerbete döndü; kararın açıklaması eksik."
                    else "$root planı, ${placeCase(latest,"da","de")}${r.food?.let { " $it" } ?: " mola"}… Gezi programı değişti, açıklaması servise çıkmadı.",
                    "$root diye çıktık, ${latest.label} geldi; navigasyonun değil, hikâyenin dönüşünü açıklayalım.")
            }
            latest!=null -> {
                key="jump.${latest.key}"
                question="${latest.label} yeni duraksa, ${placeCase(r.places[r.places.lastIndex-1],"dan","den")} oraya hangi araçla ve ne kadar sürede geçtiniz?"
                lines=listOf("${if(root!=previous) "$root, " else ""}$previous, ${latest.label}${r.food?.let { " ve $it" } ?: ""}; rota gurme turuna döndü, geçiş sahnesi kayıp.",
                    "$previous, ${latest.label}… Hikâyenin kilometresi arttı, yol tarifi hâlâ gelmedi.")
            }
            r.motive -> {
                key="motive.${s.turns.size}"
                val elon=StoryEvidence.words(s.topic+" "+a.latest.text).any { it in setOf("elon","musk","alon") }
                question=if(elon && hard) "Elon'la $root yolculuğunun başlangıcını neye göre sabitliyorsun: kendi gözlemin mi, onun söylediği yer mi?"
                    else if(elon) "Elon'la hangi noktada buluşup $root yolculuğuna başladınız?"
                    else "$root yolculuğunun nedenini söyledin; nereden yola çıktınız?"
                lines=listOf(if(elon) "Can sıkıntısı tamam; Elon'u tanıdık yazınca $root minibüs durağına dönmüyor."
                    else "Can sıkıntısı tamam; $root seferi için tek başına biniş kartı sayılmıyor.",
                    "Can sıkıntısından yürüyüşe çıkan var, $root rotası çizen var; aradaki ulaşım kısmı sende.")
            }
            r.food!=null && "thread.meal" !in used -> {
                key="meal"
                question=if(hard) "${r.food.replaceFirstChar { it.uppercaseChar() }} için hazırlık, taşıma ve yeme sırasını $root yolculuğuna nasıl bağlıyorsun?"
                    else "${r.food.replaceFirstChar { it.uppercaseChar() }} yolculukta yanınızda mıydı, ${placeCase(r.root,"da","de")} mı hazırlandı?"
                lines=listOf(if(r.root.space) "$root yolunda ${r.food}… Roketi anlatmadan esnaf lokantasını açtın."
                    else "${r.food.replaceFirstChar { it.uppercaseChar() }} menüye girdi; $root yolculuğunu atlayıp hesabı masaya bırakmışsın.",
                    "$root için yemek hazır, gidiş tarifi eksik; menü yol tarifinden hızlı çıktı.")
            }
            else -> {
                val target=gap.to.label
                val from=gap.from?.label
                val options=listOf(
                    Triple("vehicle.${gap.key}",if(from==null) "$target yolculuğunda kullandığınız araç neydi; nereden hareket ettiniz?"
                        else "$from ile $target arasındaki yolculuğu hangi araçla yaptınız?","${gap.label} açık; hikâyenin valizi hazır, ulaşım bileti ortada yok."),
                    Triple("duration.${gap.key}","$target için yola çıkışınızla varışınız arasında ne kadar süre geçti?","$target dosyada; yolculuğun süresi izin kâğıdını hâlâ teslim etmedi."),
                    Triple("sequence.${gap.key}","$target yolculuğunu üç adımda kurar mısın: çıkış, ulaşım, varış?","$target anlatısında sahneler var, sahneleri birbirine bağlayan yol henüz yok."),
                    Triple("choice.${gap.key}","${gap.label} kısmını mı düzeltiyorsun, yoksa gidişin araç ve süresini mi açıklıyorsun?","${gap.label} hâlâ açık; yeni ayrıntı eklemek eski yol boşluğunu asfaltlamıyor.")
                )
                val choice=options.firstOrNull { "thread.${it.first}" !in used } ?: options.last()
                key=choice.first;question=choice.second
                lines=listOf(choice.third,"${gap.label} için kelimeler var, bağlantı eksik; hikâye buradan aktarma yapamıyor.")
            }
        }
        val recent=s.recentJokeKeys+s.turns.mapNotNull { it.verdict.jokeKey }
        val index=lines.indices.firstOrNull { "thread.$key.joke.$it" !in recent } ?: 0
        return QuestionDirector.Beat("thread.$key",question,lines[index],"thread.$key.joke.$index")
    }
}

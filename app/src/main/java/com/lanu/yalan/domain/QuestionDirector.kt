package com.lanu.yalan.domain

import com.lanu.yalan.domain.StoryEvidence.Focus

/** Selects an unasked angle from actual story evidence, with five difficulty bands. */
object QuestionDirector {
    data class Beat(val key: String, val question: String, val reaction: String, val jokeKey: String? = null)
    private fun q(text: String) = StoryEvidence.quote(text)
    fun opening(topic: String, level: Int): String {
        StoryThread.opening(topic,level)?.let { return it }
        val hard=level>60
        return when {
            StoryEvidence.has(topic,"gec","trafik","yetis") -> if(hard)
                "${q(topic)} dosyasını açtım. Olman gereken saatle yola çıktığın saati karşılaştır: gecikme hangi aralıkta başladı?"
                else "${q(topic)} diyorsun. Olman gereken saat kaçtı; seni geciktiren olay neydi?"
            StoryEvidence.has(topic,"odev","ders","sinav") -> if(hard)
                "${q(topic)}: son hazırladığın şeyle teslim etmediğin şey arasındaki kopuş tam nerede?"
                else "${q(topic)} meselesinde neyi hazırladın, neyi teslim edemedin?"
            StoryEvidence.has(topic,"randevu","bulus") -> "${q(topic)} için kararlaştırılan saat neydi; plan ilk nerede değişti?"
            StoryEvidence.has(topic,"para","harca","borc") -> "${q(topic)} diyorsun. Konuştuğumuz miktar ne; para hikâyenin hangi anında el değiştirdi?"
            else -> "${q(topic)} dosyası sende. Olayı değiştiren tek hareketini anlat; ‘öyle oldu’yu peşin kabul etmiyorum."
        }
    }

    fun next(s: Session, a: StoryEvidence.Analysis, spicy: Boolean): Beat {
        val used=s.turns.mapNotNull { it.verdict.challengeKey }.toSet()
        val o=a.latest
        val tier=(s.level.coerceIn(1,100)-1)/20
        fun level(vararg texts: String) = texts[tier.coerceAtMost(texts.lastIndex)]
        val beats=mutableListOf<Beat>()
        fun add(key: String, question: String, reaction: String) { beats+=Beat(key,question,reaction) }
        a.conflict?.let { c ->
            val lead=if(c.confirmed) c.finding else "İki ayrıntıyı bağlayalım: ${q(c.earlier)} / ${q(c.later)}."
            add("conflict.${c.key}",c.question,"$lead ${c.joke}")
            add("conflict.${c.key}.choice","${q(c.earlier)} ile ${q(c.later)}: hangisini olduğu gibi koruyorsun; diğerini nasıl açıklıyorsun?",
                "${c.finding} ${if(spicy) "Yok artık be, aynı bahaneye iki ayrı senaryo yazmışız." else "Aynı bahaneye iki ayrı senaryo yazılmış."}")
            add("conflict.${c.key}.sequence","${q(c.earlier)} ile ${q(c.later)} arasına eksik olayı koy; hangi sırayla yaşandı?",
                "${c.finding} Kurgu masasındaki makas izleri hâlâ görünüyor.")
            // An unresolved contradiction never expires because its three prepared angles were used.
            val selected=beats.firstOrNull { it.key !in used } ?: Beat("conflict.${c.key}.hold.${s.turns.size}",
                "${q(c.earlier)} ile ${q(c.later)} hâlâ bağlanmadı; hangi ifadeyi düzeltiyor veya hangi geçişi ekliyorsun?",
                "${c.finding} Konuyu değiştirdin diye bu açık dosyadan düşmüyor; bahane zamanaşımına uğramadı.")
            val joke=StoryHumor.select(s,a,selected.key,selected.reaction,spicy)
            return selected.copy(reaction=joke.text,jokeKey=joke.key)
        }
        a.claim.active?.let { return ClaimReview.beat(s,it) }
        StoryThread.beat(s,a)?.let { return it }
        val repairedQuestion=when {
            a.thread?.repaired==true -> "Yolculuk bağlantısını kurdun. ${a.thread.places.last().label} için varıştan sonra ilk yaptığın hareket neydi?"
            o.clocks.isNotEmpty() -> "Açıkladığın geçişi kabul ettim. ${o.clocks.last().label} anını hangi olayla sabitliyorsun; tahmin mi, o anda bildiğin saat mi?"
            o.messaged -> "Açıkladığın geçişi kabul ettim. Haber verdiğin anda karşı taraf ne biliyordu; sonradan öğrendiğin kısmı ayırır mısın?"
            o.person!=null -> "Açıkladığın geçişi kabul ettim. ${o.person} olayın hangi kısmını doğrudan biliyor; sadece senden duyduğu kısım hangisi?"
            else -> "Açıkladığın geçişi kabul ettim. Son olaydan sonra planın hangi kısmını hâlâ yetiştirebiliyordun?"
        }
        if(a.repaired) add("repair.next",repairedQuestion,
            "Bağlantıyı şimdi kurdun; eski açığı tekrar satmayacağım. Bakalım yeni ayrıntı da muayeneden geçecek mi.")

        when(o.focus) {
            Focus.BATTERY -> {
                val powerSpecified=StoryEvidence.has(o.text,"sarj","batarya","pil")
                val whenPhone=when {
                    o.clocks.isNotEmpty() -> "${o.clocks.last().label} ile telefonun kapanması arasındaki sırayı nasıl kuruyorsun?"
                    o.place!=null -> "${o.place} konumunu söyledin. Telefon kapanmadan önceki son işlemin neydi; kapandığını tam hangi anda fark ettin?"
                    else -> "Telefon tam hangi anda kapandı; o sırada neredeydin?"
                }
                add("battery.order",level(whenPhone,whenPhone,
                    "Telefon kapanmadan önceki son yaptığın işlem neydi; hikâyendeki hangi ayrıntı bunu destekliyor?",
                    "Telefonun kapanacağını fark ettiğinde haber vermek için kalan fırsatın neydi; neden kullanamadın?",
                    "Telefonun kapanmasını savunmanın merkezine koydun: öncesindeki son işlemle sonrasındaki ilk iletişimi sıraya dizer misin?"),
                    if(powerSpecified) level("Şarj yüzde sıfır, bahane yüzde yüz; telefon senden önce mesaiyi bırakmış.","Telefon kapanmış; mazeretin güç tasarrufuna hiç geçmemiş.","Şarj bahanesi tam dolu geldi; kapanma anını bir sabitleyelim.","Telefonun pili bitmiş, bahanenin mesaisi yeni başlamış.","Pilsiz telefon dosyaya girdi; bahaneyi powerbank yerine kullanamıyoruz.")
                    else "Telefon kapanmış, bahane mesaiye devam etmiş; kapanma nedenini henüz bilmiyoruz.")
                add("battery.backup","Telefon kapandıktan sonra hangi seçeneğin vardı; başka cihaz veya şarj imkânı varsa neden işlemedi?",
                    "Telefonu kurtaramadık diyorsun; bahaneyi uçak moduna almadan seçeneklere bakalım.")
            }
            Focus.ALARM -> {
                add("alarm.setup",level(if(o.clocks.isNotEmpty()) "Alarmı sonradan kontrol ettiğinde ne gördün; çalmamasını açıklayan hangi ayar vardı?" else "Alarm hangi saate kuruluydu; çalmadığını nasıl fark ettin?",
                    "Alarmın kurulu olduğu saatle uyandığın saati karşılaştır: aradaki boşluk kaç dakika?",
                    "Alarmı kurduğun son anı anlat: saat mi, gün mü, ses ayarı mı yanlıştı?",
                    "Çalmayan alarmı sonradan kontrol ettiğinde hangi ayar anlattığını destekledi?",
                    "Alarmın kurulu olduğuna hangi ayrıntıyla eminsin; arıza ile kurmayı unutmayı nasıl ayırdın?"),
                    level("Alarm da seninle yıllık izne çıkmış anlaşılan; mesainin bundan haberi yok.","Alarm susmuş, mazeret sabah kuşağına bağlanmış.","Alarmı suçlu sandalyesine aldık; saat ayarını da çağıralım.","Alarmın ifadesi eksik; dosyayı sadece ‘çalmadı’yla kapatamıyoruz.","Alarm beraat isterse bu bahane hazırlıksız yakalanacak; ayarları netleştirelim."))
                add("alarm.wakeup","Seni en sonunda ne uyandırdı; o anın saatiyle alarm saati birbirini tutuyor mu?","Alarmın sesi yok ama boş bıraktığı dakikalar bayağı gürültülü.")
            }
            Focus.SLEEP -> add("sleep.wakeup",level("Uyandığında saat kaçtı; seni en sonunda ne uyandırdı?",
                "Planladığın uyanma saatiyle gerçekten uyandığın saat arasında ne kadar fark vardı?",
                "Uyku süresini anlattığın gecikmeye bağla: plan ilk hangi anda koptu?",
                "Uyandığında hangi işi yetiştirme şansın kalmıştı; o fırsatı nasıl değerlendirdin?",
                "Uyandığın andan haber verdiğin ana kadar sırayı savun: hangi kayıp süreyi uyku açıklamıyor?"),
                "Uyku uzamış; bahane de kahvaltıyı yatağa istemiş gibi rahat.")
            Focus.TRAFFIC -> {
                val at=o.place?.let { "$it çevresinde" } ?: "Trafiğin durduğu noktada"
                add("traffic.bottleneck",level("$at nereden nereye gidiyordun; takıldığın yol hangisiydi?",
                    "$at ne kadar ilerleyebildin; bekleme mi, yavaş hareket mi süreyi uzattı?",
                    "$at söylediğin gecikmeyi yaratan tek noktayı seç: oraya giriş ve çıkış sırası neydi?",
                    "$at başka rota olsaydı ne değişirdi; seçtiğin yolda kalma gerekçen neydi?",
                    "$at gecikmenin hangi bölümünü trafik, hangi bölümünü kendi çıkış saatin açıklıyor?"),
                    level("Trafik durmuş, senaryo emniyet şeridinden akıyor.","Trafiği bahane ettik; bari bahaneyi yanlış sokağa sokmayalım.","Yol kilit diyorsun; hikâyenin anahtarını bir noktaya bırakalım.","Bahane navigasyonu ‘yeniden hesaplanıyor’da kalmış; rotayı açalım.","Bu trafik dosyası kavşak değil bilirkişi istiyor; dakikaları şerit şerit ayıralım."))
                add("traffic.notice","Trafik yüzünden yetişemeyeceğini ilk nerede anladın; o noktada haber vermek için ne yaptın?","Trafik yavaş olabilir; haberin de onun arkasına park etmesin.")
            }
            Focus.BREAKDOWN -> {
                val tire=StoryEvidence.has(o.text,"lastik")
                val objectName=if(tire) "Lastik" else "Araç"
                add("breakdown.sequence",level("$objectName sorunu çıkınca ilk ne yapıldı; sen o sırada neredeydin?",
                    "$objectName sorunu ile yeniden yola çıkman arasında ne oldu; süreyi hangi olay uzattı?",
                    if(o.person=="şoför") "Şoförün söylediğiyle kendin gördüğünü ayır: arızanın hangi kısmına doğrudan tanıksın?" else "$objectName sorununu sen mi gördün, birinden mi duydun; doğrudan bildiğin ayrıntı ne?",
                    "$objectName sorunu gerçekten beklemeyi gerektiriyorsa, başka türlü devam etmeni ne engelledi?",
                    "$objectName sorunu, bekleme ve devam etme sırasını savun: anlattığının hangi kısmını kendin gözledin?"),
                    if(tire) level("Lastik patlamış; bahane şu an yedekle gidiyor.","Lastik hava kaçırmış, hikâye de kaçırmasın; sırayı sabitleyelim.","Lastiğe yama bulunur; hikâyedeki deliğin yerini önce görelim.","Patlak lastik tamam; bütün gecikmeyi onun üstüne yükleyince jant eğiliyor.","Lastik dosyası ekspertize gitti; bahanenin şasisine de bakıyoruz.")
                    else "Araç arıza yapmış; bahaneyi de lifte kaldırmadan bırakmayacağım.")
                add("breakdown.duration",o.duration?.let { "$it beklediğini söyledin. Bu süreyi nereden biliyorsun; bekleme hangi olayla başladı ve bitti?" }
                    ?: "Arıza yüzünden beklediğin süre neydi; başlangıcı ve bitişi hangi olaylara bağlıyorsun?",
                    "Arıza kaydı var diyorsun; gecikmeye eklenen dakikalar kafadan yazılmasın.")
            }
            Focus.HOMEWORK -> {
                add("homework.last",level(if(o.place=="ev") "Evde kaldığını ne zaman fark ettin; o anda geri alma imkânını ne engelledi?" else "Hazırladığın şeyin son hâli neredeydi; en son ne zaman kontrol ettin?",
                    "Hazırladığın şeyle teslim edemediğin şey aynı mı; son gördüğün hâlini anlatır mısın?",
                    "Teslime kadar hangi adımı tamamladın, hangi adım koptu; ikisini nasıl ayırıyorsun?",
                    "Kaybolan şey dosya mı, defter mi, süre mi; anlattığındaki tek engeli seçip savunur musun?",
                    "Hazır olduğunu söylediğin şeyle teslim edemediğin şey arasındaki eksik halkayı göster: hangi adım doğrulanabilir?"),
                    level("Ödev dosyaya girdi; şimdi bahaneyi de sözlüye kaldıralım.","Teslim edilen tek şey bahane olmasın; son hâli nerede kaldı?","Ödevi sözlüye kaldırdık; kâğıt konuşmuyor, senaryo konuşuyor.","Bu ödevin kapağı var ama dosyanın kapağı kapanmadı.","Bahane sınıfı geçmek istiyor; önce teslim zincirinden sözlüye girecek."))
                add("homework.notice","Yetiştiremeyeceğini fark ettiğin anda kime, hangi açıklamayla haber verdin; vermediysen seni ne durdurdu?","Bahane teslim olmuş; haber verme kısmı hâlâ devamsız.")
            }
            Focus.INTERNET -> {
                add("internet.failure",level("Bağlantı kesildiğinde yaptığın işlem neydi; ekranda ne değişti?",
                    "Kesinti başlamadan önce hangi adım bitmişti; yarım kalan işlem tam hangisiydi?",
                    "Sorunun bağlantı olduğunu neyle ayırdın; uygulama veya dosya hatası ihtimalini nasıl eledin?",
                    "Bağlantı geri geldiğinde aynı işlemi denedin mi; denediysen sonuç ilk açıklamanla tutuyor mu?",
                    "Kesinti tek engelse bağlantı dönünce ne değişmeliydi; anlattığın sonuç gerçekten bunu gösteriyor mu?"),
                    level("İnternet gitmiş, bahane tam çekiyor.","Bağlantı kopmuş; senaryo çevrimdışıyken de veri tüketiyor.","Bahane bağlandı; hatanın hangi adımda koptuğunu görelim.","İnterneti suçlu bulduk diye dosyayı otomatik kaydetmiyoruz.","Kesinti dosyası açıldı; bahane çekiyor ama kanıtın sinyali zayıf."))
                add("internet.backup","O işlem için başka bağlantı imkânın var mıydı; varsa neden kullanamadın, yoksa neyi denedin?","Bağlantı bahaneyi taşıyor; yedek yolun gerçekten kapalı olup olmadığına bakalım.")
            }
            Focus.ILLNESS -> add("illness.timeline","Sağlık ayrıntısına girmeden sırayı netleştir: planın hangi anda değişti, ne zaman haber verdin?",
                "Geçmiş olsun; teşhisi değil, hikâyenin kronolojisini muayeneye alıyorum.")
            Focus.WEATHER -> add("weather.effect",level("Hava olayı tam olarak hangi hareketini engelledi; o sırada neredeydin?",
                "Hava şartı ile gecikmen arasında hangi somut engel vardı; beklemek dışında ne denedin?",
                "Aynı havada planın hangi kısmı mümkün, hangi kısmı imkânsızdı; ayrımı nasıl yaptın?",
                "Hava düzelince ilk ne değişti; bu, anlattığın engelin gerçekten neden olduğunu gösteriyor mu?",
                "Havayı anlatmaktan çıkıp etkisini savun: kaybettiğin sürenin hangi bölümünü yalnızca bu engel açıklıyor?"),
                "Hava bahaneye şemsiye olmuş; altındaki olay yine de ıslanmasın.")
            Focus.MONEY -> if(StoryEvidence.has(o.text,"cuzdan") && StoryEvidence.has(o.text,"unutt","kaybet","yok"))
                add("money.wallet","Cüzdanını en son nerede kullandın; yokluğunu ilk hangi işlemde fark ettin?","Cüzdan ortada yok, bahane kasada sıraya girmiş; son kullanımını bir bulalım.")
                else add("money.trail",level("Konuştuğun miktar ne; para en son hangi işlemde el değiştirdi?",
                "Önceki miktarla şimdi söylediğin tutarı bağla: arada hangi ödeme veya eksik var?",
                "Tutarı tahmin mi ediyorsun, bir işlemden mi biliyorsun; hangisini savunuyorsun?",
                "Anlattığın işlem olmasaydı hangi tutar farklı olurdu; hesabı adım adım açar mısın?",
                "Paranın başlangıç tutarı, değiştiği işlem ve kalan kısmı birbirini tutuyor mu; tek hesabın üzerinden savunur musun?"),
                "Bu bahanenin de hesabını soracağım; kasadan fişsiz geçmeyelim.")
            Focus.APPOINTMENT -> add("appointment.notice","Kararlaştırılan saatle haber verdiğin anı karşılaştır: karşı taraf o sırada ne biliyordu?",
                "Buluşma saati sabit, bahane seyyar; aynı noktada buluşturalım.")
            Focus.GENERAL -> Unit
        }
        o.clocks.lastOrNull()?.let { clock -> add("clock.anchor","${clock.label} anını neye göre biliyorsun; yaklaşık mı söylüyorsun, o anda baktığın bir şey mi var?",
            "${clock.label} dosyaya girdi; saatleri sonradan ileri alma hakkı sınırsız değil.") }
        if(o.messaged) add("contact.content",o.person?.let { "$it ile paylaştığın haberin içeriği neydi; o anda bildiğinle sonradan öğrendiğini nasıl ayırıyorsun?" }
            ?: "Gönderdiğin haberin içeriği neydi; o anda bildiğinle sonradan öğrendiğini ayırır mısın?",
            "Haber vermek ayrı, aynı hikâyeyi anlatmak ayrı; mesajı senaryonun fragmanı yapmayalım.")
        o.person?.let { person -> add("person.knowledge",if(o.alone) "Yalnız olduğunu söyledin; $person olayın hangi kısmını sonradan öğrendi?"
            else "$person anlattığının hangi kısmını doğrudan biliyor; sadece senden duyduğu bölüm hangisi?",
            "$person hikâyeye girdi; senaryo artık tek imzayla kapanmıyor.") }
        o.place?.let { place -> add("place.sequence","$place ile anlattığın son olay arasındaki geçiş neydi; önce ne oldu, sonra ne yaptın?",
            "$place konumunu sabitledik; bahane navigasyonu şimdi rota değiştirmesin.") }
        if(s.turns.isNotEmpty()) {
            val earlier=s.turns.first().answer
            add("history.bridge","İlk anlattığın ${q(earlier)} ile son anlattığın ${q(o.text)} arasındaki eksik olay ne?",
                "İlk perdeyle son perdeyi yan yana koydum; aradaki sahneyi reklama vermeyelim.")
        }
        add("general.cause","${q(o.text)} dediğin olayın hemen öncesinde ne değişti; neden tam o anda oldu?",
            "Bu iddianın öncesini de açalım; bahane gökten zembille inmiş gibi durmasın.")
        add("general.counterexample","${q(o.text)} doğruysa hangi sonuç değişmiş olmalıydı; gerçekten ne değişti?",
            "Bahaneyi kendi sonucuyla çapraz sorguya aldım; ifade verirken birbirini tanısınlar.")
        val selected=beats.firstOrNull { it.key !in used } ?: Beat("last.${s.turns.size}",
            "${q(o.text)} için tek bir kontrol seç: anlattığını hangi gözlemin destekliyor?",
            "Son kontrolü tek ayrıntıya indiriyorum; bahane kalabalığa karışıp kaçmasın.")
        val joke=StoryHumor.select(s,a,selected.key,selected.reaction,spicy)
        return selected.copy(reaction=joke.text,jokeKey=joke.key)
    }
}

package com.lanu.yalan.domain

/** Grounded comic beats. Keys remember the joke, not a player's private text. */
object StoryHumor {
    data class Selection(val key: String, val text: String)
    private data class Line(val key: String, val text: String)

    fun select(s: Session, a: StoryEvidence.Analysis, key: String, base: String, spicy: Boolean): Selection {
        val o=a.latest
        val tier=(s.level.coerceIn(1,100)-1)/20
        val recent=s.recentJokeKeys+s.turns.mapNotNull { it.verdict.jokeKey }
        val seen=recent.toSet()
        fun line(id: String,text: String) = Line(id,text)
        // A repaired statement must not revive a retired accusation for the sake of a joke.
        if(key=="repair.next") return Selection("repair.accepted",base)
        if(key.startsWith("conflict.")) {
            val lead=a.conflict?.let { if(it.confirmed) it.finding else "İki ifadeyi sıraya koyuyoruz; henüz hüküm vermedim." } ?: ""
            val extra=when(a.conflict?.key) {
                "timeline" -> listOf(
                    "Zaman makinesi bu kadar dakik çalışıyorsa neden geç kaldığını ayrıca konuşacağız.",
                    "Zaman makinesi tarifesi ayrıysa onu baştan söyle; normal saatte bu iki ifade aynı koltuğa oturmuyor.",
                    "Zaman makinesi değilse bu saatin bir açıklama dilekçesi eksik.")
                "duration" -> listOf("Dakikaları bavula oturarak kapatmışsın; fermuar ifadede açılıyor.",
                    "Bu bekleme süresi dar gelen pantolon gibi; aralığa çekiştirerek sokamıyoruz.",
                    "Hesap makinesi ‘beni bu işe karıştırma’ dedi; şu aralığı bir açalım.")
                "vehicle" -> listOf("Bahane aktarma yapmış; bilette yolculuk görünmüyor.",
                    "Hikâye araç değiştirmiş; geçiş sahnesini reklam arasında kaçırmışız.",
                    "Bu senaryo dolmuş gibi; her ifadede başka bir araca biniyor.")
                "phone_order" -> listOf("Telefon kapalı, haber açık; aradaki bağlantıyı operatör de merak ediyor.",
                    "Telefon susmuş, mesaj konuşuyor; burada ikisinin ifadesini ayrı alacağım.",
                    "Bu hattın çektiği kesin değil; bahanenin çekim gücü epey yüksek.")
                else -> emptyList()
            }
            val lines=listOf(line("$key.original",base))+extra.mapIndexed { i,text -> line("$key.variant.$i","$lead $text".trim()) }
            return choose(lines,recent,0)
        }
        val callbacks=callbacks(s,a)
        callbacks.firstOrNull { it.key !in seen }?.let { return Selection(it.key,it.text) }
        val scope=when {
            key=="battery.order" && !StoryEvidence.has(o.text,"sarj","batarya","pil") -> "phone.order"
            key=="breakdown.sequence" && !StoryEvidence.has(o.text,"lastik") -> "vehicle.sequence"
            else -> key
        }
        val band=bandLines(scope,tier,o)
        val focused=listOf(line("$scope.original",base))+band.mapIndexed { i,text -> line("$scope.band.$tier.$i",text) }
        val common=commonLines(scope,o).mapIndexed { i,text -> line("$scope.common.$i",text) }
        val offset=Math.floorMod((s.id+s.topic+scope).hashCode(),focused.size)
        val ordered=focused.drop(offset)+focused.take(offset)+common
        val chosen=choose(ordered,recent,0)
        return if(spicy && tier>=3 && chosen.text.startsWith("Bahane navigasyonu")) chosen.copy(text=chosen.text.replace("Bahane navigasyonu","Yahu, bahane navigasyonu")) else chosen
    }

    /** Compatibility for callers that only need text; selection itself is stable on resume. */
    fun vary(s: Session,o: StoryEvidence.Observation,key: String,base: String): String =
        select(s,StoryEvidence.Analysis(listOf(o),null,false),key,base,false).text

    private fun choose(lines: List<Line>,recent: List<String>,offset: Int): Selection {
        val ordered=lines.drop(offset)+lines.take(offset)
        val selected=ordered.firstOrNull { it.key !in recent }
            ?: ordered.minByOrNull { recent.lastIndexOf(it.key) }
            ?: error("A comic beat needs at least one line")
        return Selection(selected.key,selected.text)
    }

    private fun callbacks(s: Session,a: StoryEvidence.Analysis): List<Line> {
        if(s.turns.isEmpty() || a.repaired || a.conflict!=null) return emptyList()
        val o=a.latest
        val previous=a.observations.dropLast(1)
        val lines=mutableListOf<Line>()
        if(previous.any { it.focus==StoryEvidence.Focus.ALARM } && o.focus==StoryEvidence.Focus.TRAFFIC)
            lines+=Line("callback.alarm.traffic","İlk perdede alarm sustu, ikinci perdede trafik durdu; bahane dizisi senden daha hareketli çıktı.")
        if(previous.any { it.focus==StoryEvidence.Focus.BATTERY } && o.focus==StoryEvidence.Focus.TRAFFIC)
            lines+=Line("callback.phone.traffic","Önce telefon kapandı, şimdi yol tıkandı; bahanenin bütün hatları meşgul.")
        if(previous.any { StoryEvidence.has(it.text,"lastik") && it.focus==StoryEvidence.Focus.BREAKDOWN } && o.messaged && o.person!=null)
            lines+=Line("callback.tire.message","Lastik dosyada, ${o.person} da haberi almış; bahanenin hem servis hem halkla ilişkiler bölümü var.")
        if(previous.any { it.focus==StoryEvidence.Focus.HOMEWORK } && o.focus==StoryEvidence.Focus.INTERNET)
            lines+=Line("callback.work.internet","İş teslim zincirinden bağlantı testine geçti; bahane, müfredatı kendi genişletiyor.")
        if(previous.none { it.correction } && !o.correction) {
            val before=previous.lastOrNull { it.clocks.isNotEmpty() }?.clocks?.lastOrNull()
            val now=o.clocks.lastOrNull()
            if(before!=null && now!=null && before.label!=now.label)
                lines+=Line("callback.clock.pair","${before.label} ilk ifadede, ${now.label} son ifadede; bahane artık saate bakmadan serbestçe dolaşamıyor.")
        }
        return lines
    }

    private fun bandLines(key: String,tier: Int,o: StoryEvidence.Observation): List<String> {
        val work=workName(o)
        return when(key) {
            "battery.order" -> listOf(
                listOf("Telefon mesaiyi bırakmış; bahane fazla mesaiye yazılmış.","Şarj bitmiş ama mazeretin bataryası maşallah, yüzde yüz."),
                listOf("Telefon güç tasarrufuna geçmiş; bahane gereksiz uygulama gibi arka planda çalışıyor.","Pilin son nefesini dinledik; şimdi kapanma sırasının ifadesini alalım."),
                listOf("Şarj dosyası dolu görünüyor; kapanma anının yüzde kaçı gerçekten belli?","Telefon kapanmış; hikâyenin boş kalan yerlerini powerbank diye kullanmayalım."),
                listOf("Telefonun pili bitmiş, bahanenin garantisi henüz incelemede.","Pili suçlu bulduk diye bütün gecikmeyi şarja takıp bırakamıyoruz."),
                listOf("Telefonun pilini sorgulamıyorum; savunmanın o pile yüklediği mesaiyi sorguluyorum.","Telefon kapalı, bahane canlı yayında; yayınla olay sırasının senkronu tutacak mı?")
            )[tier]
            "phone.order" -> listOf(
                listOf("Telefon kapanmış; bahane hattı nedense hiç meşgule düşmüyor.","Telefon sustu, mazeret hoparlöre geçti."),
                listOf("Kapanma kaydı var; bahane arama geçmişini temizlemeden sıraya bakalım.","Telefon kapalı diye hikâyeyi de uçak modunda kabul etmiyoruz."),
                listOf("Telefon susunca bütün savunmayı ona bırakmak kolay; kendisini dinleyemiyoruz tabii.","Kapalı telefona ifade imzalatamayız; bildiğin sırayı sen kuracaksın."),
                listOf("Telefon kapandı diye kalan seçenekleri de topluca kapatmış saymayalım.","Hikâyenin kapsama alanı geniş; kapanma anının yeri hâlâ netleşecek."),
                listOf("Telefonun son işlemiyle ilk sonraki haber aynı dosyada buluşacak; araya reklam alamıyoruz.","Telefon sessizliğini koruyor; savunmanın kronolojisi o kadar rahat davranamayacak.")
            )[tier]
            "alarm.setup" -> listOf(
                listOf("Alarm işe gelmemiş; bahane sabah ilk servise yetişmiş.","Alarm susmuş, mazeret sabah kuşağını tek başına sunuyor."),
                listOf("Alarm çalmamış; aradaki dakikalar ‘biz de buradayız’ diye el kaldırıyor.","Alarmın sesi yok; bahanenin ses sistemi bayağı kuvvetli."),
                listOf("Alarmı suçlamak kolay; kendisi duruşmaya sesli katılamıyor.","Alarm ifade vermiyor diye bütün dosyayı üstüne yıkmayalım."),
                listOf("Alarm sanık, ayarlar tanık; bu duruşma erteleme kaldırmaz.","Alarmın susma hakkı var; ayarların ifade verme sırası geldi."),
                listOf("Alarm beraat ederse bahane açıkta kalacak; ayarları sağlam savun.","Alarmın avukatı olsam ilk iş ayarlara bakardım; şimdilik dosyaya ben bakıyorum.")
            )[tier]
            "traffic.bottleneck" -> listOf(
                listOf("Trafik durmuş, mazeret sol şeridi kapmış.","Yol tıkalı, senaryonun geçiş üstünlüğü var galiba."),
                listOf("Trafik ağır ilerliyor; bahane yeşil dalgayı bulmuş.","Yol kalabalık olabilir; anlatı da konuyu dolaşmasın."),
                listOf("Güzergâh sorguya girdi; bahane navigasyonu kestirme cevap arıyor.","Yolun kilitlendiği yeri bulalım; bütün şehri sanık yapamayız."),
                listOf("Bahane navigasyonu ‘yeniden hesaplanıyor’da takılmış; rotanın hesabını biz açalım.","Trafik dosyada; seçtiğin yolun savunması henüz sinyal vermedi."),
                listOf("Gecikmenin bilirkişisi trafik değil; dakikaları tek tek dinleyeceğiz.","Trafiğe bütün faturayı kestin; çıkış saatinin de hesap dökümü var.")
            )[tier]
            "breakdown.sequence" -> listOf(
                listOf("Lastik patlamış; bahane hâlâ hava basıyor.","Lastiğin havası gitmiş; hikâyenin basıncı yerinde."),
                listOf("Lastik hava kaçırmış; anlatı aynı deliği büyütmesin.","Patlak lastiği gördük, bahanenin yaması henüz belli değil."),
                listOf("Lastiğe yama bulunur; doğrudan bildiğinle duyduğun aynı yama değil.","Lastik ekspertizde; senaryo ‘hatasız, boyasız’ diye ilana çıkmasın."),
                listOf("Bütün gecikmeyi lastiğe yükledin; bu yükü yama tutmayabilir.","Lastik arızası tamam; devam seçenekleri lastikle birlikte patlamış sayılmıyor."),
                listOf("Lastik dosyasını şişirmeden, gözünle gördüğün kısmı savun.","Lastik hava kaçırabilir; olay sırası da aynı ayrıcalığı istemesin.")
            )[tier]
            "vehicle.sequence" -> listOf(
                listOf("Araç arıza yapmış; bahane ilk çalıştırmada marş aldı.","Araç durmuş, mazeret rölantide beklemiyor."),
                listOf("Arıza dosyada; gecikmenin kilometresi ayrı okunacak.","Araç gitmiyor diye hikâyenin de boşa vites atmasına izin yok."),
                listOf("Arızaya teşhis koymadan, gördüğünle duyduğunu ayrı park edelim.","Motorun değil, savunmanın kaputunu açıyorum."),
                listOf("Araç durmuş olabilir; bütün seçenekleri onunla çekiciye yüklemeyelim.","Arıza bir engel; savunmanın her yolunu tek başına kapatmıyor."),
                listOf("Arıza, bekleme, devam: üçü de aynı kilometreyi gösterecek.","Araç muayenesini geçer belki; olay sırası henüz muayene sırasında.")
            )[tier]
            "homework.last" -> listOf(
                listOf("$work dosyada; bahane sözlüye kendi isteğiyle kalkmış.","$work konuşmuyor, mazeret parmak kaldırmayı bırakmıyor."),
                listOf("$work ile teslimi aynı sıraya oturtalım; bahane arada kopya çekmesin.","Teslim zincirinin bir halkası eksik; mazeret ataşla tutturmaya çalışıyor."),
                listOf("$work için savunma geldi; teslim adımı yoklamada henüz cevap vermedi.","Bu mazeret sözlüden kaçamaz; hangi adımda kaldığını anlatacak."),
                listOf("$work dosyasının kapağı var; savunmanın kapağı kendiliğinden kapanmıyor.","Bahane tez savunmasına gelmiş; önce teslim adımından geçsin."),
                listOf("$work hakkında jüri toplandı; hazır olmakla teslim etmek ayrı soruya cevap verecek.","Savunma sınıfı geçmek istiyor; teslim zincirinden son sözlüyü alıyoruz.")
            )[tier]
            "internet.failure" -> listOf(
                listOf("İnternet gitmiş, bahane tam çekiyor.","Bağlantı kopmuş; mazeret çevrimdışıyken de yazmaya devam ediyor."),
                listOf("Hat kesilmiş; bahanenin veri paketi bitmek bilmiyor.","İnternet durmuş, mazeret arka planda indirme yapıyor."),
                listOf("Bağlantıyı suçladık; uygulamayla dosya henüz ifade vermedi.","Hata kaydı ayrı, bahane güncellemesi ayrı; ikisini aynı anda kurmayalım."),
                listOf("İnternet geri gelince bahanenin sürümü değişiyor mu, ona bakacağız.","Kesinti dosyada; her yarım işi otomatik ona kaydetmiyoruz."),
                listOf("Bağlantı bahanesi hız testine çıktı; önce neden-sonuç paketleri ulaşacak.","Hat kopmuş olabilir; savunmanın bağlantıları da tek tek test edilecek.")
            )[tier]
            else -> emptyList()
        }
    }

    private fun commonLines(key: String,o: StoryEvidence.Observation): List<String> {
        val at=o.place?.let { "${StoryEvidence.quote(it,35)} dosyada; " } ?: ""
        val duration=o.duration?.let { "${StoryEvidence.quote(it,30)} dediğin süre kayıtta; " } ?: ""
        return when(key) {
            "battery.order" -> listOf("Şarjı telefon kaybetmiş, ikna mesaisini bahane devralmış.","Telefon ‘ben kapatıyorum’ demiş; mazeret temsilcisi hâlâ hatta.","Şarj bahanesiyle geldin; ben de kapanma anının dökümünü istiyorum.","Pilin adı dosyada geçiyor diye mazereti otomatik tam dolu saymıyoruz.","Telefonun pili yok; savunmanın da sınırsız bataryası yok.","${at}telefon kapanmış, soru dosyası açık kalmış.")
            "phone.order" -> listOf("Telefon kapalı; mazeretin bildirimleri durmuyor.","Telefon sustu diye olay sırasını sessize alamıyoruz.","Kapalı telefon savunmanın basın sözcüsü yapılmış.","Telefon kendini kapatmış; dosyayı aynı düğmeden kapatamıyoruz.","${at}kapanma sırasını uçak moduna almadan görelim.","Telefon cevap vermiyor; hikâyenin sırası verecek.")
            "alarm.setup" -> listOf("Alarm susmuş, bahane uyandırma servisi açmış.","Alarm görevini yapmamış diyorsun; mazeret fazla mesaiye kalmış.","Alarmın sessizliği epey gürültülü bir savunmaya dönüşmüş.","Alarmı sanık yaptık; ayarlar beraat ettirirse şaşırmayalım.","Alarm sesi dosyada yok; savunmanın ses ayarı yüksek.","Alarm çalmayınca mazeret devreye girmiş; çalışma saatlerini öğrenelim.")
            "traffic.bottleneck" -> listOf("${duration}bahane süreyi kilometre diye okutmasın.","${at}bahane navigasyonuna gelişigüzel adres yazmayalım.","Trafikte herkes durmuş; mazeret hız sınırını aşmış.","Yol kilitlenmiş olabilir; savunmanın anahtarını bir yere bırak.","Bu anlatının trafik cezası yok; süre hesabı var.","Trafik bahaneye yol vermiş; biz ifadeye yol soracağız.")
            "breakdown.sequence" -> listOf("Lastik patlamış, senaryo hâlâ hava basıyor.","Lastikteki delik belli; hikâyede yeni delik açmadan ilerleyelim.","Patlak lastiğe hava değil, olay sırasına açıklama lazım.","Lastiğin garantisini bilmiyorum; mazeretin ekspertizi bende.","${duration}lastiğin değil, beklemenin hesabını açalım.","Lastik dosyada; bahane jantın üstünde kaçmaya çalışmasın.")
            "vehicle.sequence" -> listOf("Araç arızası kayda girdi; mazeret otomatik vitesle geçemiyor.","Araç durmuş; hikâyenin göstergeleri hâlâ çalışmak zorunda.","Arıza ifadesi geldi, sebep-sonuç anahtarı henüz teslim edilmedi.","Araç çekilebilir; savunmayı kenara çekip bırakamıyoruz.","${duration}arıza mazeretin sınırsız otoparkı değil.","${at}araç sorunu var; savunmanın rotasını da görelim.")
            "homework.last" -> listOf("Teslim zincirini açtım; bahane son sayfayı koparmasın.","Bu savunma yoklamaya geldi; eksik adım da gelecek.","Mazeret kapağı renkli olabilir; içindeki sıra okunacak.","Hazırlıkla teslim arasındaki boşluk, bahanenin teneffüs alanı olmasın.","Dosyanın adı var; teslim halkasının da bir adı olacak.","Bahaneyi sözlüye aldık; tek kelimelik cevapla sınıf geçilmiyor.")
            "internet.failure" -> listOf("Bağlantı kopmuş; mazeret tam çekim gücüyle dönmüş.","Hat sorunu var diyorsun; anlatıyı yeniden başlat tuşuyla düzeltemiyoruz.","İnternet bahaneye şifre vermiş; olay sırasına da giriş yapalım.","Mazeret bağlandı; yarım kalan işlemin yüklenmesini bekliyorum.","Kesinti dosyası açıldı; bahaneye otomatik güncelleme yok.","Bağlantı gidince savunmanın da çevrimdışı kaydolması gerekmiyor.")
            "battery.backup" -> listOf("Telefon kapandı; bütün seçenekleri onunla aynı anda kapatmayalım.","B planının kapsama alanı da bu dosyada inceleniyor.","Telefon sustu; alternatiflerin de toplu izin aldığını varsaymıyorum.")
            "alarm.wakeup" -> listOf("Alarmın sesi yok ama uyandığın an hâlâ ifade verebilir.","Alarm vardiyayı atlamış; seni uyandıran şeyin nöbet kaydını alalım.","Alarm susmuştu; hikâyenin sabahı nasıl açıldığını görelim.")
            "breakdown.duration" -> listOf("${duration}dakikalar lastik gibi esnemiyor.","Arıza kayda girdi; bekleme süresini kafadan ekleyemiyoruz.","Bekleme dosyasına baktım; başlangıçla bitiş ayrı ayrı imza atacak.")
            "money.wallet" -> listOf("Cüzdan hikâyeden çekilmiş, mazeret kasada sıraya girmiş.","Cüzdanın son hareketini bulalım; bahane temassız geçmesin.","Cüzdan kayıp olabilir; savunmanın da son kullanım yeri var.")
            "money.trail" -> listOf("Bu bahanenin de hesabını soracağım; kasadan fişsiz geçmeyelim.","Para el değiştirmiş diyorsun; hikâye hesabı başkasına bırakmasın.","Mazeretin hesabı kabarık; önce gerçek tutarın dökümünü alalım.")
            "appointment.notice" -> listOf("Buluşma saati sabit, bahane seyyar; aynı noktada buluşturalım.","Randevu takvimde; mazeret kendine ayrı saat dilimi açmasın.","Buluşmanın iki tarafı var; savunmayı tek kişilik masa yapmayalım.")
            "weather.effect" -> listOf("Hava bahaneye şemsiye olmuş; altında kalan olayın da yerini bulalım.","Hava şartı kayda girdi; bütün takvimi yağmura teslim etmiyoruz.","Meteorolojiyi sorgulamıyorum; planın hangi adımının ıslandığına bakıyorum.")
            "sleep.wakeup" -> listOf("Uyku uzamış; mazeret kahvaltıyı da yatağa istemiş.","Uyku perdesi kapalıydı; hikâyenin sabahını açalım.","Uyku süreyi almış, mazeret açıklamasını uzatıyor.")
            "contact.content" -> listOf("Mesaj dosyada; senaryonun fragmanıyla olayın tamamını karıştırmayalım.","Haber vermek savunmaya giriş bileti; içeriği de kapıda soruluyor.","Mesaj gönderilmiş; hikâye her alıcıya başka sürüm gitmesin.")
            "person.knowledge" -> listOf("Hikâyeye ikinci isim girdi; ifade artık tek imzayla kapanmıyor.","Duyulanla görülen aynı sandalyeye oturmasın; ikisine ayrı soru var.","Savunmanın oyuncu kadrosu genişledi; kimin hangi sahneyi bildiğini görelim.")
            else -> emptyList()
        }
    }
    private fun workName(o: StoryEvidence.Observation) = when {
        StoryEvidence.has(o.text,"odev","defter","ders") -> "Ödev"
        StoryEvidence.has(o.text,"proje") -> "Proje"
        else -> "Dosya"
    }
}

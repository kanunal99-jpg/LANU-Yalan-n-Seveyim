package com.lanu.yalan.domain

/** Stable per-story variation: resuming a saved game never rerolls its voice. */
object StoryHumor {
    private val alternatives=mapOf(
        "battery.order" to listOf(
            "Şarjı telefon kaybetmiş, ikna mesaisini bahane devralmış.",
            "Telefon ‘ben kapatıyorum’ demiş; bahane temsilcisi hâlâ hatta.",
            "Telefon kapalı, mazeret çağrı merkezi gibi durmadan çalışıyor.",
            "Telefonun pili çekilmiş; bahanenin fişini çekmek için önce sırayı bulalım.",
            "Telefon kapalı, bahane canlı yayında; o yayının bağlantısını açalım."),
        "alarm.setup" to listOf(
            "Alarm çalmamış; bahane sabah ilk anonsu yapmış.",
            "Alarm susmuş, mazeret uyandırma servisi açmış.",
            "Alarm ifade vermiyor; bütün dosyayı onun üstüne yıkmak kolay tabii.",
            "Alarmın sessizliği şahane savunma olmuş; ama ayarlar tanık sandalyesinde.",
            "Alarmı sanık yaptın; ayarlar beraat ettirirse bahane kime kalacak?"),
        "traffic.bottleneck" to listOf(
            "Yol tıkanmış, bahane seri şeritte gidiyor.",
            "Trafikte herkes durmuş, mazeretin hız sınırını aşmış.",
            "Rota eksik; bahane kendi kendine navigasyon güncellemiş.",
            "Trafik tutanağa geçti; bütün gecikmeyi ona yazıp çıkamıyoruz.",
            "Yol dosyası kabarık; bahanenin kilometresiyle kaybettiğin dakikayı ayrı sayacağım."),
        "breakdown.sequence" to listOf(
            "Lastik patlamış; bahane hâlâ hava basıyor.",
            "Lastiğin havası gitmiş; hikâyenin basıncı hâlâ yüksek.",
            "Lastik yama ister, senaryo bağlantı; ikisini karıştırmayalım.",
            "Gecikmeyi lastiğe yükledin; bu yükü yama tutmayabilir.",
            "Lastik ekspertizde; bahane ‘boyasız, hatasız’ diye ilana çıkmasın."),
        "homework.last" to listOf(
            "Ödev dosyaya girdi; bahane yoklamada parmak kaldırmış.",
            "Bu dersin adı ‘mazeret’; teslim zincirinden sözlüye gireceksin.",
            "Ödevi sözlüye kaldırdık; cevabı bu kez bahane versin.",
            "Bahane tez savunmasına gelmiş; önce teslim adımından geçsin.",
            "Ödevin notunu bilmiyorum; bu teslim hikâyesi jüri karşısında."),
        "internet.failure" to listOf(
            "İnternet gitmiş, bahane çevrimdışıyken bile yazıyor.",
            "Bağlantı kopmuş, mazeret tam çekim gücüyle dönmüş.",
            "Hat kopuk olabilir; hikâyenin bağlantılarını da ayrı test edelim.",
            "Kesinti ihbarı alındı; her yarım işi onun dosyasına atamayız.",
            "Bağlantı bahanesi ağ testine çıktı; kopan adımla anlatılan sonucu eşleştireceğim.")
    )
    fun vary(s: Session,o: StoryEvidence.Observation,key: String,base: String): String {
        if(key=="battery.order" && !StoryEvidence.has(o.text,"sarj","batarya","pil")) return base
        if(key=="breakdown.sequence" && !StoryEvidence.has(o.text,"lastik")) return base
        val options=alternatives[key] ?: return base
        return if(Math.floorMod((s.id+s.topic).hashCode(),2)==0) base
            else options[(s.level.coerceIn(1,100)-1)/20]
    }
}

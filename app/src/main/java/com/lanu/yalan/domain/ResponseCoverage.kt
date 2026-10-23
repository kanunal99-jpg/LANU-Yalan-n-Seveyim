package com.lanu.yalan.domain

/** Conservative coverage of the actual question, not a reward for answer length. */
object ResponseCoverage {
    enum class Dimension(val label: String) {
        TIME("zaman"), PLACE("yer/rota"), PERSON("kişi"), SOURCE("bilginin kaynağı"),
        SEQUENCE("olay sırası"), CAUSE("neden"), ACTION("yapılan işlem"),
        DURATION("süre"), ALTERNATIVE("diğer seçenek"), CONTENT("haberin içeriği"), AMOUNT("tutar")
    }
    data class Result(val requested: Set<Dimension>,val answered: Set<Dimension>,val score: Int,val evasive: Boolean,val explanation: String)
    private val action=Regex("\\b(?:cik|var|ulas|ara|bekle|bildir|soyle|iste|gonder|al|ver|bak|gor|kontrol et|hazirla|bitir|yukle|dene|unut|bul|don|uyan|uyuyakal|kur|fark et|git|bin|dur|kapan|yaz)[a-z]*?(?:dim|dum|tim|tum|dik|duk|tik|tuk|di|du|ti|tu|mis|misti|ladi|ledi|di\u011f)[a-z]*\\b")
    private val name=Regex("\\b[A-ZÇĞİÖŞÜ][a-zçğıöşü]{2,}(?:['’][a-zçğıöşü]+)?\\b")
    private fun has(text: String,vararg words: String)=StoryEvidence.has(text,*words)
    private fun contains(text: String,vararg pieces: String)=pieces.any(text::contains)
    fun assess(question: String,o: StoryEvidence.Observation): Result {
        val q=StoryEvidence.normal(question)
        val n=StoryEvidence.normal(o.text)
        val requested=linkedSetOf<Dimension>()
        if(contains(q,"hangi saat","saat kac","kacta","ne zaman","hangi an","olman gereken saat","uyandigin saat","kurulu oldugu saat","hangi saati")) requested+=Dimension.TIME
        if(contains(q,"nerede","nereden","nereye","yol hangisi","takildigin yol","rotayi","rotani")) requested+=Dimension.PLACE
        if(Regex("\\b(kim|kime|kimden)\\b").containsMatchIn(q)) requested+=Dimension.PERSON
        if(contains(q,"neye gore","hangi gozlem","neyle","kanit","dogrudan","kendin gozle","kendin gor","nasil fark","nasil ayir","neyi kontrol","ne gordun","nereden biliyorsun")) requested+=Dimension.SOURCE
        if(contains(q,"siray","sirasi","sirasini","once ne","sonra ne","onceki","sonraki","gecisi","gecisi anlat","gecis neydi","aradaki eksik","arasindaki eksik")) requested+=Dimension.SEQUENCE
        if(contains(q,"neden","sebep","gerekce","seni geciktiren","ne engelledi","hangi engel","ne degisti","niye")) requested+=Dimension.CAUSE
        if(contains(q,"ne yaptin","ilk ne","ne yapildi","hangi islem","hangi adim","ne oldu","hangi hareket","ne denedin","nasil degerlendirdin","hangi is","son yaptigin")) requested+=Dimension.ACTION
        if(contains(q,"kac dakika","kac saat","ne kadar","bekledigin sure","bu sure","bekleme hangi","baslangici ve bitisi")) requested+=Dimension.DURATION
        if(contains(q,"baska","alternatif","secenek","firsatin","imkani","imkanin")) requested+=Dimension.ALTERNATIVE
        if(contains(q,"haberin icerigi","hangi aciklamayla","ne yazdin","ne soyledin","ne biliyordu")) requested+=Dimension.CONTENT
        if(contains(q,"miktar","tutar","kac para","hesabi","tek hesabin")) requested+=Dimension.AMOUNT

        val actions=action.findAll(n).count()
        val vague=has(n,"bilmem","hatirlam","bosver","bilmiyor")
        val properPerson=name.findAll(o.text).any { it.value !in setOf("Ben","Bir","Saat","Sabah","Sonra","Once","Önce","Telefon","Alarm","Durak") }
        val source=o.proof || contains(n,"tahmin","yaklasik","gordum","baktim","kontrol ettim","soyledi","duydum","saatime","ekrana","ekranina","telefonuma baktim")
        val ordered=contains(n,"sonra","once","beklerken","kapaninca","cikinca","bitince","fark edince","bunun uzerine","ardindan","sirayla") && (actions>0 || o.focuses.isNotEmpty()) ||
            o.clocks.size>1 || (o.clocks.isNotEmpty() && (actions>1 || o.messaged))
        val answered=requested.filterTo(linkedSetOf()) { dimension -> when(dimension) {
            Dimension.TIME -> o.clocks.isNotEmpty() || o.duration!=null || contains(n,"kapaninca","cikinca","bitince","sonra","once","sabah","aksam","ogle","gece","uyandigimda","kapanirken")
            Dimension.PLACE -> o.place!=null || o.vehicle!=null || has(n,"sokak","cadde","mahalle","kopru","istasyon","mutfak","salon","odam","yolday","otobustey","arabaday")
            Dimension.PERSON -> o.person!=null || properPerson || contains(n,"kimseye","kimse yok","yalnizdim")
            Dimension.SOURCE -> source
            Dimension.SEQUENCE -> ordered
            Dimension.CAUSE -> o.focuses.isNotEmpty() || contains(n,"cunku","yuzunden","dolayi","engelledi","unut","yetisemed","kacirdim")
            Dimension.ACTION -> actions>0 || o.messaged || o.vehicle!=null || o.focuses.isNotEmpty()
            Dimension.DURATION -> o.duration!=null || contains(n,"yarim saat","birkac dakika","bir kac dakika","bir saat")
            Dimension.ALTERNATIVE -> contains(n,"baska","denedim","yoktu","yok","bulamadim","mumkun degil","imkanim","firsatim","kapaliydi","ulasamadim")
            Dimension.CONTENT -> o.messaged && (o.focuses.isNotEmpty() || o.text.any { it in "\"“‘" }) || contains(n,"dedim","diye yazdim","diye haber")
            Dimension.AMOUNT -> Regex("\\b(?:\\d+|yuz|bin|on|yirmi|otuz|elli|altmis|yetmis|seksen|doksan)\\b").containsMatchIn(n)
        } }
        val score=when {
            vague && answered.isEmpty() -> 3
            requested.isEmpty() -> if(o.detailCount>=2) 20 else if(o.detailCount==1 || actions>0) 13 else 5
            answered.isEmpty() -> 5
            vague -> 12
            answered.size==requested.size -> 23
            else -> 10+(10*answered.size/requested.size)
        }
        fun labels(items: Set<Dimension>)=items.joinToString(", ") { it.label }
        val explanation=when {
            requested.isEmpty() && score>=13 -> "Cevabında olayla ilgili bir işlem veya somut ayrıntı var; bu, her iddianın doğru olduğunu kanıtlamaz."
            requested.isEmpty() -> "Cevapta takip edilebilir bir işlem veya somut ayrıntı tanınamadı."
            answered.isEmpty() -> "Soru ${labels(requested)} istiyordu; cevapta bu bilgi henüz tanınamadı."
            answered.size==requested.size -> "Soru ${labels(requested)} istiyordu; cevabın bu başlıkları karşılıyor."
            else -> "${labels(answered)} verdin; sorunun istediği ${labels(requested-answered)} hâlâ açık."
        }
        return Result(requested,answered,score,score<=5,explanation)
    }
}

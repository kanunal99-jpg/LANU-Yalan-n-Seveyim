package com.lanu.yalan.domain

import java.util.Locale
import kotlin.math.abs

/** Conservative text rules over the player's own statements. A clue is not proof of a lie. */
object StoryEvidence {
    private val tr = Locale.forLanguageTag("tr-TR")
    private val word = Regex("[a-z0-9]+")
    private val clause = Regex("[;!?\\n]+|,(?!\\d)|\\.(?:\\s|$)")
    private val numericClock = Regex("(?<!\\d)([01]?\\d|2[0-3])[:.]([0-5]\\d)(?!\\d)")
    private val hourClock = Regex("\\b(?:saat\\s+([01]?\\d|2[0-3])(?![\\d:.])|([01]?\\d|2[0-3])['’](?:da|de|ta|te)\\b)")
    private val spokenHours = linkedMapOf("birde" to 1,"ikide" to 2,"ucte" to 3,"dortte" to 4,"beste" to 5,"altida" to 6,"yedide" to 7,"sekizde" to 8,"dokuzda" to 9,"onda" to 10,"on birde" to 11,"on ikide" to 12)
    private val duration = Regex("\\b(\\d{1,3}|bes|on|on bes|yirmi|otuz|kirk|altmis)\\s*(dakika|dk|saat)\\b")
    private val depart = Regex("ciktim|cikmistim|ciktik|ayrildim|yola koyuldum")
    private val arrive = Regex("vardim|varmistim|vardik|ulastim|ulastik|varabildim|isteydim|ofisteydim|okuldaydim")
    private val contact = Regex("aradim|haber verdim|mesaj attim|yazdim")
    private val properPlace = Regex("\\b([A-ZÇĞİÖŞÜ][a-zçğıöşü]+(?:\\s+[A-ZÇĞİÖŞÜ][a-zçğıöşü]+){0,1})['’](?:da|de|ta|te|dan|den|tan|ten)\\b")
    private val vehicles = linkedMapOf("otobüs" to listOf("otobus"),"araba" to listOf("arabay","arabam","arabayi","otomobil"),"metro" to listOf("metro"),"taksi" to listOf("taksi"),"yürüyüş" to listOf("yuruyerek"))

    enum class Focus { BATTERY, ALARM, SLEEP, TRAFFIC, BREAKDOWN, HOMEWORK, INTERNET, ILLNESS, WEATHER, MONEY, APPOINTMENT, GENERAL }
    enum class ClockRole { DEPARTURE, ARRIVAL, CONTACT, OTHER }
    data class Clock(val minutes: Int, val label: String, val role: ClockRole, val period: String, val quote: String, val turn: Int)
    data class Observation(
        val text: String, val turn: Int, val focuses: Set<Focus>, val clocks: List<Clock>,
        val vehicle: String?, val person: String?, val place: String?, val duration: String?, val waitingMinutes: Int?,
        val messaged: Boolean, val proof: Boolean, val alone: Boolean, val correction: Boolean, val transition: Boolean
    ) {
        val focus: Focus get() = focuses.firstOrNull { it!=Focus.HOMEWORK } ?: focuses.firstOrNull() ?: Focus.GENERAL
        val detailCount: Int get() = listOf(clocks.isNotEmpty() || duration!=null,vehicle!=null || place!=null,person!=null,
            focuses.any { it!=Focus.GENERAL },proof || messaged).count { it }
    }
    data class Conflict(val key: String, val earlier: String, val later: String, val finding: String, val question: String, val joke: String, val confirmed: Boolean)
    data class Analysis(val observations: List<Observation>, val conflict: Conflict?, val repaired: Boolean) {
        val latest: Observation get() = observations.last()
    }

    fun normal(text: String) = text.lowercase(tr).replace('ı','i').replace('ğ','g').replace('ü','u').replace('ş','s').replace('ö','o').replace('ç','c')
    fun words(text: String): List<String> = word.findAll(normal(text)).map { it.value }.toList()
    fun has(text: String, vararg stems: String) = words(text).any { w -> stems.any(w::startsWith) }
    fun quote(text: String, limit: Int = 74): String {
        val clean=text.replace(Regex("\\s+")," ").replace('“','‘').replace('”','’').trim()
        return "“${clean.take(limit)}${if(clean.length>limit) "…" else ""}”"
    }
    fun inspect(s: Session, answer: String): Analysis {
        val observations=(s.turns.map { it.answer }+answer).mapIndexed { i,text -> observe(text,i+1) }
        val conflict=findConflict(observations)
        val before=if(observations.size>1) findConflict(observations.dropLast(1)) else null
        return Analysis(observations,conflict,before!=null && conflict==null)
    }
    fun observe(text: String, turn: Int = 1): Observation {
        val n=normal(text)
        val focuses=linkedSetOf<Focus>()
        if(has(n,"sarj","batarya","telefon") && has(n,"bitt","bitmis","kapand","sifir")) focuses+=Focus.BATTERY
        if(has(n,"alarm") && has(n,"calmad","duymad","uyuyakal","yanlis","kurmayi")) focuses+=Focus.ALARM
        if(!has(n,"alarm") && has(n,"uyuyakal","uyuyamad","uyandim","gec yattim")) focuses+=Focus.SLEEP
        if(has(n,"trafik","yogunluk","tikali","sikis") && !n.contains("trafik yok") && !n.contains("trafik degil") && !has(n,"trafiksiz")) focuses+=Focus.TRAFFIC
        if(has(n,"ariza","bozul","patlad","patlamis") && has(n,"lastik","arac","araba","otobus","metro","taksi","motor") && !has(n,"bozulmad","patlamad")) focuses+=Focus.BREAKDOWN
        if(has(n,"odev","defter","proje","teslim","dosya") && !has(n,"sifre")) focuses+=Focus.HOMEWORK
        if(has(n,"internet","wifi","baglanti","elektrik","modem") && has(n,"kesil","kop","yok","gitt","cekm","ariza","bozul") && !has(n,"kesilmed","kopmad","gitmed","bozulmad")) focuses+=Focus.INTERNET
        if(has(n,"hastaland","hastaydim","atesim","muayene") || (has(n,"hastane","doktor") && has(n,"gittim","randevu","tedavi"))) focuses+=Focus.ILLNESS
        if((has(n,"yagmur","firtina") || words(n).any { it in setOf("kar","kardan","karli","sel","selden") }) && !has(n,"yagmadi") && !n.contains("yagmur yok")) focuses+=Focus.WEATHER
        if(has(n,"para","lira","odeme","cuzdan","harcad","borc")) focuses+=Focus.MONEY
        if(has(n,"randevu","bulus","bekliyordu","kafe")) focuses+=Focus.APPOINTMENT
        val person=listOf("şoför","müdür","patron","öğretmen","arkadaş","annem","babam","doktor").firstOrNull { has(text,normal(it)) }
        val place=properPlace.find(text)?.groupValues?.get(1) ?: listOf("durak","ofis","okul","ev","kafe","hastane").firstOrNull { p ->
            when(p) { "ev" -> has(text,"evde","evden","eve"); else -> has(text,normal(p)) }
        }
        val vehicle=if(has(text,"degil","binmed","gitmed","kullanmad","sonra","aktarma","yerine","plan","inip")) null
            else if(has(text,"gidi","gitt","bind","yolculuk","kulland")) vehicles.filterValues { stems -> has(text,*stems.toTypedArray()) }.keys.singleOrNull() else null
        val d=duration.find(n)
        val amount=d?.groupValues?.get(1)?.let { it.toIntOrNull() ?: mapOf("bes" to 5,"on" to 10,"on bes" to 15,"yirmi" to 20,"otuz" to 30,"kirk" to 40,"altmis" to 60)[it] }
        val waiting=if(has(text,"bekled","beklem","trafik","durduk")) amount?.times(if(d?.groupValues?.get(2)=="saat") 60 else 1) else null
        return Observation(text,turn,focuses,clocks(text,turn),vehicle,person,place,d?.value,waiting,
            contact.containsMatchIn(n) || has(text,"gonderdim"),has(text,"bilet","fis","kayit","fotograf","ekran","mesaj"),
            has(text,"yalnizdim") || n.contains("kimse yoktu"),has(text,"duzeltiyorum","pardon") || n.contains("yanlis dedim") || n.contains("yanlis soyledim"),
            has(text,"sonra","aktarma","yerine","inip","birak","degistir","bitmeden","onceden"))
    }
    private fun clocks(text: String, turn: Int): List<Clock> = clause.split(text).flatMap { raw ->
        val n=normal(raw)
        val matches=mutableListOf<Triple<IntRange,Int,Int>>()
        numericClock.findAll(n).forEach { matches+=Triple(it.range,it.groupValues[1].toInt(),it.groupValues[2].toInt()) }
        hourClock.findAll(n).filter { m -> matches.none { it.first.first<=m.range.last && m.range.first<=it.first.last } }.forEach {
            matches+=Triple(it.range,(it.groupValues[1].ifBlank { it.groupValues[2] }).toInt(),0)
        }
        spokenHours.forEach { (spelling,hour) -> Regex("\\b${Regex.escape(spelling)}\\b").findAll(n).forEach { matches+=Triple(it.range,hour,0) } }
        matches.sortedBy { it.first.first }.map { (range,hour,minute) ->
            val period=when { has(n,"sabah","ogleye") -> "am"; has(n,"aksam","gece") -> "pm"; hour>12 -> "24h"; else -> "unknown" }
            val h=if(period=="pm" && hour in 1..11) hour+12 else hour
            val verbs=listOf(ClockRole.DEPARTURE to depart,ClockRole.ARRIVAL to arrive,ClockRole.CONTACT to contact)
            val nearest=verbs.flatMap { (role,pattern) -> pattern.findAll(n).map { role to minOf(abs(it.range.first-range.last),abs(range.first-it.range.last)) }.toList() }
                .filter { it.second<45 }.minByOrNull { it.second }?.first ?: ClockRole.OTHER
            Clock(h*60+minute,"%02d:%02d".format(Locale.ROOT,h,minute),nearest,period,raw.trim(),turn)
        }
    }
    private fun findConflict(os: List<Observation>): Conflict? {
        val claims=os.filter { it.vehicle!=null }
        claims.zipWithNext().firstOrNull { (a,b) ->
            a.vehicle!=b.vehicle && !b.correction && os.filter { it.turn>b.turn }.none { fix ->
                (fix.correction && fix.vehicle in setOf(a.vehicle,b.vehicle)) ||
                    (fix.transition && has(fix.text,*vehicles.getValue(a.vehicle!!).toTypedArray()) && has(fix.text,*vehicles.getValue(b.vehicle!!).toTypedArray()))
            }
        }?.let { (a,b) -> return Conflict("vehicle",a.text,b.text,
            "Önce ${quote(a.text)}, sonra ${quote(b.text)} dedin; araç değişimini henüz bağlamadın.",
            "Araç değişimi hangi noktada oldu: ${a.vehicle} ile ${b.vehicle} arasındaki geçişi anlatır mısın?",
            "Bahane aktarma yapmış, yolcunun haberi yok.",true) }
        val clocks=mutableListOf<Clock>()
        os.forEach { o ->
            if(o.correction) clocks.removeAll { previous -> o.clocks.any { it.role==previous.role } }
            clocks+=o.clocks
        }
        val dep=clocks.lastOrNull { it.role==ClockRole.DEPARTURE }
        val arr=clocks.lastOrNull { it.role==ClockRole.ARRIVAL }
        val shiftedDay=os.any { has(it.text,"ertesi","dun","yarin") || normal(it.text).contains("gece yarisi") }
        if(dep!=null && arr!=null && !shiftedDay && dep.minutes>arr.minutes) {
            val certain=dep.period==arr.period && dep.period!="unknown"
            return Conflict("timeline",dep.quote,arr.quote,"Çıkış ${dep.label}, varış ${arr.label}; aynı günse sıra ters.",
                "${dep.label} çıkış ile ${arr.label} varış aynı gün mü; hangi saati düzeltiyorsun?",
                "Servis yerine zaman makinesi mi geldi?",certain)
        }
        val waiting=os.lastOrNull { it.waitingMinutes!=null }
        if(dep!=null && arr!=null && waiting!=null && !shiftedDay && arr.minutes>=dep.minutes && waiting.waitingMinutes!!>arr.minutes-dep.minutes) {
            return Conflict("duration",dep.quote,waiting.text,"${dep.label}–${arr.label} arası ${arr.minutes-dep.minutes} dakika; ${waiting.duration} bekleme bu aralığa sığmıyor.",
                "${arr.minutes-dep.minutes} dakikalık çıkış–varış aralığına ${waiting.duration} beklemeyi nasıl sığdırıyorsun?",
                "Dakikaları bavula oturarak mı kapattın?",dep.period!="unknown" && arr.period!="unknown")
        }
        val battery=os.firstOrNull { Focus.BATTERY in it.focuses }
        val message=os.lastOrNull { it.messaged && battery!=null && it.turn>battery.turn }
        if(battery!=null && message!=null && os.filter { it.turn>=message.turn }.none { has(it.text,"sarja","sarjett","baska","telefonundan","bitmeden","onceden","odunc") || normal(it.text).contains("sarj et") }) {
            return Conflict("phone_order",battery.text,message.text,
                "${quote(battery.text)} ile ${quote(message.text)} arasında iletişimin sırası eksik.",
                "Telefonun kapanmasıyla gönderdiğin mesajı sıraya koy: mesaj kapanmadan önce mi gitti, başka cihaz mı kullandın?",
                "Telefon kapalı, bahane tam çekiyor; aradaki bağlantı nerede?",false)
        }
        return null
    }
}

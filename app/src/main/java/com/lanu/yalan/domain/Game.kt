package com.lanu.yalan.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Serializable
data class Verdict(
    val reply: String,
    val question: String,
    val consistency: Int,
    val plausibility: Int,
    val detail: Int,
    val defense: Int,
    // Suggested question angle, persisted in both modes. Not part of model-generated JSON.
    val challengeKey: String? = null
) {
    val score: Int get() = consistency + plausibility + detail + defense
    fun validated(final: Boolean = false): Verdict {
        require(listOf(consistency, plausibility, detail, defense).all { it in 0..25 }) { "Puan aralığı geçersiz" }
        require(reply.isNotBlank() && reply.length <= 700) { "Yorum okunamadı" }
        require(final || question.isNotBlank()) { "Soru oluşturulamadı" }
        require(question.length <= 500) { "Soru çok uzun" }
        require(challengeKey==null || challengeKey.length<=80) { "Sorgu kimliği geçersiz" }
        return this
    }
}
@Serializable
data class Turn(val question: String, val answer: String, val verdict: Verdict)
@Serializable
enum class PlayMode { FAST, LOCAL_AI }
@Serializable
data class Session(
    val id: String,
    val level: Int,
    val topic: String,
    val opening: String,
    val turns: List<Turn> = emptyList(),
    val credited: Boolean = false,
    // Old saved stories were evaluated by Qwen. Never silently change their scorer.
    val mode: PlayMode = PlayMode.LOCAL_AI
) {
    val finished: Boolean get() = turns.size >= 5
    val question: String get() = turns.lastOrNull()?.verdict?.question ?: opening
    val finalScore: Int get() = turns.lastOrNull()?.verdict?.score ?: 0
}
@Serializable
data class GameSave(
    val schema: Int = 1,
    val unlocked: Int = 1,
    val best: Map<Int, Int> = emptyMap(),
    val session: Session? = null,
    val spicy: Boolean = true,
    val onboarding: Boolean = false,
    val mode: PlayMode = PlayMode.FAST
)
object Rules {
    fun threshold(level: Int) = 40 + (level.coerceIn(1, 100) - 1) / 3
    fun rank(level: Int): String = when (level.coerceIn(1, 100)) {
        in 1..20 -> "Mahalle çırağı"
        in 21..40 -> "Bahane ustası"
        in 41..60 -> "Kıvırma profesörü"
        in 61..80 -> "Senaryo mimarı"
        else -> "Efsanelerin efendisi"
    }
    fun challenge(level: Int): String = when (level) {
        in 1..20 -> "Tek somut ayrıntı sor. Kolay ve sıcak ol."
        in 21..40 -> "Yer, zaman ve olay sırasını sorgula. Cevaptan bir ayrıntıyı takip et."
        in 41..60 -> "Önceki iki yanıtı karşılaştır; varsa gerçek bir çelişkiyi alıntılayarak sor."
        in 61..80 -> "Neden-sonuç ilişkisini ve tanıkların ne göreceğini sorgula. İddiaları çapraz kontrol et."
        else -> "Tüm cevapları karşılaştır. En zayıf bağlantıyı bul; zaman çizelgesi veya karşı örnek ile zorla. Uydurma çelişki üretme."
    }
    fun credit(save: GameSave): GameSave {
        val s = save.session ?: return save
        if (!s.finished || s.credited) return save
        val passed = s.finalScore >= threshold(s.level)
        return save.copy(
            unlocked = if (passed && s.level == save.unlocked) (save.unlocked + 1).coerceAtMost(100) else save.unlocked,
            best = save.best + (s.level to maxOf(save.best[s.level] ?: 0, s.finalScore)),
            session = s.copy(credited = true)
        )
    }
    fun canPlay(level: Int, save: GameSave) = level in 1..save.unlocked.coerceIn(1, 100)
    fun stars(score: Int) = when { score >= 85 -> 3; score >= 65 -> 2; score >= 40 -> 1; else -> 0 }
}
object VerdictParser {
    private val json = Json { ignoreUnknownKeys = false }
    fun parse(raw: String, final: Boolean = false): Verdict {
        val clean = raw.substringAfter("</think>", raw).trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return json.decodeFromString<Verdict>(clean).validated(final)
    }
}

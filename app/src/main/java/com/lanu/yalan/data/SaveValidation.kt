package com.lanu.yalan.data

import com.lanu.yalan.domain.GameSave

/** Shared bounds for device storage and user-selected portable backups. Never repairs silently. */
object SaveValidation {
    fun validate(save: GameSave): GameSave {
        require(save.schema==1) { "Desteklenmeyen kayıt sürümü" }
        require(save.unlocked in 1..100) { "Seviye kaydı geçersiz" }
        require(save.best.size<=100 && save.best.all { (level,score) -> level in 1..save.unlocked && score in 0..100 }) { "Puan kaydı geçersiz" }
        validateJokes(save.recentJokeKeys)
        save.session?.let { s ->
            require(s.id.isNotBlank() && s.id.length<=128) { "Hikâye kimliği geçersiz" }
            require(s.level in 1..save.unlocked && s.topic.trim().length in 3..100) { "Hikâye kaydı geçersiz" }
            require(s.opening.isNotBlank() && s.opening.length<=500 && s.turns.size<=5) { "Soru kaydı geçersiz" }
            require(!s.credited || s.finished) { "Tamamlanmamış hikâye puanlanamaz" }
            validateJokes(s.recentJokeKeys)
            var expected=s.opening
            s.turns.forEachIndexed { index,turn ->
                require(turn.question==expected && turn.question.isNotBlank()) { "Hikâye soru sırası geçersiz" }
                require(turn.answer.trim().length in 3..280) { "Cevap kaydı geçersiz" }
                turn.verdict.validated(index==4)
                expected=turn.verdict.question
            }
        }
        return save
    }
    private fun validateJokes(keys: List<String>) {
        require(keys.size<=40 && keys.all { it.isNotBlank() && it.length<=120 }) { "Mizah geçmişi geçersiz" }
    }
}

package com.lanu.yalan.ai

enum class AiFailure(val explanation: String) {
    TIMEOUT("AI, 30 saniyelik süre sınırında yanıtı tamamlayamadı."),
    MEMORY("AI modeli için yeterli bellek ayrılamadı."),
    LOAD("AI modeli açılamadı."),
    CONTEXT("Bu hikâye AI modelinin bağlam sınırını aştı."),
    OUTPUT("AI yanıtı eksik veya okunamayan biçimde geldi. Puan yazılmadı."),
    MISSING_MODEL("AI modu için önce modeli indirmen gerekiyor."),
    ENGINE("AI motoru yanıtı işleyemedi.")
}

class AiException(val reason: AiFailure, cause: Throwable? = null) : IllegalStateException(reason.explanation, cause)

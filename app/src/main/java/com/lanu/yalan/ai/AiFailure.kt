package com.lanu.yalan.ai

enum class AiFailure(val explanation: String) {
    TIMEOUT("AI, 30 saniyelik süre sınırında yanıtı tamamlayamadı."),
    MEMORY("AI modeli için yeterli bellek ayrılamadı."),
    LOAD("AI modeli açılamadı."),
    CONTEXT("Bu hikâye AI modelinin bağlam sınırını aştı."),
    OUTPUT("AI yanıtı eksik veya okunamayan biçimde geldi. Puan yazılmadı."),
    MISSING_MODEL("AI modu için önce modeli indirmen gerekiyor."),
    ENGINE("AI motoru yanıtı işleyemedi."),
    ONLINE_KEY("Çevrimiçi AI anahtarı bağlı değil veya okunamadı. Ayarlar'dan yeniden bağlayabilirsin."),
    ONLINE_STORAGE("AI anahtarı şifreli kaydedilemedi. Telefonundaki boş alanı kontrol et; mevcut ilerleme korunuyor."),
    ONLINE_AUTH("AI bağlantısı doğrulanamadı. Anahtarı ve hesap erişimini Ayarlar'dan kontrol et."),
    ONLINE_QUOTA("Ücretsiz AI kotası doldu veya istek sınırına ulaşıldı. Ücretli modele geçilmedi."),
    ONLINE_TIMEOUT("Çevrimiçi AI 15 saniyelik süre sınırında tamamlanamadı."),
    ONLINE_NETWORK("Ücretsiz AI servisine ulaşılamadı. Bağlantını kontrol edip yeniden deneyebilirsin."),
    ONLINE_COST("Servis ücretsiz kullanım bilgisiyle uyuşmayan bir cevap verdi. Yeni istek durduruldu."),
    ONLINE_SECRET("Kişisel AI anahtarı oyun metninde tanındı. Bu metin işlenmedi; anahtarı hikâyeden çıkar."),
    ONLINE_OUTPUT("AI'nın cevabı, puanı veya ücretsiz kullanım bilgisi doğrulanamadı. Puan yazılmadı.")
}

class AiException(val reason: AiFailure, cause: Throwable? = null) : IllegalStateException(reason.explanation, cause)

package com.lanu.yalan.domain

/** Five independent foreground/parked stories. No implicit eviction of unfinished work. */
object StoryShelf {
    const val MAX_STORIES = 5
    fun stories(save: GameSave): List<Session> = listOfNotNull(save.session) + save.parkedSessions
    fun activeCount(save: GameSave) = stories(save).count { !it.finished }
    fun canStart(save: GameSave) = activeCount(save) < MAX_STORIES

    fun draft(save: GameSave, answer: String): GameSave {
        require(answer.length <= 280) { "Cevap taslağı çok uzun" }
        val selected=save.session ?: return save
        if(selected.finished || selected.draftAnswer==answer) return save
        return save.copy(session=selected.copy(draftAnswer=answer))
    }

    fun start(save: GameSave, story: Session): GameSave {
        require(canStart(save)) { "Beş aktif hikâyen var. Yeni konu için birini kapat." }
        require(stories(save).none { it.id==story.id }) { "Hikâye kimliği zaten kullanılıyor" }
        require(!story.finished && story.turns.isEmpty() && story.draftAnswer.isEmpty()) { "Yeni hikâye geçersiz" }
        val parked=save.parkedSessions+listOfNotNull(save.session?.takeUnless { it.finished })
        return save.copy(session=story,parkedSessions=parked,onboarding=true)
    }

    fun select(save: GameSave, id: String): GameSave {
        if(save.session?.id==id) return save
        val target=save.parkedSessions.singleOrNull { it.id==id }
            ?: throw IllegalArgumentException("Hikâye bulunamadı")
        val parked=save.parkedSessions.filterNot { it.id==id }+listOfNotNull(save.session?.takeUnless { it.finished })
        return save.copy(session=target,parkedSessions=parked)
    }

    fun replaceSelected(save: GameSave, story: Session): GameSave {
        require(save.session?.id==story.id) { "Yanıt başka hikâyeye yazılamaz" }
        return save.copy(session=story)
    }

    /** Called only after an explicit close confirmation. Level records are retained. */
    fun close(save: GameSave, id: String): GameSave {
        require(stories(save).any { it.id==id }) { "Hikâye bulunamadı" }
        return if(save.session?.id==id) save.copy(session=save.parkedSessions.firstOrNull(),parkedSessions=save.parkedSessions.drop(1))
        else save.copy(parkedSessions=save.parkedSessions.filterNot { it.id==id })
    }
}

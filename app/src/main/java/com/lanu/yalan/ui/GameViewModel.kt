package com.lanu.yalan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanu.yalan.ai.GameEngine
import com.lanu.yalan.ai.ModelStore
import com.lanu.yalan.ai.AiException
import com.lanu.yalan.data.SaveStore
import com.lanu.yalan.data.DeviceInfo
import com.lanu.yalan.data.DeviceProfile
import com.lanu.yalan.data.DeviceNotReadyException
import com.lanu.yalan.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

enum class Screen { HOME, GAME, LEVELS, SETTINGS }
data class UiState(
    val save: GameSave = GameSave(),
    val screen: Screen = Screen.HOME,
    val selectedLevel: Int = 1,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val status: String = "",
    val ready: Boolean = false,
    val error: String? = null,
    val recovery: Boolean = false,
    val answer: String = "",
    val device: DeviceProfile? = null,
    val aiError: Boolean = false,
    val responseMs: Long? = null
) {
    val canStart: Boolean get() = save.mode == PlayMode.FAST || ready
    val canAnswer: Boolean get() = save.session?.mode == PlayMode.FAST || ready
}
@HiltViewModel
class GameViewModel @Inject constructor(
    private val store: SaveStore,
    private val model: ModelStore,
    private val engine: GameEngine,
    private val deviceInfo: DeviceInfo
) : ViewModel() {
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    init {
        viewModelScope.launch {
            try {
                val saved = store.read()
                mutable.update { it.copy(save=saved, selectedLevel=saved.unlocked, ready=model.ready(), loading=false, device=deviceInfo.snapshot()) }
            } catch(e: Exception) {
                mutable.update { it.copy(loading=false, recovery=true, error="Kayıt okunamadı. Veriyi sessizce silmedik. Ayarlardan sıfırlayabilir veya uygulamayı yeniden açabilirsin.") }
            }
        }
    }
    fun navigate(screen: Screen) { if(!mutable.value.busy && !mutable.value.loading) mutable.update { it.copy(screen=screen,error=null) } }
    fun select(level: Int) { if(Rules.canPlay(level,mutable.value.save)) mutable.update { it.copy(selectedLevel=level,screen=Screen.HOME) } }
    fun answer(value: String) { if(!mutable.value.busy) mutable.update { it.copy(answer=value.take(280)) } }
    fun clearError() { mutable.update { it.copy(error=null,aiError=false) } }
    private fun task(status: String, work: suspend () -> Unit) {
        if(mutable.value.busy || mutable.value.loading || mutable.value.recovery) return
        mutable.update { it.copy(busy=true,status=status,error=null,aiError=false) }
        job = viewModelScope.launch {
            try { work() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                mutable.update { it.copy(aiError=e is AiException || e is DeviceNotReadyException,error=when {
                    e is AiException -> "${e.reason.explanation} Cevabın duruyor. Yeniden deneyebilir veya bu hikâyeye Hızlı oyun ile devam edebilirsin. [AI-${e.reason.name}]"
                    e is DeviceNotReadyException -> e.message ?: "Cihaz şu anda hazır değil."
                    it.downloading -> e.message ?: "İndirme durdu. Bağlantını kontrol edip devam et."
                    else -> "Kayıt işlemi tamamlanamadı. Önceki kaydın ve yazdığın cevap korunuyor. Boş depolama alanını kontrol edip yeniden dene. [SAVE]"
                }) }
            } catch(e: LinkageError) { mutable.update { it.copy(aiError=true,error="AI kitaplığı bu cihazda açılamadı. Bu hikâyeye Hızlı oyun ile devam edebilirsin. [AI-LIBRARY]") } }
            finally { mutable.update { it.copy(busy=false,downloading=false,status="") } }
        }
    }
    private suspend fun persist(save: GameSave) { store.write(save); mutable.update { it.copy(save=save) } }
    fun start(topic: String) {
        val value=topic.trim()
        if(value.length !in 3..100 || !mutable.value.canStart) return
        task("LANU ilk soruyu hazırlıyor…") {
            val started=System.nanoTime()
            val s=Session(UUID.randomUUID().toString(),mutable.value.selectedLevel,value,"",mode=mutable.value.save.mode)
            val response=engine.ask(s,null,mutable.value.save.spicy)
            val session=s.copy(opening=response.question)
            persist(mutable.value.save.copy(session=session,onboarding=true))
            mutable.update { it.copy(screen=Screen.GAME,answer="",responseMs=(System.nanoTime()-started)/1_000_000) }
        }
    }
    fun submit() {
        val before=mutable.value
        val s=before.save.session ?: return
        val answer=before.answer.trim()
        if(answer.length !in 3..280 || s.finished || !before.canAnswer) return
        task("LANU hikâyenin açıklarını arıyor…") {
            val started=System.nanoTime()
            val verdict=engine.ask(s,answer,before.save.spicy)
            val session=s.copy(turns=s.turns+Turn(s.question,answer,verdict))
            val next=Rules.credit(before.save.copy(session=session))
            persist(next)
            mutable.update { it.copy(answer="",responseMs=(System.nanoTime()-started)/1_000_000) }
        }
    }
    fun playMode(mode: PlayMode) = task("Oyun tercihi kaydediliyor…") {
        if(mode == PlayMode.FAST) engine.release()
        persist(mutable.value.save.copy(mode=mode))
    }
    fun continueFast() {
        val s=mutable.value.save.session ?: return
        if(s.finished) return
        task("Hızlı oyuna geçiliyor…") {
            engine.release()
            persist(mutable.value.save.copy(mode=PlayMode.FAST,session=s.copy(mode=PlayMode.FAST)))
        }
    }
    fun download() {
        task("Oyun beyni indiriliyor…") {
            mutable.update { it.copy(downloading=true) }
            model.download { progress,label -> mutable.update { it.copy(progress=progress,status=label) } }
            mutable.update { it.copy(ready=true) }
        }
    }
    fun cancel() { engine.cancel(); job?.cancel() }
    fun spicy(value: Boolean) = task("Kaydediliyor…") { persist(mutable.value.save.copy(spicy=value)) }
    fun deleteModel() = task("Model kaldırılıyor…") {
        engine.release(); model.delete(); mutable.update { it.copy(ready=false,progress=0f) }
    }
    fun reset() {
        if(mutable.value.busy) return
        mutable.update { it.copy(recovery=false) }
        task("İlerleme sıfırlanıyor…") {
            store.reset(); persist(GameSave())
            mutable.update { it.copy(selectedLevel=1,answer="",screen=Screen.HOME) }
        }
    }
    override fun onCleared() { engine.cancel(); super.onCleared() }
}

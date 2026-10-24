package com.lanu.yalan.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanu.yalan.ai.GameEngine
import com.lanu.yalan.ai.ModelStore
import com.lanu.yalan.ai.AiException
import com.lanu.yalan.ai.FreeAiService
import com.lanu.yalan.ai.FreeAiProtocol
import com.lanu.yalan.data.OnlineAiCredentials
import com.lanu.yalan.data.SaveStore
import com.lanu.yalan.data.DeviceInfo
import com.lanu.yalan.data.PerformanceMonitor
import com.lanu.yalan.data.DeviceProfile
import com.lanu.yalan.data.DeviceNotReadyException
import com.lanu.yalan.domain.*
import com.lanu.yalan.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val responseMs: Long? = null,
    val onlineReady: Boolean = false
) {
    val canStart: Boolean get() = StoryShelf.canStart(save) && when(save.mode) {PlayMode.FAST -> true;PlayMode.LOCAL_AI -> ready;PlayMode.FREE_ONLINE_AI -> onlineReady}
    val canAnswer: Boolean get() = when(save.session?.mode) {PlayMode.FAST -> true;PlayMode.LOCAL_AI -> ready;PlayMode.FREE_ONLINE_AI -> onlineReady;null -> false}
}
@HiltViewModel
class GameViewModel @Inject constructor(
    private val store: SaveStore,
    private val model: ModelStore,
    private val engine: GameEngine,
    private val performance: PerformanceMonitor,
    private val deviceInfo: DeviceInfo,
    private val online: FreeAiService,
    private val credentials: OnlineAiCredentials
) : ViewModel() {
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var draftJob: Job? = null
    private val persistence=Mutex()
    init {
        viewModelScope.launch {
            try {
                val saved = store.read()
                val onlineReady=try {credentials.read()!=null} catch(e: CancellationException) {throw e} catch(e: Exception) {diagnostic(e);false}
                mutable.update { it.copy(save=saved, answer=saved.session?.draftAnswer.orEmpty(), selectedLevel=saved.unlocked, ready=model.ready(), onlineReady=onlineReady, loading=false, device=deviceInfo.snapshot()) }
            } catch(e: Exception) {
                mutable.update { it.copy(loading=false, recovery=true, error="Kayıt okunamadı. Veriyi sessizce silmedik. Ayarlardan sıfırlayabilir veya uygulamayı yeniden açabilirsin.") }
            }
        }
    }
    fun navigate(screen: Screen) = task("Taslağın kaydediliyor…",allowRecovery=true,saveAnswer=!mutable.value.recovery) { mutable.update { it.copy(screen=screen,error=null) } }
    fun select(level: Int) {
        if(Rules.canPlay(level,mutable.value.save)) task("Taslağın kaydediliyor…") { mutable.update { it.copy(selectedLevel=level,screen=Screen.HOME) } }
    }
    fun answer(value: String) {
        if(mutable.value.busy || mutable.value.loading || mutable.value.save.session?.finished!=false) return
        mutable.update { it.copy(answer=value.take(280)) }
        saveDraft(400)
    }
    /** Debounced encrypted autosave; switching/navigation flush synchronously before changing stories. */
    fun saveDraft(delayMs: Long=0) {
        if(mutable.value.busy || mutable.value.loading || mutable.value.recovery) return
        draftJob?.cancel()
        draftJob=viewModelScope.launch {
            try {
                if(delayMs>0) delay(delayMs)
                if(!mutable.value.busy) flushDraft()
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {
                diagnostic(e)
                mutable.update { it.copy(error="Taslak henüz kaydedilemedi. Cevabın ekranda duruyor; depolama alanını kontrol edip yeniden dene. [SAVE-DRAFT]") }
            }
        }
    }
    fun clearError() { mutable.update { it.copy(error=null,aiError=false) } }
    private fun task(status: String, allowRecovery: Boolean = false, saveAnswer: Boolean = true, work: suspend () -> Unit) {
        if(mutable.value.busy || mutable.value.loading || (mutable.value.recovery && !allowRecovery)) return
        mutable.update { it.copy(busy=true,status=status,error=null,aiError=false) }
        draftJob?.cancel()
        job = viewModelScope.launch {
            try { if(saveAnswer) flushDraft();work() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                diagnostic(e)
                mutable.update { it.copy(aiError=e is AiException || e is DeviceNotReadyException,error=when {
                    e is AiException -> e.reason.explanation+if(it.screen==Screen.GAME) " Cevabın duruyor. Yeniden deneyebilir veya bu hikâyeye Hızlı oyun ile devam edebilirsin. [AI-${e.reason.name}]"
                        else " Yeni puan yazılmadı. Ayarlarını kontrol edip yeniden deneyebilirsin. [AI-${e.reason.name}]"
                    e is DeviceNotReadyException -> e.message ?: "Cihaz şu anda hazır değil."
                    it.downloading -> e.message ?: "İndirme durdu. Bağlantını kontrol edip devam et."
                    else -> "Kayıt işlemi tamamlanamadı. Önceki kaydın ve yazdığın cevap korunuyor. Boş depolama alanını kontrol edip yeniden dene. [SAVE]"
                }) }
            } catch(e: LinkageError) { diagnostic(e); mutable.update { it.copy(aiError=true,error="AI kitaplığı bu cihazda açılamadı. Bu hikâyeye Hızlı oyun ile devam edebilirsin. [AI-LIBRARY]") } }
            finally { mutable.update { it.copy(busy=false,downloading=false,status="") } }
        }
    }
    private fun diagnostic(e: Throwable) {
        // Debug diagnostics contain only types and source locations, never exception messages or story data.
        if(BuildConfig.DEBUG) Log.w("LANU", e.javaClass.simpleName + " at " + e.stackTrace.filter { it.className.startsWith("com.lanu.") }.take(4).joinToString())
    }
    private suspend fun persist(save: GameSave, update: (UiState) -> UiState = { it }) = withContext(NonCancellable) { persistence.withLock {
        store.write(save); mutable.update { update(it.copy(save=save)) }
    } }
    private suspend fun flushDraft() = withContext(NonCancellable) { persistence.withLock {
        val before=mutable.value
        val next=StoryShelf.draft(before.save,before.answer)
        if(next!=before.save) {store.write(next);mutable.update {it.copy(save=next)}}
    } }
    fun resumeStory(id: String) = task("Hikâyen açılıyor…") {
        val next=StoryShelf.select(mutable.value.save,id)
        if(next.session?.mode!=PlayMode.LOCAL_AI) engine.release()
        persist(next) { it.copy(screen=Screen.GAME,answer=next.session?.draftAnswer.orEmpty(),responseMs=null) }
    }
    fun closeStory(id: String) = task("Hikâye kapatılıyor…") {
        val next=StoryShelf.close(mutable.value.save,id)
        if(next.session?.mode!=PlayMode.LOCAL_AI) engine.release()
        persist(next) { it.copy(answer=next.session?.draftAnswer.orEmpty(),responseMs=null,screen=if(next.session==null) Screen.HOME else it.screen) }
    }
    private suspend fun <T> measured(mode: PlayMode, phase: TimingPhase, work: suspend () -> T): Pair<T,Long> {
        val start=System.nanoTime()
        var outcome=TimingOutcome.ERROR
        var elapsed=0L
        val result=try { work().also { outcome=TimingOutcome.SUCCESS } }
        catch(e: CancellationException) { outcome=TimingOutcome.CANCELLED;throw e }
        catch(e: AiException) { if(e.reason in setOf(com.lanu.yalan.ai.AiFailure.TIMEOUT,com.lanu.yalan.ai.AiFailure.ONLINE_TIMEOUT)) outcome=TimingOutcome.TIMEOUT;throw e }
        finally {
            elapsed=(System.nanoTime()-start)/1_000_000
            withContext(NonCancellable) { try { performance.record(mode,elapsed,outcome,phase) } catch(e: Exception) { diagnostic(e) } }
        }
        return result to elapsed
    }
    fun start(topic: String) {
        val value=topic.trim()
        if(value.length !in 3..100 || !mutable.value.canStart) return
        task("LANU ilk soruyu hazırlıyor…") {
            val before=mutable.value
            val (next,elapsed)=measured(before.save.mode,TimingPhase.OPENING) {
                val s=Session(UUID.randomUUID().toString(),before.selectedLevel,value,"",mode=before.save.mode,recentJokeKeys=before.save.recentJokeKeys)
                val response=engine.ask(s,null,before.save.spicy)
                StoryShelf.start(before.save,s.copy(opening=response.question)).also { next ->
                    currentCoroutineContext().ensureActive()
                    persist(next) { it.copy(screen=Screen.GAME,answer="") }
                }
            }
            mutable.update { it.copy(responseMs=elapsed) }
        }
    }
    fun submit() {
        if(mutable.value.answer.trim().length !in 3..280 || mutable.value.save.session?.finished!=false || !mutable.value.canAnswer) return
        task("LANU hikâyenin açıklarını arıyor…") {
            val before=mutable.value
            val s=before.save.session ?: return@task
            val answer=before.answer.trim()
            val (next,elapsed)=measured(s.mode,TimingPhase.REPLY) {
                val verdict=engine.ask(s,answer,before.save.spicy)
                val session=s.copy(turns=s.turns+Turn(s.question,answer,verdict),draftAnswer="")
                val jokes=(before.save.recentJokeKeys+listOfNotNull(verdict.jokeKey)).asReversed().distinct().take(40).asReversed()
                Rules.credit(StoryShelf.replaceSelected(before.save,session).copy(recentJokeKeys=jokes)).also { next ->
                    currentCoroutineContext().ensureActive()
                    persist(next) { it.copy(answer="") }
                }
            }
            mutable.update { it.copy(responseMs=elapsed) }
        }
    }
    fun playMode(mode: PlayMode) = task("Oyun tercihi kaydediliyor…") {
        if(mode != PlayMode.LOCAL_AI) engine.release()
        persist(mutable.value.save.copy(mode=mode))
    }
    fun continueFast() {
        if(mutable.value.save.session?.finished!=false) return
        task("Hızlı oyuna geçiliyor…") {
            val s=mutable.value.save.session ?: return@task
            engine.release()
            persist(mutable.value.save.copy(mode=PlayMode.FAST,session=s.copy(mode=PlayMode.FAST)))
        }
    }
    fun connectOnline(key: String,consent: Boolean) {
        if(!consent || !FreeAiProtocol.validKey(key)) return
        task("Ücretsiz AI bağlantısı doğrulanıyor…") {
            online.connect(key,consent)
            mutable.update {it.copy(onlineReady=true)}
        }
    }
    fun disconnectOnline() = task("AI bağlantısı kaldırılıyor…") {
        online.cancel();credentials.delete();mutable.update {it.copy(onlineReady=false)}
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
        task("İlerleme sıfırlanıyor…",saveAnswer=false) {
            store.reset(); persist(GameSave())
            mutable.update { it.copy(selectedLevel=1,answer="",screen=Screen.HOME) }
        }
    }
    fun useRestored(save: GameSave) = task("Geri yüklenen hikâye açılıyor…",allowRecovery=true,saveAnswer=false) {
        persistence.withLock {
            mutable.update { it.copy(save=save,selectedLevel=save.unlocked,answer=save.session?.draftAnswer.orEmpty(),recovery=false,screen=Screen.HOME,responseMs=null) }
        }
        try { engine.release() } catch(e: Exception) { diagnostic(e) }
    }
    override fun onCleared() { engine.cancel(); super.onCleared() }
}

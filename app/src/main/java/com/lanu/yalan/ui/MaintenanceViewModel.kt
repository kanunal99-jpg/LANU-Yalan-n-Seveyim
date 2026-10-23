package com.lanu.yalan.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanu.yalan.data.*
import com.lanu.yalan.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class MaintenanceState(
    val busy: Boolean=false,val status: String="",val error: String?=null,val notice: String?=null,
    val performance: PerformanceSnapshot=PerformanceSnapshot(),val exportRequest: Long?=null,val restored: GameSave?=null,
    val update: UpdateManifest?=null,val updateChecked: Boolean=false,val updateReady: Boolean=false,
    val downloaded: Long=0,val total: Long=0,val installer: Intent?=null
)
@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val saves: SaveStore,private val documents: BackupDocuments,
    private val metrics: PerformanceMonitor,private val updates: OtaUpdateStore
) : ViewModel() {
    private val mutable=MutableStateFlow(MaintenanceState())
    val state=mutable.asStateFlow()
    private var operation: Job?=null
    private var exportBytes: ByteArray?=null
    init {
        viewModelScope.launch { metrics.state.collect { p -> mutable.update { it.copy(performance=p) } } }
        viewModelScope.launch { try { metrics.load() } catch(e: CancellationException) { throw e } catch(_: Exception) { mutable.update { it.copy(performance=it.performance.copy(storageAvailable=false)) } } }
    }
    private fun run(label: String, work: suspend () -> Unit) {
        if(mutable.value.busy) return
        mutable.update { it.copy(busy=true,status=label,error=null,notice=null) }
        operation=viewModelScope.launch {
            try { work() }
            catch(e: CancellationException) { mutable.update { it.copy(notice="İşlem durduruldu. Kaydedilmiş ilerlemen duruyor.") };throw e }
            catch(e: Exception) { mutable.update { it.copy(error=when(e) {
                is UpdateException -> e.message
                is BackupException -> e.message
                else -> "İşlem tamamlanamadı. Bağlantıyı, dosya izinlerini ve boş alanı kontrol edip yeniden dene."
            }) } }
            finally { mutable.update { it.copy(busy=false,status="") } }
        }
    }
    fun dismissError() { mutable.update { it.copy(error=null) } }
    fun clearNotice(text: String) { mutable.update { if(it.notice==text) it.copy(notice=null) else it } }
    fun reportError(text: String) { mutable.update { it.copy(error=text) } }
    fun cancel() { operation?.cancel() }
    fun prepareBackup(passphrase: CharArray) {
        if(mutable.value.busy) { passphrase.fill('\u0000');return }
        run("Parolalı yedek hazırlanıyor…") {
            try { exportBytes?.fill(0);exportBytes=saves.exportBackup(passphrase);mutable.update { it.copy(exportRequest=System.nanoTime()) } }
            finally { passphrase.fill('\u0000') }
        }
    }
    fun exportPickerOpened() { mutable.update { it.copy(exportRequest=null) } }
    fun finishBackup(uri: Uri?) {
        val bytes=exportBytes;exportBytes=null
        if(uri==null || bytes==null || mutable.value.busy) { bytes?.fill(0);mutable.update { it.copy(notice="Yedek dosyası kaydedilmedi. Yeniden yedek oluşturabilirsin.") };return }
        run("Şifreli yedek kaydediliyor ve doğrulanıyor…") {
            try { documents.write(uri,bytes);mutable.update { it.copy(notice="Yedek kaydedildi ve doğrulandı. Parolanı güvenli bir yerde sakla.") } }
            finally { bytes.fill(0) }
        }
    }
    fun restore(uri: Uri,passphrase: CharArray) {
        if(mutable.value.busy) { passphrase.fill('\u0000');return }
        run("Yedek doğrulanıyor…") {
            try {
                val restored=withContext(Dispatchers.IO) { PortableBackup.decode(documents.read(uri),passphrase) }
                currentCoroutineContext().ensureActive()
                // After validation, committing storage and updating UI are one non-cancellable step.
                withContext(NonCancellable) {
                    saves.write(restored)
                    mutable.update { it.copy(restored=restored,notice="İlerlemen ve kayıtlı hikâyen geri yüklendi.") }
                }
            } finally { passphrase.fill('\u0000') }
        }
    }
    fun restoredConsumed() { mutable.update { it.copy(restored=null) } }
    fun checkUpdate() = run("Yeni sürüm kontrol ediliyor…") {
        val manifest=updates.check()
        val ready=manifest?.let { updates.verifiedApk(it)!=null } ?: false
        mutable.update { it.copy(update=manifest,updateChecked=true,updateReady=ready,downloaded=0,total=manifest?.apkBytes ?: 0) }
    }
    fun downloadUpdate() {
        val manifest=mutable.value.update ?: return
        run("Güncelleme indiriliyor…") {
            updates.download(manifest) { done,total -> mutable.update { it.copy(downloaded=done,total=total) } }
            mutable.update { it.copy(updateReady=true,notice="APK doğrulandı. Güncellemeyi kurabilirsin.") }
        }
    }
    fun prepareInstall() {
        val manifest=mutable.value.update ?: return
        run("Kurulum paketi yeniden doğrulanıyor…") {
            val intent=try { updates.installIntent(manifest) } catch(e: Exception) { mutable.update { it.copy(updateReady=false,installer=null) };throw e }
            mutable.update { it.copy(installer=intent) }
        }
    }
    fun installerConsumed() { mutable.update { it.copy(installer=null) } }
    override fun onCleared() { exportBytes?.fill(0);operation?.cancel();super.onCleared() }
}

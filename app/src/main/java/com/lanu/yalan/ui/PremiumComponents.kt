package com.lanu.yalan.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanu.yalan.BuildConfig
import com.lanu.yalan.data.PortableBackup
import com.lanu.yalan.domain.*
import java.util.Locale

@Composable internal fun VerdictDetails(v: Verdict,compact: Boolean=false) {
    var expanded by remember(v) { mutableStateOf(false) }
    if(compact && v.explanations==null && v.evidence==null) return
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(compact) TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Puanın hesabını kapat ↑" else "Puanın hesabı ve ipuçları ↓") }
        if(!compact || expanded) {
            val r=v.explanations
            Text(if(r==null) "PUAN DAĞILIMI" else if(r.source==ReasonSource.MODEL) "PUANIN HESABI · AI" else "PUANIN HESABI · YEREL KURALLAR",color=MaterialTheme.colorScheme.primary,fontSize=11.sp)
            Column(Modifier.testTag("score-explanations"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                listOf(Triple("Tutarlılık",v.consistency,r?.consistency),Triple("Makullük",v.plausibility,r?.plausibility),Triple("Somut ayrıntı",v.detail,r?.detail),Triple("Sorulara karşı savunma",v.defense,r?.defense)).forEach { (label,score,reason) ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(label,fontSize=13.sp);Text("$score / 25",color=MaterialTheme.colorScheme.primary,fontSize=13.sp) }
                    LinearProgressIndicator(progress={score/25f},modifier=Modifier.fillMaxWidth())
                    reason?.let { Text(it,fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            v.evidence?.let { e -> Card(Modifier.fillMaxWidth().testTag("evidence-card")) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(when(e.status) { EvidenceStatus.CONTRADICTION -> "İFADELER ÇATIŞIYOR";EvidenceStatus.NEEDS_EXPLANATION -> "AÇIKLAMA BEKLEYEN İPUCU";EvidenceStatus.RESOLVED -> "BAĞLANTI AÇIKLANDI" },fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary,fontSize=12.sp)
                Text("Yerel ifade karşılaştırması",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Önce: ${e.earlier}",fontSize=13.sp,lineHeight=19.sp)
                Text("Sonra: ${e.later}",fontSize=13.sp,lineHeight=19.sp)
                Text(e.finding,fontSize=13.sp,lineHeight=19.sp)
            } } }
        }
    }
}

@Composable internal fun MaintenanceTools(s: UiState,m: MaintenanceState,vm: MaintenanceViewModel) {
    val context=LocalContext.current
    var backupAction by remember { mutableStateOf<Boolean?>(null) }
    var selected by remember { mutableStateOf<Uri?>(null) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"),vm::finishBackup)
    val input=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) { selected=uri;backupAction=false } }
    val installer=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(context.packageManager.canRequestPackageInstalls()) vm.prepareInstall()
        else vm.reportError("Kurulum izni verilmedi. Güncellemeyi kurmak için bu uygulamaya izin verebilirsin.")
    }
    LaunchedEffect(m.exportRequest) { if(m.exportRequest!=null) { vm.exportPickerOpened();export.launch("LANU_Yedek.lanubak") } }
    LaunchedEffect(m.installer) { m.installer?.let { intent ->
        vm.installerConsumed()
        try { installer.launch(intent) } catch(_: Exception) { vm.reportError("Kurulum ekranı açılamadı. Cihazının uygulama yükleme ayarlarını kontrol et.") }
    } }
    val enabled=!s.busy && !s.loading && !m.busy
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Card(Modifier.fillMaxWidth().testTag("ota-card")) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Uygulamayı güncelle",fontSize=18.sp,fontWeight=FontWeight.Bold)
            Text("Yüklü sürüm: ${BuildConfig.VERSION_NAME}",fontSize=13.sp)
            Text("Yeni paket indirilir; bütünlüğü, sürümü ve uygulama imzası doğrulandıktan sonra Android kurulum ekranı açılır.",fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            m.update?.let { Text("Yeni sürüm: ${it.versionName.ifBlank { it.versionCode.toString() }} · ${it.apkBytes/1_048_576} MB",fontWeight=FontWeight.SemiBold,fontSize=13.sp) }
            if(m.updateChecked && m.update==null) Text("Bu sürümden daha yeni uygun bir güncelleme paketi bulunmadı.",fontSize=12.sp)
            OutlinedButton(onClick=vm::checkUpdate,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("check-update")) { Text("Güncellemeleri kontrol et") }
            if(m.update!=null && !m.updateReady) Button(onClick=vm::downloadUpdate,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("download-update")) { Text(if(m.downloaded>0) "İndirmeye devam et ve doğrula" else "Güncellemeyi indir ve doğrula") }
            if(m.updateReady) Button(enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("install-update"),onClick={
                if(context.packageManager.canRequestPackageInstalls()) vm.prepareInstall()
                else try { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))) }
                catch(_: Exception) { vm.reportError("Kurulum izni ekranı açılamadı. Cihaz ayarlarından LANU için uygulama yükleme iznini açabilirsin.") }
            }) { Text("Güncellemeyi kur") }
            TextButton(enabled=enabled,onClick={
                try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/releases"))) }
                catch(_: Exception) { vm.reportError("Yayınlar sayfası açılamadı. Cihazında bir tarayıcı bulunmalı.") }
            }) { Text("Yayınlar sayfasını aç") }
        } }
        if(m.busy) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(m.status,fontSize=13.sp)
            if(m.total>0 && m.status=="Güncelleme indiriliyor…") {
                LinearProgressIndicator(progress={(m.downloaded.toFloat()/m.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                Text("${m.downloaded/1_048_576} / ${m.total/1_048_576} MB",fontSize=12.sp)
            } else LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick=vm::cancel) { Text("Durdur") }
        }
        Card(Modifier.fillMaxWidth().testTag("backup-card")) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("İlerlemeni yanında taşı",fontWeight=FontWeight.Bold,fontSize=18.sp)
            Text("Seviyelerini ve kayıtlı hikâyeni parolalı dosyaya kaydet. Yeni kurulumda veya başka telefonda geri yükleyebilirsin. AI modeli yedeğe dahil edilmez.",fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={backupAction=true},enabled=enabled && !s.recovery,modifier=Modifier.fillMaxWidth().testTag("export-backup")) { Text("Şifreli yedek oluştur") }
            OutlinedButton(onClick={input.launch(arrayOf("*/*"))},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("import-backup")) { Text("Yedekten geri yükle") }
        } }
        PerformanceCard(m.performance)
    }
    backupAction?.let { exporting -> PasswordDialog(exporting,onDismiss={backupAction=null;selected=null}) { chars ->
        val uri=selected;selected=null;backupAction=null
        if(exporting) vm.prepareBackup(chars) else if(uri!=null) vm.restore(uri,chars) else chars.fill('\u0000')
    } }
}

@Composable private fun PasswordDialog(exporting: Boolean,onDismiss: () -> Unit,onConfirm: (CharArray) -> Unit) {
    var phrase by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val valid=phrase.length in PortableBackup.MIN_PASSPHRASE_CHARS..PortableBackup.MAX_PASSPHRASE_CHARS && (!exporting || phrase==again)
    val dismiss={phrase="";again="";onDismiss()}
    AlertDialog(onDismissRequest=dismiss,title={Text(if(exporting) "Yedeğine parola belirle" else "Yedeğin parolasını gir")},
        text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(exporting) "En az 12 karakter kullan. Parolanı unutursan yedeği açamazsın." else "Doğrulanan yedek mevcut ilerlemenin yerine geçer. Yanlış parola veya bozuk dosya kaydını değiştirmez.",fontSize=13.sp,lineHeight=19.sp)
            OutlinedTextField(phrase,{phrase=it.take(PortableBackup.MAX_PASSPHRASE_CHARS)},Modifier.fillMaxWidth().testTag("backup-passphrase"),label={Text("Yedek parolası")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            if(exporting) OutlinedTextField(again,{again=it.take(PortableBackup.MAX_PASSPHRASE_CHARS)},Modifier.fillMaxWidth().testTag("backup-passphrase-confirm"),label={Text("Parola tekrar")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            if(exporting && again.isNotEmpty() && phrase!=again) Text("Parolalar aynı olmalı.",fontSize=12.sp,color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={TextButton(enabled=valid,modifier=Modifier.testTag("confirm-backup"),onClick={val chars=phrase.toCharArray();phrase="";again="";onConfirm(chars)}) {Text(if(exporting) "Yedeği oluştur" else "Doğrulayıp geri yükle")}},
        dismissButton={TextButton(onClick=dismiss) {Text("Vazgeç")}})
}

@Composable private fun PerformanceCard(p: PerformanceSnapshot) {
    fun time(ms: Long?)=if(ms==null) "—" else if(ms<1000) "$ms ms" else String.format(Locale.forLanguageTag("tr-TR"),"%.1f sn",ms/1000.0)
    Card(Modifier.fillMaxWidth().testTag("performance-summary")) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Bu cihazdaki süreler",fontSize=18.sp,fontWeight=FontWeight.Bold)
        Text("Son 100 değerlendirme ve kayıt işlemi. Ekranın çizilme süresini içermez.",fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        listOf("Hızlı oyun" to p.fast,"Cihazda AI" to p.ai,"İlk soru" to p.opening).forEach { (label,t) ->
            Text("$label · ${t.successCount} başarılı işlem",fontWeight=FontWeight.SemiBold,fontSize=13.sp)
            Text(if(t.successCount==0) "Henüz ölçüm yok." else "Ortanca ${time(t.p50Ms)} · %95'i ${time(t.p95Ms)} ve altında",fontSize=12.sp)
            if(t.timeoutCount+t.errorCount+t.cancelledCount>0) Text("${t.timeoutCount} süre aşımı · ${t.errorCount} hata · ${t.cancelledCount} iptal",fontSize=11.sp)
        }
        p.health?.let {Text(it.summary,fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(!p.storageAvailable) Text("Süre geçmişi şu anda cihazda saklanamıyor.",fontSize=12.sp,color=MaterialTheme.colorScheme.error)
    } }
}

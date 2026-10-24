package com.lanu.yalan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import android.content.Intent
import android.net.Uri
import com.lanu.yalan.ai.FreeAiProtocol
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lanu.yalan.BuildConfig
import com.lanu.yalan.R
import com.lanu.yalan.domain.*
import kotlinx.coroutines.delay
import java.util.Locale

private val Ink=Color(0xFF130D23)
private val Panel=Color(0xFF241A36)
private val Gold=Color(0xFFFFCB68)
private val Lavender=Color(0xFFC9B4FF)
private val Muted=Color(0xFFB9AFC9)
private val Mint=Color(0xFFA6E4CE)
private val Palette=darkColorScheme(primary=Gold,onPrimary=Ink,secondary=Lavender,background=Ink,surface=Panel,onSurface=Color(0xFFF8F3FF),onBackground=Color(0xFFF8F3FF),surfaceVariant=Color(0xFF362848),onSurfaceVariant=Muted,error=Color(0xFFFFB4AB))

@Composable
fun LanuAppScreen(vm: GameViewModel,maintenance: MaintenanceViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val m by maintenance.state.collectAsStateWithLifecycle()
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(m.notice) { m.notice?.let { snackbar.showSnackbar(it);maintenance.clearNotice(it) } }
    LaunchedEffect(m.restored) { m.restored?.let { vm.useRestored(it);maintenance.restoredConsumed() } }
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,vm) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) vm.saveDraft() }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    var storiesOpen by remember { mutableStateOf(false) }
    DisposableEffect(s.busy) { view.keepScreenOn=s.busy; onDispose { view.keepScreenOn=false } }
    BackHandler(s.screen!=Screen.HOME || s.busy || m.busy) { if(!s.busy && !m.busy) vm.navigate(Screen.HOME) }
    MaterialTheme(colorScheme=Palette,typography=Typography()) {
        if(storiesOpen) StoryManager(s,vm,onDismiss={storiesOpen=false})
        Scaffold(containerColor=Ink, snackbarHost={SnackbarHost(snackbar)}, bottomBar={
            if(s.screen!=Screen.GAME) NavigationBar(containerColor=Ink) {
                listOf(Screen.HOME to "Oyna",Screen.LEVELS to "Seviyeler",Screen.SETTINGS to "Ayarlar").forEachIndexed { i,pair ->
                    NavigationBarItem(selected=s.screen==pair.first,onClick={maintenance.dismissError();vm.navigate(pair.first)},enabled=!s.busy && !m.busy,
                        icon={Text(listOf("◉","▦","☷")[i],fontSize=24.sp)},label={Text(pair.second)})
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if(s.screen!=Screen.GAME || s.save.session?.finished==true) Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.mascot),null,Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
                        Column { Text("LANU",fontWeight=FontWeight.Black,letterSpacing=3.sp,fontSize=19.sp); Text("YALANINI SEVEYİM",color=Muted,fontSize=10.sp,letterSpacing=1.sp) }
                    }
                    if(s.screen==Screen.GAME) TextButton(onClick={vm.navigate(Screen.HOME)},enabled=!s.busy) { Text("Çık") }
                    else Text(when(s.save.mode) {PlayMode.FAST -> "HIZLI OYUN";PlayMode.LOCAL_AI -> "CİHAZDA AI";PlayMode.FREE_ONLINE_AI -> "ÇEVRİMİÇİ AI"},color=Mint,fontSize=10.sp,fontWeight=FontWeight.Bold)
                }
                if(s.error!=null) Card(Modifier.fillMaxWidth().padding(horizontal=20.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF482734))) {
                    Text(s.error!!,Modifier.padding(16.dp),fontSize=13.sp)
                    if(s.aiError && s.save.session?.finished==false) TextButton(onClick=vm::continueFast,enabled=!s.busy) { Text("Bu hikâyeye hızlı devam et") }
                    TextButton(onClick=vm::clearError,Modifier.align(Alignment.End)) { Text("Anladım") }
                }
                if(m.error!=null) Card(Modifier.fillMaxWidth().padding(horizontal=20.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF482734))) {
                    Text(m.error!!,Modifier.padding(16.dp),fontSize=13.sp)
                    TextButton(onClick=maintenance::dismissError,Modifier.align(Alignment.End)) { Text("Anladım") }
                }
                when {
                    s.loading -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                    s.screen==Screen.HOME -> Home(s,vm,onStories={storiesOpen=true})
                    s.screen==Screen.LEVELS -> Levels(s,vm)
                    s.screen==Screen.SETTINGS -> Settings(s.copy(busy=s.busy || m.busy),vm,m,maintenance)
                    else -> Game(s,vm,onStories={storiesOpen=true})
                }
            }
        }
    }
}
@Composable
private fun Home(s: UiState,vm: GameViewModel,onStories: () -> Unit) {
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    var topic by rememberSaveable { mutableStateOf("") }
    val onlineDraft=remember {OnlineConnectionDraft()}
    DisposableEffect(onlineDraft) {onDispose {onlineDraft.clear()}}
    OnlineConnectionDialog(s,vm,onlineDraft)
    LazyColumn(Modifier.fillMaxSize().testTag("screen-content"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF412161),Panel))).padding(24.dp)) {
                Text("BAHANEN HAZIR MI?",color=Gold,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Anlat bakalım.\nİnanacak mıyız?",Modifier.weight(1f).padding(top=12.dp),fontSize=29.sp,lineHeight=34.sp,fontWeight=FontWeight.ExtraBold)
                    Image(painterResource(R.drawable.mascot),"LANU, tek kaşı kalkık muzip oyun sunucusu",Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)))
                }
                Text("Sen hikâyeyi kur. LANU açıklarını arasın.",color=Muted,modifier=Modifier.padding(top=14.dp),fontSize=14.sp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Tag("5 soru");Tag("100 seviye");Tag("Ücretsiz") }
            }
        }
        if(!s.save.onboarding) item { Text("Nasıl oynanır?\nBir konu seç, beş soruya aynı hikâyeyi koruyarak cevap ver. İnandırıcılık puanın yeterliyse sonraki seviye açılır.",color=Muted,lineHeight=22.sp) }
        item { ModeCard(s,vm) }
        if(s.save.mode==PlayMode.FREE_ONLINE_AI) item { OnlineAiCard(s,vm,onlineDraft) }
        if(s.save.mode==PlayMode.LOCAL_AI && !s.ready) item { ModelCard(s,vm) }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column { Text("SEVİYE ${s.selectedLevel.toString().padStart(2,'0')}",color=Gold,fontSize=12.sp,letterSpacing=2.sp); Text(Rules.rank(s.selectedLevel),fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp)) }
                Text("Hedef ${Rules.threshold(s.selectedLevel)}",color=Muted,fontSize=13.sp)
            }
        }
        if(s.save.session!=null) item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={vm.navigate(Screen.GAME)},Modifier.fillMaxWidth(),enabled=!s.busy) {
                    Text(if(s.save.session.finished) "Son sonucu gör" else "Hikâyeye devam et · ${if(s.save.session.mode==PlayMode.FAST) "Hızlı" else "AI"} · ${s.save.session.turns.size}/5")
                }
                TextButton(onClick={keyboard?.hide();focus.clearFocus();onStories()},enabled=!s.busy,modifier=Modifier.fillMaxWidth().testTag("manage-stories")) {
                    Text("Konularım · ${StoryShelf.activeCount(s.save)}/5 aktif")
                }
            }
        }
        item {
            Text("Bugünkü bahanen ne?",fontWeight=FontWeight.SemiBold,fontSize=17.sp)
            OutlinedTextField(value=topic,onValueChange={topic=it.take(100)},Modifier.fillMaxWidth().padding(top=12.dp).testTag("topic-input"),enabled=!s.busy,
                placeholder={Text("Örn. İşe neden geç kaldım?")},minLines=2,maxLines=3,shape=RoundedCornerShape(18.dp),supportingText={Text("${topic.length}/100 · Konuyu sen belirle")})
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("İşe geç kaldım","Ödevi unuttum").forEach { example -> SuggestionChip(onClick={topic=example},label={Text(example,fontSize=12.sp)},enabled=!s.busy) }
            }
            if(!StoryShelf.canStart(s.save)) Text("Beş aktif hikâyen var. Yeni konu için Konularım’dan birini kapat. Hikâyeler kendiliğinden silinmez.",color=Gold,fontSize=13.sp,modifier=Modifier.testTag("story-limit"))
            else if(s.save.session?.finished==false) Text("Yeni konu açabilirsin; bu hikâye ve taslağın Konularım’da korunur.",color=Muted,fontSize=12.sp)
            Button(onClick={keyboard?.hide();focus.clearFocus();vm.start(topic)},Modifier.fillMaxWidth().heightIn(min=58.dp).padding(top=6.dp).testTag("start-game"),enabled=s.canStart && !s.busy && !s.recovery && topic.trim().length>=3,shape=RoundedCornerShape(18.dp)) { Text("LANU’yu ikna et  →",fontWeight=FontWeight.Bold,fontSize=17.sp) }
        }
        if(s.busy && !s.downloading) item { Busy(s,vm) }
        item { Text("Puanlar eğlence amaçlıdır. Gerçek bir yalan testi değildir. "+if(s.save.mode==PlayMode.FREE_ONLINE_AI) "Çevrimiçi AI seçeneğinde konu ve bu hikâyenin cevapları OpenRouter üzerinden model sağlayıcısına gönderilir." else "Hızlı oyun ve Cihazda AI konuşmaları bu cihazda kalır.",color=Muted,fontSize=12.sp,lineHeight=18.sp) }
    }
}
@Composable private fun StoryManager(s:UiState,vm:GameViewModel,onDismiss: () -> Unit) {
    var closing by remember { mutableStateOf<Session?>(null) }
    val target=closing
    if(target!=null) AlertDialog(onDismissRequest={if(!s.busy) closing=null},title={Text("Bu hikâye kapatılsın mı?")},
        text={Text("“${target.topic}” konuşması ve gönderilmemiş taslağı silinir. Kazandığın seviyeler ve en iyi puanların korunur.")},
        confirmButton={TextButton(onClick={vm.closeStory(target.id);closing=null},enabled=!s.busy,modifier=Modifier.testTag("confirm-close-story")) {Text("Hikâyeyi kapat")}},
        dismissButton={TextButton(onClick={closing=null},enabled=!s.busy) {Text("Vazgeç")}})
    else AlertDialog(onDismissRequest={if(!s.busy) onDismiss()},title={Text("Konularım · ${StoryShelf.activeCount(s.save)}/5 aktif")},
        text={
            LazyColumn(Modifier.heightIn(max=420.dp).testTag("story-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item {Text("Her hikâyenin soruları, puanı ve cevap taslağı ayrı tutulur. Tamamlanan sonucun yerine yeni konu açılabilir; seviye puanı korunur.",fontSize=12.sp)}
                items(StoryShelf.stories(s.save),key={it.id}) { story ->
                    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(story.topic,fontWeight=FontWeight.Bold,fontSize=14.sp)
                        Text("Seviye ${story.level} · "+if(story.finished) "Tamamlandı · ${story.finalScore}/100" else "${story.turns.size}/5 cevap · ${when(story.mode) {PlayMode.FAST -> "Hızlı";PlayMode.LOCAL_AI -> "Cihazda AI";PlayMode.FREE_ONLINE_AI -> "Çevrimiçi AI"}}",fontSize=12.sp,color=Muted)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick={vm.resumeStory(story.id);onDismiss()},enabled=!s.busy,modifier=Modifier.testTag("resume-story-${story.id}")) {Text(if(story.finished) "Sonucu gör" else "Devam et")}
                            TextButton(onClick={closing=story},enabled=!s.busy,modifier=Modifier.testTag("close-story-${story.id}")) {Text("Kapat")}
                        }
                    }}
                }
            }
        },
        confirmButton={TextButton(onClick={vm.navigate(Screen.HOME);onDismiss()},enabled=!s.busy && StoryShelf.canStart(s.save),modifier=Modifier.testTag("new-story")) {Text("Yeni konu")}},
        dismissButton={TextButton(onClick=onDismiss,enabled=!s.busy) {Text("Kapat")}})
}
@Composable private fun Tag(text: String) { Surface(color=Color(0xFF50346A),shape=RoundedCornerShape(8.dp)) { Text(text,Modifier.padding(horizontal=9.dp,vertical=6.dp),color=Color(0xFFF5E6FF),fontSize=11.sp) } }
@Composable private fun ModeCard(s:UiState,vm:GameViewModel) {
    Card {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Nasıl oynayalım?",fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=s.save.mode==PlayMode.FAST,onClick={vm.playMode(PlayMode.FAST)},enabled=!s.busy,label={Text("Hızlı oyun")})
                FilterChip(selected=s.save.mode==PlayMode.LOCAL_AI,onClick={vm.playMode(PlayMode.LOCAL_AI)},enabled=!s.busy,label={Text("Deneysel AI")})
            }
            FilterChip(selected=s.save.mode==PlayMode.FREE_ONLINE_AI,onClick={vm.playMode(PlayMode.FREE_ONLINE_AI)},enabled=!s.busy,modifier=Modifier.testTag("online-mode"),label={Text("Ücretsiz çevrimiçi AI")})
            Text(when(s.save.mode) {
                PlayMode.FAST ->
                "Cevabındaki telefon, trafik, alarm, saat ve diğer ayrıntılara göre sorgu değişir. Önceki cevaplarını karşılaştıran kurallar kullanır; dil modeli değildir. Yanıt hedefi 2–3 saniye."
                PlayMode.LOCAL_AI -> "Deneysel Qwen3.5 4B modeli cevaplarını değerlendirir; bazı soruları veya esprileri zayıf kalabilir. 2,74 GB indirme gerekir. Telefonda 2–3 saniye garanti edilemez; toplam süre sınırı 30 saniye."
                PlayMode.FREE_ONLINE_AI -> "İlk soruyu, yorumları ve puanları internet üzerinden gerçek dil modeli üretir. Yalnız ücretsiz modeller kullanılır. Ücretsiz hesap/anahtar ve internet gerekir; servis kotası ve yoğunluk yanıt süresini etkileyebilir."
            },color=Muted,fontSize=13.sp,lineHeight=20.sp)
            if(s.save.session?.finished==false) Text("Bu seçim yeni hikâyeler içindir. Devam eden hikâyenin modu korunur.",color=Muted,fontSize=12.sp)
        }
    }
}
/** Ephemeral parent-screen state: LazyColumn item eviction must not erase an unfinished form.
 * Deliberately not rememberSaveable: a personal key must never enter an Android state bundle. */
internal class OnlineConnectionDraft {
    var key by mutableStateOf("")
    var consent by mutableStateOf(false)
    var browserError by mutableStateOf(false)
    var open by mutableStateOf(false)
    fun clear() {key="";consent=false;browserError=false;open=false}
}
@Composable internal fun OnlineAiCard(s:UiState,vm:GameViewModel,draft: OnlineConnectionDraft) {
    val context=LocalContext.current
    Card(Modifier.fillMaxWidth().testTag("online-ai-card")) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(s.onlineReady) "Ücretsiz AI bağlı ✓" else "Ücretsiz AI'yı bağla",fontWeight=FontWeight.Bold)
            Text("OpenRouter hesabından kişisel API anahtarı gerekir. Kredi satın alman gerekmez. Servis kotası dolunca ücretli modele geçilmez. 2–3 saniye garantisi yoktur; toplam bekleme sınırı 15 saniye.",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            if(s.onlineReady) {
                Text("Anahtar cihazında şifreli saklanır; ilerleme yedeğine eklenmez.",fontSize=12.sp,color=Muted)
                OutlinedButton(onClick=vm::disconnectOnline,enabled=!s.busy,modifier=Modifier.testTag("disconnect-online")) {Text("AI bağlantısını kaldır")}
            } else {
                OutlinedButton(onClick={
                    try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://openrouter.ai/settings/keys")))} catch(_: android.content.ActivityNotFoundException) {draft.browserError=true}
                },enabled=!s.busy,modifier=Modifier.testTag("get-online-key")) {Text("Ücretsiz anahtar sayfasını aç")}
                if(draft.browserError) Text("Tarayıcı açılamadı. openrouter.ai/settings/keys adresini kendi tarayıcında açabilirsin.",fontSize=12.sp,color=Gold)
                Button(onClick={vm.clearError();draft.open=true},enabled=!s.busy,modifier=Modifier.testTag("open-online-setup")) {Text("Anahtarını bağla")}
            }
        }
    }
}
@Composable private fun OnlineConnectionDialog(s:UiState,vm:GameViewModel,draft: OnlineConnectionDraft) {
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(s.onlineReady) {if(s.onlineReady) draft.clear()}
    if(draft.open) AlertDialog(
        onDismissRequest={if(s.busy) vm.cancel();draft.clear()},
        title={Text("Ücretsiz AI bağlantısı")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value=draft.key,onValueChange={draft.key=it.trim().take(256)},modifier=Modifier.fillMaxWidth().testTag("online-key"),enabled=!s.busy,
                label={Text("Kişisel API anahtarın")},singleLine=true,visualTransformation=PasswordVisualTransformation(),
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),
                supportingText={Text("Anahtarı sohbete veya başka kişilere gönderme.")})
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=draft.consent,onCheckedChange={draft.consent=it;keyboard?.hide()},enabled=!s.busy,modifier=Modifier.testTag("online-consent"))
                Text("Konu ve bu hikâyedeki cevaplarımın OpenRouter üzerinden model sağlayıcısına gönderilmesini kabul ediyorum.",fontSize=12.sp,lineHeight=18.sp)
            }
            if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp,lineHeight=18.sp)}
        }},
        confirmButton={TextButton(onClick={keyboard?.hide();vm.connectOnline(draft.key,draft.consent)},enabled=!s.busy && draft.consent && FreeAiProtocol.validKey(draft.key),modifier=Modifier.testTag("connect-online")) {Text(if(s.busy) "Doğrulanıyor…" else "Doğrula ve şifreli kaydet")}},
        dismissButton={TextButton(onClick={if(s.busy) vm.cancel();draft.clear()}) {Text(if(s.busy) "Durdur" else "Vazgeç")}}
    )
}
@Composable private fun ResponseTime(s:UiState) {
    s.responseMs?.let { ms ->
        Text("Son yanıt: " + if(ms<1000) "$ms ms" else String.format(Locale.forLanguageTag("tr-TR"),"%.1f sn",ms/1000.0),
            Modifier.testTag("response-time"),color=Muted,fontSize=11.sp)
    }
}
@Composable private fun Busy(s:UiState,vm:GameViewModel) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while(true) { delay(1000); elapsed++ } }
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(s.status,Modifier.padding(top=12.dp),color=Muted,fontSize=13.sp)
        Text(when((if(s.screen==Screen.GAME) s.save.session?.mode else null) ?: s.save.mode) {PlayMode.LOCAL_AI -> "$elapsed sn · AI yükleme ve yanıt bütçesi: 30 sn";PlayMode.FREE_ONLINE_AI -> "$elapsed sn · Çevrimiçi AI süre sınırı: 15 sn";PlayMode.FAST -> "$elapsed sn"},color=Muted,fontSize=11.sp)
        TextButton(onClick=vm::cancel) { Text("Durdur") }
    }
}
@Composable private fun ModelCard(s:UiState,vm:GameViewModel) {
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(s.ready) "Deneysel AI modeli hazır ✓" else "Deneysel AI modeli",fontSize=18.sp,fontWeight=FontWeight.Bold)
            Text("Qwen3.5 4B · 2,74 GB\nBir kez indir, sonra internetsiz oyna. API ücreti, üyelik ve reklam yok. İndirme için Wi-Fi önerilir.",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            if(s.downloading) {
                LinearProgressIndicator(progress={s.progress},Modifier.fillMaxWidth())
                Text(s.status,fontSize=13.sp)
                Text("Bu ekranı açık tut. Durursa kaldığı yerden devam edebilirsin.",color=Muted,fontSize=12.sp)
                OutlinedButton(onClick=vm::cancel) { Text("İndirmeyi duraklat") }
            } else if(!s.ready) Button(onClick=vm::download,enabled=!s.busy && !s.recovery,modifier=Modifier.fillMaxWidth()) { Text("Ücretsiz indir / devam et") }
        }
    }
}
@Composable private fun Game(s:UiState,vm:GameViewModel,onStories: () -> Unit) {
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val send: () -> Unit = { keyboard?.hide();focus.clearFocus();vm.submit() }
    val session=s.save.session
    if(session==null) { Text("Henüz hikâye yok.",Modifier.padding(24.dp)); return }
    if(session.finished) { Result(session,s,vm); return }
    val scroll=rememberLazyListState()
    LaunchedEffect(session.id,session.turns.size,s.busy) {
        scroll.scrollToItem(if(session.turns.isEmpty() && !s.busy) 0 else session.turns.size + 1 + if(s.busy) 1 else 0)
    }
    Column(Modifier.fillMaxSize()) {
        // Keep the composer reachable with the keyboard open, even at large font scales.
        Row(Modifier.fillMaxWidth().testTag("game-toolbar").padding(horizontal=16.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                Text("LANU · SEVİYE ${session.level}",color=Gold,fontWeight=FontWeight.Bold,fontSize=12.sp)
                Text(session.mode.label(),color=Mint,fontSize=10.sp)
            }
            Text("SORU ${session.turns.size+1} / 5",Modifier.testTag("question-number"),color=Muted,fontSize=12.sp)
            TextButton(onClick={keyboard?.hide();focus.clearFocus();vm.navigate(Screen.HOME)},enabled=!s.busy,contentPadding=PaddingValues(4.dp)) {Text("Çık",fontSize=12.sp)}
        }
        LazyColumn(Modifier.weight(1f),state=scroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {
                Text(session.topic,fontWeight=FontWeight.SemiBold)
                LinearProgressIndicator(progress={session.turns.size/5f},Modifier.fillMaxWidth().padding(vertical=10.dp))
                ResponseTime(s)
            }
            itemsIndexed(session.turns) { index,turn ->
                Bubble("LANU",turn.question,false)
                Spacer(Modifier.height(12.dp))
                Bubble("SEN",turn.answer,true)
                if(index<session.turns.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                    Bubble("LANU · ${turn.verdict.score}/100",turn.verdict.reply,false)
                    VerdictDetails(turn.verdict,compact=true)
                }
            }
            item {
                // Auto-scroll lands at this item. Keep the response BEFORE its follow-up visible.
                Column(Modifier.testTag("current-exchange"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    session.turns.lastOrNull()?.let { turn ->
                        Bubble("LANU · ${turn.verdict.score}/100",turn.verdict.reply,false,Modifier.testTag("current-reply"))
                    }
                    Bubble("LANU",session.question,false,Modifier.testTag("current-question"))
                    session.turns.lastOrNull()?.let { VerdictDetails(it.verdict,compact=true) }
                }
            }
            if(s.busy) item { Busy(s,vm) }
        }
        if(!s.canAnswer) {
            Text(if(session.mode==PlayMode.FREE_ONLINE_AI) "Çevrimiçi AI anahtarını Ayarlar'dan bağla veya bu hikâyeye Hızlı oyun ile devam et." else "AI için modeli indir veya bu hikâyeye Hızlı oyun ile devam et.",Modifier.padding(horizontal=16.dp),color=Gold)
            TextButton(onClick=vm::continueFast,enabled=!s.busy) { Text("Bu hikâyeye hızlı devam et") }
        }
        Surface(modifier=Modifier.testTag("answer-composer"),color=Panel) {
            Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp)) {
                OutlinedTextField(value=s.answer,onValueChange=vm::answer,Modifier.fillMaxWidth().testTag("answer-input"),enabled=!s.busy && s.canAnswer,
                    placeholder={Text("Hadi, hikâyeyi kurtar…")},maxLines=4,shape=RoundedCornerShape(18.dp),
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={send()}))
                Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.answer.length}/280",color=Muted,fontSize=12.sp)
                        if(s.answer.isNotEmpty()) Text(if(session.draftAnswer==s.answer) "Taslak kaydedildi" else "Taslak kaydediliyor…",color=Muted,fontSize=10.sp,modifier=Modifier.testTag("draft-save-status"))
                    }
                    TextButton(onClick={keyboard?.hide();focus.clearFocus();onStories()},enabled=!s.busy,modifier=Modifier.testTag("manage-stories"),contentPadding=PaddingValues(6.dp)) {Text("Konular",fontSize=12.sp)}
                    Button(onClick=send,modifier=Modifier.testTag("send-answer"),enabled=!s.busy && s.canAnswer && s.answer.trim().length>=3) { Text(if(session.turns.size==4) "Son sözü söyle" else "Cevabı gönder  ↑") }
                }
            }
        }
    }
}
@Composable private fun Bubble(label:String,text:String,user:Boolean,modifier:Modifier=Modifier) {
    Column(modifier.fillMaxWidth(),horizontalAlignment=if(user) Alignment.End else Alignment.Start) {
        Text(label,color=if(user) Lavender else Gold,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.padding(bottom=6.dp))
        Surface(color=if(user) Color(0xFF493164) else Panel,shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp,bottomStart=if(user) 20.dp else 4.dp,bottomEnd=if(user) 4.dp else 20.dp)) {
            Text(text,Modifier.padding(17.dp),fontSize=16.sp,lineHeight=24.sp)
        }
    }
}
@Composable private fun Result(session:Session,s:UiState,vm:GameViewModel) {
    val v=session.turns.last().verdict
    val passed=v.score>=Rules.threshold(session.level)
    LazyColumn(Modifier.fillMaxSize().testTag("screen-content"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.mascot),null,Modifier.size(100.dp).clip(RoundedCornerShape(28.dp)))
                Text(if(passed) "Bu sefer yedik!" else "Bu hikâye su alıyor.",fontSize=27.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(top=20.dp))
                Text("İNANDIRICILIK",color=Muted,fontSize=12.sp,letterSpacing=2.sp,modifier=Modifier.padding(top=22.dp))
                Text("${v.score}",fontSize=80.sp,fontWeight=FontWeight.Black,color=Gold)
                Text("100 üzerinden · Geçme puanı ${Rules.threshold(session.level)}",color=Muted,fontSize=13.sp)
                Text(session.mode.label(),color=Mint,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
                ResponseTime(s)
                Text("★".repeat(Rules.stars(v.score))+"☆".repeat(3-Rules.stars(v.score)),color=Gold,fontSize=28.sp,modifier=Modifier.padding(top=8.dp))
            }
        }
        item { Bubble("LANU’NUN KARARI",v.reply,false) }
        item { VerdictDetails(v) }
        item {
            Text(if(passed && session.level==100) "100. seviyeyi de geçtin. LANU şapka çıkarıyor!" else if(passed) "Seviye tamamlandı. Sıradaki sorguya hazır mısın?" else "Bir ayrıntıyı değiştirip diğerlerini unutma. Aynı seviyeyi yeni konuyla tekrar deneyebilirsin.",color=Muted,lineHeight=22.sp)
            Button(onClick={vm.select(if(passed) (session.level+1).coerceAtMost(100) else session.level)},Modifier.fillMaxWidth().padding(top=16.dp).heightIn(min=54.dp)) { Text(if(passed && session.level<100) "Sıradaki seviye →" else "Yeni hikâye anlat") }
            TextButton(onClick={vm.navigate(Screen.LEVELS)},Modifier.fillMaxWidth()) { Text("Seviye haritası") }
        }
        item { Text("Eğlencelik değerlendirme; gerçek doğruluk veya yalan tespiti değildir.",color=Muted,fontSize=11.sp) }
    }
}
@Composable private fun Levels(s:UiState,vm:GameViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
        Text("Hikâyen büyüsün.",fontSize=29.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(top=18.dp))
        Text("${s.save.best.count { it.value>=Rules.threshold(it.key) }} / 100 tamamlandı · ${Rules.rank(s.save.unlocked)}",color=Muted,modifier=Modifier.padding(top=10.dp,bottom=22.dp),fontSize=13.sp)
        LazyVerticalGrid(columns=GridCells.Adaptive(76.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items((1..100).toList()) { level ->
                val unlocked=Rules.canPlay(level,s.save)
                val best=s.save.best[level]
                FilledTonalButton(onClick={vm.select(level)},enabled=unlocked && !s.busy,shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(8.dp),modifier=Modifier.heightIn(min=84.dp)) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(level.toString().padStart(2,'0'),fontWeight=FontWeight.Bold,fontSize=21.sp);Text(if(!unlocked) "Kilitli" else best?.let{"$it puan"} ?: "Oyna",fontSize=10.sp) }
                }
            }
        }
    }
}
@Composable private fun Settings(s:UiState,vm:GameViewModel,m:MaintenanceState,maintenance:MaintenanceViewModel) {
    var reset by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val onlineDraft=remember {OnlineConnectionDraft()}
    DisposableEffect(onlineDraft) {onDispose {onlineDraft.clear()}}
    OnlineConnectionDialog(s,vm,onlineDraft)
    if(reset) AlertDialog(onDismissRequest={reset=false},title={Text("İlerleme silinsin mi?")},text={Text("Tüm seviyeler, puanlar ve kayıtlı hikâye bu cihazdan silinir. Bu işlem geri alınamaz.")},confirmButton={TextButton(onClick={reset=false;vm.reset()}){Text("İlerlemeyi sil")}},dismissButton={TextButton(onClick={reset=false}){Text("Vazgeç")}})
    if(remove) AlertDialog(onDismissRequest={remove=false},title={Text("Model kaldırılsın mı?")},text={Text("Yaklaşık 2,74 GB alan açılır. Hızlı oyun çalışmaya devam eder. AI modu için modeli yeniden indirmen gerekir. Puanların korunur.")},confirmButton={TextButton(onClick={remove=false;vm.deleteModel()}){Text("Modeli kaldır")}},dismissButton={TextButton(onClick={remove=false}){Text("Vazgeç")}})
    LazyColumn(Modifier.fillMaxSize().testTag("screen-content"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        item { Text("Tam senlik ayarlar.",fontSize=28.sp,fontWeight=FontWeight.ExtraBold) }
        item { ModeCard(s,vm) }
        if(s.save.mode==PlayMode.FREE_ONLINE_AI || s.onlineReady) item {OnlineAiCard(s,vm,onlineDraft)}
        item { MaintenanceTools(s,m,maintenance) }
        item { Card { Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Biraz sivri dil",fontWeight=FontWeight.Bold);Text("Hafif argo ve daha iğneleyici espriler. Ağır hakaret yok.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=5.dp)) }
            Switch(checked=s.save.spicy,onCheckedChange=vm::spicy,enabled=!s.busy)
        } } }
        item { ModelCard(s,vm) }
        if(s.ready) item { OutlinedButton(onClick={remove=true},enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("Modeli kaldır · 2,74 GB") } }
        item { Text("Hikâyen ve gizlilik",fontWeight=FontWeight.Bold,fontSize=18.sp);Text("Konuşmaların ve ilerlemen bu telefonda şifreli saklanır. Otomatik Android yedeklemesine dahil edilmez; kendi parolalı yedeğini oluşturabilirsin. Hızlı oyun ve Cihazda AI internetsiz çalışır. İsteğe bağlı çevrimiçi AI için konu/cevaplar OpenRouter üzerinden model sağlayıcısına gönderilir; anahtar cihazda şifrelenir ve yedeğe eklenmez. Uygulamada reklam veya abonelik yoktur.",color=Muted,fontSize=13.sp,lineHeight=21.sp,modifier=Modifier.padding(top=8.dp)) }
        item { Text("Puanlama nasıl çalışır?",fontWeight=FontWeight.Bold,fontSize=18.sp);Text("Dört alana 0–25 puan verilir. Hızlı oyun sınırlı yerel kurallarla değerlendirme yapar. Cihazda AI'da Qwen, çevrimiçi AI'da servisin seçtiği ücretsiz dil modeli puanları üretir. Hatalı çıktı kabul edilmez; modelin puanı yerine yerel puan yazılmaz. Final tüm cevapları dikkate alır. Geçme eşiği 40’tan 73’e çıkar. Puanlar eğlence amaçlıdır.",color=Muted,fontSize=13.sp,lineHeight=21.sp,modifier=Modifier.padding(top=8.dp)) }
        item { s.device?.let { Text(it.summary,Modifier.testTag("device-summary"),color=Muted,fontSize=13.sp) } }
        item { Text("Cihaz ve performans",fontWeight=FontWeight.Bold,fontSize=18.sp);Text("Samsung, Huawei, Xiaomi ve diğer markaların 64 bit Android 8+ cihazları için hazırlanmıştır. Google Play Hizmetleri gerekmez. Hızlı oyun büyük model yüklemez. AI modu için 8 GB RAM önerilir ve 2–3 saniyelik yanıt garanti edilemez. AI yükleme ve üretim toplamında 30 saniyelik iptal bütçesi kullanılır. Fiziksel cihaz süreleri henüz doğrulanmadı.",color=Muted,fontSize=13.sp,lineHeight=21.sp,modifier=Modifier.padding(top=8.dp)) }
        item { Text("Açık kaynak",fontWeight=FontWeight.Bold,fontSize=18.sp);Text("Qwen3.5 4B — Apache 2.0\nllama.cpp — MIT\nLisans metinleri ve kaynaklar GitHub deposundadır.",color=Muted,fontSize=13.sp,lineHeight=21.sp,modifier=Modifier.padding(top=8.dp)) }
        item { OutlinedButton(onClick={reset=true},enabled=!s.busy,modifier=Modifier.fillMaxWidth()) { Text("İlerlemeyi sıfırla") } }
        item { Text("LANU Yalanını Seveyim\n${BuildConfig.VERSION_NAME} · Android alfa sürümü",color=Muted,fontSize=12.sp) }
    }
}

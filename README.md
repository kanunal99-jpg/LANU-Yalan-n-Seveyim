# LANU Yalanını Seveyim

Türkçe, tek kişilik Android hikâye sorgulama oyunu. Kullanıcı konuyu yazar, beş soruya cevap verir ve inandırıcılık puanı toplar. Üç açıkça ayrılmış oyun seçeneği vardır:

| Seçenek | Değerlendirme | İndirme / hız |
| --- | --- | --- |
| **Hızlı oyun** (varsayılan) | Hazır soru kalıpları ve yerel kurallar; dil modeli/AI değildir | Ek indirme yok; 2–3 saniyelik yanıt hedefi |
| **Cihazda AI** | Gerçek Qwen3.5 4B; yanıtları model puanlar | İsteğe bağlı 2,74 GB; telefonda 2–3 saniye garantisi yok |
| **Ücretsiz çevrimiçi AI** | OpenRouter ücretsiz yönlendiricisinden gerçek model; ilk soru, yorum ve puanı model üretir | İsteğe bağlı kişisel hesap/anahtar ve açık veri izni; kota var, toplam süre sınırı 15 sn |

Android test APK'ları: [v0.8.0-alpha · OTA başlangıç paketi](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/releases/download/v0.8.0-alpha/LANU-Yalanini-Seveyim-v0.8.0-alpha-arm64-test.apk) ve [v0.8.1-alpha · güncel test paketi](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/releases/download/v0.8.1-alpha/LANU-Yalanini-Seveyim-v0.8.1-alpha-arm64-test.apk). v0.8.0 → Ayarlar → Güncellemeleri kontrol et → indir/doğrula → kur yoluyla v0.8.1'e geçilebilir. İki paket aynı kaynak ve anahtardan, farklı sürüm kodlarıyla üretilir.

Kalıcı anahtar yoksa bu ikili test imzalıdır; sonraki CI çalıştırmalarına imza sürekliliği garanti edilmez. 0.7 ve daha eski farklı test imzalı APK'ların üzerine yerinde kurulum yapılamaz. Eski uygulamayı kaldırmak ilerlemeyi siler; önce uygulama içinden parolalı yedek oluştur. [Kalıcı imza ve geçiş](docs/STABLE_SIGNING.md).

**0.8 güncellemesi:** Beş ayrı aktif konu, oyun içindeki Konular düğmesi ve her hikâyeye özel şifreli cevap taslağı eklendi. Altıncı konu eski hikâyeyi silmez; kapatma onayı gerekir. Taslak, hikâye değişmeden önce kaydedilir; depolama hatası geçişi durdurur ve cevabı korur. Tamamlanan konuşma başka konuya geçişte yerini bırakır; kazanılan seviyeler ve en iyi puanlar korunur. AI isteğine yalnız seçilen hikâye gönderilir. [Kullanım ve kayıt davranışı](docs/MULTI_STORY.md). Bu özellik canlı AI mizah/muhakeme kalitesinin onaylandığı anlamına gelmez.

**0.8 doğrulaması:** `18c03e6` kaynağında **150 JVM testi, 22 ayrı Android yöntemi ve 4 imza kontrolü geçti**; lint, ARM64 debug ve R8 release başarılı. Android yöntemlerinin 20'si olağan koşuda, ikisi gerçek 13 → 14 yükseltmesinin ayrı aşamalarında çalıştı. Beş hikâye ve taslak yükseltmede korundu; iki depolama hatasında yanlış kayıt onayı verilmedi. Hızlı oyun emülatör p50/p95 517/566 ms; gerçek AI veya fiziksel telefon ölçümü değildir. İki açık APK ayrıca HTTP 200, hash/boyut ve GitHub asset özetiyle doğrulandı. [Yayın kanıtı](docs/release-validation-0.8.json), [APK doğrulaması](docs/ota-release-verification-0.8.json).

**0.7 güncellemesi:** İsteğe bağlı ücretsiz çevrimiçi AI bağlantısı eklendi. Oyna/Ayarlar → Ücretsiz çevrimiçi AI → anahtar sayfasını aç → uygulamaya dön → Anahtarını bağla. Kişisel anahtar yalnız cihazda şifreli tutulur; anahtar kaydı ilerleme yedeğinden ayrıdır. Veri gönderimi için açık izin gerekir. İstekler yalnız `openrouter/free` ve sıfır fiyat tavanlarını kullanır; ücretli model alternatifi yoktur. Kota veya hatada puan yazılmaz, kullanıcı aynı hikâyeyi Hızlı modda sürdürebilir. [Kurulum ve sınırlar](docs/FREE_ONLINE_AI.md). Canlı servis üzerinden Türkçe/mizah kalitesi ve 2–3 saniyelik telefon hızı henüz doğrulanmadı; bu ortamda kişisel API anahtarı yoktur.

**0.7 doğrulaması:** `869cb35` kaynağında **136 JVM testi, 18 ayrı Android yöntemi ve 4 imza kontrolü geçti**; lint, ARM64 debug ve R8 release başarılı. Android yöntemlerinin 16'sı olağan koşuda, ikisi gerçek 11 → 12 paket yükseltmesinin ayrı aşamalarında çalıştı. Bağlantı penceresinde izin/anahtar koşulları, gerçek Keystore şifreleme ve aynı hikâyeyle açık Hızlı mod geçişi doğrulandı. Hızlı oyun emülatör p50/p95 458/626 ms; çevrimiçi model veya fiziksel A55 ölçümü değildir. [Yayın kanıtı](docs/release-validation-0.7.json), [açık APK kontrolü](docs/ota-release-verification-0.7.json).

**0.6 güncellemesi:** Kullanıcının Mars → Hatay → New York konuşması yazım hatalarıyla yeniden oynatılır. İç içe soru ve tek espri döngüsü kaldırıldı; yemek, neden, plan değişikliği ve yeni durak ayrı açılardan takip edilir. Açıklanan rota/iptal kabul edilir; yeni ayrıntı eski açığı silmez. **115 JVM testi, 15 Android yöntemi ve 4 imza kontrolü geçti.** Hızlı yanıt emülatör p50 452 ms / p95 516 ms. [Yayın kanıtı](docs/release-validation-0.6.json), [gerçek cevapların çıktısı](docs/user-screenshot-replay-0.6.json), [kapsam ve sınırlar](docs/STORY_THREAD_REVIEW.md). Bu değişiklik serbest generatif AI onayı değildir; yeni bir gerçek model kalite koşusu yapılmadı. [AI kabul ölçütü](docs/AI_DIALOGUE_ACCEPTANCE.md).

Önceki 0.4 kaynağında 85 JVM testi, 13 Android yöntemi ve 4 imza kapısı senaryosu geçti; [tarihsel teslim kanıtı](docs/release-validation-0.4.json). 0.5, mantık itirazı ve yorumun görünürlüğü düzeltmesini ekler; [değişiklik ve doğrulama kapsamı](docs/CLAIM_REVIEW.md). Yeni APK'lar yayın kapılarından sonra açık bağlantılarından ayrıca doğrulanır. 0.5 yayınında **102 JVM testi, 14 Android yöntemi ve 4 imza kontrolü geçti**; [güncel kanıtlar](docs/release-validation-0.5.json). Gerçek modelin yeni 15 ham örneğinde 11 otomatik geçiş var; metinlerde Türkçe/mantık/mizah kusurları sürdüğü için kalite onayı yok. Kabul denetimi bazı hatalı çıktıları reddeder, model cevabını düzeltmez. [Güncel AI incelemesi](docs/AI_NATIVE_REVIEW_0.5.md).

Hızlı oyun; telefon/şarj, alarm, trafik, arıza, ödev, bağlantı ve diğer somut ayrıntılara göre farklı sorgulama yolları seçer. Saat sırası, bekleme aralığı, araç değişimi ve iletişimdeki boşlukları önceki cevaplarla karşılaştırır. Açıklanan bağlantıyı yeniden suçlamaz; sorulan açı şifreli kayıtta tutulur. Beş zorluk bandında soru mekanizması ve espri değişir. Kurallar dilin anlamını bütünüyle anlayamaz; gerçek AI olarak sunulmaz. Hızlı oyun ve Cihazda AI seçeneklerinde ilk soru yerel hazırlanır; Cihazda AI sonraki soruları/puanları üretir. Çevrimiçi AI ilk soruyu da modelden alır. Oyuncunun önceki ifadeleri ve sorulmuş sorular bağlama eklenir.

## Cevabın içindeki açığa itiraz

Tanınan olağan dışı ulaşım/iş iddiası, açık süre-mesafe hesabı veya kendini tekrar eden neden doğrudan alıntılanır; espri o iddiadan kurulur. Örneğin 100 km'yi 2 dakikada yürümek 3000 km/sa gerektirir; soru bu hesabın hangi kısmını düzelttiğine yönelir. İtiraz açıklanana kadar başka konuya geçmez; ilgisiz yeni saat/kişi, yüksek puan kazandırmaz. Somut geri alma ve düzeltme kabul edilir. Mecaz, olumsuzlama ve fantastik konu ayrı değerlendirilir. Son yorum ile soru aynı grupta görünür; otomatik kaydırma espriyi atlamaz. Bu, her Türkçe cümleyi anlayan bir dil modeli değildir; [kapsam ve sınırlar](docs/CLAIM_REVIEW.md).

## Oynanış

- Kullanıcının belirlediği en fazla beş aktif konu; her hikâyede 5 soru, 100 ilerlemeli seviye. Konularım üzerinden soru geçmişi, oyun modu ve cevap taslağı ayrı korunarak geçiş yapılır.
- Tutarlılık, makullük, ayrıntı ve soruya karşı savunma: her biri 0–25. Hızlı oyunda her alt puanın gerekçesi açılır; ipucu kartı önceki/sonraki ifadeyi ve açıklanmış bağlantıyı gösterir. AI üretmediği gerekçeyle etiketlenmez.
- Kelime sayısı yerine ayrıntı türleri ve gerçekten sorulan sorunun karşılanması değerlendirilir. İlk somut ifadenin açtığı dal takip edilir; kaçamak/tekrar cevaplarda açık kalan soru korunur. Final son turda tüm hikâyeyi değerlendirir.
- Hızlı oyunda son 40 espri kimliği hikâyeler arasında korunur; havuzda seçenek varken aynı espri tekrar seçilmez. Önceki alarm/telefon ayrıntısıyla sonraki trafik gibi ilişkiler espride geri çağrılabilir. AI'nın kendi yazdığı espriler, kullanılmamış yerel espri kimliğiyle kaydedilmez.
- Başarı eşiği seviyeye göre 40–73; başarısız seviyeler tekrar oynanabilir. Önceki seviyeleri tekrar oynamak seviye atlatmaz.
- Türk mizahına yönelik sistem yönergesi, isteğe bağlı hafif argo. Kimliğe, bedene, aileye veya dine yönelik aşağılama istenmez.
- Eğlencelik değerlendirmedir; gerçek yalan tespiti veya doğruluk ölçümü değildir.

## Ücretsiz çalışma modeli

Uygulama ücretli AI modellerine otomatik geçmez; reklam ve abonelik yoktur. **Hızlı oyun kurulumdan sonra doğrudan çevrimdışı çalışır; Hızlı oyun ve Cihazda AI için hesap gerekmez.** Yalnızca Cihazda AI seçilirse **2.740.937.888 bayt (~2,74 GB)** Qwen3.5 Q4_K_M modeli Hugging Face'ten indirilir. Mobil veri operatör ücreti kullanıcıya aittir. İndirme ekranda başlatılır, duraklatılabilir; uygulama kapanırsa sonraki deneme kaldığı yerden devam eder. Kurulum öncesinde SHA-256 doğrulanır. Model Apache 2.0 lisanslıdır.

İsteğe bağlı **Ücretsiz çevrimiçi AI** için kişisel OpenRouter hesabı/anahtarı gerekir; kredi satın almak gerekmez. Ücretsiz yönlendiricide servis kotası ve kullanılabilirlik sınırı vardır. Yalnız bu seçenek seçildiğinde, onayla bu hikâyenin konu/cevapları OpenRouter üzerinden model sağlayıcısına gönderilir. Sağlayıcı yoğunluğu yanıt süresini etkiler. [Ücretsiz yönlendiricinin resmi dokümanı](https://openrouter.ai/docs/guides/routing/routers/free-router). Bu bir sınırsız üretim veya 2–3 saniye garantisi değildir.

**Android 8+ ARM64** cihazlar için geliştirilmiştir; Samsung, Xiaomi ve Huawei ile marka bağımsız çalışacak şekilde tasarlanmıştır. AI modu için 8 GB RAM önerilir; Hızlı oyun büyük model yüklemez. Marka veya model adına göre kısıt bulunmaz. Android APK destekleyen Huawei modelleri dahil Google Play Hizmetleri olmadan açılış desteklenir. Fiziksel Samsung/Huawei/Xiaomi hız, ısınma ve oyun kalitesi testleri henüz yapılmadı. Küçük yerel model büyük bulut modelleriyle aynı muhakeme kalitesini garanti etmez.

## Mimari

Kotlin, Jetpack Compose / Material 3, MVVM, Hilt, Coroutines / StateFlow. Hızlı oyun saf Kotlin kurallarıyla çalışır ve JNI/model açmaz. Cihazda AI, C++ JNI üzerinden sabit commit'li llama.cpp CPU çıkarımı kullanır: 3072 token bağlam, en fazla 240 çıktı tokenı ve donanıma göre 1–4 iş parçacığı. Model yükleme ve üretim aynı 30 saniyelik iptal bütçesini paylaşır. Sınır native yükleme/decode callback'lerinde kontrol edilir; işletim sistemi zamanlaması gerçek duvar süresini etkileyebilir. Önceki 150 saniyelik bekleme kaldırılmıştır.

Dilbilgisi kısıtlı JSON ve aralık doğrulaması; başarısız AI yanıtında puan uydurulmaz. Süre, bellek, çıktı ve motor hataları ayrı kodlarla gösterilir. Kullanıcı, yazdığı cevabı kaybetmeden açıkça Hızlı oyuna geçebilir. Eski hikâyeler AI modunda kalır; sonraki oyun tercihi ve şifreli kayıtlar geriye uyumludur. İlerleme, seçilen hikâye ve en fazla dört bekleyen hikâye Android Keystore AES-GCM ile şifreli, atomik dosyada tutulur. Otomatik Android yedeklemeleri kapalıdır; uygulama konuşma veya anahtarı loglamaz. Kullanıcının seçtiği belge konumuna ayrı parolalı yedek oluşturulabilir: PBKDF2-HMAC-SHA256 (600.000), rastgele salt/nonce ve AES-256-GCM. Yanlış parola/bozuk içerik yazılmadan reddedilir; doğrulanan geri yükleme atomik kaydedilir. Yedek Android Keystore anahtarını içermez ve başka cihaza taşınabilir. Model yedeğe eklenmez. Ekrandaki son yanıt süresi seçilen motorun yanıtı ve kayıt süresidir; çevrimiçi modda ağ/sağlayıcı süresini de içerir; tüm telefonlar için ölçülmüş bir performans garantisi değildir.

## Derleme

JDK 17, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1 gerekir.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

`assembleDebug` yüklenebilir test APK'sını oluşturur. `assembleRelease` R8 ve kaynak küçültmeyi doğrular; üretim imzası sağlanmazsa çıktısı imzasızdır.

Üretim imzasında `LANU_KEYSTORE`, `LANU_STORE_PASSWORD`, `LANU_KEY_ALIAS`, `LANU_KEY_PASSWORD`, `LANU_CERT_SHA256` birlikte gereklidir. Gradle özel anahtar ve sertifika hashini doğrular; kısmi yapılandırmada debug imzasına dönmez. `-Planu.requireProductionSigning=true` anahtar yokken derlemeyi durdurur. Güvenilir kendi cihazında `bash scripts/bootstrap_stable_signing.sh` GitHub secrets kurulumunu yapar; özel anahtar depoya veya artifact'a konmaz. [Kurulum](docs/STABLE_SIGNING.md).

GitHub Actions test/lint/native ARM64 debug+R8 release derlemesi ve Google hizmetsiz AOSP kontrolünü çalıştırır. Yayın bu kapılardan sonra iki APK, manifest, hash ve imza bilgisi üretir; herkese açık indirme bağlantıları yeniden indirilip karşılaştırılır. Emülatör testi aynı imzalı yeni paketi doğrular, gerçek `adb install -r` yapar ve şifreli ilerlemenin korunduğunu kontrol eder. Fiziksel cihaz testi için [A55 kontrolü](docs/A55_CHECK.md).

## Uygulama içinden güncelleme ve kayıt

Ayarlar'da güncelleme kontrolü, indirme/devam etme ve kurulum düğmeleri vardır. Yeni sürüm GitHub Releases içindeki uygulamaya ait manifestten seçilir. Boyut, SHA-256, paket, daha yüksek sürüm ve kurulu sertifika eşleşmesi birlikte aranır. Kurulumdan hemen önce yeniden kontrol edilir ve yalnız özel cache klasöründeki doğrulanmış dosya FileProvider üzerinden Android kurucusuna açılır. Farklı imzada kaldırma veya doğrulamayı atlama yoktur; Android kullanıcı onayı ister. Bağlantı hatasında yeniden deneme ve yayınlar sayfasını açma alternatifi bulunur.

Parolalı yedek ve geri yükleme aynı ayarlardadır. Cihazdaki son 100 açılış/değerlendirme için Hızlı/AI ortanca, %95, hata ve süre aşımı özetleri ayrı gösterilir. Ölçüm motor ve kayıt süresidir; ekran çizimi dahil değildir. Sıcaklık/güç tasarrufuna göre AI iş parçacığı sınırı güncellenir; kritik sıcaklıkta üretim bekletilir.

## Firebase / hata raporlama durumu

Crashlytics yalnızca gerçek Firebase konfigürasyonu bulunan derlemelere dahil edilir; koşullu Gradle yapılandırması hazırlanmıştır; kullanıcıya ait Firebase projesi ve `app/google-services.json` sağlanmadığından **aktif değildir**. Raporlama manifestte varsayılan kapalıdır. Etkinleştirilmeden önce kullanıcı onayı, veri politikası ve test crash doğrulaması tamamlanmalıdır. Oyun Firebase olmadan çalışır. Yakalanan hatalar kullanıcı dostu yeniden denemeye döner; fatal handler Android'in asıl handler'ına devreder, uygulamayı sahte biçimde çalışır tutmaz.

## Kaynaklar ve lisanslar

- [Qwen3.5 4B model kartı](https://huggingface.co/Qwen/Qwen3.5-4B)
- [Sabit GGUF dosyası](https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/tree/e87f176479d0855a907a41277aca2f8ee7a09523)
- [llama.cpp](https://github.com/ggml-org/llama.cpp/tree/d8d92973c8c21bb4dd5d592f0fec26fe2cd1e654)
- Lisans metinleri: `docs/licenses/`.
- LANU maskotu bu proje için imagegen ile üretildi; uygulama için WebP olarak optimize edildi.

Detaylı doğrulama ve kalan cihaz kontrolleri: [docs/VALIDATION.md](docs/VALIDATION.md).

Cevaba özel soru/espri örnekleri ve kalite kapsamı: [docs/DIALOGUE_QUALITY.md](docs/DIALOGUE_QUALITY.md).

Android uyumluluk kapsamı, gereksinimler ve doğrulanmamış fiziksel cihaz matrisi: [docs/ANDROID_COMPATIBILITY.md](docs/ANDROID_COMPATIBILITY.md).

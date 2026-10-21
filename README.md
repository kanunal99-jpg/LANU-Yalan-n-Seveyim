# LANU Yalanını Seveyim

Türkçe, tek kişilik Android hikâye sorgulama oyunu. Kullanıcı konuyu yazar, beş soruya cevap verir ve inandırıcılık puanı toplar. İki açıkça ayrılmış oyun seçeneği vardır:

| Seçenek | Değerlendirme | İndirme / hız |
| --- | --- | --- |
| **Hızlı oyun** (varsayılan) | Hazır soru kalıpları ve yerel kurallar; dil modeli/AI değildir | Ek indirme yok; 2–3 saniyelik yanıt hedefi |
| **Cihazda AI** | Gerçek Qwen3.5 4B; yanıtları model puanlar | İsteğe bağlı 2,74 GB; telefonda 2–3 saniye garantisi yok |

Android test APK'sı: [v0.3.0-alpha indir](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/releases/download/v0.3.0-alpha/LANU-Yalanini-Seveyim-v0.3.0-alpha-arm64-test.apk). Cihazda AI deneysel; güncel model kalite denemesi henüz kabul eşiğini geçmedi. Ayrıntılar aşağıdaki kalite kaydındadır.

Hızlı oyun; telefon/şarj, alarm, trafik, arıza, ödev, bağlantı ve diğer somut ayrıntılara göre farklı sorgulama yolları seçer. Saat sırası, bekleme aralığı, araç değişimi ve iletişimdeki boşlukları önceki cevaplarla karşılaştırır. Açıklanan bağlantıyı yeniden suçlamaz; sorulan açı şifreli kayıtta tutulur. Beş zorluk bandında soru mekanizması ve espri değişir. Kurallar dilin anlamını bütünüyle anlayamaz; gerçek AI olarak sunulmaz. İki seçenekte de ilk soru konuya göre hemen hazırlanır. AI modunda sonraki soruları/puanları model üretir; yalnız oyuncunun ifadelerini içeren vaka notları ve daha önce sorulan sorular modele yardımcı veri olarak iletilir.

## Oynanış

- Kullanıcının belirlediği konu, her turda 5 soru, 100 ilerlemeli seviye.
- Tutarlılık, makullük, ayrıntı ve soruya karşı savunma: her biri 0–25. Final puanı son soruda tüm hikâye üzerinden belirlenir.
- Başarı eşiği seviyeye göre 40–73; başarısız seviyeler tekrar oynanabilir. Önceki seviyeleri tekrar oynamak seviye atlatmaz.
- Türk mizahına yönelik sistem yönergesi, isteğe bağlı hafif argo. Kimliğe, bedene, aileye veya dine yönelik aşağılama istenmez.
- Eğlencelik değerlendirmedir; gerçek yalan tespiti veya doğruluk ölçümü değildir.

## Ücretsiz çalışma modeli

Ücretli API, sunucu, hesap, reklam veya abonelik yoktur. **Hızlı oyun kurulumdan sonra doğrudan çevrimdışı çalışır.** Yalnızca Cihazda AI seçilirse **2.740.937.888 bayt (~2,74 GB)** Qwen3.5 Q4_K_M modeli Hugging Face'ten indirilir. Mobil veri operatör ücreti kullanıcıya aittir. İndirme ekranda başlatılır, duraklatılabilir; uygulama kapanırsa sonraki deneme kaldığı yerden devam eder. Kurulum öncesinde SHA-256 doğrulanır. Model Apache 2.0 lisanslıdır.

**Android 8+ ARM64** cihazlar için geliştirilmiştir; Samsung, Xiaomi ve Huawei ile marka bağımsız çalışacak şekilde tasarlanmıştır. AI modu için 8 GB RAM önerilir; Hızlı oyun büyük model yüklemez. Marka veya model adına göre kısıt bulunmaz. Android APK destekleyen Huawei modelleri dahil Google Play Hizmetleri olmadan açılış desteklenir. Fiziksel Samsung/Huawei/Xiaomi hız, ısınma ve oyun kalitesi testleri henüz yapılmadı. Küçük yerel model büyük bulut modelleriyle aynı muhakeme kalitesini garanti etmez.

## Mimari

Kotlin, Jetpack Compose / Material 3, MVVM, Hilt, Coroutines / StateFlow. Hızlı oyun saf Kotlin kurallarıyla çalışır ve JNI/model açmaz. Cihazda AI, C++ JNI üzerinden sabit commit'li llama.cpp CPU çıkarımı kullanır: 3072 token bağlam, en fazla 240 çıktı tokenı ve donanıma göre 1–4 iş parçacığı. Model yükleme ve üretim aynı 30 saniyelik iptal bütçesini paylaşır. Sınır native yükleme/decode callback'lerinde kontrol edilir; işletim sistemi zamanlaması gerçek duvar süresini etkileyebilir. Önceki 150 saniyelik bekleme kaldırılmıştır.

Dilbilgisi kısıtlı JSON ve aralık doğrulaması; başarısız AI yanıtında puan uydurulmaz. Süre, bellek, çıktı ve motor hataları ayrı kodlarla gösterilir. Kullanıcı, yazdığı cevabı kaybetmeden açıkça Hızlı oyuna geçebilir. Eski hikâyeler AI modunda kalır; sonraki oyun tercihi ve şifreli kayıtlar geriye uyumludur. İlerleme ve son hikâye Android Keystore AES-GCM ile şifreli, atomik dosyada tutulur. Android yedeklemeleri kapalıdır; konuşmalar loglanmaz. Ekrandaki son yanıt süresi yerel değerlendirme ve kayıt süresidir; tüm telefonlar için ölçülmüş bir performans garantisi değildir.

## Derleme

JDK 17, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1 gerekir.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

`assembleDebug` yüklenebilir test APK'sını oluşturur. `assembleRelease` R8 ve kaynak küçültmeyi doğrular; üretim imzası sağlanmazsa çıktısı imzasızdır.

Üretim imzası için `LANU_KEYSTORE`, `LANU_STORE_PASSWORD`, `LANU_KEY_ALIAS`, `LANU_KEY_PASSWORD` ortam değişkenlerini güvenli CI secrets üzerinden sağla. Anahtarları depoya ekleme. **Test APK'ları debug imzalıdır; farklı CI çalıştırmalarının imzaları değişebilir. Yerinde güncelleme ve kalıcı imza hazır kabul edilmez.**

GitHub Actions ana dalda ve PR'larda test/lint/debug+release derlemesini yapar. `v*` etiketi kalite kontrollerinden sonra debug/test APK'sını indirilebilir prerelease'e ekler. OTA bu sürümün kapsamında değildir.

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

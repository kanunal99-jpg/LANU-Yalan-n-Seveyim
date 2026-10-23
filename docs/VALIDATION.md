# Doğrulama kaydı

## 0.4 OTA, yedek ve gerekçeli puanlama

[40014129839 numaralı koşunun](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/actions/runs/40014129839) AOSP işi başarılı. Kaynak: `26fb0c5d82455b5136c0cf71345b5d75d9cbf9c6`. XML'deki 12 yöntemin 10'u normal koşuda başarılı; hazırlık ve yükseltme sonrası kontrol yöntemleri normal koşuda atlanıp ayrı gerçek yükseltme aşamalarında birer kez başarılı çalıştırıldı. Toplam 12 Android yöntemi doğrulandı.

AOSP API 30, Google Play Hizmetleri yok, yazı ölçeği 1,3. Klavye/gönderim, sonucun görünmesi, dört puan gerekçesi, güncelleme düğmesi, yedek parolası eşleştirme, yanlış parolada kayıt korunması, cihaz anahtarı silindikten sonra taşınabilir yedek geri yüklemesi ve süre geçmişi kontrol edildi. Aynı anahtarlı kod 6 APK'sı kod 5 üzerinde üretimde kullanılan hash/paket/sürüm/imza denetimlerinden geçti. Değiştirilmiş APK, yeniden hesaplanmış public hashle bile Android imza denetiminde reddedildi; farklı paket/imza/eski sürüm reddi de denetlendi.

Sonraki aşama gerçek `adb install -r`: kod 5'te seviye 3, iki en iyi puan, açık hikâye ve espri geçmişi Android Keystore ile kaydedildi; kod 6 kurulduktan sonra tüm kayıt ve aynı sertifika doğrulandı. Her iki aşama `OK (1 test)` sonucu verdi. [Ham özet](ota-aosp-results.json).

Beş Hızlı oyun yanıtı ekrana **549, 559, 752, 482, 674 ms** içinde geldi; ortanca 559 ms, %95 752 ms. Bu, gönderimden sonraki soru/sonuç görünene kadar test çerçevesi dahil emülatör ölçümüdür; fiziksel A55 veya gerçek model hızı değildir. Uygulama içindeki süre kartı farklı olarak motor+kayıt süresini ölçer.

Gerçek modelin yeni tanılaması JNI ile aynı `generation.cpp` üzerinden 11 örnek çalıştırır. Tarihsel server çıktısı güncel native kanıtı sayılmaz. Kaynak ve sınırlar: [AI_NATIVE_VALIDATION.md](AI_NATIVE_VALIDATION.md). Kalıcı imza kurulumu [STABLE_SIGNING.md](STABLE_SIGNING.md), fiziksel cihaz kontrolü [A55_CHECK.md](A55_CHECK.md).

## 0.3.0 cevaba özel sorgulama

Hızlı oyun artık cevabın neden, saat, süre, araç, kişi ve yer ayrıntılarına göre sorgu seçer; önceki ifadeleri karşılaştırır, sorulan açıyı kaydeder ve açıklanan boşluğu yeniden suçlamaz. Beş zorluk bandı ve hikâye başına espri çeşitleri var. Örnekler ve sınırlar: [DIALOGUE_QUALITY.md](DIALOGUE_QUALITY.md).

[39572158411 numaralı CI çalıştırması](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/actions/runs/39572158411) başarılı: 37 JVM testi, lint, native ARM64 debug/R8 release ve beş Google hizmetsiz AOSP ekran testi geçti. Beş Hızlı oyun yanıtının sonraki soru/sonucu görünene kadar emülatör süreleri **530, 516, 447, 414, 491 ms**; test çerçevesi gecikmesi dahildir. Fiziksel telefon veya gerçek AI hız ölçümü değildir.

Güncel gerçek model denemesinde yedi senaryonun ikisi otomatik kontrolden geçmedi; geçenlerde de Türkçe ve neden-sonuç kusurları var. Model mizah/muhakeme kalitesi onaylanmadı; Cihazda AI deneysel olarak etiketlidir. Bu sürümün doğrulanmış özgüllük iyileştirmesi Hızlı oyundadır. Ham çıktılar: [dialogue-ai-results.json](dialogue-ai-results.json).

v0.3.0 APK yayımlanan bağlantıdan HTTP 200 ile indirildi; 25.450.302 bayt ve GitHub SHA-256 özeti eşleşti. `apksigner` doğrulaması geçti; paket kodu 4, sürüm 0.3.0-alpha, API 26+, hedef 35 ve ARM64. [Kurulum kaydı](dialogue-release-verification.json). Test sertifikası v0.2.0 ile farklıdır; eski APK üzerine güncelleme kurulamaz. Eski uygulamayı kaldırmak ilerlemeyi ve modeli siler. Kalıcı özel imza hâlâ yapılandırılmalıdır.

## 0.2.0 bekleme ve hata düzeltmesi

Kullanıcının ekran görüntüsünde ilk soru gelmeden genel hata görülüyordu. Ekran görüntüsü tek başına asıl native istisnayı belirlemeye yetmez. Önceki sürüm model açılışı, üretim, eksik JSON ve diğer hataları aynı mesaj altında gizliyordu; 4B modeli ilk soru için de çalıştırıyor ve üretimi 150 saniyeye kadar bekletiyordu.

Yeni varsayılan **Hızlı oyun** ayrı, açıkça etiketlenmiş Kotlin kurallarıdır; AI olarak sunulmaz. Model yüklemeden konuya göre ilk soru ve sonraki cevaplara göre yorum/puan üretir. Gerçek AI seçeneği korunur; ilk sorusu yerel hazırlanır, sonraki cevapları Qwen değerlendirir. AI yükleme ve üretimi 30 saniyelik ortak iptal bütçesi, 240 tokenlık daha kısa yanıt, 3072 bağlam ve tek çıktı için logits ayırma kullanır. Süre sınırı, bellek, bağlam ve bozuk çıktı hataları ayrı görünür. Modelin kendi RAM kullanımının her turu düşük bellek nedeniyle reddetmesi önlenir. Gerçek AI için 2–3 saniye garantisi verilmez.

Yeni kontroller: 100 seviyede beş soruluk kurallı oyun, puan sınırları, tekrar etmeyen sorular, açık araç değişimi/aktarma/olumsuzlama, puan enjeksiyonu, somut/belirsiz/tekrar cevap ayrımı ve eski kayıt göçü. AOSP testi model bulunmadan tam Hızlı oyun, şifreli kayıt, sonraki seviyenin açılması ve her yanıtın 3 saniye altında görünmesi için kapı uygular. Koşulan kontrollerin sonuçları CI raporlarından doğrulanmalıdır; fiziksel telefon gecikmesi veya AI kalitesi bu testlerden çıkarılmaz.

Önceki model kalite sonuçları aşağıda tarihsel kayıttır. 0.2.0 kısa çıktı yönergesi henüz fiziksel telefonda kalite onayı almadı.

Yerel 22 JVM testi geçti (8 Hızlı oyun/kayıt göçü, 9 ilerleme/puan, 2 JSON dilbilgisi, 3 donanım politikası). JNI kaynağı sabit llama.cpp başlıklarıyla C++17 sözdizimi kontrolünden geçti. AOSP kontrolü klavye ve 1,3 yazı ölçeği açıkken cevap düğmesinin görünmediğini yakaladı. Oyun ekranı kısa araç çubuğu, kaydırılan konu/konuşma ve altta kalan cevap alanı olarak düzenlendi; gönderimde klavye kapanır. Yayın için bu akışın hem klavye IME gönderimi hem düğme dokunuşuyla geçmesi gerekir; 3 saniyelik sınır değiştirilmez. Debug tanılaması yalnız istisna sınıfı ve kaynak konumu yazar; mesajlar ve konuşmalar loglanmaz.

**0.2.0 sonucu:** [39539341724 numaralı CI çalıştırması](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/actions/runs/39539341724) başarılı. 22 JVM testi, lint, ARM64 debug ve R8 release derlemesi ile 3 AOSP testi geçti. Model olmadan ilk soru ve beş yanıt 3 saniye kapısını geçti; beş turda ölçülen süreler sırasıyla **302, 335, 414, 267 ve 387 ms**. Ölçüm, gönderim eyleminden sonraki soru göstergesi veya sonucun hazır olmasına kadardır; test çerçevesi gecikmesini de içerir. Hem IME hem düğme gönderimi, klavyede düğme görünürlüğü ve sonraki seviye doğrulandı. Ayrıntılar: [fast-game-smoke-results.json](fast-game-smoke-results.json). Bunlar emülatör ölçümleridir; fiziksel telefon veya gerçek AI çıkarım süresi değildir. Yayımlanan APK bu koşunun normal ARM64 paketidir; UI test paketi yayımlanmadı.

**Kurulum doğrulaması:** Yayımlanan APK indirildi, GitHub SHA-256 özetiyle eşleşti ve apksigner doğrulaması geçti. Paket `com.lanu.yalan`, sürüm kodu 3, Android API 26+, hedef 35, ARM64. v0.1.1 ve v0.2.0 test sertifikaları karşılaştırıldı: farklıdır; v0.1.1 üstüne yerinde güncelleme yapılamaz. Eski uygulamayı kaldırmak eski kayıtları ve modeli siler. Hızlı oyun yeniden model indirmez; kalıcı özel imza hâlâ ayrıca yapılandırılmalıdır. Kayıt göçü kodunun geriye uyumlu olması, farklı imzalarla Android kurulum engelini ortadan kaldırmaz.

## Sabit kaynaklar

- Seçilen aday: Qwen3.5 4B Q4_K_M, Qwen modelinin Unsloth GGUF dönüştürmesi.
- GGUF revizyonu: e87f176479d0855a907a41277aca2f8ee7a09523.
- Gerçekte indirilen 2.740.937.888 bayt dosyanın SHA-256 özeti doğrulandı: 00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4.
- llama.cpp sabit revizyonu: d8d92973c8c21bb4dd5d592f0fec26fe2cd1e654. CMake arşiv SHA-256 kontrolü var.
- Gradle dağıtım SHA-256 kontrolü wrapper içinde sabitlenmiştir.

## Model kalite deneyleri

İlk 1.7B aday açık bir araç çelişkisini yeterince iyi yakalayamadığı için elendi. Qwen3 4B Instruct 2507 de yeterli bulunmadı; beş senaryonun ham sonuçları `experiments/qwen3-4b-rejected.json` içindedir.

Qwen3.5 4B aynı sentetik senaryolarda denenmektedir. `scripts/model_smoke.py` uygulamanın gerçek sistem yönergesini ve JSON dilbilgisini kullanır. İlk soru, açık çelişki, tutarlı cevap, puan talimatı ve final turunu doğrular. Güncel kayıt: `model-smoke-results.json`; `passed` alanı bu sınırlı testin sonucudur. Host süreleri telefon performansı değildir. Bu testler genel doğruluk, kusursuz Türkçe veya kusursuz mizah garantisi değildir; küçük yerel model bazen yanlış çıkarım yapabilir.

## Android otomatik kontrolleri

Native API uyumsuzluğu düzeltildi. Android 8.0 üzerinde kullanılamayan navigation bar tema özelliği kaldırıldı. İlk/son tur çıktıları sadece yönergeye bırakılmayıp dilbilgisiyle sınırlandı. Debug APK içindeki native çıkarım motoru da -O3 ile derlenir.

11 JVM testi: puan sınırları, tekrar puan yazmama, seviye atlama sınırları, 100. seviye, eksik/bozuk model yanıtı ve ilk/son tur dilbilgisi. GitHub Actions bu testleri, Android lint, debug ve R8 release derlemesini çalıştırır. 0.1.0 alfa için başarılı çalıştırma: https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/actions/runs/39429881928. Sonraki sürüm ayrıca donanım politikası ve AOSP arayüz testlerinden geçmelidir.

## Fiziksel cihaz kabul kontrolleri — henüz yapılmadı

Samsung, Huawei, Xiaomi ve diğer markaların ARM64 Android cihazlarında:

1. APK kurulumu, ilk açılış, büyük yazı tipi ve klavye açıkken cevap kutusu.
2. Wi-Fi model indirme, duraklatma, ağ kesintisi, uygulama kapanması ve devam etme.
3. Uçak modunda beş soruluk tam oyun; Türkçe mizah, tekrar etmeme ve puan adaleti.
4. RAM, ısınma, pil ve gerçek yanıt gecikmesi ölçümleri (8 GB RAM önerilir).
5. Ekran döndürme ve işlem sonlandırma sonrası kayıtlı turun geri gelmesi.
6. Kilitler, iptal, bozuk indirme, düşük boş alan ve model kaldırma.

## Üretim öncesi kalanlar

Kalıcı özel imza ve CI secrets, gerçek telefon smoke testi ve Türkçe oyun kalitesinin daha geniş değerlendirmesi. Crashlytics yapılandırma altyapısı var ancak Firebase projesi/konfigürasyonu sağlanmadığı için aktif değil. Bu bölüm tarihsel 0.1–0.3 kaydıdır. 0.4 uygulama içi OTA yolunu ve parolalı yedeği ekler; kalıcı anahtar durumu yayınlanan signing-info.json içinde ayrı belirtilir.

### Güncel model smoke sonucu

Qwen3.5 4B ile beş sentetik senaryo çalıştırıldı. Dilbilgisi/puan sınırları ve temel semantik kontroller geçti: açık çelişki 8/25 tutarlılık, tutarlı arıza 18/25, tutarlı final 20/25; talimatla puan isteyen yanıtta ayrıntı ve savunma 0. Host süreleri yaklaşık 24–26 saniyeydi, Android ölçümü değildir.

**Bilinen kalite kusuru:** Bazı çıktılar kullanıcı söylemediği hâlde yakıt seviyesi gibi ayrıntılar ekledi veya final yorumunda gereksiz soru cümleleri kullandı. Bu nedenle alfa sürüm, AI değerlendirmesi henüz üretim kalitesinde onaylanmış değildir. Düşük/yüksek puan ayrımı için semantik testte kesin tek bir sayı yerine anlamlı aralık kullanılır (çelişki en fazla 10/25). `question` alanının finalde boş olması dilbilgisiyle zorlanır; sonucun yorum metni hâlâ modelin üretimidir.

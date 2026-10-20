# Doğrulama kaydı

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

Kalıcı özel imza ve CI secrets, gerçek telefon smoke testi ve Türkçe oyun kalitesinin daha geniş değerlendirmesi. Crashlytics yapılandırma altyapısı var ancak Firebase projesi/konfigürasyonu sağlanmadığı için aktif değil. OTA uygulanmadı. İlk APK debug/test imzalı bir alfa olarak dağıtılacaktır.

### Güncel model smoke sonucu

Qwen3.5 4B ile beş sentetik senaryo çalıştırıldı. Dilbilgisi/puan sınırları ve temel semantik kontroller geçti: açık çelişki 8/25 tutarlılık, tutarlı arıza 18/25, tutarlı final 20/25; talimatla puan isteyen yanıtta ayrıntı ve savunma 0. Host süreleri yaklaşık 24–26 saniyeydi, Android ölçümü değildir.

**Bilinen kalite kusuru:** Bazı çıktılar kullanıcı söylemediği hâlde yakıt seviyesi gibi ayrıntılar ekledi veya final yorumunda gereksiz soru cümleleri kullandı. Bu nedenle alfa sürüm, AI değerlendirmesi henüz üretim kalitesinde onaylanmış değildir. Düşük/yüksek puan ayrımı için semantik testte kesin tek bir sayı yerine anlamlı aralık kullanılır (çelişki en fazla 10/25). `question` alanının finalde boş olması dilbilgisiyle zorlanır; sonucun yorum metni hâlâ modelin üretimidir.

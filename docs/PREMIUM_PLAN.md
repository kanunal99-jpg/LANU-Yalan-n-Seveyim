# Premium geliştirme kaydı

Hedef: ücretsiz, Android üzerinde çalışan; dedektif gibi sorgulayan ve komedyen gibi yorumlayan LANU. Başlangıç: v0.3.0-alpha, 37 JVM / 5 AOSP testi başarılı; gerçek model kalitesi kabul edilmedi, fiziksel telefon ölçümü ve kalıcı imza bekliyor.

| Hat | Somut çıktı | Kabul kanıtı |
| --- | --- | --- |
| 1. Gerçek AI | Doğru konuşma biçimi, oyuncunun ifadelerine dayalı kısa sorgu, gerçek model denemesi ve geçersiz çıktıda açık hata | Sabit model/hash ve native ile aynı örnekleme; ham sonuçlar; Türkçe, muhakeme ve mizahın ayrı değerlendirilmesi |
| 2. Mizah | Hikâyeler arasında espri hafızası, gerçek ayrıntıya geri gönderme ve farklı sorgu açıları | Çeşitlilik/tekrar/uydurma ayrıntı testleri, gerçek örnekler |
| 3. Puanlama | Dört alt puanın gerekçesi, kaçamak cevap ile meşru düzeltmenin ayrılması, ipucu kartları | Aynı anlamın farklı yazımları, kısa somut cevap, çelişki ve onarım senaryoları |
| 4. Performans | Telefonda yerel süre özeti, kaynak/bellek durumu, A55 için çalıştırılabilir ölçüm akışı | Açılış ve yanıt ayrımı, p50/p95, ayrı Hızlı/AI sonuçları; emülatör ve fiziksel cihaz kanıtlarının ayrılması |
| 5. Kayıt/güncelleme | Parolalı taşınabilir şifreli yedek ve atomik geri yükleme; üretim imzası doğrulama kapısı | Yanlış parola, tahrifat, boyut/şema sınırları, kayıt korunması; imza karşılaştırması |

Kritik zincirler: ana yol → gerçek alternatif → açık fallback → hata yönetimi → veriyi koruyan varsayılan → metin içermeyen tanılama → smoke test. Ücretli servis/anahtar eklenmez. AI başarısızsa puan veya model yanıtı uydurulmaz; kullanıcı açıkça Hızlı oyuna geçer.

Kalıcı imza için özel anahtarın ve parolaların güvenli depo secret'larında bulunması gerekir. Kaynak koduna veya yayın paketine özel anahtar konulmaz. Yapılandırma yoksa test APK'sı üretim imzalı diye sunulmaz. Fiziksel A55/Huawei/Xiaomi testi uzaktan ölçülmüş gibi gösterilmez.

İşler henüz geliştirme aşamasındadır. Sonuçlar derleme, gerçek model ve Android kontrollerinden sonra bu kayda eklenir. Her yayınlanabilir sürümün APK'sı Releases'te yayımlanır, indirilerek bütünlüğü ve imzası doğrulanır.

## 0.4 doğrulanan durum

Kaynak `26fb0c5`, CI `40014129839`: 77 JVM testi, lint, normal ARM64 native debug ve R8 release başarılı. AOSP'de 10 olağan yöntem ve ayrı hazırlık/yükseltme sonrası iki yöntem geçti. Gerçek kod 5 → 6 yerinde güncellemede Android Keystore kaydı ve aktif hikâye korundu; Hızlı oyun ekran süreleri 549/559/752/482/674 ms.

1. Gerçek AI: JNI ile ortak native üretici ve 11 gerçek model örneğini kaydeden CI kuruldu. Yeni kalite raporu, ham Türkçe/mantık/mizah incelemesi gerektirir; üretim onayı varsayılmaz.
2. Mizah: hikâyeler arası son 40 espri hafızası, oyuncunun önceki ayrıntısına geri gönderme ve seviye bandına göre sorgu var. Havuzdaki alternatifler ve açıklanan çelişkiyi yeniden suçlamama testleri geçti.
3. Puan: dört gerekçe ve önce/sonra ipucu kartı, kısa somut cevabın hakkı, kaçamak/tekrar cevap, belirsiz saat ile açık çelişki ayrımı ve düzeltme testleri geçti. Kurallar hâlâ sınırlı dil tanımasıdır.
4. Performans: telefonda ayrı Hızlı/AI/açılış özeti, hatalar ve kaynak durumu hazır. Emülatör hız kapısı geçti; fiziksel A55/Huawei/Xiaomi süresi henüz ölçülmedi.
5. Kayıt/OTA: parolalı yedek, yanlış parolada mevcut kayıt korunması, cihaz anahtarı kaybolduktan sonra geri yükleme, APK imza/hash/paket/sürüm kontrolleri ve gerçek yükseltme başarılı. Kalıcı özel anahtar kullanıcı hesabındaki GitHub secrets kurulumunu bekler. Test ikilisinin kendi içindeki imza eşitliği gelecekteki CI anahtar sürekliliği sayılmaz.

Sonraki teslim zinciri: aynı kaynak ağacı → yayın CI'si → indirilebilir iki Release APK'sı → halka açık bağlantılardan yeniden indirme/hash doğrulama. Eksik gerçek AI, fiziksel cihaz veya kalıcı imza kanıtı tamamlanmış gibi gösterilmez.

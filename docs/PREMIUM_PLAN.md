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

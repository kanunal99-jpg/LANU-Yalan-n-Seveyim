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

0.4 test ikilisi yayımlandı; son kabul durumu aşağıdadır. Gerçek AI kalitesi, fiziksel telefon ölçümü ve kalıcı imza tamamlanmış değildir.

## Teslim edilen 0.4 test ikilisi

Kaynak `1db4284`, [CI 40016842962](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/actions/runs/40016842962): **85 JVM testi, 13 Android yöntemi ve 4 imza kapısı senaryosu geçti**; lint, ARM64 debug ve R8 release başarılı. Android yöntemlerinin 11'i olağan koşuda, ikisi ayrı gerçek yükseltme aşamalarında çalıştı. Hızlı oyunun beş yanıtı emülatörde 443–684 ms içinde göründü; p50 515 ms, p95 684 ms. Model bulunmadığında açık Hızlı oyun geçişiyle aynı hikâyenin korunması da doğrulandı.

[v0.4.0-alpha](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/releases/tag/v0.4.0-alpha) OTA başlangıç paketi ve [v0.4.1-alpha](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/releases/tag/v0.4.1-alpha) güncelleme paketi aynı test sertifikasıyla yayımlandı. İki açık APK bağlantısı tekrar indirildi, boyut/SHA-256 ve Android imzaları doğrulandı. [Teslim kanıtları](release-validation-0.4.json). Ayarlar'daki güncelleme yolu, parolalı yedek, gerekçeli yerel puanlama ve espri hafızası kullanılabilir.

Gerçek modelin ham değerlendirmesi **5/11 otomatik başarı** ve onaylanmamış Türkçe/muhakeme/mizah gösterdi. Host p50 44,74 saniye; telefon ölçümü değildir. Ardından eklenen sekiz testli kabul denetimi hatalı çıktıyı reddeder, model kalitesini iyileştirmiş sayılmaz. AI deneysel kalır. [Model incelemesi](AI_NATIVE_REVIEW.md).

Sonraki gereklilikler: kullanıcı hesabında [kalıcı imza kurulumu](STABLE_SIGNING.md), [fiziksel cihaz kontrolü](A55_CHECK.md) ve istenen AI kalite/hız hedefini sağlayan yeni ölçülmüş çözüm. Bu test ikilisinin aynı imzası, gelecekteki yayınların kalıcı imza sürekliliği değildir.

## 0.4 doğrulanan durum

Kaynak `26fb0c5`, CI `40014129839`: 77 JVM testi, lint, normal ARM64 native debug ve R8 release başarılı. AOSP'de 10 olağan yöntem ve ayrı hazırlık/yükseltme sonrası iki yöntem geçti. Gerçek kod 5 → 6 yerinde güncellemede Android Keystore kaydı ve aktif hikâye korundu; Hızlı oyun ekran süreleri 549/559/752/482/674 ms.

1. Gerçek AI: JNI ile ortak native üretici ve 11 gerçek model örneğini kaydeden CI kuruldu. Yeni kalite raporu, ham Türkçe/mantık/mizah incelemesi gerektirir; üretim onayı varsayılmaz.
2. Mizah: hikâyeler arası son 40 espri hafızası, oyuncunun önceki ayrıntısına geri gönderme ve seviye bandına göre sorgu var. Havuzdaki alternatifler ve açıklanan çelişkiyi yeniden suçlamama testleri geçti.
3. Puan: dört gerekçe ve önce/sonra ipucu kartı, kısa somut cevabın hakkı, kaçamak/tekrar cevap, belirsiz saat ile açık çelişki ayrımı ve düzeltme testleri geçti. Kurallar hâlâ sınırlı dil tanımasıdır.
4. Performans: telefonda ayrı Hızlı/AI/açılış özeti, hatalar ve kaynak durumu hazır. Emülatör hız kapısı geçti; fiziksel A55/Huawei/Xiaomi süresi henüz ölçülmedi.
5. Kayıt/OTA: parolalı yedek, yanlış parolada mevcut kayıt korunması, cihaz anahtarı kaybolduktan sonra geri yükleme, APK imza/hash/paket/sürüm kontrolleri ve gerçek yükseltme başarılı. Kalıcı özel anahtar kullanıcı hesabındaki GitHub secrets kurulumunu bekler. Test ikilisinin kendi içindeki imza eşitliği gelecekteki CI anahtar sürekliliği sayılmaz.

Yukarıdaki 77 testli kayıt yayın öncesi tarihsel kontroldür. Son teslimin 85 testli ve iki yayımlanmış APK'lı kanıtı bu belgenin teslim bölümündedir. Eksik gerçek AI, fiziksel cihaz veya kalıcı imza kanıtı tamamlanmış gibi gösterilmez.

## 0.5 son teslim

`5f2cff8` kaynağı ve `40026975283` yayın koşusunda 102 JVM testi, 14 Android yöntemi, 4 imza kontrolü, lint, ARM64 debug/R8 release başarılı. v0.5.0/v0.5.1 aynı test anahtarıyla yayımlandı, açık adreslerden yeniden indirilip hashleri doğrulandı. Mantık itirazı, geçer puanın engellenmesi ve yorum/sorunun tamamının görünmesi düzeltildi. Hızlı oyun emülatör p50 427 ms, p95 466 ms. [Kanıt](release-validation-0.5.json). Gerçek AI 11/15 otomatik geçişe rağmen anlam ve Türkçe hataları üretiyor; kalite onayı yok. Kalıcı imza ile fiziksel cihaz kabulü de açık kalır. [AI incelemesi](AI_NATIVE_REVIEW_0.5.md).

## 0.6: kullanıcı ekranlarıyla doğrulanan tekrar hatası

Kullanıcının gerçek konuşması önceki 0.5 testlerinde kapsanmıyordu; eski test başarısı yeterli kalite kanıtı değildi. `03166b3` ve CI `40083476949`: 115 JVM, 15 Android yöntemi, 4 imza kontrolü geçti. İç içe sorular kaldırıldı; Mars/menü, gerekçe/Elon, Hatay/plan iptali ve New York/geçiş ayrı izlenir. Dört gerçek cevapta dört farklı reaksiyon ve soru Android ekranında doğrulandı. Standart Hızlı emülatör p50/p95 452/516 ms. [Kanıt](release-validation-0.6.json).

v0.6.0/v0.6.1 aynı test sertifikasıyla yayımlandı; iki açık APK tekrar indirildi ve hash/boyutu doğrulandı. 0.5 ile imza farklı olduğundan eski sürümü kaldırmadan parolalı yedek gerekir. Kalıcı imza kurulumu ve fiziksel cihaz kabulü açık kalır. Bu çalışma dar yerel kuralları iyileştirir; gerçek modelin serbest mizah/muhakeme ve 2–3 saniye hedefi tamamlanmadı. [Serbest AI kabul ölçütü](AI_DIALOGUE_ACCEPTANCE.md).

## 0.7: gerçek çevrimiçi model için ücretsiz bağlantı

`869cb35` ve CI `40105882983`: 136 JVM, 18 Android yöntemi, 4 imza kontrolü, lint ve ARM64/R8 derlemeleri başarılı. İsteğe bağlı kişisel OpenRouter anahtarı gerçek Keystore ile ayrı şifrelenir; veri gönderimi açık izin ister. İlk soru da modele gider; yanıt ve puan yerel kalıpla değiştirilmez. İki sınırlı deneme aynı `openrouter/free`, sıfır fiyat tavanı ve toplam 15 saniye bütçesini kullanır. Kota/hata durumunda puan yazılmaz; oyuncu aynı hikâyeyle Hızlı modu seçebilir. [Bağlantı](FREE_ONLINE_AI.md), [kanıt](release-validation-0.7.json).

0.7.0/0.7.1 test APK'ları aynı sertifikayla yayımlandı, açık indirmeleri hash/boyutla doğrulandı. Önceki 0.6 imzası farklıdır; yedek gerekir. Üç yerel adayın tek gerçek örnekli native taramasında yeterli kalite/hız görülmedi; yeni model seçilmedi. Kişisel anahtar yokluğu nedeniyle canlı çevrimiçi Türkçe/mizah, muhakeme ve telefon gecikmesi henüz doğrulanmadı. 21 kontrollü API testi ve üç Android bağlantı testi gerçek model kalite kabulü değildir. Kalıcı imza, fiziksel cihaz testi, beş eşzamanlı aktif hikâye ve aktif Crashlytics açık kalır.

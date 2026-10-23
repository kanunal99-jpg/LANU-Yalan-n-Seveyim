# Fiziksel Android kontrolü

Bu belge çalıştırılabilir kontrol listesidir; fiziksel cihaz testi yapılmış olduğu anlamına gelmez. Ana cihaz Samsung A55; aynı kontroller Android tabanlı Huawei ve Xiaomi'de de uygulanır. Google Play Hizmetleri gerekmez. ARM64 Android 8+ APK kullanılır; `UI_SMOKE` emülatör paketi telefona dağıtılmaz.

1. Yeni kurulumda Hızlı oyun ile bir konu aç. Klavye açıkken gönder düğmesi ve güncel soru görünür kalmalı. Sistem yazı boyutu büyütülmüşken de dene.
2. Beş cevabı tamamla. Her turun son yanıt süresini ve Ayarlar → Bu cihazdaki süreler bölümündeki başarılı işlem sayısı, ortanca ve %95 değerlerini kaydet. Bu kart motor+kayıt süresidir; ekranın çizilme süresi ayrı ölçümdür. Hedef 2–3 saniyedir.
3. "Sabah 09:00'da evden çıktım" ardından "Sabah 08:30'da ofisteydim" de. Her iki saati içeren karşılaştırma ve önce/sonra kartı görünmeli. "Düzeltiyorum, sabah 07:30'da evden çıktım" açıklaması eski çelişkiyi yeniden cezalandırmamalı.
4. Başka hikâyede "Telefonum kapandı" ardından "Sonra müdüre mesaj attım" de. Bu boşluk açıklama istemeli. "Mesajı şarj bitmeden önce göndermiştim" cevabından sonra eski açık yeniden suçlama olmamalı. Telefonun neden kapandığını söylemediysen şarj bitmiş gibi kesin bir ayrıntı eklenmemeli.
5. Aynı konuyu yeni hikâyelerde yeniden dene. Son 40 espri kimliği korunur; havuzda alternatif varken aynı espri tekrar seçilmemeli. Sorular, yeni söylediğin ayrıntıyı takip etmeli.
6. Ayarlar'dan en az 12 karakterli parolayla şifreli yedek oluştur. Yanlış parola veya değiştirilmiş yedek mevcut ilerlemeyi değiştirmemeli. Doğru yedek seviye, en iyi puan, tercihler ve kayıtlı hikâyeyi geri getirmeli. Parola unutulursa yedek açılamaz.
7. OTA testini v0.4.0 üzerinde yap: Ayarlar → Güncellemeleri kontrol et → Güncellemeyi indir ve doğrula → Güncellemeyi kur. Android izin/kurulum ekranını onayla. v0.4.1 açıldığında hikâye ve seviyeler durmalı. İlk kurulum zaten v0.4.1 ise daha yeni paket bulunmaması beklenir.
8. İndirmeyi durdurup devam et; internet kapalıyken kontrol et. Açık hata, tekrar deneme ve yayınlar sayfası alternatifi çalışmalı. İmza uyuşmazlığında uygulamayı kaldırma; kurulum güvenli biçimde engellenmeli.
9. Gerçek AI ayrı denenir. Wi-Fi ile 2,74 GB model indirildikten sonra internet kapalıyken cevap ver. AI çıktısı üretilemiyorsa cevap korunmalı ve açık Hızlı oyun seçeneği görünmeli. Bu modun 30 saniyelik yükleme+üretim bütçesi vardır; fiziksel A55'te 2–3 saniye doğrulanmış değildir. Güç tasarrufu, bellek ve sıcaklık durumunu sürelerle birlikte kaydet.

Rapor: cihaz/Android sürümü, uygulama sürümü, Hızlı/AI modu, başarılı tur sayısı, ortanca/%95 ve karşılaşılan hata kodu yeterlidir. Özel hikâye metinleri veya yedek parolası tanılama için gerekli değildir.

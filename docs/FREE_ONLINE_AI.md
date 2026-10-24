# 0.7: ücretsiz çevrimiçi AI bağlantısı

## Kullanım

Oyna veya Ayarlar → Ücretsiz çevrimiçi AI → Ücretsiz anahtar sayfasını aç. OpenRouter hesabında kişisel bir API anahtarı oluştur; yalnız uygulamanın parola alanına gir. Konu/cevapların OpenRouter üzerinden model sağlayıcısına gönderileceğini kabul et ve “Doğrula ve şifreli kaydet” düğmesine bas. Anahtarı sohbete, depoya veya başka bir kişiye gönderme. Kredi satın almak bu mod için gerekli değildir. Hızlı oyun varsayılan ve çevrimdışı kalır.

Anahtarın doğrulanması yalnız hesap metadata isteğidir; henüz hikâye göndermez. Çevrimiçi modda yeni oyun başlatılınca ilk soruyu da gerçek model üretir. Ardından yalnız bu hikâyenin konusu, soruları ve oyuncu cevapları gönderilir. Yerel espri kalıpları modelin yazdığı cevap gibi sunulmaz. Modelin metni ve puanları değiştirilmez; doğrulama yalnız kabul/ret kararı verir.

## Ücretsiz sınırı

İstek modeli kodda `openrouter/free` olarak sabittir. Alternatif deneme de aynı ücretsiz yönlendiriciyi kullanır. `provider.max_price` için prompt/completion tavanı sıfırdır; ücretli modele veya keyfi sunucu adresine geçiş bulunmaz. Servis modeli kendisi seçer, kullanılabilirlik değişir. Ücretsiz istek kotası vardır; yoğunluk veya kota nedeniyle hizmet reddedilebilir. Yanıt için toplam 15 saniyelik bütçe, en çok iki deneme vardır. Bu bir 2–3 saniye garantisi veya sınırsız ücretsiz servis vaadi değildir.

Kaynaklar, 24 Ekim 2026: [Ücretsiz yönlendirici](https://openrouter.ai/docs/guides/routing/routers/free-router), [kota ve sınırlar](https://openrouter.ai/docs/api-reference/limits), [sağlayıcı tercihleri](https://openrouter.ai/docs/guides/routing/provider-selection). Dokümanlar doğrulandı; canlı üretim hesabı/anahtarı bu çalışma ortamında yoktur. Gerçek servis üzerinden model kalitesi ve cihaz hız testi tamamlanmadı.

## Hata ve veri koruma

İlk deneme JSON schema ister; desteklenmiyorsa/çıktı geçersizse en çok bir basit JSON alternatifine izin verilir. İki deneme de aynı 15 saniyelik bütçeyi paylaşır. Kimlik, kota veya sıfır dışı ücret fişinde otomatik yeniden deneme yapılmaz. Yanıt boyutu ve JSON derinliği sınırlanır. Bitmemiş çıktı, tekrar soru, gereksiz uzun cevap, yanlış puan ve modelin talimatla puan vermesi reddedilir. Red durumunda önceki kayıt ve yazılmış cevap korunur; puan uydurulmaz. Hızlı moda geçişi kullanıcı açıkça seçer ve aynı hikâye devam eder.

Kişisel anahtar Android Keystore AES-GCM ile `noBackupFilesDir` altında ayrı atomik dosyada saklanır. Oyun kaydına ve taşınabilir yedeğe girmez. Geri yüklemeden sonra yeniden bağlamak gerekir. HTTP yalnız sabit HTTPS kaynağına gider; yönlendirme ve otomatik OkHttp tekrarları kapalıdır. İptal aktif çağrıyı durdurur. Uygulama logları istek, konuşma veya anahtar içermez. Sağlayıcı isteğinde veri toplama izni `deny` seçilir; buna uygun ücretsiz sağlayıcı yoksa istek hata verir ve yerel fallback sunulur.

## Kabul kapsamı

Kontrollü JVM fixture testleri ücretli modele geçmeme, sıfır fiyat sınırları, kimlik/kota hataları, şema alternatifi, bozuk çıktı, tekrar soru, iptal, süre sınırı ve metin/puanın değiştirilmemesini denetler. Android testleri gerçek Keystore şifreleme/tahrifat reddi, veri gönderimi izni ve anahtar yokken aynı hikâyeyi Hızlı modda sürdürme akışını sınar. Bu fixture yanıtları gerçek bir servisin ürettiği espri veya kalite kanıtı değildir.

Üç yerel modelin tek alarm örneğindeki ham çıktıları [ayrı rapordadır](local-model-screening-20261024.json); bunlar kapsamlı kıyas veya telefon ölçümü değildir. Hiçbiri yeni varsayılan model yapılmadı.

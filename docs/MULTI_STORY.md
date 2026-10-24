# 0.8: beş bağımsız aktif konu

Oyna ekranındaki **Konularım** veya oyun cevap alanındaki **Konular** düğmesi, en fazla beş aktif hikâyeyi açar. Her hikâyenin kimliği, seviyesi, soru-cevap geçmişi, son puanı, oyun modu ve gönderilmemiş cevap taslağı ayrıdır. Yeni konu açmak devam eden hikâyeyi değiştirmez; önceki hikâye bekletilir. Beş aktif konu doluysa yeni konu açılamaz; bir hikâye açık kapatma onayıyla kaldırılmalıdır. Kazanılan seviyeler ve en iyi puanlar kapatma işleminden etkilenmez.

Tamamlanan sonuç seçiliyken görüntülenir. Başka hikâyeye geçince veya yeni konu açınca tamamlanan konuşma yerini bırakır; seviye ve en iyi puan korunur. Bu özellik sınırsız tamamlanmış konuşma arşivi değildir. Aynı seviyedeki iki hikâyeyi tamamlamak aradaki seviyeleri atlatmaz.

## Kayıt ve hata davranışı

Taslak, yazma durduktan yaklaşık 400 ms sonra şifreli kayıt için sıraya alınır. Ekranda “Taslak kaydediliyor…” / “Taslak kaydedildi” görünür. Hikâye değiştirme, yeni konu, normal gezinme ve cevap gönderme, taslak kaydını bekler. Uygulama arka plana geçince de kayıt istenir. İşletim sistemi işlemi kayıt tamamlanmadan zorla sonlandırırsa son tuşlar kaybolabilir; “kaydediliyor” durumu tamamlanmış kayıt sayılmaz.

Taslak yazımı başarısızsa hikâye değişmez ve yazılmış cevap ekranda kalır. Depolama düzeldikten sonra geçiş yeniden denenebilir. Arka plan taslak yazıları, yeni kayıt okuyucuları ve normal oyun kayıtları aynı süreç kilidinde sıralanır; eski bir yazı yeni hikâyenin üstüne yazamaz, yeni okuyucu devam eden atomik yazıyı silemez. Disk yazımı ile buna karşılık gelen arayüz değişikliği aynı iptal edilemeyen işlemde tamamlanır. Atomik dosyanın yeniden adlandırılması bazı Android sürümlerinde yalnız sistem günlüğüne hata yazabildiği için yazılmış baytlar tekrar okunup karşılaştırılır; doğrulanmamış yazı tamamlanmış sayılmaz. Model yanıtı başarısızsa taslak ve mevcut tur korunur; puan uydurulmaz.

Ana hikâye eski `session` alanında kalır; en fazla dört devam eden hikâye `parkedSessions` alanındadır. Yeni isteğe bağlı alanların varsayılanları eski tek hikâyeli kayıtları kabul eder. Tüm bekleyen hikâyeler, taslaklar, kimliklerin tekilliği, soru sırası, sınırlar ve puanlar kayıt/yedek öncesinde doğrulanır. Bozuk kayıt sessizce boşaltılmaz. Önceki bir uygulama sürümüne geri dönmek yeni alanları kaybettirebilir; beş konulu yedeği 0.8 veya daha yeni sürümle aç.

Beş uzun konuşma için taşınabilir yedek içeriği 1 MiB, dosya okuma üst sınırı 2 MiB'dir. Aynı içerik sınırı cihaz kaydında da şifreleme ve atomik yazımdan önce uygulanır; sonradan okunamayacak kadar büyük bir kayıt yazılmaz. Format ve parola şifrelemesi değişmez, eski küçük yedekler okunabilir. Kişisel AI bağlantı anahtarı bu dosyalara eklenmez.

## AI bağlamının ayrılması

Aynı anda yalnız seçilen hikâyenin bir yanıtı üretilir. Diğer dört hikâye için arka planda API isteği veya model üretimi yapılmaz. İstek oluşturucuya yalnız seçilen `Session` ve açıkça gönderilen cevap verilir; diğer konular veya gönderilmemiş taslaklar AI bağlamına eklenmez. Her hikâyenin puanlama modu geçişte korunur. Eksik yerel model veya çevrimiçi anahtar varsa açık Hızlı oyun seçimi yalnız seçili hikâyeyi değiştirir.

## Doğrulama kapsamı

Yeni JVM kontrolleri; beş hikâyede tur/mod/taslak koruma, altıncı konuyu reddetme, kapatma, bitmiş sonuç sonrası devam, aynı seviyenin iki kez açılmaması, eski tek hikâyenin okunması ve tüm bekleyen içeriklerin doğrulanmasını kapsar. AI istek denetimi beş konudan yalnız seçili konunun gönderildiğini kontrol eder. Gerçek parolalı yedek testleri beş taslağı ve eski tek hikâye boyut sınırını geçen uzun kayıtları içerir.

Yeni AOSP kontrolleri, beş hikâyede gerçek arayüz geçişi, Activity yeniden oluşturma, Keystore kaydı ve parolalı yedek; kapasite sınırı ve vazgeç/onay ile kapatma; kasıtlı atomik yazım hatasında cevabın ve seçimin korunması ile yeniden denemeyi sınar. Gerçek APK yükseltme fixture'ı da beş hikâye ve ayrı taslaklar taşır. Son çalıştırma sonuçları yayın kanıtına kaydedilir; test yazılmış olması geçmiş sayılmaz.

Bu çalışma AI'nın Türkçe/mizah kalitesi kabulü değildir. Canlı çevrimiçi model için kişisel anahtar bu ortamda yoktur; A55/Huawei/Xiaomi fiziksel cihaz hızı ve kalıcı imza hâlâ ayrıca doğrulanmalıdır.

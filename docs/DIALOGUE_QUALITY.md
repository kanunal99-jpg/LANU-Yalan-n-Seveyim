# 0.3.0 cevap özgüllüğü

Önceki Hızlı oyun, cevabı alıntılayıp benzer bir espri ve genel bir ayrıntı sorusu döndürüyordu. Yeni akış oyuncunun söylediği neden, saat, süre, araç, kişi, yer ve iletişim izlerini çıkarır. Sorulan sorgu açısı kayda eklenir; aynı açı sonraki turda ve kayıt geri açıldığında atlanır. Hikâye kimliği espri çeşidini sabitler; yeni hikâyeler farklı çeşit seçebilir.

Telefon/şarj, alarm, uyku, trafik, araç arızası, ödev, bağlantı, sağlık, hava, para ve buluşma için ayrı sorgular var. Beş seviye bandı önce olay sırasını, sonra bilginin kaynağını, alternatifleri ve neden-sonuç savunmasını zorlaştırır. Bilinmeyen konularda genel olay/sonuç sorgularına dönülür. Bu, sınırlı Türkçe kurallarıdır; serbest metnin anlamını bütünüyle anlayan bir model değildir.

## Üretilen örnekler

Tam kayıt: [dialogue-fast-results.json](dialogue-fast-results.json). Bu örnekler JVM testinde gerçek `FastGame.evaluate` çıktılarından dışa aktarılır.

| Oyuncunun söylediği | LANU yorumu | Takip sorusu |
| --- | --- | --- |
| Telefonumun şarjı bitti, o yüzden arayamadım. | Şarj yüzde sıfır, bahane yüzde yüz; telefon senden önce mesaiyi bırakmış. | Telefon tam hangi anda kapandı; o sırada neredeydin? |
| Alarmım çalmadı, uyuyakaldım. | Alarm da seninle yıllık izne çıkmış anlaşılan; mesainin bundan haberi yok. | Alarm hangi saate kuruluydu; çalmadığını nasıl fark ettin? |
| Önce sabah 09:00 çıkış, sonra sabah 08:30 varış | Çıkış 09:00, varış 08:30; aynı günse sıra ters. Servis yerine zaman makinesi mi geldi? | 09:00 çıkış ile 08:30 varış aynı gün mü; hangi saati düzeltiyorsun? |

Açıklanan araç aktarması, başka gün ve açık saat düzeltmesi eski çelişkiyi kaldırır. Kapalı telefon ile mesaj gönderme tek başına kanıtlanmış çelişki sayılmaz; mesajın hangi sırada gönderildiği sorulur. Kullanıcı söylemediyse lastik, şarj veya tanık uydurulmaz. Kısa ama somut “Alarm çalmadı” cevabı, yalnızca kısa olduğu için belirsiz sayılmaz. Kimliğe veya aileye hakaret üretilmez.

## Gerçek AI

Qwen'e aynı ipuçlarının sınırlı bir özeti, önceki sorular, aday sorgu açısı ve olaya bağlı espri başlangıcı verilir. Oyuncu verisi sistem mesajına taşınmaz; sohbet kontrol belirteçleri temizlenir. Verilen ipuçları model için kesin kanıt değildir. Model farklı bir ifade üretebilir ve hâlâ yanılabilir. Puan talimatı olarak tanınan cevaplarda dört puan da JSON dilbilgisiyle sıfıra zorlanır; normal cevapların puanları modelden gelir.

`scripts/dialogue_smoke.py`, JVM testinin dışa aktardığı **üretimdeki gerçek prompt ve dilbilgisi** ile sabit GGUF üzerinde yedi sentetik senaryo çalıştırır. Anahtar sözcük/puan kontrolleri mizah kalitesini kanıtlamaz; ham yanıtlar ayrıca okunmalıdır. Host süreleri telefon hızını göstermez. Hızlı oyunun üç saniyelik UI kapısı, gerçek AI için bir süre garantisi değildir.

**Güncel stil denemesi:** Yeni yönergeye iki kısa Türkçe örnek eklendi. Telefon ve alarm için iki gerçek model çıktısı [dialogue-ai-style-probe.json](dialogue-ai-style-probe.json) içinde. Alarm yorumunda istenen espri görülüyor; telefon yorumunda fazladan soru, alarmın takip sorusunda ters neden-sonuç ifadesi kalıyor. İki otomatik JSON/konu kontrolü geçse de **modelin mizah/muhakeme kalitesi onaylanmadı**. Bu çıktıların tamamı yedi senaryolu yeni bir kabul çalıştırması olarak sunulmaz. Cihazda AI deneysel kalır; bu sürümün belirgin soru/espri düzeltmesi Hızlı oyunda kurallarla doğrulanır.

## Kontroller

Yerel 37 JVM testi ve lint geçti; test APK'sı derlendi. Bunlar 100 seviyede tam tur, farklı bahane dalları, beş zorluk bandı, yeni hikâyede espri çeşitleri, tekrar sormama, ters saatler, bekleme süresi, açıklanan çelişki, olumsuzlama, kısa somut cevap ve puan talimatı kontrollerini içerir.

Yayın iş akışı ayrıca native ARM64 debug/R8 release derlemesi ve Google hizmetsiz AOSP emülatöründe beş UI testi çalıştırır: gezinme, büyük yazı/ayarlar, klavyeyle beş yanıt ve üç saniye sınırı, iki ayrı telefon sorgusu, iki saatin ekranda karşılaştırılması. Yayın yalnız bu işlerin başarılı olmasına bağlıdır. Fiziksel Samsung/Huawei/Xiaomi kontrolleri henüz yapılmadı.

# Cevaba bağlı itiraz ve görünür espri

Kullanıcı geri bildirimi: mantıksız yanıt verildiğinde bile sıradaki düz soru geliyor, cevap içindeki açık esprili biçimde yakalanmıyor.

İki neden bulundu. Hızlı oyunda olağan dışı iddia yalnız makullük puanını etkiliyor, soru seçimini durdurmuyordu. Zaman/araç çelişkisinin hazır üç sorgu açısı tüketilince genel sorulara geçilebiliyordu. Ayrıca oyun ekranı otomatik kaydırmada son yorumu atlayıp yalnız yeni soruyu gösteriyordu.

## Değişen davranış

- Gündelik hikâyedeki açık ışınlanma, uçan halı/ejderha/zaman makinesiyle ulaşım, uzaylı kaçırması ve hayvanın ödev/rapor hazırlaması iddiaları ayrı itiraz oluşturur. Bunlar yalan ispatı olarak sunulmaz; iddia alıntılanıp açıklama istenir.
- Tek yolculukta açık yürüyüş/koşu, mesafe ve süre birlikte verildiyse hız hesaplanır. Örneğin 100 km / 2 dakika = 3000 km/sa; espri ve soru bu hesaba bağlanır. Normal yürüyüş/koşu, başka araç, çok etaplı yolculuk veya belirsiz hesap otomatik mahkûm edilmez.
- İtiraz, oyuncu konu değiştirdiğinde veya başka bir olağan dışı iddia eklediğinde kaybolmaz. Espri değişebilir; soru aynı çözümlenmemiş iddiayı savunmaya veya düzeltmeye yönelir.
- İlgisiz saat, kişi ve kelime sayısı kritik açığı kapatmaz. Çözümlenmemiş bu iddiayla veya açık çelişkiyle son puan geçme eşiğinin altında kalır. Beşinci cevapta yeni soru yerine açık bir son değerlendirme verilir.
- Somut bir düzeltme/geri alma kabul edilir. Yalnız “şakaydı” demek veya ilgisiz bir saati düzeltmek yeterli değildir. Açık mecaz, olumsuzlama, rüya ve fantastik konu günlük fizik iddiası sayılmaz.
- Otomatik kaydırma son yorum ve takip sorusunu aynı grupta açar. Puan gerekçeleri aşağıda açılabilir kalır.

Bu denetim, sınırlı ve açıklanabilir yerel kurallardır. Her Türkçe cümleyi veya her mantık hatasını anladığı iddia edilmez. Yeni kapsamın uç durum testleri `ClaimReviewTest` içindedir; üretimden gelen örnek metinler CI'da `reports/story-quality/claim-review.json` olarak kaydedilir.

## Gerçek AI yolu

Aynı düşülebilir yerel itiraz, oyuncunun sözü ve önerilen sorgu modeli yönlendiren kayda eklenir. Prompt, açık mantık sorununu ele almadan yeni konuya geçmemeyi ister. AI'nın metni veya puanı kurallarla değiştirilmez. Tanınan itirazı yüksek makullük/savunma puanıyla ödüllendiren veya onu bırakıp ilgisiz soru soran çıktı reddedilir. Modelin yeni kalitesi ve gecikmesi ayrıca ham üretimle ölçülmelidir; yerel testin geçmesi AI'nın iyi komedyen olduğunu kanıtlamaz.

Native tanılama, önceki 11 vakayı korur; ışınlanma, hız hesabı, somut geri alma ve mecazla toplam 15 örnek kullanır. Bunlar sentetik vaka kayıtlarıdır; gerçek oyuncunun tam sohbeti değildir. AI hâlâ deneysel ve telefonlarda 2–3 saniye hedefi henüz doğrulanmış değildir.

## Yayın doğrulaması

Kaynak `7dad1ff` için [Android CI 40022367843](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/actions/runs/40022367843) derleme ve AOSP işleri geçti. Genişletilen son mantık kodunda 77 odaklı JVM testi ayrıca geçti; bu, tüm Android kontrollerinin son kaynakta koştuğu iddiası değildir. Yeni Android testi, yorumla sorunun kaydırmadan birlikte görünmesini ve sonraki ilgisiz cevapta itirazın sürmesini doğrular.

0.5.0/0.5.1 test ikilisi bu değişikliğin yeni paketleridir. JVM, Android görünürlük/ilerleme, gerçek paket yükseltmesi, lint, ARM64/R8 derleme ve açık indirme/imza sonuçları tamamlandığında ayrı kanıt kaydı eklenir. Kalıcı imza yapılandırılmadıkça eski 0.4 test anahtarıyla yerinde güncelleme varsayılmaz; önce parolalı yedek alınmalıdır.

## Son yayın sonucu

0.5 test ikilisi yayımlandı: 102 JVM testi, 14 ayrı Android yöntemi ve 4 imza kontrolü geçti. İki turda yorum ve sorunun kırpılmamış sınırları cevap alanının üstünde doğrulandı. [Yayın kanıtları](release-validation-0.5.json), [gerçek AI sınırları](AI_NATIVE_REVIEW_0.5.md). AI tarafındaki denetimler bazı bozuk çıktıları reddeder; genel muhakeme ve mizah sorunu çözülmüş değildir.

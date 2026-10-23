# Native AI: 23 Ekim 2026 değerlendirmesi

**Sonuç: Modelin Türkçe, muhakeme ve mizah kalitesi onaylanmadı.** On bir gerçek model üretimi tamamlandı; otomatik kontroller yalnız **5/11** geçti. Geçen örneklerde de olayı tersine çeviren veya doğal olmayan metinler var. İşin GitHub'da yeşil tamamlanması kalite onayı değildir; diyalog adımı tanılama için `continue-on-error` kullanır.

[Çalıştırma 40014129908](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/actions/runs/40014129908), kaynak commit `26fb0c5d82455b5136c0cf71345b5d75d9cbf9c6`, artifact `12528820677`. Ham üretimler değiştirilmeden [dialogue-native-results.json](dialogue-native-results.json), motor kontrolleri [native-ai-invariants.json](native-ai-invariants.json) olarak kopyalandı. Tarihsel sunucu raporu [dialogue-ai-results.json](dialogue-ai-results.json) ayrı tutuldu.

## Süre ve kapsam

| Ölçüm | x86 host sonucu |
| --- | ---: |
| Örnek sayısı | 11 |
| p50 | 44,74 saniye |
| p95 | 52,92 saniye |
| En kısa / en uzun | 40,01 / 53,45 saniye |
| On bir turun toplamı | 512,01 saniye |
| İlk model yüklemesi, ayrıca | 1,79 saniye |

p95, sıralı 11 gözlemde doğrusal ara değerle hesaplandı; en yakın sıra yöntemiyle p95 53,45 saniyedir. Küçük ve sentetik bu örneklem genel performans dağılımı değildir. Tur süreleri model yüklemesini içermez. Ortam: AMD EPYC 7763 tabanlı 4 mantıksal CPU / 2 fiziksel çekirdekli x86_64 GitHub VM, yaklaşık 16 GB RAM; üretim dört iş parçacığıyla çalıştı. Bunlar **telefon veya A55 ölçümü değildir**.

Her host turu uygulamadaki yükleme dahil **30 saniyelik** bütçeden uzundu. Tanılama bütçesi 120 saniyeydi; dolayısıyla bu koşu, uygulamanın AI cevabını kendi bütçesinde tamamlayacağını doğrulamaz. Fiziksel cihaz hızı ve 30 saniye sınırına uyum hâlâ ölçülmedi.

Modelin dosya hash'i ModelStore ile aynı. Rapordaki beş AI/native/prompt kaynak hash'i inceleme başladığında çalışma alanındaki kaynaklarla bire bir eşleşti. Bu rapor `26fb0c5` sürümündeki **ham model üretimini**, sonradan eklenen Kotlin AI kabul denetiminden önce ölçer. Kabul denetimi model metnini veya puanlarını iyileştirmez; uygun olmayan çıktıyı `OUTPUT` hatasıyla reddeder. Bu 11 ham cevabı yeni denetimle kabul edilmiş cevaplar gibi değerlendirmemek gerekir. Bağlam ve örnekleme, Android JNI ile ortak `generation.cpp` üzerinden kullanıldı. Bütün ham çıktılar geçerli altı alanlı JSON ve 0..25 aralığında puanlar içeriyor; bu, metnin doğru olduğunu göstermiyor.

## On bir cevabın ayrı okunması

| Vaka | Otomatik | Türkçe ve muhakeme | Espri ve takip sorusu |
| --- | --- | --- | --- |
| Telefon / şarj | Geçti | Şarj bilgisini koruyor; tutarlılık 20. Soru “Telefonun ne yaptığını, o sırada neredeydin?” biçiminde dilbilgisel olarak eksik. | Telefonun önce mesaiyi bırakması olaya bağlı, kullanılabilir bir başlangıç; yerel espri ipucundan geliyor. Takip sorusu düzgün cümleye dönüşmüyor. Tam kalite onayı yok. |
| Alarm | Geçti | İkinci cümlede “uykunun sesini duymak” anlamsız bir varsayım. “Çalmadı” ifadesini soruda “susmadığını” yaparak tersine çeviriyor. | İlk espri doğal bir yerel başlangıç. Eklenen açıklama espriyi ve sorunun mantığını bozuyor. |
| Trafik | Geçti | “Senin bahanesi” gibi bozuk Türkçe var. Oyuncu navigasyon kullandığını söylemedi. Açık çelişki olmamasına rağmen tutarlılığı 14'e indiriyor. | 25 dakika ve Kadıköy kelimelerini koruması yeterli değil; soru somut rota seçimini açmak yerine kendi gerekçesini döndüren bir cümleye dönüşüyor. |
| Ödev | Geçti | “Sözlü ödev” ve kâğıdın yokluğu oyuncunun ifadesinde yok. “Sen de defter yok” ve “kâğıtla konuşmama neden” doğal Türkçe değil. | “Ödevi sözlüye kaldırdık” espri ipucunu gerçek görev türü gibi kullanmış görünüyor. Takip sorusu bu uydurulan durumun üzerine kuruluyor. |
| Ters saatler | Kaldı | 09:00 çıkış / 08:30 varışı fark ediyor fakat açıklanmamış ters sıraya **20 tutarlılık** veriyor. Soru “nasıl imkânsız oldu?” diyerek düzeltmeyi araştırmıyor. | Yorumun içine ek soru koyuyor; evden/ofisten yönünü gereksiz yere değiştiriyor. Espri ve değerlendirme birbiriyle tutarlı değil. |
| Tutarlı otobüs | Kaldı | Tutarlılık 22 ile hikâyeyi kabul ediyor; metin ise yama yapma ve geri dönme seçeneklerine sapıyor. “Otomobüslere” kullanımı doğal değil. | Yorum iki soruya dönüşüyor; `question` alanı soru olmayan bir cümle. Oyuncunun söylediği 15 dakika / yedek otobüs bağlantısını izlemiyor. |
| Puan talimatı | Kaldı | Dilbilgisi dört puanı da **0** tutuyor; bu koruma çalışıyor. Metin, oyun hikâyesine dönmek yerine unutulan/yeni talimatları sorguluyor. | Yorumda tekrar soru var. Bahane üzerine bir espri ve işe yarar bir oyun sorusu üretilmiyor. |
| Düzeltilen saat | Kaldı | Tutarlılık 19 ile düzeltmeyi kabul ediyor. Ancak “3 saat” uyduruyor ve 07:30 evden çıkışı “eve 07:30” diye bozuyor. Bir saatlik aralık zaten verilmişken süresini tekrar soruyor. | Yorumdaki tekrarlanan sorular espri oluşturmuyor; düzeltilmiş kaydı doğru özetleyemiyor. |
| Açıklanan mesaj sırası | Kaldı | **En belirgin muhakeme hatası:** Şarj bitmeden mesaj göndermeyi, telefonun sonra kapanmasıyla çelişki sayıyor. Tutarlılık 12; söylenmemiş “planı tamamlama” iddiası ekliyor. | `reply` ile `question` tamamen aynı üç cümlelik suçlama. Gerçek bir takip sorusu veya espri yok. |
| Yeni kahve dökülmesi | Geçti | Oyuncu “dosyanın üstüne döküldü” diyor; model neden üstüne değil kenarına döküldüğünü soruyor. Ayrıca “dosya yok” diyor. Açık ifadenin tersini kullanıyor. | Kahve kelimesinin bulunması otomatik kontrolü geçiriyor. Yorum büyük ölçüde tekrar; soru olaya bağlı görünürken temel bilgiyi değiştiriyor. |
| Beşinci / final turu | Kaldı | Dilbilgisi `question` alanını boş bırakıyor. Ancak `reply` içinde **iki yeni soru** soruyor; beş yanıtın nihai değerlendirmesini yapmıyor. | Final esprisi veya kapanış hükmü yok. Altıncı soruyu yalnız alan düzeyinde engellemek, yorum içine taşınmasını önlemiyor. |

Bu tablo ham cevapların insan tarafından okunmasıdır; otomatik puanlar veya metinler düzeltilmedi. Özellikle ödev örneğinde, yerel bir esprideki mecazı oyuncunun gerçek ifadesi gibi kullanması olası bir hata kaynağıdır. Bu, ilgili prompt ve çıktının karşılaştırılmasından yapılan çıkarımdır; tek başına tüm hataların nedenini açıklamaz.

## Motor doğrulamaları

Üç kontrolün tamamı geçti:

- Zorlanan `{"sentinel":9173}` dilbilgisi aynen çıktı; **10** dilbilgisi geri dönüşüyle reddetme yolu gerçekten çalıştı.
- Aşırı uzun bağlam `LANU_AI_CONTEXT` verdi.
- Aynı motor, hatadan sonraki üretimde `{"again":42}` döndürdü; **7** dilbilgisi geri dönüşü kaydedildi.

Bunlar örnekleme/dilbilgisi, bağlam sınırı ve hata sonrası toparlanma kanıtıdır. Türkçe, çıkarım, espri veya fiziksel cihaz performansı onayı değildir. Bu sonuçlarla cihaz içi AI **deneysel** olarak kalmalıdır; iyi JSON üretmesi, iyi dedektif veya iyi komedyen olması anlamına gelmiyor.

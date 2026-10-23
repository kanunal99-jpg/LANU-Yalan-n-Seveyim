# Yerel espri ipucunun AI tarafından yanlış okunması

Kaynak `7dad1ff2bba1ffdc7ac13eb999db4bbc3b6ea529`; [native koşu 40022338457](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/actions/runs/40022338457), artifact `12532480853`. Bu, espri taslağını `punchline_seed` alanında veren yönergenin ölçümüdür. On beş ham üretim tamamlandı; otomatik kontrol **3/15**, model kalitesi **onaylanmadı**. Otomatik geçen telefon cevabı anlamsız tekrar içeriyor; alarm cevabı oyuncu söylemediği hâlde alarmı kurmadığını varsayıyor. Puan talimatı örneğinde dört sıfır korunuyor ancak oyun sorusu doğal değil.

Host p50 **51.06 saniye**, doğrusal p95 **59.13 saniye**, aralık 47.22–60.61 saniye. Yükleme ayrıca ölçüldü; bütün turlar uygulamanın yükleme dahil 30 saniyelik bütçesini aştı. Bunlar x86 GitHub host süreleridir, A55 veya başka telefon ölçümü değildir. Motorun üç sınır/toparlanma kontrolü geçti; bu diyalog onayı sayılmaz.

## Somut hata

Oyuncu “100 km yolu 2 dakikada yürüdüm” diyor. Yerel espri taslağında “ayakkabı değil hızlı tren ihalesi” benzetmesi var. Model, iddiayı bir “hızlı tren ihalesiyle ilgili yazım hatası” olarak açıklıyor; olmayan olayı gerçek bilgiye dönüştürüyor ve 75/100 veriyor. Alarm ve kahve vakalarında da sözleri tersine çeviriyor; telefon/mesaj onarımında yorumla soru aynı metne dönüşüyor. Final yorumunun içinde yeni soru var. Mecaz örneğinde mesafe verilmediği hâlde 120 km/sa uyduruyor ve bu normal otobüs hızını fizik ihlali sayıyor.

Espri taslağının olaya karışması, prompt ile çıktı karşılaştırmasından görülen bir hata yoludur; tüm hataların tek nedeni olduğu iddia edilmez. Bu nedenle sonraki adayda taslak olay kaydından çıkarılır, tek bağımsız Türkçe üslup örneği ve kısa yönerge denenir. Yorumdaki soru işareti dilbilgisiyle sınırlanır; bunun mantığı düzelttiği varsayılmaz. Yeni aday ayrıca ölçülmeden kalite onayı almaz.

[Değiştirilmemiş ham üretimler](dialogue-native-results-0.5-seeded.json), [motor sınır kontrolleri](native-ai-invariants-0.5-seeded.json).

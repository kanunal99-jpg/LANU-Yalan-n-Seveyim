# AI 0.5 değerlendirmesi — 24 Ekim 2026

**Gerçek AI'nın Türkçe, mantık ve mizah kalitesi onaylanmadı.** Temiz olay kaydı adayı 15 ham üretimi tamamladı; otomatik kontroller **11/15** geçti. Bunun bir bölümü yorumdaki soru işaretinin dilbilgisiyle engellenmesidir. Soru işareti olmadan soru cümlesi veya anlamsız cümle hâlâ üretilebiliyor; 11/15 bir genel doğruluk veya komedi puanı değildir.

Kaynak `61e767b28eb3363bdb6f4dffe8a9592389c55efc`, [native koşu 40025408447](https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim/actions/runs/40025408447), artifact `12532379843`. Yayımlanan kaynak `5f2cff8` ile dışa aktarılan prompt/dilbilgisi fixture dosyasının SHA-256 değeri bire bir eşleşti. Sonraki farklar yerel yorumun kısaltılması, AI sonrası kullanılmayan yerel espri kimliğinin kaydedilmemesi ve ekran testleridir. Modelin metni ve puanları değiştirilmeden [ham rapor](dialogue-native-results-0.5.json) korunur.

Önceki espri taslağı modeli “hızlı tren ihalesi” benzetmesini gerçek olay sanmaya yöneltebiliyordu. Bu taslak vaka kaydından çıkarıldı; kısa yönerge, bağımsız bir Türkçe üslup örneği ve yorumda soru işaretini engelleyen dilbilgisi denendi. Önceki 3/15 sonucu [ayrı kayıttadır](AI_SEEDED_REVIEW.md). Yöntemler farklı olduğu için otomatik artış tek başına daha iyi muhakeme kanıtı değildir.

| Vaka | Otomatik | Ham cevabın anlamı ve üslubu |
| --- | --- | --- |
| Telefon | Geçti | Şarj kapanmasını söylenmeyen kalkma saatine bağlıyor; Türkçesi bozuk. |
| Alarm | Kaldı | “Çalmadı” ifadesini “çalarken” diye tersine çeviriyor; soru soru işaretiyle bitmiyor. |
| Trafik | Geçti | 25 dakika beklemeyi doğrudan 25 dakika geç varmak sayıyor; döngüsel soru ve zayıf espri. |
| Ödev | Geçti | Defterin evde kalmasını izliyor; anlatım yapay, mizah ve sorgu sınırlı. |
| Ters saat | Geçti | Ters sırayı ve düşük toplamı yakalıyor fakat 30 dakikayı “iki saat” diyor. |
| Tutarlı otobüs | Geçti | Otobüsün neden olmadığı zaten verilmişken tekrar soruyor; yorum anlaşılmaz. |
| Puan talimatı | Geçti | Dört sıfır korunuyor; oyun sorusu ve Türkçe doğal değil. |
| Düzeltilmiş saat | Kaldı | Düzeltilmiş tutarlı sıraya 12 tutarlılık veriyor; uygulama kabul denetimi bunu reddeder. |
| Açıklanmış mesaj | Geçti | Sırayı kabul ediyor; ancak oyuncunun söylemediği “rapor”u bağımsız örnekten taşıyor. |
| Kahve | Geçti | Dosyanın ıslanmadığını ve kâğıt olmadığını uyduruyor. |
| Final | Geçti | Soru alanı boş ve puan aralığı doğru; kapanış yorumu anlamsız. |
| Işınlanma | Kaldı | İddiayı yeterince sorgulamıyor; yüksek makullük kalıyor. |
| Gerçek dışı hız | Geçti | 100 km / 2 dakika yürümeyi reddediyor ve sıfır veriyor; önceki ihale bilgisi taşınmıyor. Espri sınırlı. |
| Geri alınan iddia | Geçti | Düzeltmeye yüksek tutarlılık veriyor; yorum hâlâ bozuk bir soru cümlesi. |
| Mecaz | Kaldı | Mecazı yine fizik iddiası sanıyor; yorumla soruyu tekrar ediyor. |

Bu nedenle AI deneysel kalır. Biçim, tanınan açık çelişki, toplam puan ve konuya bağlı soru denetimleri bazı hataları reddeder; genel Türkçe doğruluk denetimi değildir. Özellikle kabul edilen kahve veya rapor cevabının doğru olduğu varsayılamaz. Yerel Hızlı oyunun testlerini gerçek AI kalitesine mal etmiyoruz.

Host p50 **39.29 saniye**, doğrusal p95 **45.57 saniye**, aralık **34.66–47.84 saniye**; yükleme ayrıca ölçülür. Bütün host turları uygulamanın yükleme dahil 30 saniye bütçesinden uzundur. Bunlar telefon ölçümü değildir; A55 için 2–3 saniye hedefi doğrulanmadı. [Native sınır/toparlanma kontrolleri](native-ai-invariants-0.5.json) ayrı motor kanıtıdır.

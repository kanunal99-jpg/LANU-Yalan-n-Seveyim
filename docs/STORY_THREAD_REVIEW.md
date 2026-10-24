# 0.6: oyuncunun hikâyesini takip etme ve tekrar döngüsü

24 Ekim 2026, İstanbul. Kullanıcının gerçek ekranlarında 0.5'in hâlâ aynı “sis makinesi” yorumunu ve önceki soruyu iç içe alıntılayan bir takip sorusunu tekrarladığı görüldü. Önceki 0.5 testleri bu konuşmayı kapsamıyordu; geçmiş testlerin başarılı olması bu hatanın çözüldüğü anlamına gelmiyordu.

## Referans ve amaç

Kullanıcının verdiği [En Yalancı Benim mağaza sayfası](https://play.google.com/store/apps/details?id=com.yalanmakinesi.app) incelendi. Geliştiricinin açıklaması hikâyeye özel dinamik AI soruları ve cevaplar arası tutarlılık vaat ediyor. Uygulama oturum açılarak veya oynanarak incelenmedi; gerçek iç cevapları görülmüş gibi sunulmuyor. LANU'nun hedefi, kullanıcının tarif ettiği günlük Türkçe, cevaptan çıkan mizah, önceki ayrıntıya geri gönderme ve hikâyenin açıklarını araştırma davranışı.

## Düzeltme

- `AnswerRetry`: Kaçamak, tekrar veya puan talimatında sorunun tamamı yeni sorunun içine alıntılanmaz. Açık kalan zaman, kişi, neden, ulaşım gibi başlık kısa bir soruya indirilir. Cevapsız başlık korunur; reaksiyonlar da espri hafızasına kaydolur. Eski kayıttaki iç içe soru bir sonraki turda büyümez.
- `StoryThread`: Yolculuk konusu baştan takip edilir. Birbirine bağlanmayan duraklar, menüye geçiş, gerçek gerekçe, belirsiz plan değişikliği ve açık iptal ayrılır. Yeni şehir önceki yolculuk boşluğunu silmez. Eksik bağlantı kesin yalan/çelişki diye etiketlenmez. Son turda altıncı soru sorulmaz.
- Mars gibi uzay yolculuğu konu olarak seçildiyse hikâyenin kurgu dünyası kabul edilir. Ulaşım ve olay sırası bu dünya içinde sorgulanır; oyuncu yalnız uçuk bir başlangıç seçtiği için baştan elenmez. Gündelik mazeretteki olağan dışı açıklamalar için mevcut dar itiraz kuralları korunur.
- “Canım sıkılmıştı” neden sorusuna gerçek bir gerekçe olarak tanınır. Yer ve ulaşım soruları ayrı değerlendirilir. Verilmiş bir nedenin yerine “hiç ayrıntı vermedin” yorumu yazılmaz.
- Özel yer adlarının ekleri, `Hatay da` / `Newyork` yazımları ve cümle başındaki `Sonra` ayrı ele alınır. Bilinen birkaç şehir/gezegen, Türkçe yer ekli özel isim ve dar ulaşım/yemek sözlükleri kullanılır.

## Gerçek konuşma ve doğrulama

`StoryThreadTest` kullanıcının dört cevabını yazım hatalarıyla aynen yeniden oynatır; beşinci cevap bu rapora uydurulmaz. Çıktı `build/reports/story-quality/user-screenshot-replay.json` dosyasında oluşur. Farklı hikâyeler, açıklanmış rota, kesin iptal/belirsiz karar değişikliği, eski açık korunması, kayıt sonrası devam, sıradan/mecaz kullanım ve eski iç içe sorudan çıkma da sınanır.

Yerel bağımsız Kotlin/JUnit koşusunda 76 domain testi geçti. Bu koşu Android veya tüm Gradle testleri yerine sunulmaz. Yeni Android yöntemi dört cevabı gerçek ekran üzerinden girer; her turda reaksiyon ve sorunun girişin üstünde bütünüyle görünmesini ölçer, dört ekran görüntüsü alır. Tam Gradle/lint/ARM64/R8, AOSP ve gerçek paket yükseltme sonuçları yayın koşusunda ayrıca doğrulanacaktır.

## Sınır

Bu değişiklik Hızlı oyunun etiketlenmiş yerel kurallarıdır. Serbest üretim yapan AI veya her Türkçe cümleyi anlayan bir sistem değildir. Sözlük dışındaki kişiler, yemekler, olaylar ve karmaşık imalar aynı düzeyde yorumlanamaz. Daha çok kalıp eklemek tek başına istenen sınırsız dedektif/komedyen muhakemesini sağlamaz. Mevcut Qwen modelinin kalite/hız sorunu çözülmüş sayılmaz; bu değişiklik için yeni bir gerçek model kalite onayı yoktur. Ücretli servis veya çevrimiçi sağlayıcı eklenmedi.

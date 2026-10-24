# Serbest AI diyaloğu için kabul ölçütü

24 Ekim 2026. Kullanıcı referansı: [En Yalancı Benim](https://play.google.com/store/apps/details?id=com.yalanmakinesi.app). Mağaza açıklaması ve yayımlanmış dört ekran görüntüsü incelendi; ekranlar ana sayfa, konu girişi, profil ve lider tablosu gösteriyor. Oyun içi gerçek cevaplar bunlarda yer almadığından rakip uygulamanın mizahı veya teknik altyapısı doğrulanmış gibi sunulamaz. Beklenen ton kullanıcının kendi tarifidir.

0.6'daki yerel hikâye/tekrar düzeltmesi serbest AI kabulü değildir. Mevcut gerçek Qwen için 0.5 ham incelemesindeki dil, muhakeme ve hız açıkları devam eder. Yeni model veya çevrimiçi seçenek için aşağıdaki davranışlar ayrı değerlendirilmelidir.

| Ölçüt | Başarılı davranış | Başarısız davranış |
| --- | --- | --- |
| Son cevabı okuma | Oyuncunun verdiği yeni gerekçeyi kabul edip eksik bağlantıyı sorar | Verilen nedeni yok sayıp “hiç ayrıntı yok” der |
| Hikâye hafızası | İlk iddiaya ve son ayrıntıya gerektiğinde birlikte döner | Her mesajda hikâyeyi yeniden başlatır |
| Konudan sapma | Değişen yer, kişi veya amaç için hangi bağlantının eksik olduğunu söyler | Aynı “somut ayrıntı” kalıbını her cevaba uygular |
| Belirsizlik | Plan iptali ile gerçekleşmiş seyahati; yer adı ile marka adını ayırmak için sorar | Sırf bir kelime geçti diye yeni bir gerçek üretir |
| Düzeltme | Açıkça düzeltilen iddiayı emekliye ayırır | Açıklanmış çelişkiyi tekrar tekrar cezalandırır |
| Kurgu | Uçuk başlangıcı oyun dünyası içinde sınar | Her fantastik konuyu baştan kaybettirir |
| Mizah | Oyuncunun ayrıntısından kısa, doğal ve farklı bir espri çıkarır | “Bahane”, “dosya” veya “sis makinesi” cümlesini değişmeden dolaştırır |
| Kültürel ton | Dolmuş, esnaf lokantası, gündelik konuşma gibi göndermeleri bağlama uyduğu kadar kullanır | Her hikâyeye aynı kültürel klişeyi ekler; kişiye veya kimliğe saldırır |
| Soru | Cevapsız tek noktayı yeni bir sorgu açısından açar | Önceki soruyu tırnak içine alıp yeni soru sanır |
| Puan | Tutarlılık, makullük, yararlı ayrıntı ve asıl sorunun cevabına dayanır | Uzun metin, rastgele isim veya sayılarla otomatik yüksek puan verir |
| Model dürüstlüğü | Ham model sonucu ve reddedilen sonuç ayrı tutulur | Yerel metni AI üretmiş gibi gösterir |

Kabul örnekleri yalnız Mars/Hatay veya sabit test konularından seçilmemeli. Daha önce kullanılmamış gündelik, fantastik, yanlış yazılmış ve kısa cevaplı konuşmalarla en az 20 ayrı çok turlu örnek insan tarafından okunmalı. JSON veya tek soru işareti kontrolünün geçmesi, Türkçe/mantık/mizah kabulü yerine sayılmaz. Uydurulan gerçek, haksız suçlama ve talimatla puan yükseltme ayrı hatalardır.

Hız hedefi, cevap gönderme anından kullanılabilir yorum ve sorunun bütünü ekrana gelene kadardır. Sıcak/soğuk başlangıç ve model yüklemesi ayrı raporlanır; A55, Huawei ve Xiaomi fiziksel ölçümleri emülatör/CI host ölçümleriyle karıştırılmaz. 2–3 saniye yalnız gerçek ölçümle kabul edilir. Çevrimiçi seçenek kurulursa ücretsiz kota, sınır dolduğundaki davranış, gizli anahtarın sunucuda korunması ve ücretli çağrının varsayılan kapalı olması somutlaştırılmadan “tamamen ücretsiz sınırsız” sözü verilmez.

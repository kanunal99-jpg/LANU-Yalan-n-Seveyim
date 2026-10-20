# Android uyumluluk kapsamı

Marka veya model adına göre kısıt yoktur. Hedef Samsung, Android tabanlı Huawei, Xiaomi ve diğer üreticilerin Android cihazlarıdır. Çalışma kararı üretici adından değil Android sürümü, ABI, fiziksel RAM ve kullanılabilir işlemci çekirdeği sayısından alınır.

| Alan | Kapsam |
| --- | --- |
| Telefon APK | Android 8.0 / API 26 ve üzeri, arm64-v8a / 64 bit Android |
| Yapay zekâ | Aynı Qwen3.5 4B modeli CPU üzerinde; üretici GPU/NPU servisi gerektirmez |
| Bellek | 8 GB RAM önerilir. Daha düşük kapasitelerde hız ve çalışabilirlik doğrulanmadı |
| İşlemci kullanımı | En fazla 4 çekirdek; 6 GiB altı fiziksel bellekte en fazla 2; kullanılabilir çekirdek sayısı aşılmaz |
| Bellek baskısı | Android düşük bellek bildiriyorsa model açılışı kullanıcı dostu mesajla ertelenir |
| Google servisleri | Oyun, kayıt ve model indirme için GMS/Play Store veya üretici hesabı zorunlu değil |
| Tanılama | Firebase konfigürasyonu yoksa Firebase/Crashlytics runtime bağımlılıkları APK'ya eklenmez |
| İlk indirme | Yaklaşık 2,74 GB; sabit kaynak, SHA-256 kontrolü, kesintiden devam |
| Kayıt | Android Keystore ve standart Android API'leri; üreticiye özel güvenlik SDK'sı yok |

## Otomatik doğrulama

Normal CI gerçek ARM64 debug ve R8 release APK'sını derler. Cihaz kaynak politikası JVM testleriyle doğrulanır.

Ayrı bir AOSP Android 11 / API 30 x86_64 emülatöründe Google Play Hizmetleri paketinin bulunmadığı adb ile kontrol edilir. Açılış, seviye kilitleri ve 1,3 yazı ölçeğiyle ayarlar/cihaz bilgisi ekranı test edilir. Bu test için -Planu.uiSmoke=true kullanılır: native model kitaplığı dahil edilmez, model yükleme bu test paketinde açıkça engellenir ve paket yayımlanmaz. Bu kontrol yalnızca uygulama açılışı ve arayüzün GMS gerektirmediğini gösterir; ARM64 model çıkarımı veya OEM ROM davranışı testi değildir.

Alfa yayını normal ARM64 derleme ve AOSP UI testinin ikisinin de başarılı olmasını bekler.

## Fiziksel cihaz matrisi

| Üretici / sistem | Durum |
| --- | --- |
| Samsung / One UI, 64 bit Android | Hedef; fiziksel test bekliyor |
| Huawei / Android APK destekleyen EMUI veya HarmonyOS | Hedef; GMS olmadan fiziksel test bekliyor |
| Xiaomi / HyperOS veya MIUI, 64 bit Android | Hedef; fiziksel test bekliyor |
| Diğer üreticiler / 64 bit Android | Aynı standart API yolu; fiziksel test bekliyor |

Her satırda model indirme/kalıcı kayıt, uçak modu, ekran boyutu ve yazı ölçeği, klavye, tam beş soruluk tur, RAM, ısınma, pil ve yanıt süresi ölçülmelidir. Emülatör testi bu satırları geçti olarak işaretlemez. Android'in 32 bit sürümü bu model/APK için desteklenmez.

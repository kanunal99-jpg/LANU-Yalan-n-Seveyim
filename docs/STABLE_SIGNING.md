# Kalıcı imza ve güncelleme sürekliliği

Güncel test ikilisi v0.8.0 (kod 13) / v0.8.1 (kod 14) durumunu belgenin son bölümünde bulabilirsin. Aşağıdaki eski sürüm açıklamaları tarihsel kayıttır.

OTA güveninin kökü telefonda kurulu uygulamanın sertifikasıdır. Yalnız aynı paket, daha yüksek sürüm kodu, doğru boyut/hash ve aynı mevcut imza kabul edilir. Otomatik kaldırma veya imza kontrolünü atlama yolu yoktur. Android kullanıcının kurulum onayını ister.

v0.4.0-alpha (kod 5) ile v0.4.1-alpha (kod 6) aynı CI çalıştırmasında aynı anahtarla üretilir. APK paket/sürümü ve iki sertifikanın eşitliği yayından önce kontrol edilir; her release kendi `lanu-update.json`, SHA256SUMS ve `signing-info.json` dosyasını içerir. `permanentSigning` değeri, kalıcı anahtarın kullanılıp kullanılmadığını açıklar.

Kalıcı anahtar yapılandırılmamışsa bu iki APK test anahtarı kullanır. İkili arasındaki yükseltme mümkündür, başka CI çalıştırmasının test anahtarına geçiş garanti edilmez. v0.3 ve daha eski farklı test imzalı APK'lar yeni imzayla yerinde güncellenemez. Eski sürümü kaldırmak onun yerel ilerlemesini siler; bu otomatik yapılmaz. Parolalı yedek özelliği v0.4'te eklenmiştir; v0.3'te uygulama içi taşınabilir yedek yoktur.

## Güvenilir kendi bilgisayarında veya Termux'ta kurulum

`gh`, `keytool` ve `openssl` gerekir. GitHub CLI, ilgili depoya secret yazma yetkili hesabınla açık olmalıdır. Bağlı GitHub uygulamasının mevcut araçları repository secrets yazma işlemi sunmadığı için bu adım kullanıcı hesabında çalıştırılır.

```sh
git clone https://github.com/kanunal99-jpg/LANU-Yalan-n-Seveyim.git
cd LANU-Yalan-n-Seveyim
bash scripts/bootstrap_stable_signing.sh
```

Depo zaten varsa önce o klasörde `git pull --ff-only` yap. Termux araçları eksikse `pkg install gh openjdk-17 openssl` kullan. Betik, özel anahtarı ve rastgele parolayı yalnız kendi cihazındaki özel klasörde tutar; hiçbir özel anahtar kaynak koda veya yayın artifact'ına eklenmez. Anahtar veya parola yarım kalmışsa sessizce yeni anahtar üretmez. Çıktıdaki özel klasörü güvenli, ayrı bir konuma yedeklemek gerekir.

Beş secret: `LANU_KEYSTORE_BASE64`, `LANU_STORE_PASSWORD`, `LANU_KEY_ALIAS`, `LANU_KEY_PASSWORD`, `LANU_CERT_SHA256`. CI bunları geçici özel dizinde açar. Gradle anahtar/parola/sertifika hashini doğrular. Kısmi yapılandırma veya sertifika uyuşmazlığı yayını durdurur; debug imzasına sessiz dönüş yoktur. `-Planu.requireProductionSigning=true` hiçbir anahtar yokken de derlemeyi durdurur.

Kurulumdan sonraki ilk kalıcı imzalı APK eski test sertifikasından farklı olabilir. O geçişten önce v0.4 üzerindeki ilerlemeyi parolalı yedekle koru. Bir kez kalıcı sürüme geçildikten sonra aynı anahtar saklandığı ve sürüm kodları arttığı sürece sonraki OTA yayınları aynı güven zincirini kullanır.

## Yayımlanan 0.5 ikilisinin durumu

v0.5.0-alpha (kod 7) ve v0.5.1-alpha (kod 8), `5f2cff8` kaynağından aynı test sertifikasıyla yayımlandı: `3383f8091c836cc4ce3168979ceca9f68534291f5cc030f6543cbb032acac375`. `permanentSigning=false`. 0.5 ikilisi içinde OTA mümkündür; bu sertifika önceki 0.4 ikilisinin test sertifikasından farklıdır. 0.4 üzerine doğrudan yerinde kurulum yapılamaz. Önce 0.4 içinden parolalı yedek oluştur; eski uygulamayı yedeksiz kaldırma. Kalıcı anahtar kurulumu bundan sonraki yayınların sürekliliği için hâlâ gereklidir. [İndirme ve doğrulama kaydı](ota-release-verification-0.5.json).

## Yayımlanan 0.6 ikilisinin durumu

v0.6.0-alpha (kod 9) ve v0.6.1-alpha (kod 10), `03166b3` kaynağından aynı test sertifikasıyla yayımlandı: `b9cc707e16796c5d7152a703b28a55e9b2f7ee9cfd93f164fae0641b2b45ac56`. `permanentSigning=false`. İki açık APK indirildi ve hash/boyutu doğrulandı. 0.6 ikilisi içinde OTA mümkündür; 0.5 sertifikası farklı olduğu için 0.5 → 0.6 yerinde güncellenemez. Önce eski uygulamanın Ayarlar ekranından parolalı yedek oluştur; yedeksiz kaldırma. Gelecek CI yayınlarının aynı imzayı koruması için yukarıdaki kalıcı anahtar kurulumu hâlâ gereklidir. [Doğrulama kaydı](ota-release-verification-0.6.json).

## Yayımlanan 0.7 ikilisinin durumu

v0.7.0-alpha (kod 11) ve v0.7.1-alpha (kod 12), `869cb35` kaynağından aynı test sertifikasıyla yayımlandı: `92ac5830a73babd14fd31b12a3831d87a99db8cbbbdce418fa991a752a1d78fb`. Her biri 25.678.502 bayt; iki açık indirme tekrar doğrulandı. `permanentSigning=false`. 0.7 ikilisi içinde OTA mümkündür; 0.6 sertifikası farklı olduğundan 0.6 → 0.7 yerinde güncellenemez. Eski uygulamadan önce parolalı yedek oluştur; silmek ilerlemeyi ve varsa modeli kaldırır. Yeni kişisel AI anahtarı taşınabilir ilerleme yedeğine alınmaz; yeniden kurulumdan sonra uygulama içinde yeniden bağlanır. [Açık APK ve imza kaydı](ota-release-verification-0.7.json). Kalıcı imza kurulumu hâlâ gereklidir.

## Yayımlanan 0.8 ikilisinin durumu

v0.8.0-alpha (kod 13) ve v0.8.1-alpha (kod 14), `18c03e6` kaynağından aynı test sertifikasıyla yayımlandı: `a2183f340a80a0c1acd5958cdc4136c98c8b672c4a5d4f2f852633af6ce976d6`. Her biri 25.694.886 bayt. İki açık APK ayrıca HTTP 200 ile indirildi; hash/boyut ve GitHub asset özeti eşleşti. `permanentSigning=false`. 0.8 ikilisi içinde OTA mümkündür; 0.7 sertifikası farklı olduğundan 0.7 → 0.8 yerinde kurulamaz. Önce eski uygulamanın Ayarlar ekranında parolalı yedek oluştur. Kaldırma yerel ilerlemeyi ve varsa modeli siler. Yeni sürümde yedeği geri yükle, kişisel AI anahtarını yalnız uygulamada yeniden bağla. Kalıcı imza için yukarıdaki güvenilir cihaz/Termux adımı hâlâ gereklidir. [Doğrulama kaydı](ota-release-verification-0.8.json).

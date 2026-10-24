# Kalıcı imza ve güncelleme sürekliliği

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

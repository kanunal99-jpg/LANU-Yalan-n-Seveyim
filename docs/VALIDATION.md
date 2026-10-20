# Doğrulama kaydı

Bu belge tamamlanan testlerle cihazda hâlâ yapılması gereken kontrolleri ayırır.

## Kaynak doğrulamaları

- GGUF kaynağının sabit revizyonu: daeb8e2d528a760970442092f6bf1e55c3b659eb.
- İndirilen 1.282.439.264 baytlık dosyanın gerçek SHA-256 özeti kaynakla eşleşti: d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5.
- llama.cpp sabit revizyonu ve arşiv SHA-256 doğrulaması CMake içinde sabitlenmiştir.
- Gradle dağıtım SHA-256 doğrulaması wrapper içinde sabitlenmiştir.

## Otomatik kontroller

Derleme ve test çıktıları doğrulandıkça buraya sonuç eklenir. GitHub Actions kalite raporları ayrıca workflow artifacts içinde saklanır.

## Fiziksel Samsung A55 kabul kontrolleri — henüz yapılmadı

1. APK kurulumu, ilk açılış, büyük yazı tipi ve klavye açıkken cevap kutusu.
2. Wi-Fi ile model indirme, duraklatma, ağ kesintisi, uygulama kapanması ve devam etme.
3. İndirme sonrası uçak modunda beş soruluk tam oyun.
4. Türkçe konuya bağlı soru, somut çelişki yakalama ve argo ayarı.
5. Beş turun gecikme, RAM, ısınma ve pil ölçümleri.
6. Ekran döndürme ve işlem sonlandırma sonrasında kayıtlı turun geri gelmesi.
7. Puan yetersizken kilidin açılmaması, başarınca sonraki seviyenin açılması.
8. İptal, bozuk dosya, düşük boş alan ve model kaldırma akışları.

## Üretim öncesi eksikler

- Kalıcı özel imzalama anahtarı ve güvenli CI secrets.
- A55 üzerinde uçtan uca smoke test, ekran görüntüsü ve hız ölçümü.
- Türkçe modelin çeşitli konulardaki mizah/puanlama kalite değerlendirmesi.
- İstenirse Firebase Crashlytics gerçek proje yapılandırması ve onaylı tanılama.
- OTA henüz uygulanmadı.

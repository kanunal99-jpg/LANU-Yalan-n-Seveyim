# Cihaz içi AI doğrulaması

Güncel AI metni ve puanları GGUF modelinden gelir. Yerel kurallar yalnızca ayrı etiketlenen ipuçları, önerilen sorgu açısı ve espri başlangıcı sağlar. Başarısız üretimin yerine kurallardan cevap veya puan konmaz. Model üretmediği için AI sonuçlarına yerel puan gerekçeleri eklenmez.

## Aynı üretim yolu

Android JNI ve masaüstü `lanu-ai-probe`, aynı `app/src/main/cpp/generation.cpp` dosyasını derler. Tokenizasyon, 3072 token bağlam, 256/128 batch, JSON dilbilgisi, seçilen tokenın kabulü, örnekleme ve JSON tamamlanma sınırı ortaktır. Yalnız masaüstü tanılama bütçesi farklıdır: uygulamada model yüklemesi dahil 30 saniye; tanılamada varsayılan 180 saniye. Host tur süreleri model yüklemesini içermez; yükleme ayrıca kaydedilir.

Örnekleme: top-k 20 → top-p 0,8 → sıcaklık 0,5 → seed 42. Min-p ve tekrar cezası yoktur. Aynı sayısal puanı iki ölçütte vermek veya hikâyenin bir kelimesini tekrarlamak geçerlidir. Önce tek aday dilbilgisiyle kontrol edilir; reddedilirse tüm sözlüğe maske uygulanır ve yeniden örneklenir. Son aday da doğrulanır. `llama_sampler_apply` kabul etmediği için token her örnekleyicide bir kez kabul edilir. Bu yol, sabitlenen llama.cpp sürümünün `common/sampling.cpp` reddetme stratejisine dayanır.

Model: ModelStore'daki sabit Qwen3.5-4B-Q4_K_M; SHA-256 `00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4`. Llama.cpp: `d8d92973c8c21bb4dd5d592f0fec26fe2cd1e654`.

## Yeniden çalıştırma

Önce `AiFixtureExportTest` çalıştırılır. Bu, üretimdeki `PromptComposer.compose` ve `GameEngine.grammarFor` çıktılarıyla `app/build/reports/ai-quality/fixtures.json` oluşturur. On bir sentetik kayıt; ilk yedi örneğe ek olarak düzeltilmiş saat, açıklanmış telefon/mesaj sırası, yeni kahve dökülmesi ve beşinci/final turunu içerir. Bunlar modelin baştan sona oynadığı konuşmalar değildir.

```sh
cmake -S app/src/main/cpp -B build/ai-probe -DCMAKE_BUILD_TYPE=Release
cmake --build build/ai-probe --target lanu-ai-probe -j 4
python3 scripts/dialogue_smoke.py \
  --probe build/ai-probe/lanu-ai-probe \
  --model /path/to/verified/qwen35-4b-q4.gguf \
  --fixtures app/build/reports/ai-quality/fixtures.json \
  --output docs/dialogue-ai-results.json
```

Rapor ham model metnini/puanlarını, token sayılarını, dilbilgisi geri dönüş sayısını, kaynak/probe/model hashlerini ve her kontrolü saklar. Model hash uyuşmazsa çalıştırma durur. Başarılı JSON/anahtar sözcük kontrolleri doğal Türkçe, sağlam muhakeme veya iyi mizahı kanıtlamaz; ham cevaplar ayrıca okunmalıdır. `quality_approved` otomatik olarak açılmaz.

## Ölçülmüş sınır ve güncel durum

Önceki sunucu tabanlı rapor, JNI ile farklı tekrar geçmişi ve örnekleme sırası kullanıyordu; güncel native davranışını doğrulamaz. Bu yüzden eski 29–39 saniye host değerleri yeni JNI yolunun hız kanıtı değildir.

Kaynaklar korunmadan önce yapılan kontrollü native başlangıç ölçümünün ilk beş turu 49,7 / 52,7 / 67,6 / 59,2 / 84,2 saniyeydi. Bu başlangıç yolunda eski İngilizce yönerge, dilbilgisini baştan tüm sözlüğe uygulama, 1,08 tekrar cezası ve 0,65 sıcaklık vardı. Trafik, ödev ve ters saat örneklerinde model yorumun içinde tekrar soru sordu; alarm sorusu neden-sonuç ilişkisini bozdu. Bu kısmi ölçüm güncel Türkçe yönergenin veya iyileştirilen örneklemenin başarısı sayılmaz.

Çalışma alanı sıfırlanmadan önce paylaşılan native çekirdek/probe derlendi; bağımsız Kotlin/JVM çalıştırmasında prompt, dilbilgisi ve dışa aktarma için sekiz test geçti. Tam 11 örneklik yeni native koşusu ve fiziksel Android AI ölçümü tamamlanmadı. Kaynaklar geri yüklendi; yeni model kalite/hız onayı hâlâ bekliyor. Önceki `dialogue-ai-results.json` tarihsel sunucu çıktısıdır. Cihaz içi AI deneysel olarak sunulmalıdır.

## Kalıcı CI çalıştırması ve kısmi rapor

Yerel çalışma alanının sıfırlanması model koşusunu kesebildiğinden tam ölçüm GitHub Actions işi olarak çalıştırılır. Gradle, host derlemesi ve model üretimi sırayla tamamlanır; model bellekteyken Gradle başlatılmaz. İndirilen GGUF hem 2740937888 bayt boyutuyla hem ModelStore SHA-256 değeriyle doğrulanır. Üretimdeki URL:

```text
https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/e87f176479d0855a907a41277aca2f8ee7a09523/Qwen3.5-4B-Q4_K_M.gguf?download=true
```

Yalnız AI fixture ve prompt/dilbilgisi testlerini dışa aktarmak için:

```sh
./gradlew --no-daemon -Planu.uiSmoke=true :app:testDebugUnitTest \
  --tests com.lanu.yalan.ai.AiFixtureExportTest \
  --tests com.lanu.yalan.ai.PromptComposerTest \
  --tests com.lanu.yalan.ai.EngineGrammarTest --stacktrace
```

`dialogue_smoke.py` her tamamlanan gerçek model cevabından sonra raporu atomik olarak kaydeder. Bütün seçili vakalar bitene kadar `complete=false` ve `passed=false` kalır. `expected_cases`, `expected_case_names` ve `completed_cases` kısmi ölçümün sınırını belirtir. Kesilen bir koşunun kısmi başarısı tam kalite onayı gibi gösterilmez. Yeni kahve dökülmesi örneğinde takip sorusunun kahve/dökülme/ıslanma/leke/sıvı/masa ayrıntılarından birini izlemesi ayrıca kontrol edilir.

Ayrı `scripts/native_invariant_smoke.py`, modelin istediği alandan farklı sabit bir JSON dilbilgisi zorlayarak reddetme yolunu gerçekten çalıştırır; 5000 token üzerindeki bağlamın sınır hatası vermesini ve ardından aynı motorun yeni üretime dönebilmesini denetler. Sabit JSON burada motor testi içindir; diyalog puanı veya model mizahı testi değildir.

```sh
python3 scripts/native_invariant_smoke.py \
  --probe "$RUNNER_TEMP/lanu-ai-build/lanu-ai-probe" \
  --model "$RUNNER_TEMP/qwen35.gguf" \
  --output reports/native-ai-invariants.json
```

CI, kalite kontrolü başarısız olsa da `reports/` ve `app/build/reports/ai-quality/` klasörlerini `if: always()` ile artifact olarak yüklemelidir. Yeni kaynaklarla tamamlanmış 11 örneklik rapor henüz elde edilmedi; komutların hazırlanması ölçüm sonucu yerine geçmez.

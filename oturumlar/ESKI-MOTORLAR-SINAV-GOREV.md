# İŞÇİ GÖREVİ — Eski Motorların Uzman Sınavı (Lig · Düz İsviçre · Direkt Puanlama)

Sen bu görevi alan İŞÇİ oturumsun. Koordinatör aynı anda ViewModel + yeni
motorlarda (HIBRIT, EMRE_SIRALAMA) çalışıyor — **ANA KODA DOKUNMA**, yalnız
`app/src/test/` altına YENİ test dosyaları ekle. Kusur bulursan DÜZELTME,
testle belgele + rapora yaz; düzeltme kararını koordinatör verir.
(Tek istisna: bulduğun kusur test eklemeden gösterilemeyecek kadar açık bir
çökme ise bile düzeltme yapma — çökmeyi üreten testi yaz, @Ignore'suz bırak
ve rapora "KIRMIZI test bilerek bırakıldı" diye yaz.)

## Bağlam
- Motorlar: `ranking/RankingEngine.kt` (Lig + Direkt Puanlama + eleme yardımcıları),
  `ranking/SwissSystem.kt` (düz İsviçre, 59 mevcut test var).
- Mevcut testler: `app/src/test/java/com/example/ranking/` — önce OKU,
  kapsananı tekrarlama; boşlukları doldur.
- Koşturma: `./gradlew testDebugUnitTest --tests "*SeninSinifin*"`.

## Sınav başlıkları

### A) LİG (RankingEngine.createLeagueMatches + calculateLeagueResults)
- Circle-method fikstür: n çift ve TEK için herkes herkesle TAM BİR kez;
  turda takım tekrarı yok; tur sayısı n-1 (tek n'de n tur / bye).
- Çift devre (doubleRoundRobin): tam iki kez, ev-deplasman ayrımı varsa tutarlı.
- Puanlama: 3/1/0 + averaj zinciri — skorlu ve skorsuz maç karışımında
  averaj hesabı doğru mu; eşit puan + eşit averajda sıralama deterministik mi.
- matchNumber ataması: oylama sırası anlamlı mı (round ASC, matchNumber ASC).
- Bozuk veri: yabancı winnerId (üçüncü takım), mükerrer maç kaydı, silinmiş
  takımın maçı → sonuç çökmemeli, kalan takımlar tutarlı sıralanmalı.

### B) DÜZ İSVİÇRE (SwissSystem)
- Bye adaleti UZUN koşumda: n=9, 15 gibi tek sayılarla tam turnuva —
  bye dağılımı (min/max fark ≤ 1?) ölçülüp yazılsın.
- Tekrar eşleşme yasağı: tüm koşumda hiçbir çift iki kez (assert).
- recommendedRoundCount sınır davranışı: n=2,3,4 uçları.
- Geri izleme: kasıtlı zor senaryo (küçük n, çok tur) MAX_BACKTRACK'e
  çarpıyor mu; çarptığında davranış dürüst mü (sessiz yanlış eşleşme YOK).
- computeState determinizmi: aynı maç listesiyle iki çağrı birebir aynı state.
- Bozuk veri seti (A ile aynı üçlü) SwissSystem'e de uygulanır.

### C) DİREKT PUANLAMA (createDirectScoringResults)
- Eşit puanlar: sıralama deterministik ve belgeli mi.
- Puan aralığı dışı değerler (negatif, >100) ne oluyor.
- Hiç puanlanmamış öğe sonuçta nerede.

### D) ÇAPRAZ TUTARLILIK
- Aynı senaryoda RankingEngine.calculateLeagueResults ile ViewModel'in
  canlı puan tablosu MANTIĞI (RankingViewModel.calculateCurrentStandings'in
  Lig dalı — SADECE OKU, kopyalama testi yaz) aynı sırayı veriyor mu?
  Formüller iki yerde ayrı yazılmış; ayrışıyorsa bu ciddi bulgudur, rapora.

## Rapor
`oturumlar/ESKI-MOTORLAR-SINAV-RAPOR.md`: her başlık için ölçüm (rakam),
bulunan kusurlar (ÖNEM sırasıyla, vaka + hangi test gösteriyor), önerilen
düzeltmeler (koordinatöre). Yeşil/kırmızı test sayısı net yazılsın.
Bitince 3 beep: `powershell -c "[Console]::Beep(800,300); [Console]::Beep(800,300); [Console]::Beep(800,300)"`

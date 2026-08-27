# TESPİH — Ranking Pro turnuva sistemleri

> kurulum: 2026-08-28 · koordinatör: ranking-a3 [7558ae]
> Emre'nin talebi: "sistemi lig usulü, eleme usulü, İsviçre sistemi,
> geliştirilmiş İsviçre sistemi gibi sistemler ile donat"

## Ölçüm — bugünkü gerçek hâl (kendim saydım)

| sistem | durum | ölçülen kusur |
|---|---|---|
| MERGE_SORT (İkili Karşılaştırma) | 🟢 sağlam | oturum kaydı yoktu — düzeltildi (c2a0dd9) |
| EMRE_CORRECT (Gelişmiş İsviçre) | 🟢 çalışır | 7 kritik kusur düzeltildi (c2a0dd9) |
| LEAGUE (Lig) | 🟡 çalışır, kırılgan | yetim maçta `!!` → NPE; averaj/tiebreak zayıf; standings dalı boş |
| SWISS (İsviçre) | 🔴 KIRIK, UI'dan gizli | bye YOK (tek takım sessizce düşer) · tekrar eşleşme SERBEST · matchNumber hiç atanmaz · tur-1 rastgele |
| ELIMINATION (Eleme) | 🔴 KIRIK, UI'dan gizli | `getGroupSongs` İKİNCİ kez shuffled() → grup üyeliği maçlarla alakasız · pozisyonlar ÇAKIŞIYOR · puanlama ekranı stub |

## Sıra — ölçüt: FAYDA ÷ EMEK

| # | iş | keskinlik | hedef | fayda / niçin bu sırada |
|---|-----|-----------|-------|------------------------|
| 1 | SWISS motoru yeniden | %25 | %95 | 🔓 Emre'nin ADIYLA istediği sistem; kural ihlalleri turnuvayı geçersiz kılıyor |
| 2 | ELIMINATION motoru yeniden | %20 | %95 | 🔓 Emre'nin ADIYLA istediği sistem; şu an sonuç tablosu çakışık |
| 3 | LEAGUE sağlamlaştırma | %70 | %95 | çalışıyor ama NPE'ye açık; averaj/tiebreak eksik |
| 4 | Motor testleri | %30 | %90 | 🔓 üç motorun doğruluğu ancak testle savunulur |
| 5 | Eleme fikstür/bracket UI | %0 | %85 | eleme motoru bitince gösterilecek ekran gerek |
| 6 | Entegrasyon + kalan 18 bulgu | %0 | %90 | koordinatörde — paylaşılan dosyalar |

## Dosya sahipliği — ÇAKIŞMA YOK

Motorlar YENİ dosyalar olarak yazılır (mevcut mimari zaten böyle:
EmreSystemCorrect.kt, PairwiseComparisonSort.kt ayrı motorlar).
RankingEngine.kt / RankingViewModel.kt / RankingScreen.kt KOORDİNATÖRDE.

| kıta | sahip olduğu dosyalar |
|---|---|
| SWISS MOTORU | ranking/SwissSystem.kt · test/SwissSystemTest.kt |
| ELEME MOTORU | ranking/EliminationSystem.kt · test/EliminationSystemTest.kt |
| LIG MOTORU | ranking/LeagueSystem.kt · test/LeagueSystemTest.kt |
| MOTOR TESTLERİ | test/EmreSystemCorrectDeepTest.kt · test/PairwiseDeepTest.kt |
| ELEME EKRANI | ui/screens/ranking/BracketView.kt · ui/screens/ranking/GroupStandingsView.kt |
| koordinatör | RankingEngine.kt · RankingViewModel.kt · RankingScreen.kt · entegrasyon |

## Durum

- [x] Ölçüm yapıldı
- [x] Kritik 7 kusur düzeltildi + commit (c2a0dd9)
- [ ] ① SWISS motoru
- [ ] ② ELEME motoru
- [ ] ③ LIG motoru
- [ ] ④ Motor testleri
- [ ] ⑤ Eleme ekranı
- [ ] ⑥ Entegrasyon (koordinatör)

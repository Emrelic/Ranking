package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.RoundResult
import com.example.ranking.utils.LiveStandings
import com.example.ranking.utils.RankingEntry
import com.example.ranking.utils.RoundData
import com.example.ranking.utils.SwissFixtureData
import com.example.ranking.utils.SwissFixtureSerializer
import com.example.ranking.utils.SwissStateSerializer
import org.junit.Assert.*
import org.junit.Test

/**
 * ARŞİV/SERİALİZER BÜTÜNLÜK SINAVI — görev: koordinatör ranking-7d (kendini
 * ranking-0c diye tanıtıyor), oturumlar/ARSIV-SINAV-RAPOR.md'de özetlenir.
 *
 * Kapsam: SwissStateSerializer + SwissFixtureSerializer JSON gidiş-dönüş
 * (serialize → deserialize → birebir eşitlik). Yalnız OKUNDU, hiçbir üretim
 * dosyasına dokunulmadı (ORTAK.md sınırı).
 *
 * DURUM: `saveSwissState`/`loadSwissState` (RankingRepository.kt:320-343) ve
 * `saveSwissFixture` (RankingRepository.kt:425-434) RankingViewModel.kt'de
 * ("SWISS" yöntemi seçili turnuvalarda) hâlâ ÇAĞRILIYOR. CLAUDE.md bu yöntemi
 * "UI'dan gizlenmiş" diye işaretlemiş — normal kullanıcı akışından ULAŞILAMAZ,
 * ama kod canlı ve gelecekte yöntem yeniden açılırsa devreye girer. Bu yüzden
 * kayıplar aşağıda "🔴 KUSUR" değil "belgeleme_" (bilinen, ulaşılamaz ama
 * gerçek kayıp) olarak işaretlendi.
 */
class SwissJsonSerializerRoundTripTest {

    // ================= SwissStateSerializer =================

    @Test
    fun standings_bosMap_gidisDonusKorur() {
        val empty = emptyMap<Long, Double>()
        val back = SwissStateSerializer.deserializeStandings(SwissStateSerializer.serializeStandings(empty))
        assertEquals(empty, back)
    }

    @Test
    fun standings_tipikVeri_gidisDonusBirebir() {
        val standings = mapOf(1L to 2.5, 2L to 0.0, 3L to 13.0, 999_999_999L to 4.5)
        val back = SwissStateSerializer.deserializeStandings(SwissStateSerializer.serializeStandings(standings))
        assertEquals(standings, back)
    }

    @Test
    fun standings_negatifVeOndalikliPuanlarKorunur() {
        // Motor negatif puan üretmez ama serileştirici JSONObject.getDouble
        // kullanıyor — sınırları da ölçelim.
        val standings = mapOf(1L to -1.5, 2L to 0.3333333333, 3L to 0.0)
        val back = SwissStateSerializer.deserializeStandings(SwissStateSerializer.serializeStandings(standings))
        assertEquals(standings, back)
    }

    @Test
    fun pairingHistory_bosSet_gidisDonusKorur() {
        val empty = emptySet<Pair<Long, Long>>()
        val back = SwissStateSerializer.deserializePairingHistory(SwissStateSerializer.serializePairingHistory(empty))
        assertEquals(empty, back)
    }

    @Test
    fun pairingHistory_siraliCiftlerFarkliKayitOlarakKorunur() {
        // Pair(1,2) ile Pair(2,1) FARKLI kayıtlardır (Pair sırayla eşitlik kurar);
        // serileştirici bu sırayı bozmamalı.
        val history = setOf(1L to 2L, 2L to 1L, 3L to 4L)
        val back = SwissStateSerializer.deserializePairingHistory(SwissStateSerializer.serializePairingHistory(history))
        assertEquals(history, back)
    }

    @Test
    fun pairingHistory_buyukTurnuva_64TakimTumIkilikorunur() {
        val history = (1..64L).flatMap { a -> (a + 1..64L).map { b -> a to b } }.toSet()
        assertEquals(2016, history.size) // 64*63/2
        val back = SwissStateSerializer.deserializePairingHistory(SwissStateSerializer.serializePairingHistory(history))
        assertEquals(history, back)
    }

    @Test
    fun roundHistory_bosListe_gidisDonusKorur() {
        val back = SwissStateSerializer.deserializeRoundHistory(SwissStateSerializer.serializeRoundHistory(emptyList()))
        assertEquals(emptyList<RoundResult>(), back)
    }

    @Test
    fun roundHistory_beraberlikVeKazananWinnerIdKorunur() {
        val rounds = listOf(
            RoundResult(
                roundNumber = 1,
                matches = listOf(
                    Match(id = 10, listId = 1, rankingMethod = "SWISS", songId1 = 1, songId2 = 2, winnerId = 1, round = 1, isCompleted = true),
                    Match(id = 11, listId = 1, rankingMethod = "SWISS", songId1 = 3, songId2 = 4, winnerId = null, round = 1, isCompleted = true) // beraberlik
                ),
                pointsThisRound = mapOf(1L to 1.0, 2L to 0.0, 3L to 0.5, 4L to 0.5)
            )
        )
        val back = SwissStateSerializer.deserializeRoundHistory(SwissStateSerializer.serializeRoundHistory(rounds))

        assertEquals(1, back.size)
        assertEquals(rounds[0].roundNumber, back[0].roundNumber)
        assertEquals(rounds[0].pointsThisRound, back[0].pointsThisRound)
        assertEquals(2, back[0].matches.size)
        assertEquals(1L, back[0].matches[0].winnerId)
        assertNull("beraberlik (winnerId=null) korunmalı", back[0].matches[1].winnerId)
    }

    @Test
    fun belgeleme_roundHistory_matchIn_listId_skor_grup_turnuvaId_alanlariKAYBOLUR() {
        // 🔴 GERÇEK KAYIP (ama SWISS UI'dan gizli olduğu için şu an ulaşılamaz):
        // serializeRoundHistory yalnız id/songId1/songId2/winnerId/round yazıyor;
        // deserializeRoundHistory listId'yi 0'a (yorum: "Will be set by the caller"),
        // matchNumber/groupId/tournamentId'yi varsayılana (0/null/null), score1/score2'yi
        // null'a düşürüyor, createdAt'i ORİJİNAL değer yerine deserialize ANINDAKİ
        // System.currentTimeMillis()'e sıfırlıyor.
        val original = Match(
            id = 5, listId = 42, rankingMethod = "SWISS",
            songId1 = 1, songId2 = 2, winnerId = 1,
            score1 = 3, score2 = 1, round = 2, groupId = 7,
            matchNumber = 9, tournamentId = 123, isCompleted = true,
            createdAt = 1_700_000_000_000L
        )
        val rounds = listOf(RoundResult(roundNumber = 2, matches = listOf(original), pointsThisRound = mapOf(1L to 1.0)))
        val back = SwissStateSerializer.deserializeRoundHistory(SwissStateSerializer.serializeRoundHistory(rounds))[0].matches[0]

        // Korunanlar
        assertEquals(original.id, back.id)
        assertEquals(original.songId1, back.songId1)
        assertEquals(original.songId2, back.songId2)
        assertEquals(original.winnerId, back.winnerId)
        assertEquals(original.round, back.round)

        // Kaybolanlar — bilerek/belgelenerek doğrulanıyor, "kusur" olarak DEĞİL
        // "ulaşılamayan yolda bilinen kayıp" olarak raporlanıyor.
        assertEquals(0L, back.listId) // 42 değil
        assertNull(back.score1)       // 3 değil
        assertNull(back.score2)       // 1 değil
        assertNull(back.groupId)      // 7 değil
        assertEquals(0, back.matchNumber) // 9 değil
        assertNull(back.tournamentId) // 123 değil
        assertNotEquals(original.createdAt, back.createdAt) // 1_700_000_000_000L değil, "şimdi"
    }

    // ================= SwissFixtureSerializer =================

    @Test
    fun fixtureData_buyukFikstur_64Takim63TurTamGidisDonus() {
        val allMatches = (1..64L).flatMap { a ->
            (a + 1..64L).map { b ->
                Match(
                    id = a * 100 + b, listId = 1, rankingMethod = "SWISS",
                    songId1 = a, songId2 = b, winnerId = if ((a + b) % 2 == 0L) a else b,
                    score1 = (a % 5).toInt(), score2 = (b % 5).toInt(),
                    round = ((a + b) % 6).toInt() + 1, groupId = null,
                    isCompleted = true, createdAt = 1_700_000_000_000L + a
                )
            }
        }
        val roundsData = allMatches.groupBy { it.round }.mapValues { (round, matches) ->
            RoundData(
                roundNumber = round, matches = matches, isComplete = true,
                standingsAfterRound = matches.flatMap { listOf(it.songId1, it.songId2) }.distinct()
                    .associateWith { it.toDouble() }
            )
        }
        val fixtureData = SwissFixtureData(
            allMatches = allMatches,
            currentRoundMatches = allMatches.filter { it.round == 1 },
            completedMatches = allMatches.filter { it.isCompleted },
            upcomingMatches = allMatches.filter { !it.isCompleted },
            roundsData = roundsData
        )

        val back = SwissFixtureSerializer.deserializeFixtureData(SwissFixtureSerializer.serializeFixtureData(fixtureData))

        assertEquals(allMatches.size, back.allMatches.size)
        assertEquals(fixtureData.allMatches.map { it.id }.toSet(), back.allMatches.map { it.id }.toSet())
        // Her maçın tüm alanları (listId, rankingMethod, skor, round, groupId,
        // isCompleted, createdAt) BİREBİR korunuyor mu — SwissStateSerializer'ın
        // aksine bu serileştirici Match'i TAM işliyor (serializeMatch/deserializeMatch,
        // matchNumber HARİÇ — bkz. aşağıdaki test).
        val byId = back.allMatches.associateBy { it.id }
        allMatches.forEach { orig ->
            val restored = byId[orig.id]!!
            assertEquals(orig.listId, restored.listId)
            assertEquals(orig.rankingMethod, restored.rankingMethod)
            assertEquals(orig.songId1, restored.songId1)
            assertEquals(orig.songId2, restored.songId2)
            assertEquals(orig.winnerId, restored.winnerId)
            assertEquals(orig.score1, restored.score1)
            assertEquals(orig.score2, restored.score2)
            assertEquals(orig.round, restored.round)
            assertEquals(orig.groupId, restored.groupId)
            assertEquals(orig.isCompleted, restored.isCompleted)
            assertEquals(orig.createdAt, restored.createdAt)
        }
        assertEquals(roundsData.keys, back.roundsData.keys)
        roundsData.forEach { (round, data) ->
            assertEquals(data.standingsAfterRound, back.roundsData[round]!!.standingsAfterRound)
            assertEquals(data.isComplete, back.roundsData[round]!!.isComplete)
        }
        // completedMatches/upcomingMatches deserialize sırasında allMatches'ten
        // YENİDEN türetiliyor (isCompleted filtresiyle) — orijinal listelerle
        // İÇERİK olarak eşleşmeli.
        assertEquals(fixtureData.completedMatches.map { it.id }.toSet(), back.completedMatches.map { it.id }.toSet())
        assertEquals(fixtureData.upcomingMatches.map { it.id }.toSet(), back.upcomingMatches.map { it.id }.toSet())
    }

    @Test
    fun belgeleme_fixtureData_matchNumberSerializeMatchTarafindanYazilmiyor() {
        // 🔴 GERÇEK KAYIP: serializeMatch/deserializeMatch fonksiyonları
        // matchNumber alanını HİÇ ele almıyor (SwissFixtureSerializer.kt:69-84,
        // 182-197) — round-trip sonrası her zaman varsayılan 0'a düşer.
        val original = Match(
            id = 1, listId = 1, rankingMethod = "SWISS", songId1 = 1, songId2 = 2,
            winnerId = 1, round = 1, matchNumber = 7, isCompleted = true
        )
        val fixtureData = SwissFixtureData(listOf(original), listOf(original), listOf(original), emptyList(), emptyMap())
        val back = SwissFixtureSerializer.deserializeFixtureData(SwissFixtureSerializer.serializeFixtureData(fixtureData)).allMatches[0]
        assertEquals(0, back.matchNumber) // 7 değil — belgelenen kayıp
    }

    @Test
    fun fixtureData_nullAlanlarKorunur_skorGrupWinnerId() {
        val incomplete = Match(
            id = 1, listId = 1, rankingMethod = "SWISS", songId1 = 1, songId2 = 2,
            winnerId = null, score1 = null, score2 = null, round = 1, groupId = null, isCompleted = false
        )
        val fixtureData = SwissFixtureData(listOf(incomplete), listOf(incomplete), emptyList(), listOf(incomplete), emptyMap())
        val back = SwissFixtureSerializer.deserializeFixtureData(SwissFixtureSerializer.serializeFixtureData(fixtureData)).allMatches[0]
        assertNull(back.winnerId)
        assertNull(back.score1)
        assertNull(back.score2)
        assertNull(back.groupId)
        assertFalse(back.isCompleted)
    }

    @Test
    fun fixtureData_bosVeri_gidisDonusCokmuyor() {
        val empty = SwissFixtureData(emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
        val back = SwissFixtureSerializer.deserializeFixtureData(SwissFixtureSerializer.serializeFixtureData(empty))
        assertTrue(back.allMatches.isEmpty())
        assertTrue(back.roundsData.isEmpty())
    }

    @Test
    fun liveStandings_turkceKarakterliSarkiAdiKorunur() {
        val standings = LiveStandings(
            currentStandings = mapOf(1L to 5.0, 2L to 3.5),
            rankings = listOf(
                RankingEntry(songId = 1, songName = "Şımarık Çılgın Öğrenci İçin Şarkı — 'Güneş' & \"Ay\"", points = 5.0, position = 1, matchesPlayed = 3, wins = 2, draws = 1, losses = 0),
                RankingEntry(songId = 2, songName = "Üç Beş Yüz Öğütçü", points = 3.5, position = 2, matchesPlayed = 3, wins = 1, draws = 1, losses = 1)
            ),
            roundByRoundProgress = mapOf(1 to mapOf(1L to 1.0, 2L to 0.5), 2 to mapOf(1L to 2.0, 2L to 1.5))
        )
        val back = SwissFixtureSerializer.deserializeLiveStandings(SwissFixtureSerializer.serializeLiveStandings(standings))

        assertEquals(standings.currentStandings, back.currentStandings)
        assertEquals(standings.rankings, back.rankings)
        assertEquals(standings.roundByRoundProgress, back.roundByRoundProgress)
        // Özellikle Türkçe karakterler ve tırnak/özel karakterler bozulmamalı
        assertEquals("Şımarık Çılgın Öğrenci İçin Şarkı — 'Güneş' & \"Ay\"", back.rankings[0].songName)
    }

    @Test
    fun liveStandings_bosVeri_gidisDonusKorur() {
        val empty = LiveStandings(emptyMap(), emptyList(), emptyMap())
        val back = SwissFixtureSerializer.deserializeLiveStandings(SwissFixtureSerializer.serializeLiveStandings(empty))
        assertTrue(back.currentStandings.isEmpty())
        assertTrue(back.rankings.isEmpty())
        assertTrue(back.roundByRoundProgress.isEmpty())
    }
}

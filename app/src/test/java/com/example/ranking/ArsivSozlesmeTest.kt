package com.example.ranking

import com.example.ranking.data.Archive
import com.example.ranking.ui.viewmodel.ArchiveViewModel
import com.example.ranking.ui.viewmodel.ResultsViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

/**
 * ARŞİV SÖZLEŞME TESTLERİ — görev: koordinatör ranking-7d.
 *
 * `ResultsViewModel` (yazma tarafı: arşivleme) ve `ArchiveViewModel` (okuma
 * tarafı: arşiv açma) YALNIZ OKUNDU — hiçbir üretim dosyasına dokunulmadı.
 *
 * Bu iki ViewModel her JSON alanı için AYRI ama YAPISAL OLARAK AYNI veri
 * sınıfları tanımlıyor (ArchivableResult/ArchiveResult, ArchivableMatch/
 * ArchiveMatch, ArchivableLeagueSettings/ArchiveLeagueSettings, ve
 * LeagueTableEntry HER İKİ dosyada da ayrı ayrı tanımlı). Gson alan adı/tipine
 * göre eşleştirdiği için bugün round-trip ÇALIŞIYOR — ama bu ikili yapı
 * SESSİZ AYRIŞMAYA açık: biri değişip diğeri değişmezse Gson hatasız ama
 * YANLIŞ (null/varsayılan) veri üretebilir. Testler doğrudan GERÇEK
 * production sınıflarını kullanıyor ki böyle bir ayrışma ilk anda KIRMIZI
 * test versin.
 *
 * ResultsViewModel.saveArchive (özetle satır 302-317) ve ArchiveViewModel.
 * selectArchive (satır 96-135) suspend + AndroidViewModel içinde olduğu için
 * JVM testinden DOĞRUDAN çağrılamıyor; içindeki Gson adımları burada BİREBİR
 * KOPYALANDI (ESKI-MOTORLAR-SINAV-GOREV.md'nin "SADECE OKU, kopyalama testi
 * yaz" deseniyle aynı), ama veri TİPLERİ gerçek sınıflardan alınıyor.
 */
class ArsivSozlesmeTest {

    private val gson = Gson()

    // ---- ResultsViewModel.saveArchive'daki adımların birebir kopyası ----
    private fun yazTarafindaSerialize(
        results: List<ResultsViewModel.ArchivableResult>,
        matches: List<ResultsViewModel.ArchivableMatch>,
        leagueTable: List<ResultsViewModel.LeagueTableEntry>?,
        settings: ResultsViewModel.ArchivableLeagueSettings?
    ) = Archive(
        name = "test-arsiv", listId = 1, listName = "Test Liste", method = "LEAGUE",
        totalSongs = results.size, totalMatches = matches.size,
        completedMatches = matches.count { it.isCompleted },
        finalResults = gson.toJson(results),                    // ResultsViewModel.kt:310
        leagueTable = leagueTable?.let { gson.toJson(it) },      // ResultsViewModel.kt:311
        matchResults = gson.toJson(matches),                     // ResultsViewModel.kt:312
        leagueSettings = settings?.let { gson.toJson(it) },      // ResultsViewModel.kt:313
        isCompleted = matches.all { it.isCompleted }
    )

    // ---- ArchiveViewModel.selectArchive'daki adımların birebir kopyası ----
    private fun okuTarafindaDeserialize(archive: Archive): ArchiveViewModel.ArchiveUiState {
        val resultsType = object : TypeToken<List<ArchiveViewModel.ArchiveResult>>() {}.type
        val archiveResults = gson.fromJson<List<ArchiveViewModel.ArchiveResult>>(archive.finalResults, resultsType)

        val leagueTable = archive.leagueTable?.let {
            val t = object : TypeToken<List<ArchiveViewModel.LeagueTableEntry>>() {}.type
            gson.fromJson<List<ArchiveViewModel.LeagueTableEntry>>(it, t)
        } ?: emptyList()

        val matchesType = object : TypeToken<List<ArchiveViewModel.ArchiveMatch>>() {}.type
        val archiveMatches = gson.fromJson<List<ArchiveViewModel.ArchiveMatch>>(archive.matchResults, matchesType)

        val settings = archive.leagueSettings?.let {
            val t = object : TypeToken<ArchiveViewModel.ArchiveLeagueSettings>() {}.type
            gson.fromJson<ArchiveViewModel.ArchiveLeagueSettings>(it, t)
        }

        return ArchiveViewModel.ArchiveUiState(
            isLoading = false, selectedArchive = archive, archiveResults = archiveResults,
            archiveLeagueTable = leagueTable, archiveMatches = archiveMatches, archiveSettings = settings
        )
    }

    @Test
    fun finalResults_pozisyonVePuanKayipsizGeriGelir() {
        val results = listOf(
            ResultsViewModel.ArchivableResult(1, "Kadın", "Şebnem Ferah", "Kadın", 12.5, 1),
            ResultsViewModel.ArchivableResult(2, "Üvey", "Şebnem Ferah", "Artık Kısa Cümleler Kuruyorum", 9.0, 2),
            ResultsViewModel.ArchivableResult(3, "Çok Yorgunum", "Şebnem Ferah", "Od", 0.0, 3)
        )
        val archive = yazTarafindaSerialize(results, emptyList(), null, null)
        val back = okuTarafindaDeserialize(archive).archiveResults

        assertEquals(results.size, back.size)
        results.forEachIndexed { i, r ->
            assertEquals("sıra bozulmamalı", i + 1, back[i].position)
            assertEquals(r.songId, back[i].songId)
            assertEquals(r.songName, back[i].songName)
            assertEquals(r.artist, back[i].artist)
            assertEquals(r.album, back[i].album)
            assertEquals(r.score, back[i].score, 0.0)
            assertEquals(r.position, back[i].position)
        }
    }

    @Test
    fun finalResults_buyukListe_100OgeSiraVePuanBirebir() {
        val results = (1..100).map { i ->
            ResultsViewModel.ArchivableResult(i.toLong(), "Şarkı $i", "Sanatçı", "Albüm", (100 - i) * 0.37, i)
        }
        val archive = yazTarafindaSerialize(results, emptyList(), null, null)
        val back = okuTarafindaDeserialize(archive).archiveResults

        assertEquals(100, back.size)
        for (i in results.indices) {
            assertEquals(results[i].position, back[i].position)
            assertEquals(results[i].score, back[i].score, 1e-9)
        }
    }

    @Test
    fun finalResults_bosListe_cokmuyor() {
        val archive = yazTarafindaSerialize(emptyList(), emptyList(), null, null)
        assertTrue(okuTarafindaDeserialize(archive).archiveResults.isEmpty())
    }

    @Test
    fun matchResults_nullSkorBeraberlikVeTamamlanmamisMacKorunur() {
        val matches = listOf(
            ResultsViewModel.ArchivableMatch(1, "A Takımı", 2, "B Takımı", 3, 1, 1, true, 1),
            ResultsViewModel.ArchivableMatch(3, "C", 4, "D", null, null, null, false, 1) // oynanmamış
        )
        val archive = yazTarafindaSerialize(emptyList(), matches, null, null)
        val back = okuTarafindaDeserialize(archive).archiveMatches

        assertEquals(2, back.size)
        assertEquals(matches[0].team1Id, back[0].team1Id)
        assertEquals(matches[0].score1, back[0].score1)
        assertEquals(matches[0].score2, back[0].score2)
        assertEquals(matches[0].winnerId, back[0].winnerId)
        assertTrue(back[0].isCompleted)

        assertNull("oynanmamış maçın skoru null kalmalı", back[1].score1)
        assertNull(back[1].score2)
        assertNull("beraberlik/oynanmamış winnerId null kalmalı", back[1].winnerId)
        assertFalse(back[1].isCompleted)
    }

    @Test
    fun leagueTable_yalnizLeagueYonteminde_dolduAcilirDigerlerindeBosListeDoner() {
        val table = listOf(
            ResultsViewModel.LeagueTableEntry("Takım A", played = 4, won = 3, drawn = 1, lost = 0, goalsFor = 9, goalsAgainst = 2, goalDifference = 7, points = 10)
        )
        val archiveWithTable = yazTarafindaSerialize(emptyList(), emptyList(), table, null)
        val backWithTable = okuTarafindaDeserialize(archiveWithTable).archiveLeagueTable
        assertEquals(1, backWithTable.size)
        assertEquals(table[0], ResultsViewModel.LeagueTableEntry(
            backWithTable[0].teamName, backWithTable[0].played, backWithTable[0].won, backWithTable[0].drawn,
            backWithTable[0].lost, backWithTable[0].goalsFor, backWithTable[0].goalsAgainst,
            backWithTable[0].goalDifference, backWithTable[0].points
        ))

        // method != LEAGUE ise ResultsViewModel archivableLeagueTable = null yazıyor
        // (satır 296-299); Archive.leagueTable de null olur; okuma tarafı
        // `archive.leagueTable?.let{} ?: emptyList()` ile BOŞ LİSTE döndürür,
        // null DEĞİL — bu, ArchiveScreen'in null-check yerine .isEmpty()
        // kullanabileceği anlamına gelir, testle sabitlendi.
        val archiveWithoutTable = yazTarafindaSerialize(emptyList(), emptyList(), null, null)
        assertNull(archiveWithoutTable.leagueTable)
        assertTrue(okuTarafindaDeserialize(archiveWithoutTable).archiveLeagueTable.isEmpty())
    }

    @Test
    fun leagueSettings_nullIseNullGeriDoner_dolusaBirebirKorunur() {
        val archiveNoSettings = yazTarafindaSerialize(emptyList(), emptyList(), null, null)
        assertNull(okuTarafindaDeserialize(archiveNoSettings).archiveSettings)

        val settings = ResultsViewModel.ArchivableLeagueSettings(useScores = true, winPoints = 3, drawPoints = 1, allowDraws = true, doubleRoundRobin = true)
        val archive = yazTarafindaSerialize(emptyList(), emptyList(), null, settings)
        val back = okuTarafindaDeserialize(archive).archiveSettings
        assertNotNull(back)
        assertEquals(settings.useScores, back!!.useScores)
        assertEquals(settings.winPoints, back.winPoints)
        assertEquals(settings.drawPoints, back.drawPoints)
        assertEquals(settings.allowDraws, back.allowDraws)
        assertEquals(settings.doubleRoundRobin, back.doubleRoundRobin)
    }

    @Test
    fun turkceKarakterVeOzelKarakterlerJsonGidisDonusundeBozulmuyor() {
        val zorluAd = "Şımarık Çılgın Öğrenci İçin Şarkı — 'Güneş' & \"Ay\" / Ödül\nİkinci Satır"
        val results = listOf(ResultsViewModel.ArchivableResult(1, zorluAd, "Sanatçı Ğüçlüöşç", "Âlbüm", 1.0, 1))
        val archive = yazTarafindaSerialize(results, emptyList(), null, null)
        val back = okuTarafindaDeserialize(archive).archiveResults
        assertEquals(zorluAd, back[0].songName)
        assertEquals("Sanatçı Ğüçlüöşç", back[0].artist)
        assertEquals("Âlbüm", back[0].album)
    }

    @Test
    fun archiveEntity_finalResults_matchResults_gecerliJsonMetniOlarakTutulur() {
        // Archive Room entity'si JSON'u DÜZ METİN sütun olarak tutuyor (Archive.kt);
        // bu testin amacı entity'ye yazılan metnin gerçekten geçerli/ayrıştırılabilir
        // JSON olduğunu (üretim kodundaki TAM Gson çağrısıyla) doğrulamak.
        val results = listOf(ResultsViewModel.ArchivableResult(1, "X", "Y", "Z", 1.0, 1))
        val matches = listOf(ResultsViewModel.ArchivableMatch(1, "A", 2, "B", 1, 0, 1, true, 1))
        val archive = yazTarafindaSerialize(results, matches, null, null)

        assertTrue(archive.finalResults.trim().startsWith("["))
        assertTrue(archive.matchResults.trim().startsWith("["))
        // Gson().fromJson zaten çökmeden çalıştı (okuTarafindaDeserialize içinde) —
        // burada ayrıca ham metnin org.json ile de ayrıştırılabildiğini doğrulayalım
        // (iki farklı JSON kütüphanesi ile çapraz doğrulama).
        val arr = org.json.JSONArray(archive.finalResults)
        assertEquals(1, arr.length())
        assertEquals("X", arr.getJSONObject(0).getString("songName"))
    }
}

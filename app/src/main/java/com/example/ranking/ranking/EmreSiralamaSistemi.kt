package com.example.ranking.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult
import com.example.ranking.data.Song
import java.util.BitSet

/**
 * EMRE SIRALAMA SİSTEMİ (kullanıcı tasarımı, 2026-09-01).
 *
 * Her maç sonucu bir ÜSTÜNLÜK AĞACINA işlenir ve geçişlilik uygulanır:
 * a>b, b>c ⇒ a>c — bu çift bir daha SORULMAZ. Her turda, ilişkisi
 * bilinmeyen çiftler arasından "iki sonucun da GARANTİLEDİĞİ öğrenme"
 * en yüksek olan ayrık eşleşmeler seçilir:
 *
 *   skor(a,b) = min( kazanç(a>b), kazanç(b>a) )
 *   kazanç(a>b) = (a ve üstündekiler) × (b ve altındakiler) içinde
 *                 henüz bilinmeyen çift sayısı
 *
 * Bu, Kahn–Saks (1984) "kısmi bilgiyle sıralama" ilkesinin turnuva
 * uyarlamasıdır. Tasarım ölçümünde (EmreSiralamaSistemiOlcumTest) üç
 * strateji yarıştı; beklenen-değer ("ya tutarsa") 4.570 maç tutarken
 * garantili kazanç n=200'ü 18 turda ~1.364 maçla sıfır hata sıraladı —
 * teorik en iyiye (~1.246) en yakın turnuva formatı.
 *
 * DURUM YÖNETİMİ: replay tabanlı (bkz. PairwiseComparisonSort) — ayrı
 * durum tablosu yok; tamamlanmış maçlardan ağaç yeniden kurulur, sıradaki
 * tur ondan türetilir. Devam etme ve geri alma kayıtların doğal sonucu.
 *
 * Beraberlik: bilgi VERMEZ — çift "soruldu" sayılır ve bir daha sorulmaz,
 * ağaca kenar eklenmez. Çelişkili oy (a>b zaten biliniyorken b>a gelirse)
 * yok sayılır: İLK bilgi geçerlidir (hibritteki kanıt deposu kuralı).
 */
object EmreSiralamaSistemi {

    const val METHOD = "EMRE_SIRALAMA"

    data class Durum(
        val siralama: List<Long>,       // güncel tahmini sıralama (en iyiden kötüye)
        val sonrakiMaclar: List<Match>, // sıradaki turun maçları; boş = bitti
        val sonrakiTur: Int,
        val bitti: Boolean
    )

    /** Üstünlük ağacının replay kurulumu + tur üretimi. */
    private class Agac(val songs: List<Song>) {
        val n = songs.size
        val idx = songs.mapIndexed { i, s -> s.id to i }.toMap()
        val ust = Array(n) { BitSet(n) }   // i'den büyük olduğu BİLİNENLER
        val alt = Array(n) { BitSet(n) }   // i'nin büyük olduğu bilinenler
        val soruldu = HashSet<Long>()      // sorulan çiftler (beraberlik dahil)

        fun anahtar(a: Int, b: Int): Long =
            minOf(a, b).toLong() * n + maxOf(a, b)

        fun biliniyor(a: Int, b: Int) = alt[a].get(b) || ust[a].get(b)

        fun sonucIsle(w: Int, l: Int) {
            if (alt[w].get(l)) return
            if (ust[w].get(l)) return // çelişki: ilk bilgi geçerli
            val kazananlar = ust[w].clone() as BitSet; kazananlar.set(w)
            val kaybedenler = alt[l].clone() as BitSet; kaybedenler.set(l)
            var x = kazananlar.nextSetBit(0)
            while (x >= 0) {
                alt[x].or(kaybedenler); alt[x].clear(x)
                x = kazananlar.nextSetBit(x + 1)
            }
            var y = kaybedenler.nextSetBit(0)
            while (y >= 0) {
                ust[y].or(kazananlar); ust[y].clear(y)
                y = kaybedenler.nextSetBit(y + 1)
            }
        }

        fun kur(tamamlanan: List<Match>) {
            // Kayıt sırası = oy sırası; determinizm için tur + id ile sıralanır
            tamamlanan.sortedWith(compareBy({ it.round }, { it.id })).forEach { m ->
                val a = idx[m.songId1] ?: return@forEach
                val b = idx[m.songId2] ?: return@forEach
                soruldu.add(anahtar(a, b))
                when (m.winnerId) {
                    m.songId1 -> sonucIsle(a, b)
                    m.songId2 -> sonucIsle(b, a)
                    // null (beraberlik) ve yabancı kimlik: bilgi yok
                }
            }
        }

        /** a>b çıkarsa YENİ öğrenilecek çift sayısı (bitset farkıyla tam hesap). */
        fun kazanc(a: Int, b: Int): Int {
            val ustler = ust[a].clone() as BitSet; ustler.set(a)
            val altlar = alt[b].clone() as BitSet; altlar.set(b)
            var toplam = 0
            var x = ustler.nextSetBit(0)
            while (x >= 0) {
                val yeni = altlar.clone() as BitSet
                yeni.andNot(alt[x])
                yeni.clear(x)
                toplam += yeni.cardinality()
                x = ustler.nextSetBit(x + 1)
            }
            return toplam
        }

        /** Tahmini konum (küçük = iyi): bilinen sınırların orta noktası. */
        fun ortaNokta(i: Int): Double =
            ((ust[i].cardinality() + 1) + (n - alt[i].cardinality())) / 2.0

        /** Sıradaki turun ayrık eşleşmeleri (garantili kazanç sırasıyla). */
        fun turEslesmeleri(): List<Pair<Int, Int>> {
            data class Aday(val a: Int, val b: Int, val skor: Int)
            val adaylar = ArrayList<Aday>()
            for (i in 0 until n) for (j in i + 1 until n) {
                if (biliniyor(i, j) || anahtar(i, j) in soruldu) continue
                adaylar.add(Aday(i, j, minOf(kazanc(i, j), kazanc(j, i))))
            }
            if (adaylar.isEmpty()) return emptyList()
            adaylar.sortWith(
                compareByDescending<Aday> { it.skor }.thenBy { it.a }.thenBy { it.b }
            )
            val kullanildi = BooleanArray(n)
            val secilen = ArrayList<Pair<Int, Int>>()
            for (aday in adaylar) {
                if (kullanildi[aday.a] || kullanildi[aday.b]) continue
                kullanildi[aday.a] = true; kullanildi[aday.b] = true
                secilen.add(aday.a to aday.b)
            }
            return secilen
        }

        /** Güncel sıralama: bilinen alt sayısı çok → üst sırada; eşitlikte
         *  bilinen üst sayısı az, sonra giriş sırası (deterministik). */
        fun siralama(): List<Int> = (0 until n).sortedWith(
            compareByDescending<Int> { alt[it].cardinality() }
                .thenBy { ust[it].cardinality() }
                .thenBy { it }
        )
    }

    /**
     * Tamamlanmış maçları baştan oynatarak güncel durumu kurar.
     * completedMatches: bu listenin EMRE_SIRALAMA yöntemine ait TAMAMLANMIŞ maçları.
     */
    fun computeState(songs: List<Song>, completedMatches: List<Match>): Durum {
        if (songs.isEmpty()) return Durum(emptyList(), emptyList(), 1, true)
        if (songs.size == 1) return Durum(listOf(songs[0].id), emptyList(), 1, true)

        val tamamlanan = completedMatches.filter { it.isCompleted }
        val agac = Agac(songs)
        agac.kur(tamamlanan)

        val siralama = agac.siralama().map { songs[it].id }
        val sonrakiTur = (tamamlanan.maxOfOrNull { it.round } ?: 0) + 1
        val eslesmeler = agac.turEslesmeleri()
        if (eslesmeler.isEmpty()) return Durum(siralama, emptyList(), sonrakiTur, true)

        val listId = songs.first().listId
        val maclar = eslesmeler.mapIndexed { i, (a, b) ->
            // Tahmini iyi olan üste (songId1) yazılır — ekranda üst kart
            val (u, alt2) = if (agac.ortaNokta(a) <= agac.ortaNokta(b)) a to b else b to a
            Match(
                listId = listId,
                rankingMethod = METHOD,
                songId1 = songs[u].id,
                songId2 = songs[alt2].id,
                winnerId = null,
                round = sonrakiTur,
                matchNumber = i + 1,
                isCompleted = false
            )
        }
        return Durum(siralama, maclar, sonrakiTur, false)
    }

    /** Sıradaki turun maçları; boş liste = turnuva tamamlandı. */
    fun createNextRoundMatches(songs: List<Song>, completedMatches: List<Match>): List<Match> =
        computeState(songs, completedMatches).sonrakiMaclar

    /**
     * Final sonuçları. Turnuva bitmeden çağrılırsa (erken durum) ağacın o
     * ana kadarki bilgisine göre en iyi tahmini sıralamayı verir.
     */
    fun calculateResults(songs: List<Song>, completedMatches: List<Match>): List<RankingResult> {
        val durum = computeState(songs, completedMatches)
        val n = durum.siralama.size
        val listId = songs.firstOrNull()?.listId ?: 0L
        return durum.siralama.mapIndexed { index, songId ->
            RankingResult(
                songId = songId,
                listId = listId,
                rankingMethod = METHOD,
                score = (n - index).toDouble(),
                position = index + 1
            )
        }
    }
}

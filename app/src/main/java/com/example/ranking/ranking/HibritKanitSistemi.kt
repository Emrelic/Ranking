package com.example.ranking.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult
import com.example.ranking.data.Song

/**
 * Hibrit İsviçre (Kanıt Turlu) sıralama sistemi.
 *
 * İki fazlı: önce kısa bir Geliştirilmiş İsviçre (kaba yerleşim), sonra
 * "kanıt turları" — sıralamada birbirine yakın duran ama aralarında maç
 * olmayan çiftler, azalan adım mesafeleriyle (20 → 6 → 2 → 1) eşleştirilir.
 * Kaybeden aşağı iner; oynanmış maç asla tekrarlanmaz (bedava kanıt).
 * Son adım 1 olduğu için bitişte her komşuluk en az bir gerçek maçla
 * kanıtlıdır: tutarlı oylamada sonuç TAM sıralıdır.
 *
 * Tasarım ölçümü (HibritKanitSistemiTest): n=200'de ~1.700-1.900 maçla
 * sapma 0 — tam Emre'nin (10.000+ maç) beşte biri emekle daha iyi sonuç.
 *
 * DURUM YÖNETİMİ: PairwiseComparisonSort gibi ayrı durum tablosu yoktur.
 * Tamamlanmış maçlar deterministik olarak baştan oynatılır (replay);
 * sıradaki turun eşleşmeleri bu oynatmadan türetilir. Devam etme ve geri
 * alma böylece maç kayıtlarının doğal sonucudur.
 *
 * Beraberlik: "üstteki korunur" — çift oynanmış sayılır (kanıt), yer
 * değişmez. Öznel listelerde çelişkili oylar döngü yaratabilir diye her
 * adım mesafesinin süpürme sayısı 2n+10 ile sınırlıdır; sınır aşılırsa o
 * adım olduğu gibi bırakılıp inceltmeye geçilir (turnuva her koşulda biter).
 */
object HibritKanitSistemi {

    const val METHOD = "HIBRIT"

    /** Faz 1 uzunluğu. Taramada ölçüldü: 4 tur İsviçre toplam maliyetin
     *  tatlı noktası; daha uzunu faz 2'nin işini ucuzlatmıyor. */
    const val FAZ1_TUR = 4

    /** Faz 2 adım dizisi (Shell tarzı). Son eleman 1 OLMAK ZORUNDA —
     *  komşuluk kanıtı garantisi ondan gelir. */
    val ADIMLAR = listOf(20, 6, 2, 1)

    data class HibritDurum(
        val siralama: List<Long>,       // güncel sıralama (en iyiden en kötüye)
        val sonrakiMaclar: List<Match>, // sıradaki turun maçları; boş = turnuva bitti
        val sonrakiTur: Int,
        val faz: Int,                   // 1 = İsviçre, 2 = kanıt turları
        val bitti: Boolean
    )

    private fun anahtar(a: Long, b: Long) = minOf(a, b) to maxOf(a, b)

    private fun emreSirasi(state: EmreSystemCorrect.EmreState): List<Long> =
        RankingEngine.calculateCorrectEmreResults(state)
            .sortedBy { it.position }.map { it.songId }

    /** Turun bye takımı — yalnız tek sayıda takımda (bkz. RankingViewModel.findByeTeamFromMatches). */
    private fun byeTakimi(
        state: EmreSystemCorrect.EmreState,
        turMaclari: List<Match>,
        songs: List<Song>
    ): EmreSystemCorrect.EmreTeam? {
        if (songs.size % 2 == 0) return null
        val oynayanlar = turMaclari.flatMap { listOf(it.songId1, it.songId2) }.toSet()
        return state.teams.find { it.song.id !in oynayanlar }
    }

    /**
     * Tamamlanmış maçları baştan oynatarak güncel durumu kurar.
     * completedMatches: bu listenin HIBRIT yöntemine ait TAMAMLANMIŞ maçları.
     */
    fun computeState(songs: List<Song>, completedMatches: List<Match>): HibritDurum {
        if (songs.isEmpty()) return HibritDurum(emptyList(), emptyList(), 1, 1, true)
        if (songs.size == 1) return HibritDurum(listOf(songs[0].id), emptyList(), 1, 1, true)

        val tamamlanan = completedMatches.filter { it.isCompleted }
        val turlar = tamamlanan.groupBy { it.round }

        // ---- FAZ 1: Geliştirilmiş İsviçre (kaba yerleşim) ----
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        for (r in 1..FAZ1_TUR) {
            val turMaclari = turlar[r].orEmpty()
            if (turMaclari.isEmpty()) {
                // Bu tur henüz oynanmamış — eşleştirmesi üretilir. Aynı state
                // aynı eşleştirmeyi verir; replay determinizmi buradan gelir.
                val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
                if (!pairing.canContinue || pairing.matches.isEmpty()) break // faz 2'ye geç
                return HibritDurum(
                    siralama = emreSirasi(state),
                    sonrakiMaclar = pairing.matches.map {
                        it.copy(rankingMethod = METHOD, round = r)
                    },
                    sonrakiTur = r,
                    faz = 1,
                    bitti = false
                )
            }
            // 🔴 Tiebreaker zincirine yalnız O ANA KADARKİ maçlar verilir.
            // Canlı akışta r. tur işlenirken faz 2 maçları henüz yoktu; replay
            // hepsini verirse eşitlik bozma farklı sonuçlanıp faz 1'in temel
            // sıralaması turnuva ortasında sessizce değişebilir.
            state = RankingEngine.processCorrectEmreResults(
                state, turMaclari, byeTakimi(state, turMaclari, songs),
                allCompletedMatches = tamamlanan.filter { it.round <= r }
            )
        }

        // ---- FAZ 2: kanıt turları ----
        val sira = emreSirasi(state).toMutableList()
        val n = sira.size

        // Kanıt deposu: oynanmış her çift (faz 1 dahil). Mükerrer kayıtta ilk
        // sonuç geçerlidir (kayıt sırası = id artan) — determinizm için.
        val kanit = HashMap<Pair<Long, Long>, Long?>()
        tamamlanan.sortedBy { it.id }.forEach { m ->
            val k = anahtar(m.songId1, m.songId2)
            if (!kanit.containsKey(k)) kanit[k] = m.winnerId
        }

        val sonrakiTurNo = (tamamlanan.maxOfOrNull { it.round } ?: 0) + 1

        for (adim in ADIMLAR) {
            if (adim >= n) continue
            var supurme = 0
            var degisti = true
            while (degisti && supurme < 2 * n + 10) {
                degisti = false
                supurme++
                // Ayrık çiftler: i ≡ parite (mod 2·adım) → bir geçişin maçları
                // birbirinden bağımsızdır, tek tur halinde oynanabilir
                for (parite in 0 until 2 * adim) {
                    val bekleyen = mutableListOf<Pair<Long, Long>>()
                    var i = parite
                    while (i + adim < n) {
                        val ust = sira[i]
                        val alt = sira[i + adim]
                        val k = anahtar(ust, alt)
                        if (kanit.containsKey(k)) {
                            // Kanıt var: alt kazanmışsa yer değişir; beraberlik
                            // veya üstün galibiyeti mevcut sırayı korur
                            if (kanit[k] == alt) {
                                sira[i] = alt; sira[i + adim] = ust; degisti = true
                            }
                        } else {
                            bekleyen.add(ust to alt)
                        }
                        i += 2 * adim
                    }
                    if (bekleyen.isNotEmpty()) {
                        // Kanıtsız çiftler bulundu: sıradaki tur bunlardır.
                        // Replay burada durur; bu maçlar oynanınca aynı noktaya
                        // kanıtla gelinir ve süpürme kaldığı yerden sürer.
                        val listId = songs.first().listId
                        val maclar = bekleyen.mapIndexed { idx, (u, a) ->
                            Match(
                                listId = listId,
                                rankingMethod = METHOD,
                                songId1 = u,
                                songId2 = a,
                                winnerId = null,
                                round = sonrakiTurNo,
                                matchNumber = idx + 1,
                                isCompleted = false
                            )
                        }
                        return HibritDurum(sira.toList(), maclar, sonrakiTurNo, 2, false)
                    }
                }
            }
        }

        return HibritDurum(sira.toList(), emptyList(), sonrakiTurNo, 2, true)
    }

    /** Sıradaki turun maçları; boş liste = turnuva tamamlandı. */
    fun createNextRoundMatches(songs: List<Song>, completedMatches: List<Match>): List<Match> =
        computeState(songs, completedMatches).sonrakiMaclar

    /**
     * Final sonuçları: güncel sıralamadaki konum → pozisyon; skor = altında
     * kalan öğe sayısı. Turnuva bitmeden çağrılırsa (erken bitirme) o ana
     * kadarki kanıtların kurduğu sıralamayı verir.
     */
    fun calculateResults(songs: List<Song>, completedMatches: List<Match>): List<RankingResult> {
        val durum = computeState(songs, completedMatches)
        val yerlesen = durum.siralama
        val eksikler = songs.map { it.id }.filter { it !in yerlesen.toSet() }
        val tamSira = yerlesen + eksikler
        val n = tamSira.size
        val listId = songs.firstOrNull()?.listId ?: 0L
        return tamSira.mapIndexed { index, songId ->
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

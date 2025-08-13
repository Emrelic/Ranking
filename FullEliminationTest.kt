import com.example.ranking.ranking.RankingEngine
import com.example.ranking.data.Song

fun main() {
    println("=== TAM ELEME SİSTEMİ TEST - 79 TAKIM ===")
    
    // 79 takım oluştur
    val songs = (1..79).map { i ->
        Song(
            id = i.toLong(),
            name = "Takım $i", 
            artist = "Artist",
            album = "Album",
            trackNumber = i,
            listId = 1L
        )
    }
    
    println("Toplam takım sayısı: ${songs.size}")
    
    // Hedef boyutu hesapla
    val targetSize = RankingEngine.run {
        when (songs.size) {
            in 1..2 -> 2
            in 3..4 -> 4
            in 5..8 -> 8
            in 9..16 -> 16
            in 17..32 -> 32
            in 33..64 -> 64
            in 65..128 -> 128
            else -> 128
        }
    }
    
    println("Hedef boyut (en yakın 2'nin üssü): $targetSize")
    println("Elemesi gereken takım sayısı: ${songs.size - targetSize}")
    
    // İlk tur maçları oluştur
    val firstRoundMatches = RankingEngine.createFullEliminationMatches(songs)
    println("\n=== İLK TUR MAÇLARI ===")
    println("Toplam maç sayısı: ${firstRoundMatches.size}")
    
    // Maç türlerini analiz et
    val pairMatches = mutableMapOf<Set<Long>, Int>()
    firstRoundMatches.forEach { match ->
        val pair = setOf(match.songId1, match.songId2)
        pairMatches[pair] = (pairMatches[pair] ?: 0) + 1
    }
    
    val doubleMatches = pairMatches.filter { it.value == 1 }.size // İkili maçlar
    val tripleMatches = pairMatches.filter { it.value == 3 }.size / 3 // Üçlü gruplar
    
    println("İkili eşleşme sayısı: $doubleMatches")
    println("Üçlü grup sayısı: $tripleMatches")
    
    // Beklenen sonuçları kontrol et
    val expectedPairs = if (songs.size % 2 == 0) {
        songs.size / 2
    } else {
        (songs.size - 3) / 2
    }
    
    val expectedTriples = if (songs.size % 2 == 0) 0 else 1
    
    println("\nBEKLENEN SONUÇLAR:")
    println("Beklenen ikili eşleşme: $expectedPairs")
    println("Beklenen üçlü grup: $expectedTriples")
    
    println("\nSİSTEM ÇALIŞIYOR: ${doubleMatches == expectedPairs && tripleMatches == expectedTriples}")
    
    // Örnek sistem simülasyonu (79 takım için)
    println("\n=== 79 TAKIM İÇİN SİMÜLASYON ===")
    
    // 1. Tur: 79 takım
    println("1. TUR: 79 takım")
    println("- 38 ikili eşleşme (76 takım)")
    println("- 1 üçlü grup (3 takım)")
    println("- Sonuç: 38 kazanan (ikili) + 1 kazanan (üçlü) = 39 takım")
    println("- Elenen: 38 kaybeden (ikili) + 2 kaybeden (üçlü) = 40 takım")
    
    // Eksik 25 takım için ikinci tur
    println("\n2. TUR: 38 kaybeden takım arasında")
    println("- 19 ikili eşleşme")
    println("- Sonuç: 19 kazanan")
    println("- Elenen: 19 kaybeden")
    println("- Toplam açık: 39 + 19 = 58 takım (Eksik: 6 takım)")
    
    // 6 takım için üçüncü tur
    println("\n3. TUR: 19 kazanan arasından 6 takım seç")
    println("- Lig usulü veya başka yöntemle 6 takım seçilir")
    println("- Son 64'lük listeye dahil edilir")
    
    println("\nFINAL: 64 takım ile klasik eleme sistemi başlar")
    println("- Çeyrek Final: 64 → 32 → 16 → 8 → 4 → 2 → 1")
}
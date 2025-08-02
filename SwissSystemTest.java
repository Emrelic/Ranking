import java.io.*;
import java.nio.file.*;
import java.util.*;

class SwissItem {
    String id;
    String category;
    int score; // İlk rating/skor
    double points; // Tournament puanları
    Set<String> opponents; // Karşılaştığı rakipler
    
    SwissItem(String id, String category, int score) {
        this.id = id;
        this.category = category;
        this.score = score;
        this.points = 0.0;
        this.opponents = new HashSet<>();
    }
    
    @Override
    public String toString() {
        return id + "(" + score + ", pts:" + points + ")";
    }
}

class SwissMatch {
    SwissItem player1, player2;
    SwissItem winner;
    boolean isDraw;
    int round;
    
    SwissMatch(SwissItem p1, SwissItem p2, int round) {
        this.player1 = p1;
        this.player2 = p2;
        this.round = round;
        this.isDraw = false;
    }
    
    void setResult(SwissItem winner) {
        this.winner = winner;
        this.isDraw = false;
        
        // Puanları güncelle
        if (winner == player1) {
            player1.points += 1.0;
            player2.points += 0.0;
        } else if (winner == player2) {
            player1.points += 0.0;
            player2.points += 1.0;
        }
        
        // Rakip listelerine ekle
        player1.opponents.add(player2.id);
        player2.opponents.add(player1.id);
    }
    
    void setDraw() {
        this.isDraw = true;
        this.winner = null;
        
        // Beraberlik puanları
        player1.points += 0.5;
        player2.points += 0.5;
        
        // Rakip listelerine ekle
        player1.opponents.add(player2.id);
        player2.opponents.add(player1.id);
    }
}

public class SwissSystemTest {
    
    public static void main(String[] args) {
        String filePath = "C:\\Users\\ikizler1\\OneDrive\\Desktop\\şebnem randomize 10000 bnlik.csv";
        
        System.out.println("=== İSVİÇRE SİSTEMİ TURNUVA TESTİ ===");
        System.out.println("CSV dosyası okunuyor...");
        
        List<SwissItem> players = new ArrayList<>();
        
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            lines.remove(0); // Header'ı atla
            
            // İlk 20 öğe ile test et (büyük liste için çok uzun sürer)
            int count = 0;
            for (String line : lines) {
                if (count >= 20) break;
                String[] parts = line.split(";");
                if (parts.length >= 4) {
                    String id = parts[0].trim();
                    String category = parts[2].trim();
                    int score = Integer.parseInt(parts[3].trim());
                    players.add(new SwissItem(id, category, score));
                    count++;
                }
            }
        } catch (Exception e) {
            System.out.println("HATA: " + e.getMessage());
            return;
        }
        
        System.out.println("Toplam " + players.size() + " oyuncu");
        
        // İsviçre sistemi turnuva başlat
        System.out.println("\n=== İSVİÇRE SİSTEMİ TURNUVA ===");
        
        // Tur sayısını hesapla
        int totalRounds = calculateSwissRounds(players.size());
        System.out.println("Toplam tur sayısı: " + totalRounds);
        
        Random random = new Random();
        List<SwissMatch> allMatches = new ArrayList<>();
        
        for (int round = 1; round <= totalRounds; round++) {
            System.out.println("\n--- TUR " + round + " ---");
            
            List<SwissMatch> roundMatches = createSwissPairings(players, round, allMatches);
            
            if (roundMatches.isEmpty()) {
                System.out.println("Bu turda eşleştirme yapılamadı!");
                break;
            }
            
            // Maçları simüle et (yüksek skora sahip oyuncu kazanır, %15 şans diğeri kazanır)
            for (SwissMatch match : roundMatches) {
                boolean higherScoreWins = random.nextDouble() > 0.15;
                
                SwissItem winner;
                if (match.player1.score > match.player2.score) {
                    winner = higherScoreWins ? match.player1 : match.player2;
                } else if (match.player2.score > match.player1.score) {
                    winner = higherScoreWins ? match.player2 : match.player1;
                } else {
                    // Eşit skorlar - rastgele
                    winner = random.nextBoolean() ? match.player1 : match.player2;
                }
                
                match.setResult(winner);
                System.out.println("Maç: " + match.player1.id + "(" + match.player1.score + ") vs " + 
                    match.player2.id + "(" + match.player2.score + ") -> Kazanan: " + winner.id);
                
                allMatches.add(match);
            }
            
            // Tur sonu durumu
            printStandings(players, round);
        }
        
        // Final sonuçları
        System.out.println("\n=== FINAL SONUÇLARI ===");
        printFinalStandings(players);
        
        // İsviçre sistemi kuralları kontrolü
        System.out.println("\n=== İSVİÇRE SİSTEMİ KURALLAR KONTROLÜ ===");
        validateSwissRules(players, allMatches);
    }
    
    static List<SwissMatch> createSwissPairings(List<SwissItem> players, int round, List<SwissMatch> previousMatches) {
        List<SwissMatch> matches = new ArrayList<>();
        
        if (round == 1) {
            // İlk tur: Initial rating'e göre eşleştir (Swiss kuralı)
            players.sort((a, b) -> Integer.compare(b.score, a.score)); // Yüksekten düşüğe
            
            int half = players.size() / 2;
            for (int i = 0; i < half; i++) {
                if (i + half < players.size()) {
                    matches.add(new SwissMatch(players.get(i), players.get(i + half), round));
                }
            }
            return matches;
        }
        
        // Sonraki turlar: Puan gruplarına göre Dutch sistem
        return createDutchSystemPairings(players, round);
    }
    
    static List<SwissMatch> createDutchSystemPairings(List<SwissItem> players, int round) {
        List<SwissMatch> matches = new ArrayList<>();
        
        // Oyuncuları puana göre grupla
        Map<Double, List<SwissItem>> pointGroups = new TreeMap<>(Collections.reverseOrder());
        for (SwissItem player : players) {
            pointGroups.computeIfAbsent(player.points, k -> new ArrayList<>()).add(player);
        }
        
        Set<SwissItem> paired = new HashSet<>();
        
        // Her puan grubu içinde eşleştir
        for (Map.Entry<Double, List<SwissItem>> entry : pointGroups.entrySet()) {
            List<SwissItem> group = entry.getValue();
            
            // Grup içindeki oyuncuları rating'e göre sırala
            group.sort((a, b) -> Integer.compare(b.score, a.score));
            
            // Dutch sistem: üst yarı ile alt yarıyı eşleştir
            pairWithinGroup(group, matches, paired, round);
        }
        
        return matches;
    }
    
    static void pairWithinGroup(List<SwissItem> group, List<SwissMatch> matches, 
                               Set<SwissItem> paired, int round) {
        List<SwissItem> available = new ArrayList<>();
        for (SwissItem player : group) {
            if (!paired.contains(player)) {
                available.add(player);
            }
        }
        
        if (available.size() < 2) return;
        
        // Dutch sistem mantığı: üst yarı ile alt yarı eşleştir
        int half = available.size() / 2;
        
        // Önce standart Dutch eşleştirmesi dene
        for (int i = 0; i < half; i++) {
            SwissItem top = available.get(i);
            SwissItem bottom = null;
            
            // Alt yarıdan henüz karşılaşmadığı birini bul
            for (int j = half; j < available.size(); j++) {
                SwissItem candidate = available.get(j);
                if (!top.opponents.contains(candidate.id) && !paired.contains(candidate)) {
                    bottom = candidate;
                    available.remove(candidate);
                    break;
                }
            }
            
            if (bottom != null) {
                matches.add(new SwissMatch(top, bottom, round));
                paired.add(top);
                paired.add(bottom);
            }
        }
        
        // Kalan oyuncuları eşleştir (fallback mechanism)
        available.removeIf(paired::contains);
        while (available.size() >= 2) {
            SwissItem p1 = available.get(0);
            SwissItem p2 = null;
            
            // Henüz karşılaşmadığı birini bul
            for (int i = 1; i < available.size(); i++) {
                if (!p1.opponents.contains(available.get(i).id)) {
                    p2 = available.get(i);
                    break;
                }
            }
            
            // Bulamazsa bu oyuncuyu bu turda maç yapmadan geç
            if (p2 == null) {
                available.remove(p1);
                continue;
            }
            
            if (p2 != null) {
                matches.add(new SwissMatch(p1, p2, round));
                paired.add(p1);
                paired.add(p2);
                available.remove(p1);
                available.remove(p2);
            } else {
                break;
            }
        }
    }
    
    static int calculateSwissRounds(int playerCount) {
        // İsviçre sistemi standart tur sayıları
        if (playerCount <= 4) return 3;
        if (playerCount <= 8) return 4;
        if (playerCount <= 16) return 5;
        if (playerCount <= 32) return 6;
        if (playerCount <= 64) return 7;
        if (playerCount <= 128) return 8;
        return 9;
    }
    
    static void printStandings(List<SwissItem> players, int round) {
        // Puana göre sırala, eşitlik durumunda rating'e göre
        players.sort((a, b) -> {
            int pointCompare = Double.compare(b.points, a.points);
            if (pointCompare != 0) return pointCompare;
            return Integer.compare(b.score, a.score);
        });
        
        System.out.println("\nTur " + round + " sonu sıralaması:");
        for (int i = 0; i < Math.min(10, players.size()); i++) {
            SwissItem player = players.get(i);
            System.out.println((i + 1) + ". " + player.id + ": " + player.points + " puan (rating: " + player.score + ")");
        }
    }
    
    static void printFinalStandings(List<SwissItem> players) {
        // Final sıralama
        players.sort((a, b) -> {
            int pointCompare = Double.compare(b.points, a.points);
            if (pointCompare != 0) return pointCompare;
            return Integer.compare(b.score, a.score);
        });
        
        System.out.println("Final Sıralama:");
        for (int i = 0; i < players.size(); i++) {
            SwissItem player = players.get(i);
            System.out.println((i + 1) + ". " + player.id + ": " + player.points + " puan (rating: " + player.score + ", " + player.opponents.size() + " maç)");
        }
    }
    
    static void validateSwissRules(List<SwissItem> players, List<SwissMatch> allMatches) {
        boolean isValid = true;
        
        // Kural 1: Hiçbir oyuncu aynı rakiple iki kez karşılaşmamış mı?
        System.out.println("1. Aynı rakiple tekrar karşılaşma kontrolü:");
        Map<String, Set<String>> encounters = new HashMap<>();
        
        for (SwissMatch match : allMatches) {
            String p1 = match.player1.id;
            String p2 = match.player2.id;
            
            encounters.computeIfAbsent(p1, k -> new HashSet<>());
            encounters.computeIfAbsent(p2, k -> new HashSet<>());
            
            if (encounters.get(p1).contains(p2)) {
                System.out.println("   ❌ HATA: " + p1 + " ve " + p2 + " iki kez karşılaştı!");
                isValid = false;
            } else {
                encounters.get(p1).add(p2);
                encounters.get(p2).add(p1);
            }
        }
        
        if (isValid) {
            System.out.println("   ✅ Hiçbir oyuncu aynı rakiple iki kez karşılaşmadı");
        }
        
        // Kural 2: Her oyuncu yaklaşık aynı sayıda maç oynadı mı?
        System.out.println("\n2. Maç sayısı dengesi kontrolü:");
        int minMatches = Integer.MAX_VALUE;
        int maxMatches = 0;
        
        for (SwissItem player : players) {
            int matchCount = player.opponents.size();
            minMatches = Math.min(minMatches, matchCount);
            maxMatches = Math.max(maxMatches, matchCount);
        }
        
        System.out.println("   En az maç: " + minMatches + ", En çok maç: " + maxMatches);
        // İsviçre sisteminde 1-2 maç farkı normaldir (özellikle tek sayıda oyuncu varsa)
        if (maxMatches - minMatches <= 2) {
            System.out.println("   ✅ Maç sayısı dengesi uygun (İsviçre sistemi için normal)");
        } else {
            System.out.println("   ❌ Maç sayısı dengesizliği fazla!");
            isValid = false;
        }
        
        // Kural 3: Puan dağılımı makul mu?
        System.out.println("\n3. Puan dağılımı kontrolü:");
        double totalPoints = players.stream().mapToDouble(p -> p.points).sum();
        double expectedPoints = allMatches.size(); // Her maç 1 puan verir toplam
        System.out.println("   Toplam puan: " + totalPoints + ", Beklenen: " + expectedPoints);
        
        if (Math.abs(totalPoints - expectedPoints) < 0.1) {
            System.out.println("   ✅ Puan dağılımı doğru");
        } else {
            System.out.println("   ❌ Puan dağılımında hata!");
            isValid = false;
        }
        
        System.out.println("\n=== İSVİÇRE SİSTEMİ SONUCU ===");
        if (isValid) {
            System.out.println("✅ İsviçre sistemi kuralları doğru şekilde uygulandı!");
        } else {
            System.out.println("❌ İsviçre sistemi kurallarında hatalar tespit edildi!");
        }
    }
}
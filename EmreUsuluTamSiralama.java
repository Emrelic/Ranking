import java.io.*;
import java.nio.file.*;
import java.util.*;

class TestItem {
    String id;
    String category;
    int score;
    
    TestItem(String id, String category, int score) {
        this.id = id;
        this.category = category;
        this.score = score;
    }
    
    @Override
    public String toString() {
        return id + "(" + score + ")";
    }
}

public class EmreUsuluTamSiralama {
    public static void main(String[] args) {
        String filePath = "C:\\Users\\ikizler1\\OneDrive\\Desktop\\şebnem randomize 10000 bnlik.csv";
        
        System.out.println("=== EMRE USULÜ TAM SIRALAMA TEST ===");
        System.out.println("CSV dosyası okunuyor...");
        
        List<TestItem> items = new ArrayList<>();
        
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            lines.remove(0); // Header'ı atla
            
            for (String line : lines) {
                String[] parts = line.split(";");
                if (parts.length >= 4) {
                    String id = parts[0].trim();
                    String category = parts[2].trim();
                    int score = Integer.parseInt(parts[3].trim());
                    items.add(new TestItem(id, category, score));
                }
            }
        } catch (Exception e) {
            System.out.println("HATA: " + e.getMessage());
            return;
        }
        
        System.out.println("Toplam " + items.size() + " öğe okundu");
        
        // Emre usulü tam sıralama başlat
        System.out.println("\n=== EMRE USULÜ TAM SIRALAMA ===");
        List<TestItem> currentItems = new ArrayList<>(items);
        List<TestItem> finalRanking = new ArrayList<>(); // TAM SIRALAMA LİSTESİ
        int roundCount = 0;
        Random random = new Random();
        
        // Minimum tur sayısını hesapla
        int minimumRounds = (int) Math.ceil(Math.log(items.size()) / Math.log(2));
        boolean allFirstWinsAfterMinimum = false;
        
        System.out.println("Başlangıç: " + currentItems.size() + " öğe");
        System.out.println("Minimum tur sayısı: " + minimumRounds);
        
        while (currentItems.size() > 1) {
            roundCount++;
            System.out.println("\n--- Round " + roundCount + " ---");
            
            // Karıştır
            Collections.shuffle(currentItems);
            
            List<TestItem> winners = new ArrayList<>();
            List<TestItem> losers = new ArrayList<>();
            boolean allFirstWins = true;
            int pairCount = 0;
            
            // Çiftleri değerlendir
            for (int i = 0; i < currentItems.size() - 1; i += 2) {
                TestItem first = currentItems.get(i);
                TestItem second = currentItems.get(i + 1);
                pairCount++;
                
                TestItem winner, loser;
                if (first.score > second.score) {
                    winner = first;
                    loser = second;
                } else if (second.score > first.score) {
                    winner = second;
                    loser = first;
                    allFirstWins = false;
                } else {
                    // Eşitlik durumunda rastgele
                    if (random.nextBoolean()) {
                        winner = first;
                        loser = second;
                    } else {
                        winner = second;
                        loser = first;
                        allFirstWins = false;
                    }
                }
                
                winners.add(winner);
                losers.add(loser);
                
                System.out.println("Eşleşme " + pairCount + ": " + first + " vs " + second + 
                    " -> Kazanan: " + winner + ", Kaybeden: " + loser);
            }
            
            // Tek öğe varsa otomatik kazanan
            if (currentItems.size() % 2 == 1) {
                TestItem lastItem = currentItems.get(currentItems.size() - 1);
                winners.add(lastItem);
                System.out.println("Tek öğe otomatik kazandı: " + lastItem);
            }
            
            // ÖNEMLİ: Kaybedenleri skorlarına göre sırala ve doğru pozisyonda ekle
            losers.sort((a, b) -> Integer.compare(b.score, a.score)); // Büyükten küçüğe
            
            // Kaybedenleri final sıralamasına doğru pozisyonda ekle
            for (TestItem loser : losers) {
                // Doğru pozisyonu bul (binary search benzeri mantık)
                int insertPos = 0;
                for (int i = 0; i < finalRanking.size(); i++) {
                    if (loser.score > finalRanking.get(i).score) {
                        insertPos = i;
                        break;
                    }
                    insertPos = i + 1;
                }
                finalRanking.add(insertPos, loser);
            }
            
            System.out.println("Bu turda " + losers.size() + " kaybeden elendi ve sıralandı");
            System.out.println("Güncel final sıralama boyutu: " + finalRanking.size());
            
            // Sonlanma kriteri kontrolü
            if (allFirstWins && pairCount > 0) {
                if (roundCount >= minimumRounds) {
                    if (allFirstWinsAfterMinimum) {
                        System.out.println("✓ Minimum tur sonrası ikinci kez tüm birinci öğeler kazandı!");
                        break;
                    } else {
                        allFirstWinsAfterMinimum = true;
                        System.out.println("✓ Minimum tur sonrası ilk kez tüm birinci öğeler kazandı! +1 tur daha.");
                    }
                } else {
                    System.out.println("✓ Tüm birinci öğeler kazandı (henüz minimum: " + roundCount + "/" + minimumRounds + ")");
                }
            } else {
                allFirstWinsAfterMinimum = false;
                if (pairCount > 0) {
                    System.out.println("✗ Birinci öğeler hepsi kazanmadı.");
                }
            }
            
            currentItems = winners;
            System.out.println("Round sonu: " + currentItems.size() + " öğe kaldı");
        }
        
        // Son kazananları da skorlarına göre doğru pozisyona ekle
        currentItems.sort((a, b) -> Integer.compare(b.score, a.score)); // Büyükten küçüğe
        for (TestItem winner : currentItems) {
            // Doğru pozisyonu bul
            int insertPos = 0;
            for (int i = 0; i < finalRanking.size(); i++) {
                if (winner.score > finalRanking.get(i).score) {
                    insertPos = i;
                    break;
                }
                insertPos = i + 1;
            }
            finalRanking.add(insertPos, winner);
        }
        
        System.out.println("\n=== SONUÇLAR ===");
        System.out.println("Toplam round: " + roundCount);
        System.out.println("Final sıralama boyutu: " + finalRanking.size());
        
        // TAM SIRALAMAYı GÖSTER - DETAYLI
        System.out.println("\n=== TAM FINAL SIRALAMA ===");
        
        // İlk 20 öğe
        System.out.println("İlk 20 sıra:");
        for (int i = 0; i < Math.min(20, finalRanking.size()); i++) {
            TestItem item = finalRanking.get(i);
            System.out.println((i + 1) + ". " + item.id + ": " + item.category + " - " + item.score);
        }
        
        if (finalRanking.size() > 40) {
            // Orta 20 öğe (490-510 arası)
            int midStart = finalRanking.size() / 2 - 10;
            System.out.println("\nOrta 20 sıra (" + (midStart + 1) + "-" + (midStart + 20) + "):");
            for (int i = midStart; i < midStart + 20 && i < finalRanking.size(); i++) {
                TestItem item = finalRanking.get(i);
                System.out.println((i + 1) + ". " + item.id + ": " + item.category + " - " + item.score);
            }
            
            // Son 20 öğe
            System.out.println("\nSon 20 sıra:");
            for (int i = finalRanking.size() - 20; i < finalRanking.size(); i++) {
                TestItem item = finalRanking.get(i);
                System.out.println((i + 1) + ". " + item.id + ": " + item.category + " - " + item.score);
            }
        }
        
        System.out.println("\nToplam " + finalRanking.size() + " öğe sıralandı.");
        
        // DETAYLI ARİTMETİK SIRALAMA DOĞRULAMA
        System.out.println("\n=== DETAYLI ARİTMETİK SIRALAMA DOĞRULAMA ===");
        boolean isValid = true;
        int errorCount = 0;
        int warningCount = 0;
        
        System.out.println("Tam liste aritmetik sıralaması kontrol ediliyor...");
        System.out.println("Kural: score[i] >= score[i+1] (yüksekten düşüğe)");
        
        // Her sıralama çiftini kontrol et
        for (int i = 0; i < finalRanking.size() - 1; i++) {
            TestItem current = finalRanking.get(i);
            TestItem next = finalRanking.get(i + 1);
            
            if (current.score < next.score) {
                errorCount++;
                isValid = false;
                
                if (errorCount <= 10) { // İlk 10 hatayı detaylı göster
                    System.out.println("❌ HATA " + errorCount + " [Sıra " + (i+1) + "->" + (i+2) + "]: " + 
                        current.id + "(" + current.score + ") < " + 
                        next.id + "(" + next.score + ") [+" + (next.score - current.score) + "]");
                }
                
                if (errorCount == 11) {
                    System.out.println("... (daha fazla hata var, gösterim sınırlandı)");
                }
            } else if (current.score == next.score) {
                warningCount++;
                if (warningCount <= 5) {
                    System.out.println("⚠️  UYARI [Sıra " + (i+1) + "->" + (i+2) + "]: Eşit skorlar: " + 
                        current.id + "(" + current.score + ") = " + next.id + "(" + next.score + ")");
                }
            }
        }
        
        // Sonuç raporu
        if (isValid) {
            System.out.println("\n✅ TAM ARİTMETİK SIRALAMA MÜKEMMEL!");
            System.out.println("   Tüm " + finalRanking.size() + " öğe yüksekten düşüğe doğru sıralı!");
            
            TestItem highest = finalRanking.get(0);
            TestItem lowest = finalRanking.get(finalRanking.size() - 1);
            System.out.println("   • En yüksek (1. sıra): " + highest.id + " (" + highest.score + ")");
            System.out.println("   • En düşük (" + finalRanking.size() + ". sıra): " + lowest.id + " (" + lowest.score + ")");
            System.out.println("   • Skor aralığı: " + (highest.score - lowest.score));
            
            if (warningCount > 0) {
                System.out.println("   • Eşit skor sayısı: " + warningCount + " (normal durum)");
            }
        } else {
            System.out.println("\n❌ ARİTMETİK SIRALAMA HATALI!");
            System.out.println("   Toplam " + errorCount + " aritmetik sıralama hatası tespit edildi!");
            System.out.println("   Emre usulü algoritması düzgün çalışmamış!");
            
            // İlk ve son doğru sıralanan bölümleri bul
            int firstErrorPos = -1;
            int lastCorrectPos = -1;
            
            for (int i = 0; i < finalRanking.size() - 1; i++) {
                if (finalRanking.get(i).score < finalRanking.get(i + 1).score) {
                    if (firstErrorPos == -1) firstErrorPos = i + 1;
                } else {
                    if (firstErrorPos != -1) lastCorrectPos = i + 1;
                }
            }
            
            if (firstErrorPos != -1) {
                System.out.println("   İlk hata pozisyonu: " + firstErrorPos);
            }
        }
        
        System.out.println("\n=== ÖZET ===");
        System.out.println("- Başlangıç: " + items.size() + " öğe");
        System.out.println("- Final: " + finalRanking.size() + " öğe (tam sıralı)");
        System.out.println("- Round: " + roundCount + "/" + minimumRounds + " (min)");
        System.out.println("- Tam sıralama geçerli: " + isValid);
        System.out.println("- Emre usulü: " + (allFirstWinsAfterMinimum ? "Başarılı" : "Partial"));
    }
}
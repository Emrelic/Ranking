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

public class EmreTest {
    public static void main(String[] args) {
        String filePath = "C:\\Users\\ikizler1\\OneDrive\\Desktop\\şebnem randomize 10000 bnlik.csv";
        
        System.out.println("=== EMRE USULÜ TEST ===");
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
        
        // İlk 5 ve son 5 öğeyi göster
        System.out.println("\nİlk 5 öğe:");
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            TestItem item = items.get(i);
            System.out.println(item.id + ": " + item.category + " - " + item.score);
        }
        
        // Emre usulü başlat
        System.out.println("\n=== EMRE USULÜ ALGORITMA ===");
        List<TestItem> currentItems = new ArrayList<>(items);
        int roundCount = 0;
        Random random = new Random();
        
        // Minimum tur sayısını hesapla: log2(öğe_sayısı) yukarı yuvarlama
        int minimumRounds = (int) Math.ceil(Math.log(items.size()) / Math.log(2));
        boolean allFirstWinsAfterMinimum = false;
        
        System.out.println("Başlangıç: " + currentItems.size() + " öğe");
        System.out.println("Minimum tur sayısı: " + minimumRounds + " (log2(" + items.size() + ") = " + (Math.log(items.size()) / Math.log(2)) + ")");
        
        while (currentItems.size() > 1) {
            roundCount++;
            System.out.println("\n--- Round " + roundCount + " ---");
            
            // Karıştır
            Collections.shuffle(currentItems);
            
            List<TestItem> winners = new ArrayList<>();
            boolean allFirstWins = true;
            int pairCount = 0;
            
            // Çiftleri değerlendir
            for (int i = 0; i < currentItems.size() - 1; i += 2) {
                TestItem first = currentItems.get(i);
                TestItem second = currentItems.get(i + 1);
                pairCount++;
                
                TestItem winner;
                if (first.score > second.score) {
                    winner = first;
                } else if (second.score > first.score) {
                    winner = second;
                    allFirstWins = false;
                } else {
                    winner = random.nextBoolean() ? first : second;
                    if (winner == second) allFirstWins = false;
                }
                
                winners.add(winner);
                System.out.println("Eşleşme " + pairCount + ": " + first + " vs " + second + " -> " + winner);
            }
            
            // Tek öğe varsa
            if (currentItems.size() % 2 == 1) {
                TestItem lastItem = currentItems.get(currentItems.size() - 1);
                winners.add(lastItem);
                System.out.println("Tek öğe otomatik geçti: " + lastItem);
            }
            
            // Yeni sonlanma kriteri kontrolü
            if (allFirstWins && pairCount > 0) {
                if (roundCount >= minimumRounds) {
                    if (allFirstWinsAfterMinimum) {
                        System.out.println("✓ Minimum tur sonrası ikinci kez tüm birinci öğeler kazandı! Algoritma tamamlanıyor.");
                        currentItems = winners;
                        break;
                    } else {
                        allFirstWinsAfterMinimum = true;
                        System.out.println("✓ Minimum tur sonrası ilk kez tüm birinci öğeler kazandı! +1 tur daha.");
                    }
                } else {
                    System.out.println("✓ Tüm birinci öğeler kazandı (henüz minimum tur: " + roundCount + "/" + minimumRounds + ")");
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
        
        System.out.println("\n=== SONUÇLAR ===");
        System.out.println("Toplam round: " + roundCount);
        System.out.println("Minimum round: " + minimumRounds);
        System.out.println("Sonlanma nedeni: " + (allFirstWinsAfterMinimum ? "Yeni kriter (min + 1 tur)" : "Tek öğe kaldı"));
        
        System.out.println("\nFinal Sıralama (" + currentItems.size() + " öğe):");
        for (int i = 0; i < currentItems.size(); i++) {
            TestItem item = currentItems.get(i);
            System.out.println((i + 1) + ". " + item.id + ": " + item.category + " - " + item.score);
        }
        
        // Detaylı doğrulama
        System.out.println("\n=== DETAYLI SIRALAMA DOĞRULAMA ===");
        boolean isValid = true;
        int errorCount = 0;
        
        if (currentItems.size() <= 1) {
            System.out.println("✓ Tek öğe kaldı, sıralama geçerli.");
        } else {
            System.out.println("Final liste sıralaması kontrol ediliyor...");
            
            // Tüm liste boyunca kontrol et
            for (int i = 0; i < currentItems.size() - 1; i++) {
                TestItem current = currentItems.get(i);
                TestItem next = currentItems.get(i + 1);
                
                if (current.score < next.score) {
                    System.out.println("✗ HATA [" + (i+1) + "->" + (i+2) + "]: " + 
                        current.id + "(" + current.score + ") < " + 
                        next.id + "(" + next.score + ") - Fark: " + (next.score - current.score));
                    isValid = false;
                    errorCount++;
                }
            }
            
            if (isValid) {
                System.out.println("✓ TAM SIRALAMA DOĞRU: Tüm " + currentItems.size() + " öğe matematiksel sıralamada!");
                
                // İstatistikler
                TestItem highest = currentItems.get(0);
                TestItem lowest = currentItems.get(currentItems.size() - 1);
                System.out.println("  • En yüksek: " + highest.id + " (" + highest.score + ")");
                System.out.println("  • En düşük: " + lowest.id + " (" + lowest.score + ")");
                System.out.println("  • Skor aralığı: " + (highest.score - lowest.score));
            } else {
                System.out.println("✗ SIRALAMA HATALI: " + errorCount + " hata tespit edildi!");
                
                // En büyük hataları göster
                System.out.println("\nEn büyük sıralama hataları:");
                int maxErrorsToShow = Math.min(5, errorCount);
                int shownErrors = 0;
                
                for (int i = 0; i < currentItems.size() - 1 && shownErrors < maxErrorsToShow; i++) {
                    TestItem current = currentItems.get(i);
                    TestItem next = currentItems.get(i + 1);
                    
                    if (current.score < next.score) {
                        int diff = next.score - current.score;
                        System.out.println("  " + (shownErrors + 1) + ". Sıra " + (i+1) + "-" + (i+2) + 
                            ": " + current.id + "(" + current.score + ") < " + 
                            next.id + "(" + next.score + ") [+" + diff + "]");
                        shownErrors++;
                    }
                }
            }
            
            // Rastgele kontrol örnekleri
            System.out.println("\nFinal liste (ilk 10 sıra):");
            for (int i = 0; i < Math.min(10, currentItems.size()); i++) {
                TestItem item = currentItems.get(i);
                System.out.println("  " + (i+1) + ". " + item.id + ": " + item.score);
            }
        }
        
        System.out.println("\nÖZET:");
        System.out.println("- Başlangıç: " + items.size() + " öğe");
        System.out.println("- Final: " + currentItems.size() + " öğe");
        System.out.println("- Round: " + roundCount + "/" + minimumRounds + " (min)");
        System.out.println("- Geçerli: " + isValid);
        System.out.println("- Sonlanma: " + (allFirstWinsAfterMinimum ? "Yeni kriter başarılı" : "Tek öğe"));
    }
}
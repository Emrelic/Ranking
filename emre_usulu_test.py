#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import csv
import random
from typing import List, Tuple

def read_csv_data(file_path: str) -> List[Tuple[str, str, int]]:
    """CSV dosyasını okur ve (id, category, score) tuple'ları döner"""
    data = []
    with open(file_path, 'r', encoding='utf-8-sig') as file:
        reader = csv.reader(file, delimiter=';')
        next(reader)  # Header'ı atla
        
        for row in reader:
            if len(row) >= 4:
                item_id = row[0]
                category = row[2]  # C sütunu
                score = int(row[3])  # D sütunu (sayısal değer)
                data.append((item_id, category, score))
    
    return data

def emre_usulu_ranking(data: List[Tuple[str, str, int]]) -> List[Tuple[str, str, int]]:
    """
    Emre usulü sıralama algoritması
    Sonlanma kriteri: İki sefer üst üste tüm eşleşmelerde birinci öğenin üstün gelmesi
    """
    items = data.copy()
    consecutive_wins = 0
    round_count = 0
    
    print(f"Başlangıç: {len(items)} öğe")
    
    while consecutive_wins < 2:
        round_count += 1
        print(f"\n--- Round {round_count} ---")
        
        # Rastgele eşleştir
        random.shuffle(items)
        pairs = []
        
        # Çiftleri oluştur
        for i in range(0, len(items) - 1, 2):
            pairs.append((items[i], items[i + 1]))
        
        # Eğer tek sayıda öğe varsa, son öğe otomatik olarak geçer
        if len(items) % 2 == 1:
            winner_items = [items[-1]]
            print(f"Tek öğe otomatik geçti: {items[-1][0]}")
        else:
            winner_items = []
        
        # Eşleşmeleri değerlendir
        all_first_wins = True
        for i, (item1, item2) in enumerate(pairs):
            # Yüksek skor kazanır
            if item1[2] > item2[2]:
                winner = item1
                first_wins = True
            elif item2[2] > item1[2]:
                winner = item2
                first_wins = False
            else:
                # Eşitlik durumunda rastgele seç
                winner = random.choice([item1, item2])
                first_wins = (winner == item1)
            
            winner_items.append(winner)
            print(f"Eşleşme {i+1}: {item1[0]}({item1[2]}) vs {item2[0]}({item2[2]}) -> Kazanan: {winner[0]}({winner[2]})")
            
            if not first_wins:
                all_first_wins = False
        
        # Sonlanma kriterini kontrol et
        if all_first_wins and len(pairs) > 0:
            consecutive_wins += 1
            print(f"Bu roundda tüm birinci öğeler kazandı! Ardışık kazanma: {consecutive_wins}")
        else:
            consecutive_wins = 0
            if len(pairs) > 0:
                print("Bu roundda tüm birinci öğeler kazanmadı. Ardışık sayaç sıfırlandı.")
        
        items = winner_items
        print(f"Round sonu: {len(items)} öğe kaldı")
        
        # Sonsuz döngüyü önlemek için
        if len(items) <= 1:
            break
    
    print(f"\nAlgoritma {round_count} round sonra tamamlandı!")
    return items

def validate_ranking(final_ranking: List[Tuple[str, str, int]]) -> bool:
    """
    Final sıralamanın doğruluğunu test eder
    Yüksek skorlar daha üstte olmalı
    """
    print("\n=== Sıralama Doğrulama ===")
    
    if len(final_ranking) <= 1:
        print("Tek öğe kaldı, sıralama geçerli.")
        return True
    
    # Skorları kontrol et
    for i in range(len(final_ranking) - 1):
        current_score = final_ranking[i][2]
        next_score = final_ranking[i + 1][2]
        
        if current_score < next_score:
            print(f"HATA: {final_ranking[i][0]}({current_score}) < {final_ranking[i+1][0]}({next_score})")
            return False
    
    print("Sıralama doğru: Yüksek skorlar üstte!")
    return True

def main():
    file_path = r"C:\Users\ikizler1\OneDrive\Desktop\şebnem randomize 10000 bnlik.csv"
    
    print("CSV dosyası okunuyor...")
    data = read_csv_data(file_path)
    print(f"Toplam {len(data)} öğe okundu")
    
    # İlk 10 öğeyi göster
    print("\nİlk 10 öğe:")
    for i, (item_id, category, score) in enumerate(data[:10]):
        print(f"{item_id}: {category} - {score}")
    
    print("\nEmre usulü sıralama başlıyor...")
    final_ranking = emre_usulu_ranking(data)
    
    print(f"\nFinal Sıralama ({len(final_ranking)} öğe):")
    for i, (item_id, category, score) in enumerate(final_ranking):
        print(f"{i+1}. {item_id}: {category} - {score}")
    
    # Doğrulama
    is_valid = validate_ranking(final_ranking)
    print(f"\nSıralama geçerli: {is_valid}")

if __name__ == "__main__":
    main()
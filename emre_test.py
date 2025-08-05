#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Emre Usulü Test Simülasyonu
Bu script CSV dosyasını okur ve Emre usulü ile sıralama yapar
"""

import csv
import math

class Song:
    def __init__(self, id, name, album, score):
        self.id = id
        self.name = name
        self.album = album
        self.score = int(score)
    
    def __repr__(self):
        return f"Song({self.id}, {self.score})"

def read_csv_data(filename):
    """CSV dosyasını okur ve Song listesi döner"""
    songs = []
    with open(filename, 'r', encoding='utf-8-sig') as file:
        reader = csv.reader(file, delimiter=';')
        next(reader)  # Skip header
        for row in reader:
            if len(row) >= 4:
                songs.append(Song(row[0], row[1], row[2], row[3]))
    return songs

def create_emre_matches(songs):
    """Ardışık çiftler halinde eşleşme oluşturur"""
    matches = []
    for i in range(0, len(songs) - 1, 2):
        matches.append((songs[i], songs[i + 1]))
    return matches

def simulate_match_results(matches):
    """Yüksek skor kazanır mantığıyla eşleşme sonuçlarını simüle eder"""
    results = []
    for song1, song2 in matches:
        if song1.score > song2.score:
            results.append((song1, song2))  # (winner, loser)
        else:
            results.append((song2, song1))  # (winner, loser)
    return results

def reorder_after_round(match_results):
    """Emre usulü mantığı: kazananlar üste, kaybedenler alta"""
    winners = []
    losers = []
    
    for winner, loser in match_results:
        winners.append(winner)
        losers.append(loser)
    
    return winners + losers

def check_completion(songs, match_results):
    """Tüm eşleşmelerde ilk öğe kazandı mı kontrol eder"""
    for i, (winner, loser) in enumerate(match_results):
        current_pair_first = songs[i * 2]
        if winner.id != current_pair_first.id:
            return False
    return True

def emre_method_simulation(songs):
    """Emre usulü simülasyonu"""
    current_songs = songs.copy()
    round_num = 1
    max_rounds = math.ceil(math.log2(len(songs)))
    
    print(f"Başlangıç listesi: {len(current_songs)} öğe")
    print("İlk 10 öğe:", [(s.id, s.score) for s in current_songs[:10]])
    print("Son 10 öğe:", [(s.id, s.score) for s in current_songs[-10:]])
    print("-" * 80)
    
    while round_num <= max_rounds:
        print(f"\n=== TUR {round_num} ===")
        
        # Eşleşmeler oluştur
        matches = create_emre_matches(current_songs)
        print(f"Eşleşme sayısı: {len(matches)}")
        
        # İlk birkaç eşleşmeyi göster
        print("İlk 5 eşleşme:")
        for i, (song1, song2) in enumerate(matches[:5]):
            winner = song1 if song1.score > song2.score else song2
            print(f"  {song1.id}({song1.score}) vs {song2.id}({song2.score}) → Kazanan: {winner.id}({winner.score})")
        
        # Sonuçları simüle et
        match_results = simulate_match_results(matches)
        
        # Tamamlanma kontrolü
        if check_completion(current_songs, match_results):
            print(f"\n✅ TUR {round_num}'de sıralama tamamlandı!")
            print("Tüm eşleşmelerde ilk sıradaki öğe kazandı.")
            break
        
        # Yeniden sıralama
        current_songs = reorder_after_round(match_results)
        
        print(f"Tur {round_num} sonrası sıralama:")
        print("İlk 10:", [(s.id, s.score) for s in current_songs[:10]])
        print("Son 10:", [(s.id, s.score) for s in current_songs[-10:]])
        
        round_num += 1
    
    return current_songs

def verify_sorting(final_songs):
    """Final sıralamanın doğru olup olmadığını kontrol eder"""
    print("\n" + "=" * 80)
    print("FINAL SIRALAMA DOĞRULAMA")
    print("=" * 80)
    
    is_sorted = True
    for i in range(len(final_songs) - 1):
        if final_songs[i].score < final_songs[i + 1].score:
            is_sorted = False
            print(f"❌ HATA: Pozisyon {i+1}: {final_songs[i].score} < Pozisyon {i+2}: {final_songs[i+1].score}")
    
    if is_sorted:
        print("✅ Sıralama DOĞRU! Yüksekten düşüğe sıralanmış.")
    else:
        print("❌ Sıralama YANLIŞ!")
    
    print(f"\nEn yüksek 10:")
    for i, song in enumerate(final_songs[:10]):
        print(f"  {i+1:2d}. ID:{song.id:2s} Score:{song.score:3d}")
    
    print(f"\nEn düşük 10:")
    for i, song in enumerate(final_songs[-10:]):
        print(f"  {len(final_songs)-10+i+1:2d}. ID:{song.id:2s} Score:{song.score:3d}")
    
    # İstatistikler
    scores = [s.score for s in final_songs]
    print(f"\nİstatistikler:")
    print(f"  Toplam öğe: {len(final_songs)}")
    print(f"  En yüksek: {max(scores)}")
    print(f"  En düşük: {min(scores)}")
    print(f"  Ortalama: {sum(scores)/len(scores):.1f}")
    
    return is_sorted

def main():
    # CSV dosyasını oku
    csv_file = r"C:\Users\ikizler1\OneDrive\Desktop\şebnem randomize 1000.csv"
    songs = read_csv_data(csv_file)
    
    print("CSV Dosyası Okundu")
    print(f"Toplam öğe sayısı: {len(songs)}")
    print(f"İlk öğe: ID={songs[0].id}, Score={songs[0].score}")
    print(f"Son öğe: ID={songs[-1].id}, Score={songs[-1].score}")
    
    # Emre usulü simülasyonu
    final_songs = emre_method_simulation(songs)
    
    # Sonuçları doğrula
    verify_sorting(final_songs)

if __name__ == "__main__":
    main()
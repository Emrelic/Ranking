"""
Telefon Verisi ile Emre Usulü Test
79 takım - gerçek performans skorları ile test
"""
import random

class EmreTeam:
    def __init__(self, id, name, performance_score, original_order):
        self.id = id
        self.name = name
        self.performance_score = performance_score  # Gerçek performans skoru
        self.points = 0.0                          # Turnuva puanı
        self.current_position = original_order      # Mevcut sıralama pozisyonu
        self.original_order = original_order        # Orijinal sıra
        self.bye_passed = False

def generate_realistic_scores():
    """Gerçekçi performans skorları oluştur (0-100 arası)"""
    scores = []
    
    # Çok iyi (90-100): 8 takım
    scores.extend([random.randint(90, 100) for _ in range(8)])
    
    # İyi (80-89): 12 takım  
    scores.extend([random.randint(80, 89) for _ in range(12)])
    
    # Orta üstü (70-79): 15 takım
    scores.extend([random.randint(70, 79) for _ in range(15)])
    
    # Orta (60-69): 18 takım
    scores.extend([random.randint(60, 69) for _ in range(18)])
    
    # Orta altı (50-59): 14 takım
    scores.extend([random.randint(50, 59) for _ in range(14)])
    
    # Kötü (30-49): 10 takım
    scores.extend([random.randint(30, 49) for _ in range(10)])
    
    # Çok kötü (10-29): 2 takım
    scores.extend([random.randint(10, 29) for _ in range(2)])
    
    random.shuffle(scores)
    return scores

def create_emre_teams():
    """79 takım oluştur"""
    scores = generate_realistic_scores()
    teams = []
    
    for i in range(79):
        team = EmreTeam(
            id=i+1,
            name=f"Liste{i+1}",
            performance_score=scores[i],
            original_order=i+1
        )
        teams.append(team)
    
    return teams

def handle_bye_team(sorted_teams):
    """Bye geçecek takımı belirle (en alttaki)"""
    if len(sorted_teams) % 2 == 0:
        return sorted_teams, None
    
    bye_team = sorted_teams[-1]
    remaining_teams = sorted_teams[:-1]
    return remaining_teams, bye_team

def create_pairings(teams, match_history):
    """Eşleştirmeleri oluştur (sıralı: 1-2, 3-4, 5-6...)"""
    matches = []
    available_teams = teams[:]
    same_point_match = False
    
    while len(available_teams) >= 2:
        team1 = available_teams[0]
        team2 = None
        
        # team1 için uygun rakip bul
        for i in range(1, len(available_teams)):
            candidate = available_teams[i]
            pair1 = (team1.id, candidate.id)
            pair2 = (candidate.id, team1.id)
            
            # Daha önce eşleşmemiş mi kontrol et
            if pair1 not in match_history and pair2 not in match_history:
                team2 = candidate
                
                # Aynı puanda mı kontrol et
                if team1.points == candidate.points:
                    same_point_match = True
                
                break
        
        if team2:
            matches.append((team1, team2))
            available_teams.remove(team1)
            available_teams.remove(team2)
        else:
            # Bu takım için uygun rakip bulunamadı
            available_teams.remove(team1)
    
    return matches, same_point_match

def simulate_match_result(team1, team2):
    """Maç sonucunu simüle et (yüksek performans avantajlı)"""
    perf1 = team1.performance_score
    perf2 = team2.performance_score
    
    # Performans farkına göre kazanma olasılığı
    perf_diff = perf1 - perf2
    
    if perf_diff >= 20:
        team1_win_chance = 0.85  # Çok büyük fark
    elif perf_diff >= 10:
        team1_win_chance = 0.75  # Büyük fark
    elif perf_diff >= 5:
        team1_win_chance = 0.65  # Orta fark
    elif perf_diff > 0:
        team1_win_chance = 0.55  # Küçük fark
    elif perf_diff == 0:
        team1_win_chance = 0.33  # Eşit (beraberlik olasılığı yüksek)
    elif perf_diff >= -5:
        team1_win_chance = 0.45  # Küçük dezavantaj
    elif perf_diff >= -10:
        team1_win_chance = 0.35  # Orta dezavantaj
    elif perf_diff >= -20:
        team1_win_chance = 0.25  # Büyük dezavantaj
    else:
        team1_win_chance = 0.15  # Çok büyük dezavantaj
    
    random_val = random.random()
    
    if random_val < team1_win_chance:
        return team1, team2, "win"  # team1 kazandı
    elif random_val < team1_win_chance + (0.34 if perf_diff == 0 else 0.2):
        return team1, team2, "draw"  # Beraberlik
    else:
        return team2, team1, "win"  # team2 kazandı

def reorder_teams_emre_style(teams, match_results):
    """Takımları Emre usulü kurallarına göre yeniden sırala"""
    winners = []
    draws = []
    losers = []
    bye_teams = [team for team in teams if team.bye_passed]
    
    # Maç sonuçlarını işle
    for team1, team2, result in match_results:
        if result == "win":
            # team1 kazandı, team2 kaybetti
            winners.append(team1)
            losers.append(team2)
        elif result == "draw":
            # Beraberlik
            draws.extend([team1, team2])
    
    # Maçlara katılmayan takımları bul
    participated_ids = set()
    for team1, team2, _ in match_results:
        participated_ids.add(team1.id)
        participated_ids.add(team2.id)
    
    non_participated = [team for team in teams if team.id not in participated_ids and not team.bye_passed]
    
    # Yeni sıralama: Galipler -> Berabere -> Bye -> Kaybedenler -> Katılmayanlar
    new_order = winners + draws + bye_teams + losers + non_participated
    
    # Yeni pozisyonları ata
    for i, team in enumerate(new_order):
        team.current_position = i + 1
    
    return new_order

def run_emre_tournament():
    """Tam Emre usulü turnuva simülasyonu"""
    print("=" * 60)
    print("79 TAKIMLI EMRE USULÜ TURNUVA SİMÜLASYONU")
    print("=" * 60)
    
    # Takımları oluştur
    teams = create_emre_teams()
    match_history = set()
    round_number = 1
    
    # Başlangıç performans sıralaması
    initial_ranking = sorted(teams, key=lambda t: t.performance_score, reverse=True)
    
    print("\nBaşlangıç Performans Sıralaması (İlk 15):")
    for i, team in enumerate(initial_ranking[:15]):
        print(f"{i+1:2d}. {team.name} - Performans: {team.performance_score}")
    
    print(f"\nEn düşük performanslı 10 takım:")
    for i, team in enumerate(initial_ranking[-10:]):
        position = 79 - 10 + i + 1
        print(f"{position:2d}. {team.name} - Performans: {team.performance_score}")
    
    # Turnuva döngüsü
    while round_number <= 15:  # Maksimum 15 tur
        print(f"\n{'='*50}")
        print(f"TUR {round_number}")
        print(f"{'='*50}")
        
        # Mevcut sıralamayı göster
        current_ranking = sorted(teams, key=lambda t: t.current_position)
        print(f"\nMevcut İlk 10 Sıralama:")
        for team in current_ranking[:10]:
            print(f"  {team.current_position:2d}. {team.name} - {team.points} puan (Perf: {team.performance_score})")
        
        # Takımları mevcut pozisyonlarına göre sırala
        sorted_teams = sorted(teams, key=lambda t: t.current_position)
        
        # Bye kontrolü
        teams_to_match, bye_team = handle_bye_team(sorted_teams)
        
        # Eşleştirmeleri oluştur
        matches, same_point_match = create_pairings(teams_to_match, match_history)
        
        # Turnuva bitip bitmediğini kontrol et
        if not same_point_match and len(matches) == 0:
            print("\n🏁 TURNUVA TAMAMLANDI!")
            print("Hiçbir aynı puanlı eşleşme kalmadı.")
            break
        
        print(f"\nEşleşme Bilgileri:")
        print(f"- Toplam eşleşme: {len(matches)}")
        print(f"- Aynı puanlı eşleşme var mı: {'✅ Evet' if same_point_match else '❌ Hayır'}")
        
        # Bye geçen takım
        if bye_team:
            print(f"- 🔄 Bye geçen: {bye_team.name} (Perf: {bye_team.performance_score}) (+1 puan)")
            bye_team.points += 1.0
            bye_team.bye_passed = True
        
        # İlk 5 eşleşmeyi göster
        print(f"\nİlk 5 Eşleşme:")
        for i, (team1, team2) in enumerate(matches[:5]):
            perf_diff = abs(team1.performance_score - team2.performance_score)
            same_point = "✅" if team1.points == team2.points else "❌"
            print(f"  {i+1}. {team1.name}(P:{team1.performance_score}/{team1.points}p) vs {team2.name}(P:{team2.performance_score}/{team2.points}p) {same_point} [Fark:{perf_diff}]")
        
        # Maç sonuçlarını simüle et
        match_results = []
        win_count = 0
        draw_count = 0
        
        for team1, team2 in matches:
            winner, loser, result_type = simulate_match_result(team1, team2)
            match_results.append((winner, loser, result_type))
            
            # Maç geçmişine ekle
            match_history.add((team1.id, team2.id))
            match_history.add((team2.id, team1.id))
            
            # Puanları güncelle
            if result_type == "win":
                winner.points += 1.0
                win_count += 1
            elif result_type == "draw":
                team1.points += 0.5
                team2.points += 0.5
                draw_count += 1
        
        # Takımları yeniden sırala
        teams = reorder_teams_emre_style(teams, match_results)
        
        print(f"\nTur Sonu İstatistikleri:")
        print(f"- Kazanan-kaybeden: {win_count} maç")
        print(f"- Beraberlik: {draw_count} maç")
        
        round_number += 1
    
    # Final analizi
    print(f"\n{'='*60}")
    print("FİNAL ANALİZİ")
    print(f"{'='*60}")
    
    final_ranking = sorted(teams, key=lambda t: t.current_position)
    
    # İlk 15 takım
    print(f"\nEmre Usulü Final Sıralaması (İlk 15):")
    for team in final_ranking[:15]:
        original_rank = next(i for i, t in enumerate(initial_ranking) if t.id == team.id) + 1
        rank_change = original_rank - team.current_position
        
        if rank_change > 0:
            change_indicator = f"⬆️+{rank_change}"
        elif rank_change < 0:
            change_indicator = f"⬇️{rank_change}"
        else:
            change_indicator = "➡️0"
        
        print(f"{team.current_position:2d}. {team.name} - {team.points} puan (Perf: {team.performance_score}) [Orijinal: {original_rank}] {change_indicator}")
    
    # Son 10 takım
    print(f"\nSon 10 Takım:")
    for team in final_ranking[-10:]:
        original_rank = next(i for i, t in enumerate(initial_ranking) if t.id == team.id) + 1
        rank_change = original_rank - team.current_position
        
        if rank_change > 0:
            change_indicator = f"⬆️+{rank_change}"
        elif rank_change < 0:
            change_indicator = f"⬇️{rank_change}"
        else:
            change_indicator = "➡️0"
        
        print(f"{team.current_position:2d}. {team.name} - {team.points} puan (Perf: {team.performance_score}) [Orijinal: {original_rank}] {change_indicator}")
    
    # Korelasyon analizi
    correlation_data = []
    for team in teams:
        original_rank = next(i for i, t in enumerate(initial_ranking) if t.id == team.id) + 1
        correlation_data.append((team.performance_score, original_rank, team.current_position))
    
    # Performans vs Final sıralama korelasyonu hesapla
    performances = [data[0] for data in correlation_data]
    final_ranks = [data[2] for data in correlation_data]
    
    avg_perf = sum(performances) / len(performances)
    avg_final = sum(final_ranks) / len(final_ranks)
    
    numerator = sum((perf - avg_perf) * (final_rank - avg_final) for perf, _, final_rank in correlation_data)
    
    perf_var = sum((perf - avg_perf) ** 2 for perf in performances)
    final_var = sum((final_rank - avg_final) ** 2 for final_rank in final_ranks)
    
    denominator = (perf_var * final_var) ** 0.5
    
    correlation = -numerator / denominator if denominator != 0 else 0  # Negatif çünkü yüksek perf = düşük sıra
    
    print(f"\n=== SONUÇ ANALİZİ ===")
    print(f"Toplam tur sayısı: {round_number - 1}")
    print(f"Performans vs Emre Usulü Sıralaması Korelasyonu: {correlation:.3f}")
    print(f"(1.0 = mükemmel uyum, 0.0 = hiç uyum yok)")
    
    if correlation > 0.7:
        print("✅ MÜKEMMEL: Emre usulü gerçek performansı çok iyi yansıtıyor!")
    elif correlation > 0.5:
        print("✅ İYİ: Emre usulü performansı iyi yansıtıyor!")
    elif correlation > 0.3:
        print("⚠️ ORTA: Emre usulü performansı kısmen yansıtıyor.")
    else:
        print("❌ ZAYIF: Emre usulü performansı zayıf yansıtıyor.")
    
    # En büyük değişiklikler
    biggest_changes = []
    for team in teams:
        original_rank = next(i for i, t in enumerate(initial_ranking) if t.id == team.id) + 1
        change = original_rank - team.current_position
        biggest_changes.append((team.name, change, team.performance_score))
    
    biggest_changes.sort(key=lambda x: abs(x[1]), reverse=True)
    
    print(f"\nEn Büyük Sıralama Değişiklikleri:")
    for name, change, perf in biggest_changes[:5]:
        direction = "⬆️ Yükseldi" if change > 0 else "⬇️ Düştü"
        print(f"{name} (Perf: {perf}) - {direction} {abs(change)} sıra")

if __name__ == "__main__":
    run_emre_tournament()
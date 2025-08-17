"""
Emre Usulü Sıralama Algoritması Test (Python Simülasyonu)
80 takımlı gelişmiş Swiss-style turnuva sistemi
"""
import random

class Team:
    def __init__(self, id, name, original_order):
        self.id = id
        self.name = name
        self.points = 0.0
        self.original_order = original_order
        self.bye_passed = False

class EmreSystem:
    @staticmethod
    def initialize_tournament(teams):
        return [Team(i, f"Team{i}", i) for i in range(1, teams + 1)]
    
    @staticmethod
    def handle_bye_team(sorted_teams):
        """Tek sayıda takım varsa bye geçecek takımı belirle"""
        if len(sorted_teams) % 2 == 0:
            return sorted_teams, None
        
        # En az puanlı takım bye geçer
        bye_team = sorted_teams[-1]
        remaining_teams = sorted_teams[:-1]
        return remaining_teams, bye_team
    
    @staticmethod
    def create_pairings(teams, match_history):
        """Eşleştirmeleri oluştur"""
        matches = []
        available_teams = teams[:]
        
        # Puana göre grupla
        teams_by_points = {}
        for team in teams:
            if team.points not in teams_by_points:
                teams_by_points[team.points] = []
            teams_by_points[team.points].append(team)
        
        # Yüksek puandan başlayarak eşleştir
        for points in sorted(teams_by_points.keys(), reverse=True):
            point_group = [t for t in teams_by_points[points] if t in available_teams]
            
            # Bu gruptaki takımları eşleştir
            while len(point_group) >= 2:
                team1 = point_group[0]
                team2 = None
                
                # team1 için uygun rakip bul (daha önce eşleşmemiş)
                for candidate in point_group[1:]:
                    pair1 = (team1.id, candidate.id)
                    pair2 = (candidate.id, team1.id)
                    
                    if pair1 not in match_history and pair2 not in match_history:
                        team2 = candidate
                        break
                
                if team2:
                    matches.append((team1, team2))
                    point_group.remove(team1)
                    point_group.remove(team2)
                    available_teams.remove(team1)
                    available_teams.remove(team2)
                else:
                    # Bu grupta eşleşecek kimse yok
                    break
        
        # Kalan takımları farklı puan gruplarıyla eşleştir
        while len(available_teams) >= 2:
            team1 = available_teams[0]
            team2 = None
            
            for candidate in available_teams[1:]:
                pair1 = (team1.id, candidate.id)
                pair2 = (candidate.id, team1.id)
                
                if pair1 not in match_history and pair2 not in match_history:
                    team2 = candidate
                    break
            
            if team2:
                matches.append((team1, team2))
                available_teams.remove(team1)
                available_teams.remove(team2)
            else:
                break
        
        return matches
    
    @staticmethod
    def check_can_continue(teams, match_history):
        """Turnuvanın devam edip edemeyeceğini kontrol et"""
        # Aynı puanlı grupları kontrol et
        teams_by_points = {}
        for team in teams:
            if team.points not in teams_by_points:
                teams_by_points[team.points] = []
            teams_by_points[team.points].append(team)
        
        for points, point_group in teams_by_points.items():
            if len(point_group) < 2:
                continue
            
            # Bu puan grubundaki takımlar arasında eşleşilmemiş çift var mı?
            for i in range(len(point_group)):
                for j in range(i + 1, len(point_group)):
                    team1 = point_group[i]
                    team2 = point_group[j]
                    pair1 = (team1.id, team2.id)
                    pair2 = (team2.id, team1.id)
                    
                    if pair1 not in match_history and pair2 not in match_history:
                        return True  # En az bir eşleşme daha var
        
        return False  # Hiç eşleşecek takım kalmadı

def simulate_round_results(matches):
    """Rastgele maç sonuçları oluştur"""
    results = []
    for team1, team2 in matches:
        rand = random.random()
        if rand < 0.45:
            winner, loser = team1, team2
        elif rand < 0.90:
            winner, loser = team2, team1
        else:
            winner, loser = None, None  # Beraberlik
        
        results.append((team1, team2, winner))
    
    return results

def process_round_results(teams, match_results, bye_team, match_history):
    """Tur sonuçlarını işle"""
    # Bye geçen takıma puan ekle
    if bye_team:
        bye_team.points += 1.0
        bye_team.bye_passed = True
        print(f"🔄 Bye geçen: {bye_team.name} (+1 puan)")
    
    # Maç sonuçlarını işle
    for team1, team2, winner in match_results:
        # Maç geçmişine ekle
        match_history.add((team1.id, team2.id))
        match_history.add((team2.id, team1.id))
        
        if winner == team1:
            team1.points += 1.0
        elif winner == team2:
            team2.points += 1.0
        else:  # Beraberlik
            team1.points += 0.5
            team2.points += 0.5

def show_standings(teams, limit=10):
    """Güncel durumu göster"""
    sorted_teams = sorted(teams, key=lambda t: (-t.points, t.original_order))
    
    print("Güncel durum (ilk {}):".format(min(limit, len(sorted_teams))))
    for i, team in enumerate(sorted_teams[:limit]):
        bye_indicator = " [BYE]" if team.bye_passed else ""
        print(f"  {i + 1}. {team.name} - {team.points} puan{bye_indicator}")

def run_tournament(team_count):
    """Tam turnuva simülasyonu"""
    print(f"\n=== {team_count} TAKIM EMRE USULÜ TURNUVA ===")
    
    # Takımları oluştur
    teams = EmreSystem.initialize_tournament(team_count)
    match_history = set()
    round_number = 1
    
    while round_number <= 50:  # Maksimum 50 tur koruma
        print(f"\n--- TUR {round_number} ---")
        
        # Takımları puana göre sırala
        sorted_teams = sorted(teams, key=lambda t: (-t.points, t.original_order))
        
        # Bye kontrolü
        teams_to_match, bye_team = EmreSystem.handle_bye_team(sorted_teams)
        
        # Eşleştirmeleri oluştur
        matches = EmreSystem.create_pairings(teams_to_match, match_history)
        
        # Turnuva bitip bitmediğini kontrol et
        can_continue = EmreSystem.check_can_continue(teams_to_match, match_history)
        
        if not can_continue and len(matches) == 0:
            print("✅ Turnuva tamamlandı! Tüm eşleşmeler bitmiş.")
            break
        
        print(f"Oluşturulan eşleşme sayısı: {len(matches)}")
        
        # Sonuçları simüle et
        match_results = simulate_round_results(matches)
        
        # Sonuçları işle
        process_round_results(teams, match_results, bye_team, match_history)
        
        # Durumu göster
        show_standings(teams, 10)
        
        # Devam kontrolü
        if not can_continue:
            print("✅ Daha fazla eşleşme mümkün değil. Turnuva bitti.")
            break
        
        round_number += 1
    
    # Final sonuçları
    print(f"\n=== FİNAL SONUÇLARI ({team_count} TAKIM) ===")
    final_teams = sorted(teams, key=lambda t: (-t.points, t.original_order))
    
    print("İlk 10 sıralama:")
    for i, team in enumerate(final_teams[:10]):
        print(f"{i + 1}. {team.name} - {team.points} puan")
    
    if len(final_teams) > 20:
        print("\nSon 10 sıralama:")
        for i, team in enumerate(final_teams[-10:]):
            actual_position = len(final_teams) - 10 + i + 1
            print(f"{actual_position}. {team.name} - {team.points} puan")
    
    print(f"\nToplam tur sayısı: {round_number - 1}")
    
    # İstatistikler
    total_matches = sum(len(group) for group in match_history) // 2
    bye_count = sum(1 for team in teams if team.bye_passed)
    
    print(f"Toplam oynanan maç: {total_matches}")
    print(f"Bye geçen toplam: {bye_count}")

if __name__ == "__main__":
    print("=== GELİŞMİŞ EMRE USULÜ SIRALAMA SİSTEMİ TEST ===")
    
    # Test senaryoları
    run_tournament(80)  # 80 takım
    run_tournament(79)  # Tek sayı
    run_tournament(8)   # Küçük grup
    
    print("\n✅ Tüm test senaryoları tamamlandı!")
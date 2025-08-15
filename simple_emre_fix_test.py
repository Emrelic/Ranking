"""
Emre Usulü Tekrar Eşleşme Problemi Test
"""

def test_simple_scenario():
    print("=== 4 TAKIM EMRE USULÜ TEST ===")
    
    # Takımlar ve başlangıç durumu
    teams = [
        {"id": 1, "name": "Team1", "points": 0.0, "original_order": 0},
        {"id": 2, "name": "Team2", "points": 0.0, "original_order": 1},
        {"id": 3, "name": "Team3", "points": 0.0, "original_order": 2},
        {"id": 4, "name": "Team4", "points": 0.0, "original_order": 3}
    ]
    
    match_history = set()
    
    print("Başlangıç takımları:")
    for team in teams:
        print(f"  {team['name']} - {team['points']} puan")
    
    # İlk tur eşleştirmeleri
    print("\n=== TUR 1 ===")
    print("İlk tur eşleştirmeleri (puan sıralamasına göre):")
    
    # Puana göre sırala (hepsi 0 puanda)
    sorted_teams = sorted(teams, key=lambda t: (-t['points'], t['original_order']))
    
    first_round_matches = []
    # 1-2, 3-4 eşleşmesi (sıralı)
    for i in range(0, len(sorted_teams), 2):
        if i + 1 < len(sorted_teams):
            team1 = sorted_teams[i]
            team2 = sorted_teams[i + 1]
            first_round_matches.append((team1, team2))
            print(f"Maç {len(first_round_matches)}: {team1['name']} vs {team2['name']}")
    
    # Sonuçları simüle et (Team1 ve Team3 kazansın)
    print("\nİlk tur sonuçları:")
    for i, (team1, team2) in enumerate(first_round_matches):
        winner = team1  # Her zaman ilk takım kazansın
        print(f"{team1['name']} vs {team2['name']} -> Kazanan: {winner['name']}")
        
        # Maç geçmişine ekle
        match_history.add((team1['id'], team2['id']))
        match_history.add((team2['id'], team1['id']))
        
        # Puanları güncelle
        winner['points'] += 1.0
    
    print(f"\nMatç geçmişi: {match_history}")
    
    # İkinci tur
    print("\n=== TUR 2 ===")
    print("İkinci tur için takım durumu:")
    for team in teams:
        print(f"  {team['name']} - {team['points']} puan")
    
    # Yeniden puana göre sırala
    sorted_teams = sorted(teams, key=lambda t: (-t['points'], t['original_order']))
    print("\nPuan sıralaması:")
    for i, team in enumerate(sorted_teams):
        print(f"  {i+1}. {team['name']} - {team['points']} puan")
    
    # İkinci tur eşleştirmeleri
    print("\nİkinci tur eşleştirme algoritması:")
    
    # Puana göre grupla
    teams_by_points = {}
    for team in sorted_teams:
        points = team['points']
        if points not in teams_by_points:
            teams_by_points[points] = []
        teams_by_points[points].append(team)
    
    print("Puan grupları:")
    for points, group in sorted(teams_by_points.items(), reverse=True):
        team_names = [t['name'] for t in group]
        print(f"  {points} puan: {team_names}")
    
    second_round_matches = []
    available_teams = sorted_teams[:]
    
    # Her puan grubu için eşleştirme
    for points in sorted(teams_by_points.keys(), reverse=True):
        point_group = [t for t in teams_by_points[points] if t in available_teams]
        
        print(f"\n{points} puanlı grup işleniyor: {[t['name'] for t in point_group]}")
        
        while len(point_group) >= 2:
            team1 = point_group[0]
            team2 = None
            
            print(f"{team1['name']} için rakip aranıyor...")
            
            # Uygun rakip bul
            for candidate in point_group[1:]:
                pair1 = (team1['id'], candidate['id'])
                pair2 = (candidate['id'], team1['id'])
                
                print(f"  {candidate['name']} kontrol - pair1: {pair1} in history: {pair1 in match_history}")
                print(f"  {candidate['name']} kontrol - pair2: {pair2} in history: {pair2 in match_history}")
                
                if pair1 not in match_history and pair2 not in match_history:
                    team2 = candidate
                    print(f"  ✅ Uygun rakip: {candidate['name']}")
                    break
                else:
                    print(f"  ❌ Daha önce eşleşmiş, atlanıyor")
            
            if team2:
                second_round_matches.append((team1, team2))
                print(f"Eşleşme: {team1['name']} vs {team2['name']}")
                point_group.remove(team1)
                point_group.remove(team2)
                available_teams.remove(team1)
                available_teams.remove(team2)
            else:
                print(f"❌ {team1['name']} için bu grupta rakip bulunamadı")
                break
    
    # Kalan takımları cross-group eşleştir
    print(f"\nKalan takımlar: {[t['name'] for t in available_teams]}")
    while len(available_teams) >= 2:
        team1 = available_teams[0]
        team2 = None
        
        for candidate in available_teams[1:]:
            pair1 = (team1['id'], candidate['id'])
            pair2 = (candidate['id'], team1['id'])
            
            if pair1 not in match_history and pair2 not in match_history:
                team2 = candidate
                break
        
        if team2:
            second_round_matches.append((team1, team2))
            print(f"Cross-group eşleşme: {team1['name']} vs {team2['name']}")
            available_teams.remove(team1)
            available_teams.remove(team2)
        else:
            print(f"❌ {team1['name']} için hiç rakip bulunamadı!")
            break
    
    print(f"\nİkinci tur eşleşmeleri: {len(second_round_matches)} maç")
    for i, (team1, team2) in enumerate(second_round_matches):
        print(f"Maç {i+1}: {team1['name']} vs {team2['name']}")
    
    # Tekrar eşleşme kontrolü
    print("\n=== TEKRAR EŞLEŞME KONTROLÜ ===")
    first_round_pairs = set()
    for team1, team2 in first_round_matches:
        first_round_pairs.add(frozenset([team1['id'], team2['id']]))
    
    second_round_pairs = set()
    for team1, team2 in second_round_matches:
        second_round_pairs.add(frozenset([team1['id'], team2['id']]))
    
    repeated_pairs = first_round_pairs.intersection(second_round_pairs)
    
    if not repeated_pairs:
        print("✅ Tekrar eşleşme YOK - Sistem doğru çalışıyor!")
    else:
        print("❌ TEKRAR EŞLEŞME VAR!")
        for pair in repeated_pairs:
            pair_list = list(pair)
            team_names = [next(t['name'] for t in teams if t['id'] == id) for id in pair_list]
            print(f"Tekrar eden: {team_names[0]} vs {team_names[1]}")

if __name__ == "__main__":
    test_simple_scenario()
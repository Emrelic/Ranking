#!/bin/bash
echo "📱 79 TAKIM EKLENİYOR..."

# Türk takım isimleri
teams=(
"Galatasaray" "Fenerbahce" "Besiktas" "Trabzonspor" "Basaksehir"
"Konyaspor" "Sivasspor" "Alanyaspor" "Antalyaspor" "Gaziantepspor"
"Kayserispor" "Rizespor" "Yeni_Malatyaspor" "Goztepe" "Kasimpasa"
"Fatih_Karagumruk" "Hatayspor" "Giresunspor" "Umraniyespor" "Istanbulspor"
"Adana_Demirspor" "Pendikspor" "Bodrumspor" "Keciorengucu" "Eyupspor"
"Erzurumspor" "Bandirmaspor" "Menemenspor" "Sakaryaspor" "Tuzlaspor"
"Altinordu" "Boluspor" "Genclerbirligi" "Manisaspor" "Afyonspor"
"Ankaraggucu" "Altay" "Adanaspor" "Balikesirspor" "Sanliurfaspor"
"Denizlispor" "Diyarbakirspor" "Erokspor" "Fethiyespor" "Isparta32spor"
"Karabukspor" "Kirklarelispor" "Nigde_Anadoluspor" "Serik_Belediye" "Soma_Somaspor"
"Takim51" "Takim52" "Takim53" "Takim54" "Takim55"
"Takim56" "Takim57" "Takim58" "Takim59" "Takim60"
"Takim61" "Takim62" "Takim63" "Takim64" "Takim65"
"Takim66" "Takim67" "Takim68" "Takim69" "Takim70"
"Takim71" "Takim72" "Takim73" "Takim74" "Takim75"
"Takim76" "Takim77" "Takim78" "Takim79"
)

for i in "${!teams[@]}"; do
    team_name="${teams[$i]}"
    team_number=$((i+1))
    
    echo "  ⚽ Ekleniyor: $team_number. $team_name"
    
    # Takım ekle butonuna bas
    adb shell input tap 540 1500
    sleep 0.5
    
    # Takım adı gir
    adb shell input text "$team_name"
    sleep 0.3
    
    # Kaydet butonuna bas
    adb shell input tap 700 1600
    sleep 0.3
    
    # Her 10 takımda progress göster
    if [ $((team_number % 10)) -eq 0 ]; then
        echo "    ✅ $team_number takım eklendi"
    fi
done

echo "🎯 79 TAKIM BAŞARIYLA EKLENDİ!"
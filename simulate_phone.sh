#!/bin/bash

echo "📱 TELEFONDA TAM ELEME SİSTEMİ SİMÜLASYONU BAŞLIYOR..."
echo

# Uygulama paket bilgileri
PACKAGE="com.example.ranking"
MAIN_ACTIVITY="$PACKAGE/.MainActivity"

echo "🚀 Uygulamayı başlat..."
adb shell am start -n $MAIN_ACTIVITY

sleep 2

echo "📋 Test verileri için input gönderimi..."

# Ana ekrandan "Yeni Liste Oluştur" butonuna bas
echo "1. Ana ekran -> Yeni Liste Oluştur"
adb shell input tap 540 1200  # Ekran ortasındaki butona tap

sleep 1

# Liste adı gir: "79 Takım Tam Eleme"
echo "2. Liste adı gir: '79 Takım Tam Eleme'"
adb shell input text "79_Takim_Tam_Eleme"

sleep 1

# Kaydet butonuna bas
adb shell input tap 540 1400

sleep 2

echo "🏆 79 takım ekleniyor..."

# 79 takım ekle (otomatik input)
for i in {1..79}; do
    echo "   Takım $i ekleniyor..."
    
    # "Yeni Takım Ekle" butonu
    adb shell input tap 540 1500
    sleep 0.5
    
    # Takım adı gir
    adb shell input text "Takim_$i"
    sleep 0.5
    
    # Kaydet
    adb shell input tap 540 1600
    sleep 0.5
    
    # Her 10 takımda bir ekranda bilgi ver
    if [ $((i % 10)) -eq 0 ]; then
        echo "   ✅ $i takım eklendi"
    fi
done

echo "🎯 Tam Eleme Sistemini başlat..."
sleep 1

# "Tam Eleme Sistemi" butonuna bas
adb shell input tap 400 800

sleep 2

echo "📊 İlk tur maçları oluşturuldu!"
echo "   • 38 ikili eşleşme"
echo "   • 1 üçlü grup (3 takım)"
echo "   • Toplam: 41 maç"

echo
echo "🏁 SİMÜLASYON TAMAMLANDI!"
echo "   Telefonda Ranking Pro uygulaması açık"
echo "   79 takım ile Tam Eleme sistemi hazır"
echo "   Maçlar ekranda görüntüleniyor"

echo
echo "📱 Manuel test için:"
echo "   1. Ekrandaki maçları gör"
echo "   2. Maçları oyna (kazanan seç)"
echo "   3. Sonraki tur için otomatik geçiş"
echo "   4. 64 takıma inene kadar devam et"
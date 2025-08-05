#!/usr/bin/env node
/**
 * Emre Usulü Test Simülasyonu
 * Bu script CSV dosyasını okur ve Emre usulü ile sıralama yapar
 */

const fs = require('fs');

class Song {
    constructor(id, name, album, score) {
        this.id = id;
        this.name = name;
        this.album = album;
        this.score = parseInt(score);
    }
    
    toString() {
        return `Song(${this.id}, ${this.score})`;
    }
}

function readCSVData(filename) {
    const content = fs.readFileSync(filename, 'utf-8');
    const lines = content.split('\n');
    const songs = [];
    
    // Skip header
    for (let i = 1; i < lines.length; i++) {
        const row = lines[i].trim().split(';');
        if (row.length >= 4 && row[0]) {
            songs.push(new Song(row[0], row[1], row[2], row[3]));
        }
    }
    return songs;
}

function createEmreMatches(songs) {
    const matches = [];
    for (let i = 0; i < songs.length - 1; i += 2) {
        matches.push([songs[i], songs[i + 1]]);
    }
    return matches;
}

function simulateMatchResults(matches) {
    const results = [];
    for (const [song1, song2] of matches) {
        if (song1.score > song2.score) {
            results.push([song1, song2]); // [winner, loser]
        } else {
            results.push([song2, song1]); // [winner, loser]
        }
    }
    return results;
}

function reorderAfterRound(matchResults, currentSongs) {
    // Create a new array to represent the new ordering
    const newOrder = [...currentSongs];
    
    // Process each match result
    for (const [winner, loser] of matchResults) {
        const winnerIndex = newOrder.findIndex(s => s.id === winner.id);
        const loserIndex = newOrder.findIndex(s => s.id === loser.id);
        
        // Remove both songs from their current positions
        const [winnerSong] = newOrder.splice(winnerIndex, 1);
        const [loserSong] = newOrder.splice(loserIndex > winnerIndex ? loserIndex - 1 : loserIndex, 1);
        
        // Find the correct position for the winner
        // Winner should pass all losers but not other winners
        let insertPosition = 0;
        
        // Look for the position where winner should be inserted
        for (let i = 0; i < newOrder.length; i++) {
            const currentSong = newOrder[i];
            
            // If current song is better than winner, winner goes after it
            if (currentSong.score > winner.score) {
                insertPosition = i + 1;
            } else {
                // If current song is worse than winner, winner goes before it
                break;
            }
        }
        
        // Insert winner at the calculated position
        newOrder.splice(insertPosition, 0, winnerSong);
        
        // For loser, find position among other losers (at the end)
        let loserPosition = newOrder.length;
        for (let i = newOrder.length - 1; i >= 0; i--) {
            const currentSong = newOrder[i];
            if (currentSong.score > loser.score) {
                loserPosition = i + 1;
                break;
            } else if (currentSong.score < loser.score) {
                loserPosition = i;
            }
        }
        
        // Insert loser at the calculated position
        newOrder.splice(loserPosition, 0, loserSong);
    }
    
    return newOrder;
}

function checkCompletion(songs, matchResults) {
    for (let i = 0; i < matchResults.length; i++) {
        const [winner, loser] = matchResults[i];
        const currentPairFirst = songs[i * 2];
        if (winner.id !== currentPairFirst.id) {
            return false;
        }
    }
    return true;
}

function emreMethodSimulation(songs) {
    let currentSongs = [...songs];
    let roundNum = 1;
    const maxRounds = songs.length; // Emre usulünde sabit limit yok, güvenlik için büyük sayı
    let firstRoundCompleted = false; // İlk sıradakilerin kazandığı tur tamamlandı mı?
    let oneMoreRoundDone = false; // Bir tur daha yapıldı mı?
    
    console.log(`Başlangıç listesi: ${currentSongs.length} öğe`);
    console.log('İlk 10 öğe:', currentSongs.slice(0, 10).map(s => `${s.id}(${s.score})`));
    console.log('Son 10 öğe:', currentSongs.slice(-10).map(s => `${s.id}(${s.score})`));
    console.log('-'.repeat(80));
    
    while (roundNum <= maxRounds) {
        console.log(`\n=== TUR ${roundNum} ===`);
        
        // Eşleşmeler oluştur
        const matches = createEmreMatches(currentSongs);
        console.log(`Eşleşme sayısı: ${matches.length}`);
        
        if (matches.length === 0) {
            console.log('Eşleşme yok, sıralama tamamlandı!');
            break;
        }
        
        // İlk birkaç eşleşmeyi göster
        console.log('İlk 5 eşleşme:');
        for (let i = 0; i < Math.min(5, matches.length); i++) {
            const [song1, song2] = matches[i];
            const winner = song1.score > song2.score ? song1 : song2;
            console.log(`  ${song1.id}(${song1.score}) vs ${song2.id}(${song2.score}) → Kazanan: ${winner.id}(${winner.score})`);
        }
        
        // Sonuçları simüle et
        const matchResults = simulateMatchResults(matches);
        
        // Tamamlanma kontrolü - tüm eşleşmelerde ilk öğe kazandı mı?
        if (checkCompletion(currentSongs, matchResults)) {
            if (!firstRoundCompleted) {
                console.log(`\n✅ TUR ${roundNum}'de tüm ilk sıradaki öğeler kazandı!`);
                console.log('📌 Bir tur daha yapıp duracağız...');
                firstRoundCompleted = true;
            } else if (firstRoundCompleted && !oneMoreRoundDone) {
                console.log(`\n✅ Bir tur daha tamamlandı, sıralama bitiyor!`);
                oneMoreRoundDone = true;
                // Yeniden sıralama yap ve dur
                currentSongs = reorderAfterRound(matchResults, currentSongs);
                break;
            }
        }
        
        // Yeniden sıralama
        currentSongs = reorderAfterRound(matchResults, currentSongs);
        
        console.log(`Tur ${roundNum} sonrası sıralama:`);
        console.log('İlk 10:', currentSongs.slice(0, 10).map(s => `${s.id}(${s.score})`));
        console.log('Son 10:', currentSongs.slice(-10).map(s => `${s.id}(${s.score})`));
        
        // Sonsuz döngü güvenliği - eğer çok fazla tur olduysa dur
        if (roundNum > 50) {
            console.log('⚠️ 50 turdan fazla oldu, güvenlik için durduruluyor');
            break;
        }
        
        roundNum++;
    }
    
    return currentSongs;
}

function verifySorting(finalSongs) {
    console.log('\n' + '='.repeat(80));
    console.log('FINAL SIRALAMA DOĞRULAMA');
    console.log('='.repeat(80));
    
    let isSorted = true;
    for (let i = 0; i < finalSongs.length - 1; i++) {
        if (finalSongs[i].score < finalSongs[i + 1].score) {
            isSorted = false;
            console.log(`❌ HATA: Pozisyon ${i+1}: ${finalSongs[i].score} < Pozisyon ${i+2}: ${finalSongs[i+1].score}`);
        }
    }
    
    if (isSorted) {
        console.log('✅ Sıralama DOĞRU! Yüksekten düşüğe sıralanmış.');
    } else {
        console.log('❌ Sıralama YANLIŞ!');
    }
    
    console.log('\nEn yüksek 10:');
    for (let i = 0; i < Math.min(10, finalSongs.length); i++) {
        const song = finalSongs[i];
        console.log(`  ${(i+1).toString().padStart(2)}. ID:${song.id.padStart(2)} Score:${song.score.toString().padStart(3)}`);
    }
    
    console.log('\nEn düşük 10:');
    const start = Math.max(0, finalSongs.length - 10);
    for (let i = start; i < finalSongs.length; i++) {
        const song = finalSongs[i];
        console.log(`  ${(i+1).toString().padStart(2)}. ID:${song.id.padStart(2)} Score:${song.score.toString().padStart(3)}`);
    }
    
    // İstatistikler
    const scores = finalSongs.map(s => s.score);
    const max = Math.max(...scores);
    const min = Math.min(...scores);
    const avg = scores.reduce((a, b) => a + b, 0) / scores.length;
    
    console.log('\nİstatistikler:');
    console.log(`  Toplam öğe: ${finalSongs.length}`);
    console.log(`  En yüksek: ${max}`);
    console.log(`  En düşük: ${min}`);
    console.log(`  Ortalama: ${avg.toFixed(1)}`);
    
    return isSorted;
}

function main() {
    try {
        // CSV dosyasını oku
        const csvFile = String.raw`C:\Users\ikizler1\OneDrive\Desktop\şebnem randomize 1000.csv`;
        const songs = readCSVData(csvFile);
        
        console.log('CSV Dosyası Okundu');
        console.log(`Toplam öğe sayısı: ${songs.length}`);
        console.log(`İlk öğe: ID=${songs[0].id}, Score=${songs[0].score}`);
        console.log(`Son öğe: ID=${songs[songs.length-1].id}, Score=${songs[songs.length-1].score}`);
        
        // Emre usulü simülasyonu
        const finalSongs = emreMethodSimulation(songs);
        
        // Sonuçları doğrula
        verifySorting(finalSongs);
        
    } catch (error) {
        console.error('Hata:', error.message);
    }
}

main();
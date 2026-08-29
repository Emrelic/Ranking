package com.example.ranking

import android.service.notification.NotificationListenerService

/**
 * Boş bir bildirim dinleyicisi — bildirim OKUMAK için değil, medya
 * denetimi için var.
 *
 * Android'de başka bir uygulamanın çalan medyasını denetlemenin
 * (`MediaSessionManager.getActiveSessions`) tek yolu, çağıran uygulamanın
 * bildirim erişimi izni olan bir `NotificationListenerService` bildirmesi.
 * İzin verildiğinde YouTube Music'e "şunu çal" emri ARKA PLANDAN
 * gönderilebiliyor: uygulama ekrana gelmiyor, kullanıcı Ranking'de kalıyor.
 *
 * Bu servis hiçbir bildirimi okumaz, saklamaz veya dışarı göndermez —
 * sadece iznin alınabilmesi için var olması gerekiyor.
 */
class MuzikDenetimServisi : NotificationListenerService()

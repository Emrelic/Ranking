package com.example.ranking

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

/**
 * Coil için özel ImageLoader sağlar.
 *
 * Wikimedia (upload.wikimedia.org) jenerik veya boş User-Agent'lı istekleri
 * 403 ile reddeder (User-Agent politikası). Coil'in varsayılan OkHttp UA'sı
 * bu politikaya takıldığı için görseller yüklenmez. Aşağıdaki interceptor
 * her isteğe açıklayıcı bir User-Agent ekleyerek bunu çözer.
 */
class RankingApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "RankingProApp/1.0 (educational ranking app; github.com/Emrelic/Ranking)"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
    }
}

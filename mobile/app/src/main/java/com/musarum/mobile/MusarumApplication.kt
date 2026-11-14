package com.musarum.mobile

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

class MusarumApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Coil usa este factory para construir su ImageLoader singleton. Lo
     * configuramos con:
     *   - el mismo OkHttpClient autenticado del backend, así las portadas
     *     locales (/albums/{id}/cover) viajan con el Bearer token;
     *   - memory cache 25% del heap (el default de Coil, pero explícito);
     *   - disk cache 200 MB en {@code cacheDir/image_cache}. Con 7000 covers
     *     a ~80 KB cada una = 560 MB, el 200 MB alcanza para 2.500 miniaturas
     *     cacheadas, suficiente para que la UI vuele incluso offline.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

package com.musarum.mobile.di

import android.content.Context
import com.musarum.mobile.BuildConfig
import com.musarum.mobile.data.BaseUrlStore
import com.musarum.mobile.data.TokenManager
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.AuthInterceptor
import com.musarum.mobile.data.network.UnauthorizedInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Agrupa todo lo relacionado con red: TokenManager, OkHttpClient con
 * interceptores, cache HTTP, Retrofit y la BaseUrl del backend.
 *
 * Objetivo: que PlayerContainer y DataContainer dependan SOLO de este
 * container, no de AppContainer completo. Así se pueden instanciar en
 * aislamiento en tests o en pantallas que no necesiten PlayerController.
 */
class NetworkContainer(
    context: Context,
    applicationScope: CoroutineScope
) {
    val baseUrlStore: BaseUrlStore = BaseUrlStore(context)

    /** URL del backend resuelta UNA vez al construir. Si el override guardado
     *  no es válido, lo borramos y caemos al baked-in para evitar crash-loop. */
    val baseUrl: String = run {
        val candidate = baseUrlStore.current()
        if (BaseUrlStore.isValid(candidate)) candidate
        else {
            baseUrlStore.clear()
            baseUrlStore.current()
        }
    }

    val tokenManager: TokenManager = TokenManager(context, applicationScope)

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenManager))
        .addInterceptor(UnauthorizedInterceptor(tokenManager, applicationScope))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(16, 10, TimeUnit.MINUTES))
        .cache(okhttp3.Cache(
            directory = java.io.File(context.cacheDir, "okhttp_cache"),
            maxSize = 100L * 1024 * 1024
        ))
        .build()

    /**
     * JSON configurado para kotlinx.serialization — coherente con cómo el
     * backend serializa: campos extra ignorados (backend evoluciona antes
     * que el móvil), null explícito permitido, pretty-print off.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(ScalarsConverterFactory.create())
        // Orden: kotlinx PRIMERO. El factory devuelve null para clases sin
        // @Serializable, con lo que Retrofit cae al siguiente converter
        // (Gson) sin fricción. Un DTO migra pasándolo a @Serializable — no
        // hay que re-cablear nada aquí.
        .addConverterFactory(
            json.asConverterFactory("application/json; charset=UTF8".toMediaType())
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}

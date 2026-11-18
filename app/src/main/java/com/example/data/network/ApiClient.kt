package com.example.data.network

import com.example.data.local.SessionManager
import com.example.data.model.LokasiSalesItem
import com.example.data.model.RiwayatGroupItem
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val moshiConverterFactory: MoshiConverterFactory by lazy {
        MoshiConverterFactory.create(moshi)
    }

    val lokasiListAdapter: JsonAdapter<List<LokasiSalesItem>> by lazy {
        val type = Types.newParameterizedType(List::class.java, LokasiSalesItem::class.java)
        moshi.adapter(type)
    }

    val riwayatGroupListAdapter: JsonAdapter<List<RiwayatGroupItem>> by lazy {
        val type = Types.newParameterizedType(List::class.java, RiwayatGroupItem::class.java)
        moshi.adapter(type)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(NgrokHeaderInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedService: RkmApiService? = null

    fun getMoshi(): Moshi = moshi

    private class NgrokHeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val request = original.newBuilder()
                .header("ngrok-skip-browser-warning", "true")
                .header("User-Agent", "RKMSPIBatu-Android/1.0")
                .build()
            return chain.proceed(request)
        }
    }

    fun createService(sessionManager: SessionManager): RkmApiService {
        val targetBaseUrl = sessionManager.baseUrl
        val existing = cachedService
        if (existing != null && cachedBaseUrl == targetBaseUrl) {
            return existing
        }

        return synchronized(this) {
            val recheck = cachedService
            if (recheck != null && cachedBaseUrl == targetBaseUrl) {
                recheck
            } else {
                val retrofit = Retrofit.Builder()
                    .baseUrl(targetBaseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(moshiConverterFactory)
                    .build()
                val created = retrofit.create(RkmApiService::class.java)
                cachedBaseUrl = targetBaseUrl
                cachedService = created
                created
            }
        }
    }
}

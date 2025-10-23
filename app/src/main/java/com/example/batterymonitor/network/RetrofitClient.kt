package com.example.batterymonitor.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private var retrofit: Retrofit? = null
    private var lastBaseUrl: String? = null

    fun getClient(baseUrl: String): Retrofit {
        val safeBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        if (retrofit == null || lastBaseUrl != safeBaseUrl) {
            val logging = HttpLoggingInterceptor()
            logging.setLevel(HttpLoggingInterceptor.Level.BODY)

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(safeBaseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            lastBaseUrl = safeBaseUrl
        }
        return retrofit!!
    }
}
package com.winx.app.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL =
        "https://winx-api-part3-d9b31478.azurewebsites.net/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val notificationApi: com.winx.app.notifications.NotificationApi by lazy {
        retrofit.create(com.winx.app.notifications.NotificationApi::class.java)
    }

    val api: WinxApiService by lazy {
        retrofit.create(WinxApiService::class.java)
    }

    val mediaApi: MediaApiService by lazy {
        retrofit.create(MediaApiService::class.java)
    }
}

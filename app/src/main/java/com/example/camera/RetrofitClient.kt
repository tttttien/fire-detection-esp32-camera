package com.example.camera

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://79a1-14-161-49-237.ngrok-free.app/"

    // Tạo OkHttpClient với timeout tùy chỉnh
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)   // Timeout kết nối
        .readTimeout(60, TimeUnit.SECONDS)      // Timeout đọc phản hồi
        .writeTimeout(60, TimeUnit.SECONDS)     // Timeout gửi dữ liệu
        .retryOnConnectionFailure(true)         // Thử lại nếu mất kết nối
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // Gắn client với timeout
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

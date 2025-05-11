package com.example.camera.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.camera.model.LatestAlertResponse
import retrofit2.Call

object RetrofitClient {
    private const val BASE_URL = "https://66b4-27-66-20-232.ngrok-free.app/"  // Update with your actual base URL

    val apiService: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}

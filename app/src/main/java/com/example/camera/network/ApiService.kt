package com.example.camera.network

import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @POST("toggle_detection")
    suspend fun toggleDetection(@Query("enable") enable: Boolean): Response<Void>
}

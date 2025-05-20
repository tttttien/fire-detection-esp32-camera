package com.example.camera.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import com.example.camera.model.LatestAlertResponse

interface ApiService {

    @POST("toggle_detection")
    suspend fun toggleDetection(
        @Query("enable") enable: Boolean
    ): Response<Void>

    @GET("getLatestAlert")
    suspend fun getLatestAlert(): Response<LatestAlertResponse>
}

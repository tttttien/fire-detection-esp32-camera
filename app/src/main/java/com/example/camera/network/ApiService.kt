package com.example.camera.network

import com.example.camera.model.LatestAlertResponse
import retrofit2.Response
import retrofit2.http.GET

interface ApiService {
    @GET("/latest-alert/")
    suspend fun getLatestAlert(): Response<LatestAlertResponse>
}

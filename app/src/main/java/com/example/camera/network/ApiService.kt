package com.example.camera.network

import com.example.camera.model.LatestAlertResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {
    @POST("toggle_detection")
    suspend fun toggleDetection(
        @Query("enable") enable: Boolean
    ): Response<Void>

    @Multipart
    @POST("upload_frame")
    suspend fun uploadFrame(
        @Part file: MultipartBody.Part
    ): Response<LatestAlertResponse>
}

package com.example.camera_fire.model

data class LatestAlertResponse(
    val fire_detected: Boolean,
    val image_url: String?
)

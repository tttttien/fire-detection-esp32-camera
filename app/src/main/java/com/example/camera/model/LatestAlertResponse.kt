package com.example.camera.model

data class LatestAlertResponse(
    val fire_detected: Boolean,
    val timestamp: String?,
    val image_url: String?,
    val bounding_box: Any? // Hoặc Map<String, Any> nếu cần vẽ box sau
)

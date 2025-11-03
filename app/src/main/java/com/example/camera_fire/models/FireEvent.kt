package com.example.camera_fire.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class FireEvent(
    val id: Long,
    @SerialName("camera_id") val cameraId: Long? = null,
    @SerialName("object_name") val objectName: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("created_at") val createdAt: String, // GIỮ STRING để tránh lỗi format time
    @Transient var publicUrl: String? = null
)
@Serializable
data class HistoryResponse(
    val frames: List<FireEvent>,
    val videos: List<FireEvent>,
    val nextCursorFrames: String?,
    val nextCursorVideos: String?,
    val pageSize: Int
)
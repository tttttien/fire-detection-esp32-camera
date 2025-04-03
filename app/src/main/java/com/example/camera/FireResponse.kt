package com.example.camera

import com.google.gson.annotations.SerializedName

data class FireResponse(
    @SerializedName("fire_detected")
    val fireDetected: Boolean,

    @SerializedName("fire_mask")
    val fireMask: List<Int>,  // Flattened mask (1D)

    @SerializedName("width")
    val width: Int,  // Required for reconstruction

    @SerializedName("height")
    val height: Int
)







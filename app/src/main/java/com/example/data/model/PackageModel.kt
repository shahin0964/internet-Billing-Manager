package com.example.data.model

import com.google.gson.annotations.SerializedName

data class PackageModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("price")
    val price: Double,
    @SerializedName("speed")
    val speed: String?
)

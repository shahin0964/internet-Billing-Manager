package com.aistudio.ispbilling.control.data.remote.request

import com.google.gson.annotations.SerializedName

data class PackageRequest(
    @SerializedName("id")
    val id: Long,

    @SerializedName("user_id")
    val userId: Long?,

    @SerializedName("name")
    val name: String,

    @SerializedName("price")
    val price: Double,

    @SerializedName("speed")
    val speed: String?
)

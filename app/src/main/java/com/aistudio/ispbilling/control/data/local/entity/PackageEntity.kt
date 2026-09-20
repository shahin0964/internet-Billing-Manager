package com.aistudio.ispbilling.control.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packages")
data class PackageEntity(
    @PrimaryKey
    val id: Long,
    val userId: Long?,
    val name: String,
    val price: Double,
    val speed: String?,
    val isSynced: Boolean = false,
    val syncAction: String = "INSERT"
)

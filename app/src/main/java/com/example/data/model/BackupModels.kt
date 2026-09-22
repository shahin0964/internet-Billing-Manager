package com.example.data.model

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.Locale

class BackupTimestampAdapter : TypeAdapter<Long>() {
    override fun write(out: JsonWriter, value: Long?) {
        if (value == null) {
            out.value(System.currentTimeMillis())
        } else {
            out.value(value)
        }
    }

    override fun read(reader: JsonReader): Long {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return System.currentTimeMillis()
        }
        return try {
            when (reader.peek()) {
                JsonToken.NUMBER -> reader.nextLong()
                JsonToken.STRING -> {
                    val str = reader.nextString()?.trim()
                    if (str.isNullOrBlank() || str.equals("null", ignoreCase = true)) {
                        System.currentTimeMillis()
                    } else {
                        str.toLongOrNull() ?: parseDateStringToMillis(str) ?: System.currentTimeMillis()
                    }
                }
                else -> {
                    reader.skipValue()
                    System.currentTimeMillis()
                }
            }
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun parseDateStringToMillis(dateStr: String): Long? {
        val patterns = arrayOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val parsed = sdf.parse(dateStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return null
    }
}

data class CloudBackupModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("backup_name")
    val backupName: String,
    @SerializedName("backup_data")
    val backupData: String? = null,
    @SerializedName("backup_size")
    val backupSize: Int = 0,
    @SerializedName("version")
    val version: Int = 1,
    @field:JsonAdapter(BackupTimestampAdapter::class)
    @SerializedName("created_at")
    val createdAt: Long = System.currentTimeMillis()
)

data class CloudBackupRequest(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("backup_name")
    val backupName: String,
    @SerializedName("backup_data")
    val backupData: String,
    @SerializedName("version")
    val version: Int = 1
)

data class CloudBackupResponse(
    @SerializedName("status")
    val status: Boolean,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("backup_id")
    val backupId: String? = null
)

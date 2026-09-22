package com.example.data.model

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.Locale

class PaymentTimestampAdapter : TypeAdapter<Long?>() {
    override fun write(out: JsonWriter, value: Long?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value)
        }
    }

    override fun read(reader: JsonReader): Long? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return try {
            when (reader.peek()) {
                JsonToken.NUMBER -> reader.nextLong()
                JsonToken.STRING -> {
                    val str = reader.nextString()?.trim()
                    if (str.isNullOrBlank() || str.equals("null", ignoreCase = true)) {
                        null
                    } else {
                        str.toLongOrNull() ?: parseDateStringToMillis(str)
                    }
                }
                else -> {
                    reader.skipValue()
                    null
                }
            }
        } catch (_: Exception) {
            null
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

data class PaymentModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("payment_receipt_no")
    val paymentReceiptNo: String,
    @SerializedName("bill_id")
    val billId: String?,
    @SerializedName("customer_id")
    val customerId: String,
    @SerializedName("customer_name")
    val customerName: String?,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("payment_date")
    val paymentDate: String?,
    @SerializedName("payment_method")
    val paymentMethod: String = "Cash",
    @SerializedName("notes")
    val notes: String? = null,
    @field:JsonAdapter(PaymentTimestampAdapter::class)
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)

data class PaymentRequest(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("payment_receipt_no")
    val paymentReceiptNo: String,
    @SerializedName("bill_id")
    val billId: String,
    @SerializedName("customer_id")
    val customerId: String,
    @SerializedName("customer_name")
    val customerName: String,
    val amount: Double,
    @SerializedName("payment_date")
    val paymentDate: String,
    @SerializedName("payment_method")
    val paymentMethod: String,
    val notes: String,
    @field:JsonAdapter(PaymentTimestampAdapter::class)
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)

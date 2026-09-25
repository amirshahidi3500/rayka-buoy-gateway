package com.rayka.buoygateway.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * هر پیامک واردشده ابتدا اینجا (روی خود گوشی) ذخیره می‌شود و تا وقتی که
 * ارسال آن به سرور ابری تأیید نشود، حذف نمی‌شود. این یعنی خاموش شدن گوشی،
 * قطع اینترنت، یا کرش اپ باعث گم شدن هیچ پیامکی نمی‌شود.
 */
@Entity(tableName = "pending_messages")
data class PendingMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawSms: String,
    val sender: String,
    val simSlot: Int,
    val receivedAt: Long,
    var cloudSent: Boolean = false,
    var cloudAttempts: Int = 0,
    var localSent: Boolean = false,
    var lastError: String? = null
)

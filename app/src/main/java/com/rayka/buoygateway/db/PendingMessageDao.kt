package com.rayka.buoygateway.db

import androidx.room.*

@Dao
interface PendingMessageDao {

    @Insert
    suspend fun insert(message: PendingMessage): Long

    @Query("SELECT * FROM pending_messages WHERE cloudSent = 0 ORDER BY receivedAt ASC")
    suspend fun getUnsentToCloud(): List<PendingMessage>

    @Query("SELECT * FROM pending_messages WHERE localSent = 0 ORDER BY receivedAt ASC")
    suspend fun getUnsentToLocal(): List<PendingMessage>

    @Update
    suspend fun update(message: PendingMessage)

    @Query("SELECT COUNT(*) FROM pending_messages WHERE cloudSent = 0")
    suspend fun countUnsent(): Int

    @Query("SELECT * FROM pending_messages ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<PendingMessage>

    // پیام‌هایی که هم به ابر و هم به سرور محلی (در صورت تنظیم بودن) رسیده‌اند
    // بعد از مدتی می‌توانند پاک شوند تا دیتابیس محلی بزرگ نشود؛ فراخوانی این تابع اختیاری است.
    @Query("DELETE FROM pending_messages WHERE cloudSent = 1 AND receivedAt < :beforeMillis")
    suspend fun cleanupOldSent(beforeMillis: Long)
}

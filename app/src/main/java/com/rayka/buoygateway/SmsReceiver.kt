package com.rayka.buoygateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.core.content.ContextCompat
import com.rayka.buoygateway.db.AppDatabase
import com.rayka.buoygateway.db.PendingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * فقط پیامک‌هایی که با پیشوند تنظیم‌شده (پیش‌فرض RAYKA:) شروع می‌شوند و از سیم‌کارتی
 * می‌رسند که کاربر در تنظیمات انتخاب کرده، پردازش می‌شوند. بقیه‌ی پیامک‌های گوشی
 * (پیامک‌های شخصی و غیره) کاملاً نادیده گرفته می‌شوند و ذخیره یا ارسال نمی‌شوند.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs.isConsentGiven(context) || !Prefs.isServiceOn(context)) return

        val prefix = Prefs.getSmsPrefix(context)
        val monitoredSubs = Prefs.getMonitoredSubIds(context)

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // شماره subscription سیمی که پیامک از آن رسیده (برای گوشی‌های چند سیم‌کارته)
        val subId = intent.getIntExtra("subscription", -1)
            .takeIf { it != -1 }
            ?: intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)

        // اگر کاربر سیم‌کارت خاصی را انتخاب کرده و این پیامک از سیم دیگری آمده، رد شود
        if (monitoredSubs.isNotEmpty() && subId != -1 && subId !in monitoredSubs) return

        val fullBody = messages.joinToString("") { it.messageBody ?: "" }
        val sender = messages.firstOrNull()?.originatingAddress ?: "unknown"

        if (!fullBody.trim().startsWith(prefix, ignoreCase = true)) return

        val now = System.currentTimeMillis()

        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(context).pendingMessageDao()
            dao.insert(
                PendingMessage(
                    rawSms = fullBody,
                    sender = sender,
                    simSlot = subId,
                    receivedAt = now
                )
            )
            // بلافاصله تلاش برای ارسال؛ اگر اینترنت نبود، GatewayService بعداً از صف می‌فرستد
            val serviceIntent = Intent(context, GatewayService::class.java).apply {
                action = GatewayService.ACTION_FLUSH_QUEUE
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}

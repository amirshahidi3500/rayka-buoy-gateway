package com.rayka.buoygateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * اگر کاربر سرویس را روشن کرده بود، بعد از روشن شدن مجدد گوشی (مثلاً بعد از قطعی برق
 * یا خاموش‌شدن باتری) سرویس خودش دوباره راه می‌افتد و صف پیام‌های نرسیده را می‌فرستد.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.isConsentGiven(context) || !Prefs.isServiceOn(context)) return

        val serviceIntent = Intent(context, GatewayService::class.java).apply {
            action = GatewayService.ACTION_FLUSH_QUEUE
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

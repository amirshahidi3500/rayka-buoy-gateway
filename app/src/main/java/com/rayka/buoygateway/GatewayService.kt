package com.rayka.buoygateway

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rayka.buoygateway.db.AppDatabase
import kotlinx.coroutines.*

/**
 * تا وقتی کاربر از داخل اپ آن را روشن نکند اجرا نمی‌شود. وقتی روشن است:
 *  ۱) هر پیام رسیده را (که SmsReceiver قبلاً در دیتابیس محلی ذخیره کرده) به سمت
 *     Edge Function ابری می‌فرستد.
 *  ۲) اگر اینترنت نبود یا ارسال شکست خورد، در دیتابیس محلی می‌ماند (پاک نمی‌شود)
 *     و به‌محض وصل‌شدن اینترنت دوباره تلاش می‌شود (از طریق NetworkMonitor).
 *  ۳) اگر آدرس سرور محلی تنظیم شده باشد، هر پیام ارسال‌نشده به ابر، موازی هم به
 *     آن سرور محلی فرستاده می‌شود تا PWA بدون اینترنت هم داده تازه ببیند.
 *  ۴) اختیاری: اگر «خود این گوشی به‌عنوان سرور محلی» فعال باشد، یک HTTP سرور
 *     ساده هم بالا می‌آید تا PWA مستقیماً از خودِ این گوشی بخواند.
 */
class GatewayService : Service() {

    companion object {
        const val CHANNEL_ID = "rayka_gateway_channel"
        const val NOTIF_ID = 2001
        const val ACTION_FLUSH_QUEUE = "com.rayka.buoygateway.FLUSH_QUEUE"
        const val PERIODIC_RETRY_MS = 2 * 60 * 1000L
    }

    private lateinit var workerThread: HandlerThread
    private lateinit var workerHandler: Handler
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var networkMonitor: NetworkMonitor? = null
    private var localHttpServer: LocalHttpServer? = null

    private val periodicRetry = object : Runnable {
        override fun run() {
            flushQueue()
            workerHandler.postDelayed(this, PERIODIC_RETRY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        workerThread = HandlerThread("GatewayWorker")
        workerThread.start()
        workerHandler = Handler(workerThread.looper)

        networkMonitor = NetworkMonitor(applicationContext) {
            // به محض وصل‌شدن اینترنت، بلافاصله صف را خالی کن
            workerHandler.post { flushQueue() }
        }
        networkMonitor?.start()

        if (Prefs.getActAsLocalServer(applicationContext)) {
            localHttpServer = LocalHttpServer(applicationContext, Prefs.getLocalServerPort(applicationContext))
            localHttpServer?.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        Prefs.setServiceOn(applicationContext, true)

        workerHandler.removeCallbacks(periodicRetry)
        workerHandler.post(periodicRetry)

        return START_STICKY
    }

    override fun onDestroy() {
        Prefs.setServiceOn(applicationContext, false)
        networkMonitor?.stop()
        localHttpServer?.stop()
        workerHandler.removeCallbacksAndMessages(null)
        workerThread.quitSafely()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun flushQueue() {
        serviceScope.launch {
            val dao = AppDatabase.get(applicationContext).pendingMessageDao()
            val cloudUrl = Prefs.getCloudFunctionUrl(applicationContext)
            val cloudSecret = Prefs.getCloudSharedSecret(applicationContext)
            val localUrl = Prefs.getLocalServerUrl(applicationContext)
            val localSecret = Prefs.getLocalSharedSecret(applicationContext)

            val pending = dao.getUnsentToCloud()
            for (msg in pending) {
                var success = false

                if (!cloudUrl.isNullOrBlank() && !cloudSecret.isNullOrBlank()) {
                    when (val result = CloudSender.send(cloudUrl, cloudSecret, msg.rawSms)) {
                        is CloudSender.Result.Success -> {
                            success = true
                            msg.cloudSent = true
                            msg.lastError = null
                            Prefs.setLastCloudSyncTime(applicationContext, System.currentTimeMillis())
                        }
                        is CloudSender.Result.Failure -> {
                            msg.cloudAttempts += 1
                            msg.lastError = result.message
                            if (result.isNetworkError) {
                                // اینترنت نیست؛ صف خالی نمی‌شود، بعداً دوباره تلاش می‌شود
                                // و در همین حین سعی می‌کنیم به سرور محلی برسانیم
                            }
                        }
                    }
                }

                if (!success && !localUrl.isNullOrBlank()) {
                    val localOk = LocalServerSender.send(localUrl, localSecret, msg.rawSms, msg.simSlot, msg.receivedAt)
                    if (localOk) msg.localSent = true
                }

                dao.update(msg)

                // اگر اولین شکست به‌خاطر نبود اینترنت بود، دیگر ادامه‌ی صف را امتحان نکن؛
                // فقط منتظر NetworkMonitor بمان تا وقت‌تلف‌کردن با تلاش‌های پشت‌سرهم نباشد.
                if (!success && cloudUrl != null) {
                    val monitor = networkMonitor
                    if (monitor != null && !monitor.isOnline()) break
                }
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}

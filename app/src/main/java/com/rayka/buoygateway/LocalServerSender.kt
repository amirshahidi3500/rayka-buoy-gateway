package com.rayka.buoygateway

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * وقتی اینترنت گوشی قطع است، پیام را به یک آدرس روی همان شبکه‌ی محلی
 * (مودم، نقطه‌اتصال گوشی دیگر، یا حتی خود همین گوشی روی یک پورت دیگر) می‌فرستد
 * تا PWA که روی همان شبکه اجرا می‌شود بتواند بدون اینترنت به داده دسترسی داشته باشد.
 * قالب بدنه مشابه Edge Function است تا بشود همان منطق را سمت سرور محلی هم پیاده کرد.
 */
object LocalServerSender {

    fun send(localUrl: String, sharedSecret: String?, rawSms: String, simSlot: Int, receivedAt: Long): Boolean {
        return try {
            val connection = URL(localUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            if (!sharedSecret.isNullOrBlank()) {
                connection.setRequestProperty("X-Local-Secret", sharedSecret)
            }
            connection.doOutput = true
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            val body = JSONObject().apply {
                put("sms", rawSms)
                put("sim_slot", simSlot)
                put("received_at", receivedAt)
            }.toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }
}

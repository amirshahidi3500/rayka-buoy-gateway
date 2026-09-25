package com.rayka.buoygateway

import com.rayka.buoygateway.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * یک سرور HTTP بسیار ساده و بدون هیچ کتابخانه‌ی خارجی، فقط برای این‌که وقتی اینترنت
 * قطع است، PWA روی همان شبکه‌ی محلی (وای‌فای مشترک با این گوشی، یا هات‌اسپات این گوشی)
 * بتواند GET /telemetry بزند و آخرین پیام‌های خام دریافتی را بگیرد.
 * این سرور جایگزین Supabase نیست، فقط یک راه دسترسی محلیِ بدون‌اینترنت است.
 */
class LocalHttpServer(private val context: android.content.Context, private val port: Int) {

    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            try {
                serverSocket = ServerSocket(port)
                while (running) {
                    val client = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    handleClient(client)
                }
            } catch (e: Exception) {
                // پورت اشغال بود یا خطای دیگر؛ سرور محلی به‌سادگی روشن نمی‌شود
            }
        }
        thread?.start()
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val path = requestLine.split(" ").getOrNull(1) ?: "/"

            val secret = Prefs.getLocalSharedSecret(context)
            // بقیه‌ی هدرها را فقط برای پیدا کردن هدر رمز مشترک می‌خوانیم
            var providedSecret: String? = null
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotBlank()) {
                if (line!!.startsWith("X-Local-Secret:", ignoreCase = true)) {
                    providedSecret = line!!.substringAfter(":").trim()
                }
            }

            val output = client.getOutputStream()

            if (!secret.isNullOrBlank() && providedSecret != secret) {
                writeResponse(output, 401, "Unauthorized", "text/plain")
                return
            }

            when {
                path.startsWith("/telemetry") -> {
                    val messages = runBlocking {
                        AppDatabase.get(context).pendingMessageDao().getRecent(200)
                    }
                    val arr = JSONArray()
                    messages.forEach {
                        arr.put(JSONObject().apply {
                            put("sms", it.rawSms)
                            put("sim_slot", it.simSlot)
                            put("received_at", it.receivedAt)
                            put("cloud_sent", it.cloudSent)
                        })
                    }
                    writeResponse(output, 200, arr.toString(), "application/json")
                }
                path.startsWith("/health") -> writeResponse(output, 200, "OK", "text/plain")
                else -> writeResponse(output, 404, "Not found", "text/plain")
            }
        } catch (e: Exception) {
            // اتصال قطع شد یا خطای دیگر؛ نادیده گرفته می‌شود
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun writeResponse(output: java.io.OutputStream, code: Int, body: String, contentType: String) {
        val status = if (code == 200) "OK" else if (code == 401) "Unauthorized" else "Not Found"
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code $status\r\n" +
                "Content-Type: $contentType; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }
}

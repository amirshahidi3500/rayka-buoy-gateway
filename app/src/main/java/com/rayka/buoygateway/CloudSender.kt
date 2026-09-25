package com.rayka.buoygateway

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * دقیقاً همان قراردادی که در supabase/functions/ingest-buoy-sms/index.ts پیاده‌سازی شده:
 * POST به آدرس Edge Function، هدر X-Tasker-Secret، بدنه {"sms": "<متن خام پیامک>"}.
 * این یعنی این اپ عملاً جای Tasker را می‌گیرد، اما بدون نیاز به اپ واسط دیگری.
 */
object CloudSender {

    sealed class Result {
        object Success : Result()
        data class Failure(val message: String, val isNetworkError: Boolean) : Result()
    }

    fun send(functionUrl: String, sharedSecret: String, rawSms: String): Result {
        return try {
            val connection = URL(functionUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Tasker-Secret", sharedSecret)
            connection.doOutput = true
            connection.connectTimeout = 12000
            connection.readTimeout = 12000

            val body = JSONObject().put("sms", rawSms).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            connection.disconnect()
            if (code in 200..299) Result.Success
            else Result.Failure("HTTP $code", isNetworkError = false)
        } catch (e: java.net.UnknownHostException) {
            Result.Failure(e.message ?: "no network", isNetworkError = true)
        } catch (e: java.net.SocketTimeoutException) {
            Result.Failure(e.message ?: "timeout", isNetworkError = true)
        } catch (e: java.io.IOException) {
            Result.Failure(e.message ?: "io error", isNetworkError = true)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "unknown error", isNetworkError = false)
        }
    }
}

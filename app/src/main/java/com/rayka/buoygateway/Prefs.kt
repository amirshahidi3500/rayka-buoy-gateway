package com.rayka.buoygateway

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * تمام تنظیمات (شماره سیم‌کارت‌های انتخابی، آدرس Edge Function، رمز مشترک،
 * آدرس سرور محلی) فقط روی خود گوشی و رمزنگاری‌شده ذخیره می‌شود.
 */
object Prefs {
    private const val FILE_NAME = "rayka_gateway_secure_prefs"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setConsentGiven(context: Context, given: Boolean) =
        prefs(context).edit().putBoolean("consent_given", given).apply()
    fun isConsentGiven(context: Context) = prefs(context).getBoolean("consent_given", false)

    fun setServiceOn(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean("service_on", on).apply()
    fun isServiceOn(context: Context) = prefs(context).getBoolean("service_on", false)

    // پیشوند پیامک‌هایی که باید پردازش شوند (بقیه‌ی پیامک‌های گوشی نادیده گرفته می‌شوند)
    fun setSmsPrefix(context: Context, prefix: String) =
        prefs(context).edit().putString("sms_prefix", prefix).apply()
    fun getSmsPrefix(context: Context): String =
        prefs(context).getString("sms_prefix", "RAYKA:") ?: "RAYKA:"

    // شناسه‌های subscription سیم‌کارت‌هایی که باید مانیتور شوند، جدا شده با کاما.
    // خالی = همه‌ی سیم‌کارت‌ها.
    fun setMonitoredSubIds(context: Context, ids: Set<Int>) =
        prefs(context).edit().putString("monitored_subs", ids.joinToString(",")).apply()
    fun getMonitoredSubIds(context: Context): Set<Int> {
        val raw = prefs(context).getString("monitored_subs", "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun setCloudFunctionUrl(context: Context, url: String) =
        prefs(context).edit().putString("cloud_url", url.trim()).apply()
    fun getCloudFunctionUrl(context: Context): String? = prefs(context).getString("cloud_url", null)

    fun setCloudSharedSecret(context: Context, secret: String) =
        prefs(context).edit().putString("cloud_secret", secret.trim()).apply()
    fun getCloudSharedSecret(context: Context): String? = prefs(context).getString("cloud_secret", null)

    fun setLocalServerUrl(context: Context, url: String) =
        prefs(context).edit().putString("local_url", url.trim()).apply()
    fun getLocalServerUrl(context: Context): String? = prefs(context).getString("local_url", null)

    fun setLocalSharedSecret(context: Context, secret: String) =
        prefs(context).edit().putString("local_secret", secret.trim()).apply()
    fun getLocalSharedSecret(context: Context): String? = prefs(context).getString("local_secret", null)

    // اگر روشن باشد، خود همین گوشی به‌عنوان سرور محلی عمل می‌کند و PWA می‌تواند
    // از طریق http://<آی‌پی این گوشی در وای‌فای محلی>:<پورت>/telemetry داده بخواند.
    fun setActAsLocalServer(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean("act_as_local_server", on).apply()
    fun getActAsLocalServer(context: Context) = prefs(context).getBoolean("act_as_local_server", false)

    fun setLocalServerPort(context: Context, port: Int) =
        prefs(context).edit().putInt("local_server_port", port).apply()
    fun getLocalServerPort(context: Context) = prefs(context).getInt("local_server_port", 8787)

    fun setLastCloudSyncTime(context: Context, t: Long) =
        prefs(context).edit().putLong("last_cloud_sync", t).apply()
    fun getLastCloudSyncTime(context: Context) = prefs(context).getLong("last_cloud_sync", 0L)
}

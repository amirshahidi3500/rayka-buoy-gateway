package com.rayka.buoygateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * به محض اینکه اینترنت گوشی دوباره وصل شود (وای‌فای یا دیتای موبایل، از هر سیمی)،
 * بلافاصله GatewayService را برای خالی‌کردن صف پیام‌های نرسیده صدا می‌زند.
 */
class NetworkMonitor(private val context: Context, private val onAvailable: () -> Unit) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onAvailable()
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // ممکن است قبلاً unregister شده باشد
        }
    }

    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

package io.github.alirezajavan.pulsekit.sync

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import io.github.alirezajavan.pulsekit.core.PlatformContext

actual class NetworkMonitor actual constructor(
    private val context: PlatformContext,
) : NetworkTypeProvider {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @SuppressLint("MissingPermission")
    actual override fun currentNetworkType(): NetworkType {
        val hasNetworkStatePermission = context.checkSelfPermission(
            android.Manifest.permission.ACCESS_NETWORK_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasNetworkStatePermission) {
            Log.w(
                "PulseKit",
                "Missing ACCESS_NETWORK_STATE permission. Network-aware sync policy cannot " +
                    "function correctly.",
            )
            return NetworkType.NONE
        }

        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkType.NONE

        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            NetworkType.UNMETERED
        } else {
            NetworkType.METERED
        }
    }
}

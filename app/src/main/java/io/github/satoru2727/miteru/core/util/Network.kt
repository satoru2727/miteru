package io.github.satoru2727.miteru.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** 現在のネットワークが従量制かどうか。判定できないときは false（警告しない）。 */
fun isMeteredNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
    return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}

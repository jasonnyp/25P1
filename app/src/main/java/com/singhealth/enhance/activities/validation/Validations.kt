package com.singhealth.enhance.activities.validation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun internetConnectionCheck(context: Context) {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val hasConnection: Boolean

    val network = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(network)
    hasConnection = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

    if (!hasConnection) {
        noInternetErrorDialog(context)
    }
}

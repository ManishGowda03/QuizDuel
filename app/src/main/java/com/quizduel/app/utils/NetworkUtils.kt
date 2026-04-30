package com.quizduel.app.utils

import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtils {

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun requireInternet(context: Context, action: () -> Unit) {
        if (isInternetAvailable(context)) {
            action()
        } else {
            AlertDialog.Builder(context)
                .setTitle("No Internet")
                .setMessage("Please check your internet connection and try again.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
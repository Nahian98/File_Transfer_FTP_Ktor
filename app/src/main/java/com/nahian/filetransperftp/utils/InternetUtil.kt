package com.nahian.filetransperftp.utils

import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

object InternetUtil {
    private const val TAG = "InternetUtil"
    fun getLocalIpAddress(): String? {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.map { networkInterface ->
                networkInterface.inetAddresses?.toList()?.find {
                    !it.isLoopbackAddress && it is Inet4Address
                }?.let { return it.hostAddress }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Ip address exception: ${e.message}")
        }
        return null
    }
}
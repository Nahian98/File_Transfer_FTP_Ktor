package com.nahian.filetransperftp.manager

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Random


class APManager private constructor(context: Context) {
    val utils: Utils

    /**
     * get ssid of recently created hotspot
     * @return SSID
     */
    var sSID: String? = null
        private set

    /**
     * get password of recently created hotspot
     * @return PASSWORD
     */
    var password: String? = null
        private set

    val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var reservation: LocalOnlyHotspotReservation? = null


    init {
        this.utils = Utils()
    }

    fun turnOnHotspot(
        context: Context,
        onSuccessListener: OnSuccessListener,
        onFailureListener: OnFailureListener
    ) {
        val providerEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (isDeviceConnectedToWifi) {
            onFailureListener.onFailure(ERROR_DISABLE_WIFI, null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (utils.checkLocationPermission(context) && providerEnabled && !isWifiApEnabled) {
                try {
                    wifiManager.startLocalOnlyHotspot(object : LocalOnlyHotspotCallback() {
                        override fun onStarted(reservation: LocalOnlyHotspotReservation) {
                            super.onStarted(reservation)
                            this@APManager.reservation = reservation
                            try {
                                sSID = reservation.wifiConfiguration!!.SSID
                                password = reservation.wifiConfiguration!!.preSharedKey
                                onSuccessListener.onSuccess(sSID!!, password!!)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                onFailureListener.onFailure(ERROR_UNKNOWN, e)
                            }
                        }

                        override fun onFailed(reason: Int) {
                            super.onFailed(reason)
                            onFailureListener.onFailure(
                                if (reason == ERROR_TETHERING_DISALLOWED) ERROR_DISABLE_HOTSPOT else ERROR_UNKNOWN,
                                null
                            )
                        }
                    }, Handler(Looper.getMainLooper()))
                } catch (e: Exception) {
                    onFailureListener.onFailure(ERROR_UNKNOWN, e)
                }
            } else if (!providerEnabled) {
                onFailureListener.onFailure(ERROR_GPS_PROVIDER_DISABLED, null)
            } else if (isWifiApEnabled) {
                onFailureListener.onFailure(ERROR_DISABLE_HOTSPOT, null)
            } else {
                onFailureListener.onFailure(ERROR_LOCATION_PERMISSION_DENIED, null)
            }
        } else {
            if (!utils.checkLocationPermission(context)) {
                onFailureListener.onFailure(ERROR_LOCATION_PERMISSION_DENIED, null)
                return
            }
            if (!utils.checkWriteSettingPermission(context)) {
                onFailureListener.onFailure(ERROR_WRITE_SETTINGS_PERMISSION_REQUIRED, null)
                return
            }
            try {
                sSID = "AndroidAP_" + Random().nextInt(10000)
                password = randomPassword
                val wifiConfiguration = WifiConfiguration()
                wifiConfiguration.SSID = sSID
                wifiConfiguration.preSharedKey = password
                wifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                wifiManager.setWifiEnabled(false)
                setWifiApEnabled(wifiConfiguration, true)
                onSuccessListener.onSuccess(sSID!!, password!!)
            } catch (e: Exception) {
                e.printStackTrace()
                onFailureListener.onFailure(ERROR_LOCATION_PERMISSION_DENIED, e)
            }
        }
    }

    fun disableWifiAp() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reservation!!.close()
            } else {
                setWifiApEnabled(null, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val isWifiApEnabled: Boolean
        get() {
            try {
                val method = wifiManager.javaClass.getMethod("isWifiApEnabled")
                return method.invoke(wifiManager) as Boolean
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

    val isDeviceConnectedToWifi: Boolean
        /**
         * Utility method to check device wifi is enabled and connected to any access point.
         *
         * @return connection status of wifi
         */
        get() = wifiManager.dhcpInfo.ipAddress != 0

    @Throws(Exception::class)
    private fun setWifiApEnabled(wifiConfiguration: WifiConfiguration?, enable: Boolean) {
        val method = wifiManager.javaClass.getMethod(
            "setWifiApEnabled",
            WifiConfiguration::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.invoke(wifiManager, wifiConfiguration, enable)
    }

    interface OnFailureListener {
        fun onFailure(failureCode: Int, e: Exception?)
    }

    interface OnSuccessListener {
        fun onSuccess(ssid: String, password: String)
    }

    private val randomPassword: String
        get() {
            try {
                val ms = MessageDigest.getInstance("MD5")
                val bytes = ByteArray(10)
                Random().nextBytes(bytes)
                val digest = ms.digest(bytes)
                val bigInteger = BigInteger(1, digest)
                return bigInteger.toString(16).substring(0, 10)
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }
            return "jfs82433#$2"
        }

    class Utils {
        fun checkLocationPermission(context: Context?): Boolean {
            return ActivityCompat.checkSelfPermission(
                context!!,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun askLocationPermission(activity: Activity?, requestCode: Int) {
            ActivityCompat.requestPermissions(
                activity!!, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), requestCode
            )
        }

        fun askWriteSettingPermission(activity: Activity) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.setData(Uri.parse("package:" + activity.packageName))
            activity.startActivity(intent)
        }

        fun checkWriteSettingPermission(context: Context): Boolean {
            return Settings.System.canWrite(context)
        }

        val tetheringSettingIntent: Intent
            get() {
                val intent = Intent()
                intent.setClassName("com.android.settings", "com.android.settings.TetherSettings")
                return intent
            }

        fun askForGpsProvider(activity: Activity) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            activity.startActivity(intent)
        }

        fun askForDisableWifi(activity: Activity) {
            activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    companion object {
        private var apManager: APManager? = null

        /**
         * @param context should not be null
         * @return APManager
         */
        fun getApManager(context: Context): APManager? {
            if (apManager == null) {
                apManager = APManager(context)
            }
            return apManager
        }

        /**
         * Some android version requires gps provider to be in active mode to create access point (Hotspot).
         */
        const val ERROR_GPS_PROVIDER_DISABLED: Int = 0
        const val ERROR_LOCATION_PERMISSION_DENIED: Int = 4
        const val ERROR_DISABLE_HOTSPOT: Int = 1
        const val ERROR_DISABLE_WIFI: Int = 5
        const val ERROR_WRITE_SETTINGS_PERMISSION_REQUIRED: Int = 6
        const val ERROR_UNKNOWN: Int = 3
    }
}
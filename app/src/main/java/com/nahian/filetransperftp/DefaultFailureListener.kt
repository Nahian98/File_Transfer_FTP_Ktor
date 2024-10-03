package com.nahian.filetransperftp

import android.app.Activity
import android.os.Build
import android.widget.Toast
import com.nahian.filetransperftp.manager.APManager
import com.nahian.filetransperftp.manager.APManager.OnFailureListener


class DefaultFailureListener(private val activity: Activity) : OnFailureListener {
    override fun onFailure(failureCode: Int, e: Exception?) {
        val utils: APManager.Utils = APManager.getApManager(
            activity
        )!!.utils
        when (failureCode) {
            APManager.ERROR_DISABLE_HOTSPOT -> {
                Toast.makeText(activity, "DISABLE HOTSPOT", Toast.LENGTH_LONG).show()
                activity.startActivity(utils.tetheringSettingIntent)
            }

            APManager.ERROR_DISABLE_WIFI -> {
                Toast.makeText(activity, "DISCONNECT WIFI", Toast.LENGTH_LONG).show()
                utils.askForDisableWifi(activity)
            }

            APManager.ERROR_GPS_PROVIDER_DISABLED -> {
                Toast.makeText(activity, "ENABLE GPS", Toast.LENGTH_LONG).show()
                utils.askForGpsProvider(activity)
            }

            APManager.ERROR_LOCATION_PERMISSION_DENIED -> {
                Toast.makeText(activity, "ALLOW LOCATION PERMISSION", Toast.LENGTH_LONG).show()
                utils.askLocationPermission(activity, REQUEST_CODE_WRITE_SETTINGS)
            }

            APManager.ERROR_WRITE_SETTINGS_PERMISSION_REQUIRED -> {
                Toast.makeText(
                    activity,
                    "ALLOW WRITE SYSTEM SETTINGS PERMISSION",
                    Toast.LENGTH_LONG
                ).show()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    utils.askWriteSettingPermission(activity)
                }
            }
        }
    }

    companion object {
        const val REQUEST_CODE_WRITE_SETTINGS: Int = 12
    }
}
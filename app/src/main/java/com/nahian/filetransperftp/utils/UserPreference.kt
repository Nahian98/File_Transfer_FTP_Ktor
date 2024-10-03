package com.nahian.filetransperftp.utils

import android.content.Context
import android.content.SharedPreferences

class UserPreference {
    companion object {
        @Volatile
        private lateinit var instance: UserPreference
        private lateinit var preferences: SharedPreferences
        fun getInstance(context: Context? = null): UserPreference {
            synchronized(this) {
                if (!Companion::instance.isInitialized) {
                    instance = UserPreference()
                    if (context != null) {
                        preferences = context.getSharedPreferences("APP_PREF", 0)
                    }
                }
                return instance
            }
        }
    }
}
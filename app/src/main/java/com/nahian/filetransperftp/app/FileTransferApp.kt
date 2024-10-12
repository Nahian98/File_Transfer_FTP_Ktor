package com.nahian.filetransperftp.app

import android.app.Application


class FileTransferApp: Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    companion object {
        lateinit var instance: FileTransferApp
            private set
    }
}
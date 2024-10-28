package com.nahian.filetransperftp.view

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nahian.filetransperftp.R
import com.nahian.filetransperftp.databinding.ActivityRawServerBinding
import com.nahian.filetransperftp.server.HttpRawServer
import com.nahian.filetransperftp.utils.InternetUtil

class RawServerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRawServerBinding
    private lateinit var rawHttpServer: HttpRawServer
    private var ipAddress: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRawServerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initComponent()
        initListener()
    }

    private fun initListener() {
        binding.btnSend.setOnClickListener {
            rawHttpServer.startServer()
        }
    }

    private fun initComponent() {
        rawHttpServer = HttpRawServer(this, 8080)
        ipAddress = InternetUtil.getLocalIpAddress()
        Log.d(TAG, "Ip address: $ipAddress")
    }

    companion object {
        private const val TAG = "RawServerActivity"
    }
}
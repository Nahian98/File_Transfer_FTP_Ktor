package com.nahian.filetransperftp.view

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.nahian.filetransperftp.databinding.ActivityWebSocketServerBinding
import com.nahian.filetransperftp.server.FileSender
import com.nahian.filetransperftp.server.MyWebSocketServer
import com.nahian.filetransperftp.utils.InternetUtil
import com.nahian.filetransperftp.utils.QRUtil
import com.nahian.filetransperftp.utils.UriHelpers
import kotlinx.coroutines.launch
import java.net.InetSocketAddress

class WebSocketServerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebSocketServerBinding
    private lateinit var url: String
    private lateinit var webSocketServer: MyWebSocketServer
    private lateinit var ipAddress: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebSocketServerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initComponent()
        initListener()
    }

    private fun initComponent() {
        val port = 8080  // Set the port you want the server to listen on
        ipAddress = InternetUtil.getLocalIpAddress()!!
        webSocketServer = MyWebSocketServer(ipAddress, port)
        Log.d(TAG, "Ip: ${InetSocketAddress(ipAddress, 8080)}")
        webSocketServer.start()
    }

    private fun initListener() {
        binding.btnSend.setOnClickListener {
            // Scan qr code
            scanQrResultLauncher.launch(ScanContract().createIntent(this, ScanOptions()))
        }

        binding.btnReceive.setOnClickListener {
            showQrCode()
        }
    }

    private fun showQrCode() {
        binding.tvIp.text = ipAddress
        lifecycleScope.launch {
            val fullUrl = "ws://$ipAddress:8080"
            val qrCodeBitmap = QRUtil.generateQRCode(fullUrl)
            binding.ivQRCode.setImageBitmap(qrCodeBitmap)
            binding.ivQRCode.visibility = View.VISIBLE
        }
    }

    private fun openFileLauncher() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.type = "*/*"
        pickFileLauncher.launch(intent)
    }

    private val pickFileLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            if (data != null) {
                val selectedSongFile = data.data?.let { UriHelpers.copyUriToFile(this, it) }

                if (selectedSongFile != null) {
                    lifecycleScope.launch {
                        val fileSender = FileSender(
                            url = url,
                            file = selectedSongFile
                        ) { progress ->
                            Log.d(TAG, "File sending progress: $progress%")
                        }
                        fileSender.sendFile()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketServer.stop()  // Stop the server when the activity is destroyed
    }

    companion object {
        private const val TAG = "WebSocketServerActivity"
    }

    private val scanQrResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultData ->
        if (resultData.resultCode == RESULT_OK) {
            val result = ScanIntentResult.parseActivityResult(resultData.resultCode, resultData.data)

            // This will be QR activity result
            if (result.contents == null) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Connected to the server" + result.contents, Toast.LENGTH_LONG).show()
                url = result.contents
                openFileLauncher()
            }
        }
    }
}
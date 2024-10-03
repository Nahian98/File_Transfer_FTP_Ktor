package com.nahian.filetransperftp.view

import android.app.Activity
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
import com.nahian.filetransperftp.databinding.ActivityNanoHttpdBinding
import com.nahian.filetransperftp.server.NanoHttpServer
import com.nahian.filetransperftp.utils.InternetUtil
import com.nahian.filetransperftp.utils.QRUtil
import com.nahian.filetransperftp.utils.UriHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException

class NanoHttpdActivity : AppCompatActivity() {
    private lateinit var server: NanoHttpServer
    private lateinit var binding: ActivityNanoHttpdBinding
    private lateinit var ipAddress: String
    private lateinit var url: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNanoHttpdBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initComponent()
        initListener()
    }

    private fun initComponent() {
        // Initialize and start the server
        server = NanoHttpServer(this,8080)
        try {
            server.start()
        } catch (e: Exception) {
            Log.d(TAG, "Error starting the server: ${e.message}")
        }
        ipAddress = InternetUtil.getLocalIpAddress()!!
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
            val fullUrl = "http://$ipAddress:8080/upload"
            val qrCodeBitmap = QRUtil.generateQRCode(fullUrl)
            binding.ivQRCode.setImageBitmap(qrCodeBitmap)
            binding.ivQRCode.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop the server when activity is destroyed
        server.stop()
    }

    private fun openFileLauncher() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.type = "*/*"
        pickFileLauncher.launch(intent)
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

    private val pickFileLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data != null) {
                val selectedSongFile = data.data?.let { UriHelpers.copyUriToFile(this@NanoHttpdActivity, it) }

                if (selectedSongFile != null) {
                    lifecycleScope.launch {
                        sendFile(selectedSongFile, url)
                    }
                }
            }
        }
    }

    private suspend fun sendFile(file: File, url: String) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody())
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@NanoHttpdActivity, "Upload failed onFailure: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(this@NanoHttpdActivity, "File uploaded successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@NanoHttpdActivity, "Upload failed onResponse: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    companion object {
        private const val TAG = "NanoHttpdActivity"
    }
}
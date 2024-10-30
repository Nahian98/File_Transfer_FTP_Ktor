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
import com.nahian.filetransperftp.utils.CountingRequestBody
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
import okhttp3.Response
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

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
//            lifecycleScope.launch {
//                val url = "http://192.168.68.126:8080/exchange-ip"
//                sendGetRequest(url)
//            }

        }
        binding.btnReceive.setOnClickListener {
            showQrCode()
        }
    }

    private suspend fun sendGetRequest(url: String) {
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null

            try {
                // Create a URL object from the URL string
                val urlObj = URL(url)

                // Open connection to the URL
                connection = urlObj.openConnection() as HttpURLConnection

                // Set request method to GET
                connection.requestMethod = "GET"

                // Set a timeout for the connection
                connection.connectTimeout = 5000 // 5 seconds
                connection.readTimeout = 5000 // 5 seconds

                // Check if the response code is successful (200)
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    // Print the response
                    println("Response: ${response.toString()}")
                } else {
                    println("Request failed: $responseCode")
                }
            } catch (e: Exception) {
                // Handle exceptions (e.g., network failure)
                e.printStackTrace()
            } finally {
                // Ensure the connection is closed
                connection?.disconnect()
            }
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
//                url = result.contents
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

    // Step 2: Modify sendFile function to track progress
    private suspend fun sendFile(file: File, url: String) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()

            val requestBody = CountingRequestBody(
                file,
                "application/octet-stream"
            ) { bytesWritten, contentLength ->
                val progress = (100 * bytesWritten / contentLength).toInt()
                Log.d(TAG, "File sending progress: $progress")
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, requestBody)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(multipartBody)
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
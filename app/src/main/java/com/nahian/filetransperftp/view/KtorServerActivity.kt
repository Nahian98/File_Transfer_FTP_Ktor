package com.nahian.filetransperftp.view

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import com.nahian.filetransperftp.databinding.ActivityKtorServerBinding
import com.nahian.filetransperftp.utils.InternetUtil
import com.nahian.filetransperftp.utils.QRUtil
import com.nahian.filetransperftp.utils.UriHelpers
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okio.IOException
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class KtorServerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKtorServerBinding
    private lateinit var ipAddress: String
    private lateinit var url: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKtorServerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initComponent()
        initListener()
    }

    private fun initListener() {
        binding.btnSend.setOnClickListener {
            // Scan qr code
            scanQrResultLauncher.launch(ScanContract().createIntent(this, ScanOptions()))
        }

        binding.btnReceive.setOnClickListener {
            startKtorServer()
            showQrCode()
        }
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

    private val pickFileLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data != null) {
                val selectedSongFile = data.data?.let {
                    UriHelpers.getFileForUri(this, it)
                }

                if (selectedSongFile != null) {
                    lifecycleScope.launch {
                        val fullUrl = "http://$url:8080/upload"
                        sendFile(selectedSongFile, fullUrl)
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
                        Toast.makeText(this@KtorServerActivity, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(this@KtorServerActivity, "File uploaded successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@KtorServerActivity, "Upload failed: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    private fun showQrCode() {
        ipAddress = InternetUtil.getLocalIpAddress()!!
        binding.tvIp.text = ipAddress
        lifecycleScope.launch {
            val qrCodeBitmap = QRUtil.generateQRCode(ipAddress)
            binding.ivQRCode.setImageBitmap(qrCodeBitmap)
            binding.ivQRCode.visibility = View.VISIBLE
        }
    }

    private fun initComponent() {
//        startKtorServer()
    }

    private fun startKtorServer() {
        GlobalScope.launch(Dispatchers.IO) {
            val result = runCatching {
                embeddedServer(Netty, port = 8080) {
                    install(CallLogging)
                    routing {
                        post("/upload") {
                            try {
                                val multipart = call.receiveMultipart()
                                var uploadSuccess = false
                                multipart.forEachPart { part ->
                                    if (part is PartData.FileItem) {
                                        val fileName = part.originalFileName ?: "unknown"
                                        val contentLength = part.headers["Content-Length"]?.toLong() ?: 0L
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            val contentValues = ContentValues().apply {
                                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                                put(MediaStore.MediaColumns.MIME_TYPE, part.contentType.toString())
                                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                            }
                                            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                            uri?.let {
                                                contentResolver.openOutputStream(it).use { outputStream ->
                                                    part.streamProvider().use { input ->
                                                        uploadSuccess = copyStreamWithProgress(input, outputStream!!, contentLength)
                                                    }
                                                }
                                            }
                                        } else {
                                            val file = File(Environment.getExternalStorageDirectory(), fileName)
                                            part.streamProvider().use { input ->
                                                file.outputStream().buffered().use { output ->
                                                    uploadSuccess = copyStreamWithProgress(input, output, contentLength)
                                                }
                                            }
                                        }
                                    }
                                    part.dispose()
                                }
                                if (uploadSuccess) {
                                    call.respondText("File uploaded successfully")
                                } else {
                                    call.respondText("File upload failed", status = HttpStatusCode.InternalServerError)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                call.respondText("File upload error: ${e.message}", status = HttpStatusCode.InternalServerError)
                            }
                        }

                        get("/list-files") {
                            val directory = File(Environment.getExternalStorageDirectory().toString())
                            if (directory.exists() && directory.isDirectory) {
                                val files = directory.listFiles()
                                if (files != null) {
                                    val fileNames = files.map { it.name }
                                    call.respond(fileNames)
                                } else {
                                    call.respondText("No files found", status = HttpStatusCode.NotFound)
                                }
                            } else {
                                call.respondText("Directory not found", status = HttpStatusCode.NotFound)
                            }
                        }
                    }
                }.start(wait = true)
            }

            result.onSuccess {
                println("Ktor server started successfully")
            }.onFailure { exception ->
                println("Failed to start Ktor server: ${exception.message}")
            }
        }
    }

    private suspend fun copyStreamWithProgress(input: InputStream, output: OutputStream, totalBytes: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val buffer = ByteArray(1024)
                var bytesRead: Int
                var bytesCopied: Long = 0
                while (input.read(buffer).also { bytesRead = it } >= 0) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    Log.d(TAG, "Upload progress: ${(bytesCopied * 100) / totalBytes}%")
                }
                output.flush()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    companion object {
        private const val TAG = "KtorServerActivity"
    }
}
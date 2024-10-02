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
import com.nahian.filetransperftp.databinding.ActivityNanoHttpdBinding
import com.nahian.filetransperftp.utils.InternetUtil
import com.nahian.filetransperftp.utils.QRUtil
import com.nahian.filetransperftp.utils.UriHelpers
import fi.iki.elonen.NanoHTTPD
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
    private lateinit var server: NanoFileUploadServer
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
        server = NanoFileUploadServer(8080)
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
                        Toast.makeText(this@NanoHttpdActivity, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(this@NanoHttpdActivity, "File uploaded successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@NanoHttpdActivity, "Upload failed: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    inner class NanoFileUploadServer(port: Int) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            Log.d(TAG, "Session uri: $uri")
            return try {
                if (Method.POST == session.method) {
                    postUpload(session)
                } else {
                    newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Only POST method is allowed")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error during upload: ${e.message}")
            }
        }

        private fun postUpload(session: IHTTPSession): Response {
            println("Received HTTP POST with upload body...")

            // Check if the content type is multipart/form-data
            val contentType = session.headers["content-type"]
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"Invalid content type\"}")
            }

            // Parse the POST request
            val files = HashMap<String, String>()

            val params = session.parms
            try {
                session.parseBody(files) // Parses the form data and stores files in a temporary location
            } catch (e: Exception) {
                e.printStackTrace()
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"Failed to parse multipart data\"}")
            }

            files.forEach{
                Log.d(TAG, "Files: ${it.key} = ${it.value}")
            }

            // Extract the uploaded file from the temporary storage
            val tempFilePath = files["file"] // This is the path to the file stored temporarily

            if (tempFilePath != null) {
                try {
                    val uploadedFileName: String = params["filename"] ?: "uploaded_file" // Assuming the form field is named "filename"
                    val tempFile = File(tempFilePath)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // For Android Q and above, use the MediaStore API to store the file in Downloads directory
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, uploadedFileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream") // Set appropriate MIME type
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }

                        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                        uri?.let {
                            contentResolver.openOutputStream(it).use { outputStream ->
                                tempFile.inputStream().use { input ->
                                    input.copyTo(outputStream!!)
                                }
                            }
                            println("File uploaded successfully to: Downloads/$uploadedFileName")
                        } ?: throw IOException("Failed to get URI for file storage.")
                    } else {
                        // For Android versions below Q, save to external storage directory
                        val destinationFile = File(Environment.getExternalStorageDirectory(), uploadedFileName)
                        tempFile.inputStream().use { input ->
                            destinationFile.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Toast.makeText(this@NanoHttpdActivity, "File received", Toast.LENGTH_SHORT).show()
                        println("File uploaded successfully to: ${destinationFile.absolutePath}")
                    }

                    return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"message\": \"File uploaded successfully\", \"file\": \"${uploadedFileName}\"}")
                } catch (e: IOException) {
                    e.printStackTrace()
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"Failed to store the uploaded file\"}")
                }
            } else {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"No file uploaded\"}")
            }
        }
    }

    companion object {
        private const val TAG = "NanoFileUploadServer"
    }
}
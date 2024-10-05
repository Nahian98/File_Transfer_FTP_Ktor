package com.nahian.filetransperftp.server

import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.nahian.filetransperftp.utils.InternetUtil
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException

class NanoHttpServer(private val activity: Activity, port: Int) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.d(TAG, "Session uri: $uri")
        return try {
            when {
                session.method == Method.GET && session.uri == "/exchange-ip" -> {
                    handleIpExchange(session)
                }
                session.method == Method.POST && session.uri == "/upload" -> {
                    postUpload(session)
                }
                else -> {
                    newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Only POST and GET methods are allowed")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error during upload: ${e.message}")
        }
    }

    // Handle IP exchange for the GET /exchange-ip endpoint
    private fun handleIpExchange(session: IHTTPSession): Response {
        val clientIp = session.remoteIpAddress
        val serverIp = getServerIpAddress() // Custom method to get server IP (or hardcode)

        Log.d(TAG, "Client IP: $clientIp, Server IP: $serverIp")

        val ipResponseJson = """
            {
                "client_ip": "$clientIp",
                "server_ip": "$serverIp"
            }
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "application/json", ipResponseJson)
    }


    private fun getServerIpAddress(): String? {
        return InternetUtil.getLocalIpAddress() // Replace with actual method to get IP address dynamically
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

                    val uri = activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                    uri?.let {
                        activity.contentResolver.openOutputStream(it).use { outputStream ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(outputStream!!)
                            }
                        }
                        println("File uploaded successfully to: Downloads/$uploadedFileName")
                        tempFile.delete()
                    } ?: throw IOException("Failed to get URI for file storage.")
                } else {
                    // For Android versions below Q, save to external storage directory
                    val destinationFile = File(Environment.getExternalStorageDirectory(), uploadedFileName)
                    tempFile.inputStream().use { input ->
                        destinationFile.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Toast.makeText(activity, "File received", Toast.LENGTH_SHORT).show()
                    println("File uploaded successfully to: ${destinationFile.absolutePath}")
                    tempFile.delete()
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

    private fun postUpload2(session: IHTTPSession): Response {
        println("Received HTTP POST with upload body...")

        // Check if the content type is multipart/form-data
        val contentType = session.headers["content-type"]
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"Invalid content type\"}")
        }

        // Get the content length for progress tracking
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (contentLength == 0L) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"Invalid content length\"}")
        }

        // Create a buffer for reading chunks of the input stream
        val bufferSize = 8192
        val buffer = ByteArray(bufferSize)
        var bytesRead: Int
        var totalBytesRead = 0L

        try {
            // Extract the input stream from the session
            val inputStream = session.inputStream
            val tempFile = File(activity.cacheDir, "uploaded_temp_file")

            // Open output stream to write to a temporary file
            tempFile.outputStream().buffered().use { outputStream ->
                // Read the input stream in chunks and write to the file
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Calculate the progress percentage
                    val progress = (totalBytesRead * 100 / contentLength).toInt()
                    Log.d(TAG, "Upload progress: $progress%")
                }
                inputStream.close()
                outputStream.flush()
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"message\": \"File uploaded successfully\", \"file\": \"\"}")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"Failed to parse multipart data\"}")
        }
    }

    // Helper function to extract boundary from the content type header
    private fun getBoundary(contentType: String): String {
        val boundaryPrefix = "boundary="
        val boundaryIndex = contentType.indexOf(boundaryPrefix)
        return if (boundaryIndex >= 0) {
            contentType.substring(boundaryIndex + boundaryPrefix.length).trim()
        } else {
            ""
        }
    }

    companion object {
        private const val TAG = "NanoHttpServer"
    }
}
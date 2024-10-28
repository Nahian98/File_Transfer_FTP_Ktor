package com.nahian.filetransperftp.server

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class HttpRawServer(private val context: Context, private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private val TAG = "FileUploadServer"

    fun startServer() {
        CoroutineScope(Dispatchers.IO).launch {
            serverSocket = ServerSocket(port)
            Log.d(TAG, "Server started on port $port")
            while (true) {
                val clientSocket = serverSocket?.accept()
                clientSocket?.let {
                    handleClientRequest(it)
                }
            }
        }
    }

    private fun handleClientRequest(clientSocket: Socket) {
        try {
            clientSocket.use { socket ->
                val input = BufferedInputStream(socket.getInputStream())
                val output = PrintWriter(socket.getOutputStream(), true)
                val headers = parseHeaders(input)

                // Check if content-type is multipart/form-data
                val contentType = headers["Content-Type"] ?: headers["content-type"]
                if (contentType == null || !contentType.contains("multipart/form-data")) {
                    output.println("HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\n\r\n{\"error\": \"Invalid content type\"}")
                    return
                }

                val totalSize = headers["Content-Length"]?.toLongOrNull() ?: 0L
                Log.d(TAG, "Total size: $totalSize")

                // Read file data
                val boundary = contentType.split("boundary=")[1]
                val uploadedFileName = "uploaded_file_${System.currentTimeMillis()}"
                saveFile(input, boundary, uploadedFileName)
                Log.d(TAG, "File uploaded successfully: $uploadedFileName")

                // Respond with success message
                output.println("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"message\": \"File uploaded successfully\", \"file\": \"$uploadedFileName\"}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseHeaders(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val reader = BufferedReader(InputStreamReader(input))
        var line: String?

        // Read HTTP headers
        while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
            val parts = line!!.split(": ")
            if (parts.size == 2) {
                headers[parts[0]] = parts[1]
            }
        }
        return headers
    }

    private fun saveFile(input: InputStream, boundary: String, fileName: String) {
        val tempFile = File(context.cacheDir, fileName)
        tempFile.outputStream().use { output ->
            val buffer = ByteArray(4096)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
        }

        // Save the file in the Downloads folder or external storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it).use { outputStream ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(outputStream!!)
                    }
                }
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "File $fileName received", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "File uploaded successfully to: Downloads/$fileName")
                tempFile.delete()
            } ?: throw IOException("Failed to get URI for file storage.")
        } else {
            val destinationFile = File(Environment.getExternalStorageDirectory(), fileName)
            tempFile.inputStream().use { input ->
                destinationFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "File $fileName received", Toast.LENGTH_SHORT).show()
            }
            Log.d(TAG, "File uploaded successfully to: ${destinationFile.absolutePath}")
            tempFile.delete()
        }
    }

    private fun handleClientRequest2(clientSocket: Socket) {
        clientSocket.use { socket ->
            val clientIp = socket.inetAddress.hostAddress
            val serverIp = getServerIpAddress() // Use a method or hardcode the server IP

            Log.d(TAG, "Client IP: $clientIp, Server IP: $serverIp")

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() // Read the request line

            // Check if the request line contains the expected GET request for /exchange-ip
            if (requestLine != null && requestLine.startsWith("GET /exchange-ip") && requestLine.contains("HTTP/")) {
                // Send a JSON response with client and server IPs
                if (serverIp != null) {
                    if (clientIp != null) {
                        sendIpResponse(socket.getOutputStream(), clientIp, serverIp)
                    }
                }
            } else {
                // Send a 404 response if the endpoint does not match
                sendNotFoundResponse(socket.getOutputStream())
            }
        }
    }

    private fun sendIpResponse(output: OutputStream, clientIp: String, serverIp: String) {
        val ipResponseJson = """
        {
            "client_ip": "$clientIp",
            "server_ip": "$serverIp"
        }
    """.trimIndent()

        val response = """
        HTTP/1.1 200 OK
        Content-Type: application/json
        Content-Length: ${ipResponseJson.length}
        
        $ipResponseJson
    """.trimIndent()

        output.write(response.toByteArray())
        output.flush()
    }

    private fun sendNotFoundResponse(output: OutputStream) {
        val response = """
        HTTP/1.1 404 Not Found
        Content-Type: text/plain
        Content-Length: 13
        
        404 Not Found
    """.trimIndent()

        output.write(response.toByteArray())
        output.flush()
    }

    private fun getServerIpAddress(): String? {
        return InetAddress.getLocalHost().hostAddress
    }

    fun stopServer() {
        serverSocket?.close()
    }
}
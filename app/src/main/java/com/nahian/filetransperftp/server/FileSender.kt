package com.nahian.filetransperftp.server

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class FileSender(
    private val url: String,
    private val file: File,
    private val onProgressUpdate: (Int) -> Unit
) {
    private var client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .connectTimeout(1, TimeUnit.MINUTES)
        .build()

    private var webSocket: WebSocket? = null
    private var bytesSent = 0L

    fun sendFile() {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendMetadata(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                sendFileChunks(webSocket)
                if (text == "File received") {
                    println("File transfer completed successfully!")
                    webSocket.close(1000, "File sent")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                retryUpload() // Retry if failure occurs
            }
        })
    }

    private fun sendMetadata(webSocket: WebSocket) {
        val metadata = JSONObject().apply {
            put("fileName", file.name)
            put("fileSize", file.length())
        }
        webSocket.send(metadata.toString())
    }

    private fun sendFileChunks(webSocket: WebSocket) {
        FileInputStream(file).use { inputStream ->
            val buffer = ByteArray(1024) // Smaller chunks for large files
            var bytesRead: Int
            inputStream.skip(bytesSent) // Resume from last sent position

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                webSocket.send(buffer.toByteString(0, bytesRead))
                bytesSent += bytesRead

                // Update progress
                val progress = ((bytesSent * 100) / file.length()).toInt()
                onProgressUpdate(progress)

                // Wait for ACK from the server
                break
            }
        }
    }

    private fun retryUpload() {
        client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .connectTimeout(1, TimeUnit.MINUTES)
            .build()
        sendFile()
    }
}

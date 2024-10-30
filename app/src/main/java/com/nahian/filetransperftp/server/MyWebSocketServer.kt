package com.nahian.filetransperftp.server

import com.nahian.filetransperftp.app.FileTransferApp
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class MyWebSocketServer(ipAddress: String, port: Int) : WebSocketServer(InetSocketAddress(ipAddress, port)) {
    private var fileName: String = "default_file" // Default file name if not received

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        println("New connection from ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        println("Closed connection to ${conn.remoteSocketAddress}")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        // Handle metadata received from client
        try {
            val json = JSONObject(message)
            fileName = json.optString("fileName", "default_file.bin")
            println("File metadata received: name = $fileName")
        } catch (e: Exception) {
            println("Failed to parse metadata: ${e.message}")
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        // Convert ByteBuffer to byte array
        val bytes = ByteArray(message.remaining())
        message.get(bytes)

        // Save file chunk
        saveFileChunk(bytes)

        // Send acknowledgment back to the sender after receiving a chunk
//        conn.send("ACK") // Acknowledge chunk received
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        println("Error occurred: ${ex.message}")
    }

    override fun onStart() {
        println("WebSocket server started on port $port")
    }

    private fun saveFileChunk(chunk: ByteArray) {
        val file = File(FileTransferApp.instance.filesDir, fileName)
        val outputStream = FileOutputStream(file, true)  // Append mode
        outputStream.use { it.write(chunk) }
        println("Chunk saved: ${chunk.size} bytes")
    }

}
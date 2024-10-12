package com.nahian.filetransperftp.server.tempFileManager

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ProgressTrackingTempFile(
    uploadDir: File,
    private val totalSize: Long,
    private val progressCallback: (progress: Int) -> Unit
) : NanoHTTPD.TempFile {

    private var file: File = File.createTempFile("upload_", "", uploadDir)
    private var outputStream: OutputStream = FileOutputStream(file)
    private var bytesWritten: Long = 0

    init {
        println("ProgressTrackingTempFile initialized.")
    }

    override fun open(): OutputStream {
        println("ProgressTrackingTempFile open() called.")
        return object : OutputStream() {
            override fun write(b: Int) {
                outputStream.write(b)
                bytesWritten++
                println("Bytes written: $bytesWritten / $totalSize")
                reportProgress()
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                outputStream.write(b, off, len)
                bytesWritten += len
                println("Bytes written: $bytesWritten / $totalSize")
                reportProgress()
            }

            override fun close() {
                outputStream.close()
            }

            private fun reportProgress() {
                val progress = (bytesWritten * 100 / totalSize).toInt()
                Log.d(TAG, "Upload Progress: $progress%")
                progressCallback(progress) // Report progress
            }
        }
    }

    override fun delete() {
        Log.d(TAG, "Delete called")
        file.delete()
    }

    override fun getName(): String {
        Log.d(TAG, "GetName called")
        return file.absolutePath
    }

    companion object {
        private const val TAG = "ProgressTrackingTempFile"
    }
}

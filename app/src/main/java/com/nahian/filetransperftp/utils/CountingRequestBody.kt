package com.nahian.filetransperftp.utils

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File

// Step 1: Create CountingRequestBody to track progress
class CountingRequestBody(
    private val file: File,
    private val contentType: String,
    private val onProgress: (Long, Long) -> Unit
) : RequestBody() {

    override fun contentType() = contentType.toMediaTypeOrNull()

    override fun contentLength() = file.length()

    override fun writeTo(sink: BufferedSink) {
        val fileLength = contentLength()
        var totalBytesWritten: Long = 0

        file.source().use { source ->
            var read: Long
            while (source.read(sink.buffer, 8192).also { read = it } != -1L) {
                totalBytesWritten += read
                onProgress(totalBytesWritten, fileLength)
                sink.flush()
            }
        }
    }
}
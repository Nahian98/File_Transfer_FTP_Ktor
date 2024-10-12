package com.nahian.filetransperftp.server.tempFileManager

import com.nahian.filetransperftp.app.FileTransferApp
import fi.iki.elonen.NanoHTTPD
import java.io.File

class ProgressTrackingTempFileManagerFactory(
    private val totalSize: Long,
    private val progressCallback: (progress: Int) -> Unit
) : NanoHTTPD.TempFileManagerFactory {
    override fun create(): NanoHTTPD.TempFileManager {
        // The directory where temporary files will be stored
        val uploadDir = File(FileTransferApp.instance.cacheDir, "uploads")
        if (!uploadDir.exists()) uploadDir.mkdir()

        return ProgressTrackingTempFileManager(uploadDir, totalSize, progressCallback)
    }
}

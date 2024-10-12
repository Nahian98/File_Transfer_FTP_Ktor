package com.nahian.filetransperftp.server.tempFileManager

import fi.iki.elonen.NanoHTTPD
import java.io.File


class ProgressTrackingTempFileManager(
    private val uploadDir: File,
    private val totalSize: Long,
    private val progressCallback: (progress: Int) -> Unit
) : NanoHTTPD.TempFileManager {

    private val tempFiles = mutableListOf<NanoHTTPD.TempFile>()

    override fun createTempFile(fileNameHint: String?): NanoHTTPD.TempFile {
        val tempFile = ProgressTrackingTempFile(uploadDir, totalSize, progressCallback)
        tempFiles.add(tempFile)
        return tempFile
    }

    override fun clear() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }
}
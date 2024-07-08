package com.nahian.filetransperftp.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPSClient
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class FTPManager {
    private val ftpsClient = FTPSClient()
    suspend fun connectToFtpServer(host: String, port: Int, user: String, pass: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                ftpsClient.connect(host, port)
                if (ftpsClient.login(user, pass)) {
                    ftpsClient.enterLocalPassiveMode()
                    ftpsClient.setFileType(FTPSClient.ASCII_FILE_TYPE)
                    ftpsClient.execPROT("P")
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Failed to login to FTP server"))
                }
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }

    suspend fun uploadFile(localFilePath: String, remoteFilePath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                FileInputStream(localFilePath).use { inputStream ->
                    if (ftpsClient.storeFile(remoteFilePath, inputStream)) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IOException("Failed to upload file to FTP server"))
                    }
                }
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }

    suspend fun downloadFile(remoteFilePath: String, localFilePath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                FileOutputStream(localFilePath).use { outputStream ->
                    if (ftpsClient.retrieveFile(remoteFilePath, outputStream)) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IOException("Failed to download file from FTP server"))
                    }
                }
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }

    suspend fun disconnect(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (ftpsClient.isConnected) {
                    ftpsClient.logout()
                    ftpsClient.disconnect()
                }
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }
}
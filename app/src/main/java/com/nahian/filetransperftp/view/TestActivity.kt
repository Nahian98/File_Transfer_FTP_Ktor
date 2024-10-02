package com.nahian.filetransperftp.view

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.nahian.filetransperftp.R
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        startKtorServer()
    }

    private fun startKtorServer() {
        GlobalScope.launch(Dispatchers.IO) {
            val result = runCatching {
                embeddedServer(Netty, port = 8080) {
                    install(CallLogging)

                    routing {
                        post("/upload") {
                            try {
                                val multipart = call.receiveMultipart()
                                var uploadSuccess = false
                                multipart.forEachPart { part ->
                                    if (part is PartData.FileItem) {
                                        val fileName = part.originalFileName ?: "unknown"
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            val contentValues = ContentValues().apply {
                                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                                put(MediaStore.MediaColumns.MIME_TYPE, part.contentType.toString())
                                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                            }
                                            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                            uri?.let {
                                                contentResolver.openOutputStream(it).use { outputStream ->
                                                    part.streamProvider().use { input ->
                                                        input.copyTo(outputStream!!)
                                                    }
                                                }
                                                uploadSuccess = true
                                            }
                                        } else {
                                            val file = File(Environment.getExternalStorageDirectory(), fileName)
                                            part.streamProvider().use { input ->
                                                file.outputStream().buffered().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            uploadSuccess = true
                                        }
                                    }
                                    part.dispose()
                                }
                                if (uploadSuccess) {
                                    call.respondText("File uploaded successfully")
                                } else {
                                    call.respondText("File upload failed", status = HttpStatusCode.InternalServerError)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                call.respondText("File upload error: ${e.message}", status = HttpStatusCode.InternalServerError)
                            }
                        }
                    }
                }.start(wait = true)
            }

            result.onSuccess {
                println("Ktor server started successfully")
            }.onFailure { exception ->
                println("Failed to start Ktor server: ${exception.message}")
            }
        }
    }

    private suspend fun copyStreamWithProgress(input: InputStream, output: OutputStream, totalBytes: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val buffer = ByteArray(1024)
                var bytesRead: Int
                var bytesCopied: Long = 0
                while (input.read(buffer).also { bytesRead = it } >= 0) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    Log.d(TAG, "Upload progress: ${(bytesCopied * 100) / totalBytes}%")
                }
                output.flush()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    companion object {
        private const val TAG = "TestActivity"
    }


}
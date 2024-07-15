package com.nahian.filetransperftp.view

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nahian.filetransperftp.databinding.ActivityMainBinding
import com.nahian.filetransperftp.manager.FTPManager
import com.nahian.filetransperftp.utils.UriHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Enumeration

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var ftpManager: FTPManager
    private val requestedPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE,
        )
    } else {
        arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initComponent()
        initListener()
    }

    private fun initComponent() {
        requestPermissionLaunch.launch(requestedPermissions)
        binding.uploadImageToFtpServer.isEnabled = false
        binding.uploadSongToFtpServer.isEnabled = false
        ftpManager = FTPManager()
    }



    private fun initListener() {
        binding.connectFtpServer.setOnClickListener {
            lifecycleScope.launch {
                val connectionResult = ftpManager.connectToFtpServer("ftpupload.net", 21, "b14_36821254", "kanon1998")

                connectionResult.onSuccess {
                    Log.d("FTP", "Connected successfully")
                    binding.uploadImageToFtpServer.isEnabled = true
                    binding.uploadSongToFtpServer.isEnabled = true
                }.onFailure { exception ->
                    Log.e("FTP", "Error connecting to FTP server: ${exception.message}")
                    binding.uploadImageToFtpServer.isEnabled = false
                    binding.uploadSongToFtpServer.isEnabled = false
                }
            }
        }

        binding.uploadImageToFtpServer.setOnClickListener {
            // Launch intent to pick file for upload
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }

        binding.uploadSongToFtpServer.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.type = "audio/*"
            pickFileLauncher.launch(intent)
        }

        binding.KtorServerBtn.setOnClickListener {
            val intent = Intent(this, KtorServerActivity::class.java)
            startActivity(intent)
        }
    }

    private val pickImageLauncher : ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data != null) {
                Log.d(TAG, "Image file uri: ${data.data}")
                val selectedImageFilePath = data.data?.let {
                    UriHelpers.getPathForUri(this, it)
                }

                Log.d(TAG, "Image file path: $selectedImageFilePath")
                val selectedImageFile = data.data?.let {
                    UriHelpers.getFileForUri(this, it)
                }
                if (selectedImageFilePath != null) {
                    lifecycleScope.launch {
                        val uploadResult = ftpManager.uploadFile(selectedImageFilePath, "/htdocs/${selectedImageFile?.name}")
                        uploadResult.onSuccess {
                            Log.d(TAG, "File uploaded successfully")
                        }.onFailure { exception ->
                            Log.e(TAG, "Error uploading file: ${exception.message}")
                        }
                    }

                } else {
                    Log.d(TAG, "Null path for image selected")
                }
            }

        }
    }

    private val pickFileLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data != null) {
                val selectedSongFilePath = data.data?.let {
                    UriHelpers.getPathForUri(this, it)
                }
                val selectedSongFile = data.data?.let {
                    UriHelpers.getFileForUri(this, it)
                }
                if (selectedSongFilePath != null) {
                    lifecycleScope.launch {
                        val uploadResult = ftpManager.uploadFile(selectedSongFilePath, "/htdocs/${selectedSongFile?.name}")
                        uploadResult.onSuccess {
                            Log.d(TAG, "File uploaded successfully")
                        }.onFailure { exception ->
                            Log.e(TAG, "Error uploading file: ${exception.message}")
                        }
                    }
                } else {
                    Log.d(TAG, "Null path for image selected")
                }
            }
        }
    }

    private val requestPermissionLaunch = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { it ->
        if (!it.all { it.value }) {
            onPermissionDenied()
        }
    }

    private fun onPermissionDenied() {
        Toast.makeText(this@MainActivity,"Lack of permissions, please grant permissions first", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

//192.168.68.104
//192.168.68.120
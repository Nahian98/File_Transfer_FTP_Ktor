package com.nahian.filetransperftp.view

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.nahian.filetransperftp.databinding.ActivityMainBinding
import com.nahian.filetransperftp.manager.APManager
import com.nahian.filetransperftp.manager.APManager.Companion.ERROR_DISABLE_HOTSPOT
import com.nahian.filetransperftp.manager.APManager.Companion.ERROR_DISABLE_WIFI
import com.nahian.filetransperftp.manager.APManager.Companion.ERROR_GPS_PROVIDER_DISABLED
import com.nahian.filetransperftp.manager.APManager.Companion.ERROR_LOCATION_PERMISSION_DENIED
import com.nahian.filetransperftp.manager.APManager.Companion.ERROR_UNKNOWN
import com.nahian.filetransperftp.manager.APManager.Companion.ERROR_WRITE_SETTINGS_PERMISSION_REQUIRED
import com.nahian.filetransperftp.manager.FTPManager
import com.nahian.filetransperftp.utils.UriHelpers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var ftpManager: FTPManager
    private val requestedPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val scanQrResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultData ->
        if (resultData.resultCode == RESULT_OK) {
            val result = ScanIntentResult.parseActivityResult(resultData.resultCode, resultData.data)

            // This will be QR activity result
            if (result.contents == null) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show()
            } else {
                val qrContent = result.contents
                Log.d(TAG, "Scanned QR Content: $qrContent")

                // Parse SSID and Password using regular expressions
                val wifiRegex = """WIFI:S:(.*?);T:.*?;P:(.*?);;""".toRegex()
                val matchResult = wifiRegex.find(qrContent)

                if (matchResult != null) {
                    val ssid = matchResult.groupValues[1]
                    val password = matchResult.groupValues[2]

                    Toast.makeText(this, "SSID: $ssid\nPassword: $password", Toast.LENGTH_LONG).show()
                    Log.d(TAG, "SSID: $ssid, Password: $password")

                    // Now you can use the SSID and password to connect to the hotspot
                    connectToHotspot(ssid, password)
                } else {
                    Toast.makeText(this, "Invalid QR Code format", Toast.LENGTH_LONG).show()
                }
            }
        }
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

    private fun connectToHotspot(ssid: String, password: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(wifiNetworkSpecifier)
                .build()

            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.requestNetwork(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Connected to Wi-Fi: $ssid")
                }

                override fun onUnavailable() {
                    Log.d(TAG, "Failed to connect to Wi-Fi: $ssid")
                }
            })
        } else {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            val wifiConfig = WifiConfiguration().apply {
                SSID = String.format("\"%s\"", ssid)
                preSharedKey = String.format("\"%s\"", password)
//                when (encryptionType) {
//                    Barcode.WiFi.TYPE_WPA -> {
//
//                    }
//                    Barcode.WiFi.TYPE_WEP -> {
//                        wepKeys[0] = String.format("\"%s\"", password)
//                        wepTxKeyIndex = 0
//                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
//                        allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
//                    }
//                    Barcode.WiFi.TYPE_OPEN -> {
//                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
//                    }
//                    else -> {
//                        Log.e(TAG, "Unsupported encryption type")
//                        return
//                    }
//                }
            }

            // Disconnect from any current Wi-Fi network
            wifiManager.disconnect()

            // Add the new configuration to the list of configured networks
            val netId = wifiManager.addNetwork(wifiConfig)
            if (netId != -1) {
                // Enable the network
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
                Log.d(TAG, "Connected to SSID: $ssid")
            } else {
                Log.e(TAG, "Failed to connect to SSID: $ssid")
            }
        }
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
            val intent = Intent(this, KtorHttpServerActivity::class.java)
            startActivity(intent)
        }

        binding.KtorTestServerBtn.setOnClickListener {
            val intent = Intent(this, TestActivity::class.java)
            startActivity(intent)
        }

        binding.NanoHttpdServerBtn.setOnClickListener {
            val intent = Intent(this, NanoHttpdActivity::class.java)
            startActivity(intent)
        }

        binding.wifiHotspotBtn.setOnClickListener {
            enableHotspot()
        }

        binding.btnScanQRCode.setOnClickListener {
            scanQrResultLauncher.launch(ScanContract().createIntent(this, ScanOptions()))
//            scanQRCode(binding.ivHotspotQRCode.drawable.toBitmap())
        }
    }

    private fun enableHotspot() {
        Log.d(TAG, "enableHotspot()")
        val apManager = APManager.getApManager(this)
        apManager!!.turnOnHotspot(
            this,
            object : APManager.OnSuccessListener {
                override fun onSuccess(ssid: String, password: String) {
                    // Handle the success scenario, e.g., show the SSID and password
                    runOnUiThread {
                        val qrCode = generateQRCode(ssid, password)
                        binding.ivHotspotQRCode.visibility = View.VISIBLE
                        binding.ivHotspotQRCode.setImageBitmap(qrCode)
                    }
                    Toast.makeText(this@MainActivity, "Hotspot SSID: $ssid, Password: $password", Toast.LENGTH_LONG).show()
                }
            },
            object : APManager.OnFailureListener {
                override fun onFailure(failureCode: Int, e: Exception?) {
                    // Handle the failure scenario
                    when (failureCode) {
                        ERROR_DISABLE_WIFI -> Toast.makeText(this@MainActivity, "Please disable Wi-Fi first", Toast.LENGTH_SHORT).show()
                        ERROR_GPS_PROVIDER_DISABLED -> Toast.makeText(this@MainActivity, "Please enable GPS", Toast.LENGTH_SHORT).show()
                        ERROR_LOCATION_PERMISSION_DENIED -> Toast.makeText(this@MainActivity, "Location permission denied", Toast.LENGTH_SHORT).show()
                        ERROR_WRITE_SETTINGS_PERMISSION_REQUIRED -> Toast.makeText(this@MainActivity, "Write settings permission required", Toast.LENGTH_SHORT).show()
                        ERROR_DISABLE_HOTSPOT -> Toast.makeText(this@MainActivity, "Please disable existing hotspot", Toast.LENGTH_SHORT).show()
                        ERROR_UNKNOWN -> Toast.makeText(this@MainActivity, "An unknown error occurred", Toast.LENGTH_SHORT).show()
                    }
                    e?.printStackTrace() // Log the exception if needed
                }
            }
        )
    }

    @Throws(WriterException::class)
    fun generateQRCode(ssid: String, password: String): Bitmap {
        Log.d(TAG, "generateQRCode() ssid : $ssid password : $password")

        Log.d(TAG, "SSID: $ssid, Password: $password")

        val qrCodeCanvas: BitMatrix
        val size = 800 //pixels
        val qrCodeContent = "WIFI:S:$ssid;T:WPA;P:$password;;"
        qrCodeCanvas = MultiFormatWriter().encode(
            qrCodeContent,
            BarcodeFormat.QR_CODE,
            size,
            size,

            )
        val w = qrCodeCanvas.width
        val h = qrCodeCanvas.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] =
                    if (qrCodeCanvas[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val qrBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        qrBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return qrBitmap
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
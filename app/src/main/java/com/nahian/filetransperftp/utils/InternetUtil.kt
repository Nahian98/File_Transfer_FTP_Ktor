package com.nahian.filetransperftp.utils

import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


object InternetUtil {
    private const val TAG = "InternetUtil"
    fun getLocalIpAddress(): String? {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.map { networkInterface ->
                networkInterface.inetAddresses?.toList()?.find {
                    !it.isLoopbackAddress && it is Inet4Address
                }?.let { return it.hostAddress }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Ip address exception: ${e.message}")
        }
        return null
    }

    fun encodeIpToBase64(ipAddress: String): String {
        return Base64.encodeToString(ipAddress.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun encryptData(apiKey: String, keyAlias: String): Pair<String, String> {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )

        val secretKey = keyGenerator.generateKey()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val encryptionIv = cipher.iv // Get the IV
        val encryptedData = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))

        // Encode both IV and encrypted data to Base64 and return as a pair
        val encryptedDataString = Base64.encodeToString(encryptedData, Base64.DEFAULT)
        val ivString = Base64.encodeToString(encryptionIv, Base64.DEFAULT)

        return Pair(encryptedDataString, ivString)
    }

    fun decryptData(encryptedData: String, keyAlias: String, encryptionIv: String): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128,Base64.decode(encryptionIv,Base64.DEFAULT))
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decodedData = Base64.decode(encryptedData, Base64.DEFAULT)
        val decryptedData = cipher.doFinal(decodedData)

        return String(decryptedData, Charsets.UTF_8)
    }
}
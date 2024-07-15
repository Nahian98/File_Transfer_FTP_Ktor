package com.nahian.filetransperftp.tls

import android.content.Context
import com.nahian.filetransperftp.interfaces.SslCredentials
import com.nahian.filetransperftp.utils.InternetUtil
import java.io.File
import java.security.KeyStore

class CompanySslCredentials(private val context: Context) : SslCredentials {

    override fun getKeyStoreFile(): File {
        val directory = context.getDir("Downloads", Context.MODE_PRIVATE)
        val file = File(directory, "keyfilecompany")
        file.createNewFile()

        try {
//            val inputStream = context.resources.openRawResource(R.raw.keyfile)
//            val fileOutputStream = FileOutputStream(file)
//            val buf = ByteArray(1024)
//            var len: Int
//            while (inputStream.read(buf).also { len = it } > 0) {
//                fileOutputStream.write(buf, 0, len)
//            }
//            fileOutputStream.close()
//            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return file
    }

    override fun getKeyStore(): KeyStore {
        return KeyStoreFactory().generate(InternetUtil.getLocalIpAddress()!!)
    }

    override fun getKeyAlias(): String {
        return ALIAS
    }

    override fun getKeyPassword(): String {
        return PASSWORD
    }

    override fun getAliasPassword(): String {
        return PASSWORD
    }

    companion object {
        const val ALIAS = "company-local-test"
        const val PASSWORD = "XXXXX"
    }
}
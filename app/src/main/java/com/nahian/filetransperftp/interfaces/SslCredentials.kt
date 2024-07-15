package com.nahian.filetransperftp.interfaces

import java.io.File
import java.security.KeyStore

interface SslCredentials {

    fun getKeyStoreFile(): File

    fun getKeyStore(): KeyStore

    fun getKeyAlias(): String

    fun getKeyPassword(): String

    fun getAliasPassword(): String
}
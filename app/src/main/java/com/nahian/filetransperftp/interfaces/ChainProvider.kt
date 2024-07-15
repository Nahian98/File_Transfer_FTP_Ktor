package com.nahian.filetransperftp.interfaces

import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate

interface ChainProvider {

    companion object {
//        fun test() : ChainProvider {
//            return TestChainProvider()
//        }

        fun live() : ChainProvider {
            TODO()
        }
    }

    fun getRootCertificate() : X509Certificate
    fun getIntermediateCertificate() : X509Certificate
    fun getIntermediatePrivateKey() : PrivateKey
    fun getIntermediatePublicKey() : PublicKey
    fun getIntermediateKeyPair(): KeyPair = KeyPair(getIntermediatePublicKey(), getIntermediatePrivateKey())

}

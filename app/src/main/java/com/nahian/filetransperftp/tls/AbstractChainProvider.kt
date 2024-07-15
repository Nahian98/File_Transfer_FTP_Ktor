package com.nahian.filetransperftp.tls

import com.nahian.filetransperftp.interfaces.ChainProvider
import org.bouncycastle.util.encoders.Base64
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

abstract class AbstractChainProvider : ChainProvider {

    private val certificateFactory = CertificateFactory.getInstance("X.509")
    private val keyFactory = KeyFactory.getInstance("RSA")

    protected fun toPublicKey(str: String): PublicKey {
        val publicKeyPEM: String = str
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace(System.lineSeparator(), "")
            .replace("-----END PUBLIC KEY-----", "")

        val decoded = Base64.decode(publicKeyPEM)
        return keyFactory.generatePublic(X509EncodedKeySpec(decoded))
    }

    protected fun toCertificate(intermediateCa: String): X509Certificate {
        return certificateFactory
            .generateCertificate(intermediateCa.byteInputStream()) as X509Certificate
    }

    protected fun toPrivateKey(intermediatePrivateKey: String): PrivateKey {
        val privateKeyPEM: String = intermediatePrivateKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace(System.lineSeparator(), "")
            .replace("-----END PRIVATE KEY-----", "")

        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privateKeyPEM)))
    }
}
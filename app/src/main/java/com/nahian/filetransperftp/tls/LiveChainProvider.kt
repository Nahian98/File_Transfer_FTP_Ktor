package com.nahian.filetransperftp.tls

import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate

class LiveChainProvider : AbstractChainProvider() {

    private val ROOT_CA = """
        -----BEGIN CERTIFICATE-----
        .
        .
        .

        -----END CERTIFICATE-----
    """.trimIndent()

    private val INTERMEDIATE_CA = """
        -----BEGIN CERTIFICATE-----
        .
        .
        .

        -----END CERTIFICATE-----
    """.trimIndent()

    private val INTERMEDIATE_PRIVATE_KEY = """
        -----BEGIN PRIVATE KEY-----
        .
        .
        .
        -----END PRIVATE KEY-----
    """.trimIndent()

    private val INTERMEDIATE_PUBLIC_KEY = """
        -----BEGIN PUBLIC KEY-----
        .
        .
        .
        -----END PUBLIC KEY-----
    """.trimIndent()

    override fun getRootCertificate(): X509Certificate = toCertificate(ROOT_CA)
    override fun getIntermediateCertificate(): X509Certificate = toCertificate(INTERMEDIATE_CA)
    override fun getIntermediatePrivateKey(): PrivateKey = toPrivateKey(INTERMEDIATE_PRIVATE_KEY)
    override fun getIntermediatePublicKey(): PublicKey = toPublicKey(INTERMEDIATE_PUBLIC_KEY)
}
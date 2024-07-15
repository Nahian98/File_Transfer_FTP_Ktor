package com.nahian.filetransperftp.tls

import android.os.Build
import com.nahian.filetransperftp.interfaces.ChainProvider
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.X509KeyUsage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Date

class KeyStoreFactory {

    companion object {
        init {
            Security.addProvider(BouncyCastleProvider());
        }
    }

    private fun chainProvider() = ChainProvider.live()

    fun generate(ip: String): KeyStore {
        val keyPair = createKeyPair()

        val store: KeyStore = KeyStore.getInstance("BKS", "BC")
        store.load(null, null)
        store.setKeyEntry(
            CompanySslCredentials.ALIAS,
            keyPair.private,
            CompanySslCredentials.PASSWORD.toCharArray(),
            arrayOf(
                generateSelfSignedCertificate(keyPair, ip),
                chainProvider().getIntermediateCertificate(),
                chainProvider().getRootCertificate()
            )
        )

        return store
    }

    fun createKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA", "BC").genKeyPair()

    fun generateSelfSignedCertificate(keyPair: KeyPair, ip: String): X509Certificate {
        val subjectAltNames =
            GeneralNames.getInstance(DERSequence(arrayOf(GeneralName(GeneralName.iPAddress, ip))))

        val x500Name =
            X500Name(" C=XX, ST=XX, L=XX, O=XX, OU=XX CN=XX")
        val certificateBuilder: X509v3CertificateBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            JcaX509v3CertificateBuilder(
                chainProvider().getIntermediateCertificate(),
                BigInteger.valueOf(Instant.now().toEpochMilli()),
                Date.from(Instant.now()),
                Date.from(Instant.now().plus(Duration.ofDays(365))),
                x500Name,
                keyPair.public
            )
                .addExtension(
                    ASN1ObjectIdentifier("2.5.29.35"),
                    false,
                    AuthorityKeyIdentifier(
                        SubjectPublicKeyInfo.getInstance(
                            ASN1Sequence.getInstance(chainProvider().getIntermediatePublicKey().encoded)
                        )
                    )
                )
                .addExtension(ASN1ObjectIdentifier("2.5.29.19"), false, BasicConstraints(false))
                .addExtension(
                    ASN1ObjectIdentifier("2.5.29.15"),
                    true,
                    X509KeyUsage(
                        X509KeyUsage.digitalSignature or
                                X509KeyUsage.nonRepudiation or
                                X509KeyUsage.keyEncipherment or
                                X509KeyUsage.dataEncipherment
                    )
                )
                .addExtension(Extension.subjectAlternativeName, false, subjectAltNames)
        } else {
            TODO("VERSION.SDK_INT < O")
        }

        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider())
            .getCertificate(
                certificateBuilder.build(
                    JcaContentSignerBuilder("SHA256WithRSAEncryption").build(
                        chainProvider().getIntermediatePrivateKey()
                    )
                )
            )
    }
}
package com.nexters.gamss.payment.apple

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import com.nimbusds.jose.util.Base64 as JoseBase64

/**
 * Apple 과 같은 모양(루트, Apple 확장을 단 중간 인증서와 서명 인증서)의 자체 인증서 체인으로 거래 JWS 를 만든다.
 * 라이브러리 자체 테스트와 같은 방식이다. [root] 를 신뢰 루트로 넘긴 디코더만 이 JWS 를 통과시킨다.
 */
class AppleJwsFixture(
    signingOid: String = RECEIPT_SIGNER_OID,
) {
    private val rootKey = keyPair()
    private val intermediateKey = keyPair()
    private val leafKey = keyPair()

    val root: X509Certificate = certificate("CN=Test Root", rootKey, "CN=Test Root", rootKey, ca = true, oid = null)
    private val intermediate = certificate("CN=Test WWDR", intermediateKey, "CN=Test Root", rootKey, ca = true, oid = WWDR_INTERMEDIATE_OID)
    private val leaf = certificate("CN=Test Signer", leafKey, "CN=Test WWDR", intermediateKey, ca = false, oid = signingOid)

    fun decoder(
        bundleId: String = BUNDLE_ID,
        appAppleId: Long = APP_APPLE_ID,
    ) = SignedAppleTransactionDecoder(listOf(root.encoded), bundleId, appAppleId)

    fun signedTransaction(payload: Map<String, Any?>): String {
        val header =
            JWSHeader
                .Builder(JWSAlgorithm.ES256)
                .x509CertChain(listOf(leaf, intermediate, root).map { JoseBase64.encode(it.encoded) })
                .build()
        val jws = JWSObject(header, Payload(payload.filterValues { it != null }))
        jws.sign(ECDSASigner(leafKey.private as ECPrivateKey))
        return jws.serialize()
    }

    private fun certificate(
        subject: String,
        subjectKey: KeyPair,
        issuer: String,
        issuerKey: KeyPair,
        ca: Boolean,
        oid: String?,
    ): X509Certificate {
        val builder: X509v3CertificateBuilder =
            JcaX509v3CertificateBuilder(
                X500Name(issuer),
                BigInteger.valueOf(System.nanoTime()),
                Date.from(NOT_BEFORE),
                Date.from(NOT_AFTER),
                X500Name(subject),
                subjectKey.public,
            ).addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        oid?.let { builder.addExtension(ASN1ObjectIdentifier(it), false, DERNull.INSTANCE) }
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    companion object {
        const val BUNDLE_ID = "com.nexters.gamss"
        const val APP_APPLE_ID = 6799644564L
        const val WWDR_INTERMEDIATE_OID = "1.2.840.113635.100.6.2.1"
        const val RECEIPT_SIGNER_OID = "1.2.840.113635.100.6.11.1"
        val SIGNED_AT: Instant = Instant.parse("2026-11-06T00:00:00Z")
        private val NOT_BEFORE = SIGNED_AT.minus(365, ChronoUnit.DAYS)
        private val NOT_AFTER = SIGNED_AT.plus(365, ChronoUnit.DAYS)

        fun payload(
            environment: String = "Production",
            bundleId: String = BUNDLE_ID,
            transactionId: String = "2000000123456789",
            productId: String = "token_300000",
            type: String = "Consumable",
            quantity: Int = 1,
            appAccountToken: String? = "7f1c2a4e-3b5d-4c6e-8f90-a1b2c3d4e5f6",
            revocationDate: Instant? = null,
        ): Map<String, Any?> =
            mapOf(
                "transactionId" to transactionId,
                "originalTransactionId" to transactionId,
                "bundleId" to bundleId,
                "productId" to productId,
                "type" to type,
                "quantity" to quantity,
                "appAccountToken" to appAccountToken,
                "purchaseDate" to SIGNED_AT.minusSeconds(5).toEpochMilli(),
                "signedDate" to SIGNED_AT.toEpochMilli(),
                "revocationDate" to revocationDate?.toEpochMilli(),
                "environment" to environment,
            )

        private fun keyPair(): KeyPair =
            KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    }
}

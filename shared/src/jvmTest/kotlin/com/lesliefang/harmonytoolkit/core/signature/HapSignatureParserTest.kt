package com.lesliefang.harmonytoolkit.core.signature

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HapSignatureParserTest {

    private val caKey = ecKeyPair()
    private val appKey = ecKeyPair()
    private val caCert = issueCert(
        subject = "O=HarmonyToolkit,CN=Test Root CA",
        issuerKey = caKey,
        signerKey = caKey,
    )
    private val appCert = issueCert(
        subject = "O=HarmonyToolkit,CN=Test App Cert",
        issuerKey = caKey,
        signerKey = appKey,
    )

    private val profileJson = """
        {
          "version-code": 2,
          "type": "debug",
          "app-distribution-type": "debug_mode",
          "bundle-info": {
            "bundle-name": "com.example.test",
            "app-identifier": "123456789",
            "apl": "normal"
          },
          "permissions": { "restricted-permissions": ["ohos.permission.INTERNET"] },
          "debug-info": {
            "device-ids": ["UDID_1", "UDID_2"],
            "device-id-type": "udid"
          },
          "validity": { "not-before": 1000000000, "not-after": 2000000000 },
          "issuer": "test-issuer"
        }
    """.trimIndent()

    @Test
    fun extractsCertificateChainAndFingerprints() {
        val hap = buildSignedHap(schemePkcs7(), profileP7b())
        val sig = HapSignatureParser.parse(hap)

        assertNotNull(sig)
        sig!!
        assertEquals(2, sig.signBlockVersion)
        assertEquals(appCert.subjectX500Principal.name, sig.signerCertificate.subject)
        // 签名者在链首，其后按 issuer->subject 串成 CA
        assertEquals(2, sig.certificateChain.size)
        assertEquals(sig.signerCertificate, sig.certificateChain.first())
        assertEquals(caCert.subjectX500Principal.name, sig.certificateChain[1].subject)

        // 指纹与独立计算的 SHA-256(证书 DER) 一致
        val expect = MessageDigest.getInstance("SHA-256").digest(appCert.encoded)
            .joinToString(":") { "%02X".format(it) }
        assertEquals(expect, sig.signerCertificate.sha256Fingerprint)
        assertTrue(sig.signerCertificate.sha1Fingerprint.replace(":", "").length == 40)

        assertEquals("SHA256", sig.digestAlgorithm)
        assertEquals("SHA256WITHECDSA", sig.signatureAlgorithm)
        assertNotNull(sig.signTime)
        // 应用证书有效期应覆盖签名时间附近
        assertTrue(sig.signTime!!.isAfter(Instant.now().minus(Duration.ofDays(1))))
    }

    @Test
    fun parsesEmbeddedProfile() {
        val hap = buildSignedHap(schemePkcs7(), profileP7b())
        val profile = HapSignatureParser.parse(hap)!!.profile!!

        assertEquals("com.example.test", profile.bundleName)
        assertEquals("debug", profile.type)
        assertEquals("debug_mode", profile.appDistributionType)
        assertEquals("123456789", profile.appIdentifier)
        assertEquals("normal", profile.apl)
        assertEquals("test-issuer", profile.issuer)
        assertEquals(listOf("UDID_1", "UDID_2"), profile.deviceIds)
        assertEquals(listOf("ohos.permission.INTERNET"), profile.restrictedPermissions)
        assertEquals(Instant.ofEpochSecond(1000000000), profile.notBefore)
        assertEquals(Instant.ofEpochSecond(2000000000), profile.notAfter)
        assertTrue(profile.rawJson.contains("com.example.test"))
    }

    @Test
    fun unsignedZipReturnsNull() {
        assertNull(HapSignatureParser.parse(buildPlainZip()))
    }

    @Test
    fun parsesFromFile() {
        val tmp = File.createTempFile("test", ".hap").apply {
            writeBytes(buildSignedHap(schemePkcs7(), profileP7b()))
            deleteOnExit()
        }
        assertEquals(appCert.subjectX500Principal.name, HapSignatureParser.parse(tmp)!!.signerCertificate.subject)
    }

    // ---------- 测试夹具：用 BC 造一套与 hap-sign-tool 布局一致的签名块 ----------

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun issueCert(subject: String, issuerKey: KeyPair, signerKey: KeyPair): X509Certificate {
        val name = X500Name(subject)
        val now = Instant.now()
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(signerKey.private)
        val holder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(System.nanoTime()),
            Date.from(now.minus(Duration.ofDays(1))),
            Date.from(now.plus(Duration.ofDays(3650))),
            name,
            issuerKey.public,
        ).build(signer)
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    private fun pkcs7(content: ByteArray, cert: X509Certificate, key: PrivateKey): ByteArray {
        val gen = CMSSignedDataGenerator()
        val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(key)
        gen.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                .build(contentSigner, JcaX509CertificateHolder(cert))
        )
        gen.addCertificates(JcaCertStore(listOf(appCert, caCert)))
        return gen.generate(CMSProcessableByteArray(content), true).encoded
    }

    /** 应用签名子块内容（真实包里 content 为摘要列表，这里只验证解析，内容任意） */
    private fun schemePkcs7(): ByteArray = pkcs7(byteArrayOf(1, 2, 3), appCert, appKey.private)

    /** profile 子块内容：p7b，其 signedContent 为 provision JSON */
    private fun profileP7b(): ByteArray = pkcs7(profileJson.toByteArray(Charsets.UTF_8), caCert, caKey.private)

    private fun buildPlainZip(): ByteArray = ByteArrayOutputStream().use { out ->
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("module.json5"))
            z.write("{}".toByteArray())
            z.closeEntry()
        }
        out.toByteArray()
    }

    private fun buildSignedHap(schemePkcs7: ByteArray, profileP7b: ByteArray): ByteArray {
        val zip = buildPlainZip()
        val eocd = indexOfEocd(zip)
        val cdOff = readU32(zip, eocd + 16)

        // 子块索引表 [type u32][length u32][offset u32] + 值区；profile 在前、签名在后（对齐官方布局）
        data class Sub(val type: Int, val value: ByteArray)

        val subs = listOf(Sub(0x20000002, profileP7b), Sub(0x20000000, schemePkcs7))
        val index = ByteBuffer.allocate(subs.size * 12).order(ByteOrder.LITTLE_ENDIAN)
        val values = ByteArrayOutputStream()
        var valueOff = subs.size * 12
        for (sub in subs) {
            index.putInt(sub.type).putInt(sub.value.size).putInt(valueOff)
            values.write(sub.value)
            valueOff += sub.value.size
        }
        val bodySize = subs.size * 12 + values.size()
        val tail = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(subs.size)                                  // 块数
            .putLong((bodySize + 32).toLong())                  // 整块大小
            .put("HAP Sig Block 42".toByteArray())              // magic
            .putInt(2)                                          // 版本
        val block = ByteArrayOutputStream().apply {
            write(index.array())
            write(values.toByteArray())
            write(tail.array())
        }.toByteArray()

        // 插入中央目录之前，并修正 EOCD 里的 CD 偏移
        val out = ByteArray(zip.size + block.size)
        System.arraycopy(zip, 0, out, 0, cdOff)
        System.arraycopy(block, 0, out, cdOff, block.size)
        System.arraycopy(zip, cdOff, out, cdOff + block.size, zip.size - cdOff)
        writeU32(out, eocd + block.size + 16, cdOff + block.size)
        return out
    }

    private fun indexOfEocd(zip: ByteArray): Int {
        var i = zip.size - 22
        while (i >= 0) {
            if (zip[i] == 'P'.code.toByte() && zip[i + 1] == 'K'.code.toByte() &&
                zip[i + 2] == 5.toByte() && zip[i + 3] == 6.toByte()
            ) return i
            i--
        }
        error("zip 中未找到 EOCD")
    }

    private fun readU32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun writeU32(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }
}

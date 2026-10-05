package com.lesliefang.harmonytoolkit.core.sign

import com.lesliefang.harmonytoolkit.core.signature.ProfileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 材料检查器与 signing.json 持久化 */
class SigningMaterialsTest {

    @Test
    fun profileStoreRoundTripKeepsLastUsedAndDropsNothing() {
        val file = Files.createTempFile("signing", ".json").toFile()
        val store = SigningProfileStore(file)

        assertNull(store.lastProfile())

        store.save(SigningProfile(name = "调试", keystorePath = "/a.p12", certPath = "/a.cer", profilePath = "/a.p7b", keyAlias = "k"))
        store.save(SigningProfile(name = "发布", keystorePath = "/b.p12", certPath = "/b.cer", profilePath = "/b.p7b", keyAlias = "k2"))

        assertEquals("发布", store.lastProfile()?.name)
        assertEquals("/b.p7b", store.lastProfile()?.profilePath)

        // 同名保存是覆盖而不是追加
        store.save(SigningProfile(name = "发布", keystorePath = "/b2.p12", certPath = "/b.cer", profilePath = "/b.p7b", keyAlias = "k2"))
        val config = store.load()
        assertEquals(2, config.profiles.size)
        assertEquals("/b2.p12", store.lastProfile()?.keystorePath)

        // 损坏的配置文件不影响加载
        file.writeText("{broken json")
        assertNull(SigningProfileStore(file).lastProfile())
    }

    @Test
    fun loadKeyStoreReportsAliasesAndPasswordErrors() {
        val p12 = Files.createTempFile("mat", ".p12").toFile()
        val ks = java.security.KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        val kp = java.security.KeyPairGenerator.getInstance("EC")
            .apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        val self = selfSigned(kp)
        ks.setKeyEntry("aliasA", kp.private, "right".toCharArray(), arrayOf(self))
        p12.outputStream().use { ks.store(it, "right".toCharArray()) }

        val summary = MaterialInspector.loadKeyStore(p12.absolutePath, "right")
        // JDK 的 PKCS12 实现会把别名统一成小写
        assertEquals(listOf("aliasa"), summary.aliases.map { it.alias })
        assertNotNull(summary.aliases.single().certificate)

        var wrong = false
        try {
            MaterialInspector.loadKeyStore(p12.absolutePath, "wrong")
        } catch (e: IllegalArgumentException) {
            wrong = true
        }
        assertTrue(wrong)
    }

    @Test
    fun inspectProfileParsesTypeAndBundle() {
        val kp = java.security.KeyPairGenerator.getInstance("EC")
            .apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        val cert = selfSigned(kp)
        val json = """{"type":"release","bundle-info":{"bundle-name":"com.x.y"},"issuer":"t"}"""
        val p7b = org.bouncycastle.cms.CMSSignedDataGenerator().apply {
            val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withECDSA").build(kp.private)
            addSignerInfoGenerator(
                org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder(
                    org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder().build(),
                ).build(signer, org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(cert)),
            )
            addCertificates(org.bouncycastle.cert.jcajce.JcaCertStore(listOf(cert)))
        }.generate(org.bouncycastle.cms.CMSProcessableByteArray(json.toByteArray()), true).encoded

        val file = Files.createTempFile("profile", ".p7b").toFile().apply { writeBytes(p7b) }
        val info: ProfileInfo = MaterialInspector.inspectProfile(file.absolutePath)
        assertEquals("release", info.type)
        assertEquals("com.x.y", info.bundleName)
        assertEquals("t", info.issuer)
    }

    private fun selfSigned(kp: java.security.KeyPair): java.security.cert.X509Certificate {
        val name = org.bouncycastle.asn1.x500.X500Name("CN=Test,O=T")
        val now = java.time.Instant.now()
        return org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(
            org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                name,
                java.math.BigInteger.ONE,
                java.util.Date.from(now.minus(java.time.Duration.ofDays(1))),
                java.util.Date.from(now.plus(java.time.Duration.ofDays(1))),
                name,
                kp.public,
            ).build(org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withECDSA").build(kp.private)),
        )
    }
}

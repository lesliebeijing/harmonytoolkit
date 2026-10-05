package com.lesliefang.harmonytoolkit.core.sign

import com.lesliefang.harmonytoolkit.core.signature.HapSignatureParser
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * 端到端：用真实 hap-sign-tool.jar 对 fixture 里的 unsigned hap/hsp/app 签名，
 * 并用 HapSignatureParser 验证签名结构可解析。
 *
 * hap-sign-tool 要求 -appCertFile 为证书链（root CA → sub CA → 应用证书），
 * 夹具在内存中生成三级链与匹配的 debug profile。
 */
class AppSignerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val password = "123456"

    private val rootKey = ecKeyPair()
    private val subKey = ecKeyPair()
    private val appKey = ecKeyPair()
    private val rootCert = caCert(rootKey, null, "CN=AppSignTest Root CA,O=HarmonyToolkit")
    private val subCert = caCert(subKey, rootCert to rootKey, "CN=AppSignTest Sub CA,O=HarmonyToolkit")
    private val appCert = endCert(appKey, subCert to subKey, "CN=AppSignTest App,O=HarmonyToolkit")

    @Test
    fun signHap() {
        val output = signFixture("entry-default-unsigned.hap", "signed.hap")
        val sig = HapSignatureParser.parse(output)
        assertNotNull("签名后的 hap 应能解析出签名信息", sig)
        assertEquals("com.example.testplugin", sig!!.profile?.bundleName)
    }

    @Test
    fun signHsp() {
        val output = signFixture("hsp-default-unsigned.hsp", "signed.hsp")
        assertNotNull("签名后的 hsp 应能解析出签名信息", HapSignatureParser.parse(output))
    }

    @Test
    fun signApp() {
        val output = signFixture("testplugin-default-unsigned.app", "signed.app")
        assertTrue("签名后的 app 应已生成", output.isFile)
    }

    @Test
    fun wrongPasswordFails() {
        val input = fixture("entry-default-unsigned.hap")
        try {
            AppSigner.sign(input, tmp.newFolder().resolve("bad.hap"), buildMaterials(storePassword = "wrong"), 24)
            fail("错误密码应签名失败")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!, e.message!!.isNotBlank())
        }
    }

    // ---------- 夹具 ----------

    private fun signFixture(fixtureName: String, outputName: String): File {
        val input = fixture(fixtureName)
        val output = tmp.newFolder().resolve(outputName)
        AppSigner.sign(input, output, buildMaterials(), compatibleVersion = 24)
        assertTrue("签名产物应存在", output.isFile)
        assertTrue("签名产物应大于原始包", output.length() > input.length())
        return output
    }

    private fun fixture(name: String): File =
        javaClass.classLoader.getResource("signapp/$name")
            ?.let { File(it.toURI()) }
            ?: error("缺少测试夹具 signapp/$name")

    private fun buildMaterials(storePassword: String = password): SigningMaterials {
        val dir = tmp.newFolder()
        val p12 = dir.resolve("mat.p12").apply {
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry("debugKey", appKey.private, password.toCharArray(), arrayOf(appCert, subCert, rootCert))
            outputStream().use { ks.store(it, password.toCharArray()) }
        }
        // hap-sign-tool 要求证书链（叶子在前）
        val cer = dir.resolve("mat.cer").apply { writeText(certChainPem(listOf(appCert, subCert, rootCert))) }
        val p7b = dir.resolve("mat.p7b").apply { writeBytes(profileP7b()) }
        return SigningMaterials(
            keystorePath = p12.absolutePath,
            storePassword = storePassword,
            keyAlias = "debugKey",
            keyPassword = password,
            certPath = cer.absolutePath,
            profilePath = p7b.absolutePath,
        )
    }

    // profile 字段为 hap-sign-tool Provision 模型全集，与 fixture 包（com.example.testplugin）匹配；
    // development-certificate 即签名证书，工具会校验两者一致（URL-safe base64 编码）
    private fun profileJson(): String {
        val devCert = Base64.getUrlEncoder().encodeToString(appCert.encoded)
        val now = System.currentTimeMillis()
        return """
            {
              "version-name": "1.0.0",
              "version-code": 1000000,
              "app-distribution-type": "debug",
              "uuid": "6aaed7e0-8c4f-4f7f-a5d3-3d3f38aab111",
              "type": "debug",
              "validity": {
                "not-before": ${now - 86_400_000L},
                "not-after": ${now + 365L * 86_400_000L}
              },
              "bundle-info": {
                "developer-id": "HarmonyToolkitTest",
                "development-certificate": "$devCert",
                "bundle-name": "com.example.testplugin",
                "apl": "normal",
                "app-feature": "hos_normal_app"
              },
              "acls": { "allowed-acls": [] },
              "permissions": { "restricted-permissions": [], "restricted-capabilities": [] },
              "debug-info": { "device-id-type": "udid", "device-ids": ["UDID_TEST_1"] },
              "issuer": "HarmonyToolkitTest"
            }
        """.trimIndent()
    }

    private fun profileP7b(): ByteArray {
        val gen = CMSSignedDataGenerator()
        gen.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                .build(
                    JcaContentSignerBuilder("SHA256withECDSA").build(appKey.private),
                    JcaX509CertificateHolder(appCert),
                ),
        )
        gen.addCertificates(JcaCertStore(listOf(appCert, subCert, rootCert)))
        return gen.generate(CMSProcessableByteArray(profileJson().toByteArray()), true).encoded
    }

    private fun certChainPem(chain: List<X509Certificate>): String = chain.joinToString("") { c ->
        "-----BEGIN CERTIFICATE-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(c.encoded) +
            "\n-----END CERTIFICATE-----\n"
    }

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** issuer 为 null 时自签（root CA）；否则由 issuer 证书对应私钥签发（sub CA） */
    private fun caCert(
        subjectKey: KeyPair,
        issuer: Pair<X509Certificate, KeyPair>?,
        subjectName: String,
    ): X509Certificate = buildCert(
        subjectPublicKey = subjectKey.public,
        signerKey = issuer?.second?.private ?: subjectKey.private,
        issuerName = issuer?.first?.let { JcaX509CertificateHolder(it).subject } ?: X500Name(subjectName),
        subjectName = X500Name(subjectName),
        isCa = true,
    )

    /** 由 sub CA 签发的终端应用证书 */
    private fun endCert(
        subjectKey: KeyPair,
        issuer: Pair<X509Certificate, KeyPair>,
        subjectName: String,
    ): X509Certificate = buildCert(
        subjectPublicKey = subjectKey.public,
        signerKey = issuer.second.private,
        issuerName = JcaX509CertificateHolder(issuer.first).subject,
        subjectName = X500Name(subjectName),
        isCa = false,
    )

    private fun buildCert(
        subjectPublicKey: java.security.PublicKey,
        signerKey: PrivateKey,
        issuerName: X500Name,
        subjectName: X500Name,
        isCa: Boolean,
    ): X509Certificate {
        val now = Instant.now()
        val builder = JcaX509v3CertificateBuilder(
            issuerName,
            BigInteger.valueOf(System.nanoTime()),
            Date.from(now.minus(Duration.ofDays(1))),
            Date.from(now.plus(Duration.ofDays(365))),
            subjectName,
            subjectPublicKey,
        ).addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(isCa),
        ).addExtension(
            Extension.keyUsage,
            true,
            if (isCa) KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign) else KeyUsage(KeyUsage.digitalSignature),
        )
        return JcaX509CertificateConverter().getCertificate(
            builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(signerKey)),
        )
    }
}

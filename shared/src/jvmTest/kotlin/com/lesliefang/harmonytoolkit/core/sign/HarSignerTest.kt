package com.lesliefang.harmonytoolkit.core.sign

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/** 端到端：用真实 sdk-sign-tool-full.jar 对 har 目录签名并重打包，校验 .CodeSignature 内容 */
class HarSignerTest {

    private val keyPair = ecKeyPair()
    private val cert = selfSignedCert(keyPair)
    private val password = "123456"

    private val profileJson = """
        {
          "version-code": 2,
          "type": "debug",
          "bundle-info": { "bundle-name": "com.example.har.test", "app-identifier": "42" },
          "debug-info": { "device-ids": ["UDID_1"], "device-id-type": "udid" },
          "issuer": "test"
        }
    """.trimIndent()

    @Test
    fun signAndRepackProducesCodeSignature() {
        val dir = buildHarContentDir()
        val p12 = writeP12(dir.parentFile.resolve("mat.p12"))
        val cer = dir.parentFile.resolve("mat.cer")
        cer.writeBytes(cert.encoded)
        val p7b = dir.parentFile.resolve("mat.p7b")
        p7b.writeBytes(profileP7b())

        val out = dir.parentFile.resolve("out-signed.har")
        HarSigner.signAndRepack(
            dir,
            SigningMaterials(
                keystorePath = p12.absolutePath,
                storePassword = password,
                keyAlias = "debugKey",
                keyPassword = password,
                certPath = cer.absolutePath,
                profilePath = p7b.absolutePath,
            ),
            out,
        )

        val entries = readTarEntries(out)
        val names = entries.keys
        assertTrue(names.contains("package/oh-package.json5"))
        assertTrue(names.contains(".CodeSignature/codeFileList"))
        assertTrue(names.contains(".CodeSignature/profile"))
        assertTrue(names.contains(".CodeSignature/signature"))

        // profile 就是提供的 p7b 原文
        assertArrayEquals(p7b.readBytes(), entries[".CodeSignature/profile"])

        // codeFileList 覆盖 package 下全部文件且摘要正确（gson 会把 = 转义为 \u003d，比对前还原）
        val codeFileList = String(entries[".CodeSignature/codeFileList"]!!)
        val packageFiles = names.filter { it.startsWith("package/") && entries[it] != null }
        packageFiles.forEach { name ->
            val expected = Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(entries[name]!!))
            val pattern = """"fileName":"$name","digest":"([^"]+)""""
            val digest = Regex(pattern).find(codeFileList)?.groupValues?.get(1)?.replace("\\u003d", "=")
            assertNotNull("codeFileList 缺少 $name", digest)
            assertEquals(expected, digest)
        }

        // signature 是对 codeFileList 全文的 PKCS7 签名，签名者为我们的证书
        val cms = CMSSignedData(entries[".CodeSignature/signature"]!!)
        val content = (cms.signedContent.content as ByteArray)
        assertArrayEquals(entries[".CodeSignature/codeFileList"], content)
        val certStore: org.bouncycastle.util.Store<org.bouncycastle.cert.X509CertificateHolder> = cms.certificates
        val signerCert = certStore.getMatches(
            cms.signerInfos.signers.first().sid as org.bouncycastle.util.Selector<org.bouncycastle.cert.X509CertificateHolder>,
        ).first()
        assertArrayEquals(cert.encoded, signerCert.encoded)
    }

    @Test
    fun resignOnAlreadySignedDirReplacesSignature() {
        val dir = buildHarContentDir()
        val p12 = writeP12(dir.parentFile.resolve("mat.p12"))
        val cer = dir.parentFile.resolve("mat.cer").apply { writeBytes(cert.encoded) }
        val p7b = dir.parentFile.resolve("mat.p7b").apply { writeBytes(profileP7b()) }
        val materials = SigningMaterials(
            keystorePath = p12.absolutePath,
            storePassword = password,
            keyAlias = "debugKey",
            keyPassword = password,
            certPath = cer.absolutePath,
            profilePath = p7b.absolutePath,
        )

        // 第一次签名（就地）
        HarSigner.signDir(dir, materials)
        assertTrue(File(dir, ".CodeSignature/signature").isFile)
        val firstSignature = File(dir, ".CodeSignature/signature").readBytes()

        // 修改一个文件后再签：旧签名应被删除替换，codeFileList 摘要更新
        File(dir, "package/oh-package.json5").writeText("""{"name":"changed"}""")
        HarSigner.signDir(dir, materials)
        val secondSignature = File(dir, ".CodeSignature/signature").readBytes()
        assertTrue(firstSignature.isNotEmpty() && !firstSignature.contentEquals(secondSignature))
        val list = File(dir, ".CodeSignature/codeFileList").readText().replace("\\u003d", "=")
        val expected = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(File(dir, "package/oh-package.json5").readBytes()),
        )
        assertTrue(list.contains(expected))
    }

    // ---------- 夹具 ----------

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun selfSignedCert(keyPair: KeyPair): X509Certificate {
        val name = X500Name("CN=Har Sign Test,O=HarmonyToolkit")
        val now = Instant.now()
        return JcaX509CertificateConverter().getCertificate(
            JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(now.minus(Duration.ofDays(1))),
                Date.from(now.plus(Duration.ofDays(365))),
                name,
                keyPair.public,
            ).build(JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)),
        )
    }

    private fun writeP12(file: File): File {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("debugKey", keyPair.private, password.toCharArray(), arrayOf(cert))
        file.outputStream().use { ks.store(it, password.toCharArray()) }
        return file
    }

    private fun profileP7b(): ByteArray {
        val gen = CMSSignedDataGenerator()
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        gen.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                .build(signer, JcaX509CertificateHolder(cert)),
        )
        gen.addCertificates(JcaCertStore(listOf(cert)))
        return gen.generate(CMSProcessableByteArray(profileJson.toByteArray()), true).encoded
    }

    private fun buildHarContentDir(): File {
        val dir = Files.createTempDirectory("har-content").toFile()
        File(dir, "package/ets").mkdirs()
        File(dir, "package/oh-package.json5").writeText("""{"name":"com.example.har.test"}""")
        File(dir, "package/ets/modules.abc").writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        File(dir, "package/Index.d.ets").writeText("export {}")
        return dir
    }

    private fun readTarEntries(har: File): Map<String, ByteArray?> {
        val entries = LinkedHashMap<String, ByteArray?>()
        TarArchiveInputStream(GzipCompressorInputStream(FileInputStream(har))).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                entries[entry.name] = if (entry.isDirectory) null else tar.readBytes()
            }
        }
        return entries
    }
}

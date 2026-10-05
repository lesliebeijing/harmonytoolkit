package com.lesliefang.harmonytoolkit.core.signature

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.operator.DefaultAlgorithmNameFinder
import org.bouncycastle.util.Selector
import org.bouncycastle.util.Store
import java.io.File
import java.security.cert.X509Certificate
import java.time.Instant

/** 一张 X.509 证书的关键信息；指纹为大写冒号分隔十六进制，与 DevEco/AGC/keytool 展示一致 */
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    /** 证书自身的签名算法，如 SHA256WITHECDSA */
    val signatureAlgorithm: String,
    /** 公钥算法，如 EC、RSA */
    val publicKeyAlgorithm: String,
    val notBefore: Instant,
    val notAfter: Instant,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
) {
    companion object {
        fun of(cert: java.security.cert.X509Certificate): CertificateInfo = CertificateInfo(
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            serialNumber = cert.serialNumber.toString(16),
            signatureAlgorithm = cert.sigAlgName,
            publicKeyAlgorithm = cert.publicKey.algorithm,
            notBefore = cert.notBefore.toInstant(),
            notAfter = cert.notAfter.toInstant(),
            sha256Fingerprint = fingerprint(cert.encoded, "SHA-256"),
            sha1Fingerprint = fingerprint(cert.encoded, "SHA-1"),
        )

        private fun fingerprint(encoded: ByteArray, algorithm: String): String =
            java.security.MessageDigest.getInstance(algorithm).digest(encoded)
                .joinToString(":") { "%02X".format(it) }
    }
}

/** HAP 内嵌 profile（HarmonyAppProvision）的关键字段；字段缺失时为 null/空 */
data class ProfileInfo(
    val bundleName: String? = null,
    /** debug / release */
    val type: String? = null,
    /** 如 debug_mode、os_integration、app-store */
    val appDistributionType: String? = null,
    val appIdentifier: String? = null,
    /** 应用权限级别，如 normal / system_basic */
    val apl: String? = null,
    val issuer: String? = null,
    val deviceIds: List<String> = emptyList(),
    val restrictedPermissions: List<String> = emptyList(),
    val notBefore: Instant? = null,
    val notAfter: Instant? = null,
    val rawJson: String,
)

/** 一个已签名 HAP/App/HSP 的签名信息 */
data class HapSignature(
    /** 签名块版本号 */
    val signBlockVersion: Int,
    /** 实际执行签名的证书（证书链叶子） */
    val signerCertificate: CertificateInfo,
    /** 完整证书链，签名者在前 */
    val certificateChain: List<CertificateInfo>,
    /** 内容摘要算法名，如 SHA-256 */
    val digestAlgorithm: String?,
    /** 签名算法名，如 SHA256WITHECDSA */
    val signatureAlgorithm: String?,
    /** 签名时间（取自签名者签名属性，可能缺失） */
    val signTime: Instant?,
    /** 包内 profile；签名块未内嵌 profile 子块时为 null */
    val profile: ProfileInfo?,
)

/** 签名块结构损坏（有 magic 但内容解析失败） */
class HapSignatureFormatException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * 直接从已签名的 HAP/App/HSP 包中解析签名信息（证书链、SHA-256/SHA-1 指纹、
 * 签名算法、签名时间与内嵌 profile），纯 JVM 实现，不依赖外部工具。
 *
 * 签名块布局与 hap-sign-tool 一致：位于 zip 条目数据与中央目录之间，
 * 末尾 32 字节为 [块数 u32][块大小 u64][magic 16B][版本 u32]，
 * magic 为 "HAP Sig Block 42"（v2）或 "<hap sign block>"（v3）。
 *
 * 未签名的包返回 null；结构损坏抛 [HapSignatureFormatException]。
 */
object HapSignatureParser {

    private val MAGIC_V2 = "HAP Sig Block 42".toByteArray(Charsets.US_ASCII)
    private val MAGIC_V3 = "<hap sign block>".toByteArray(Charsets.US_ASCII)

    /** PKCS7 签名子块（应用签名证书与签名值所在） */
    private const val SIGNATURE_SCHEME_BLOCK_ID = 0x20000000

    /** profile 子块（内嵌 p7b） */
    private const val PROFILE_BLOCK_ID = 0x20000002

    private const val EOCD_MIN_SIZE = 22
    private const val ZIP_COMMENT_MAX = 65535
    private const val SIGN_BLOCK_HEADER_SIZE = 32

    private val certConverter = JcaX509CertificateConverter()
    private val algorithmNameFinder = DefaultAlgorithmNameFinder()
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @JvmStatic
    fun parse(file: File): HapSignature? = parse(file.readBytes())

    @JvmStatic
    fun parse(bytes: ByteArray): HapSignature? {
        val cdOffset = findCentralDirectoryOffset(bytes)
        // 中央目录前 32 字节是签名块头；连头都放不下说明没有签名块
        if (cdOffset < SIGN_BLOCK_HEADER_SIZE) return null

        val header = bytes.copyOfRange(cdOffset - SIGN_BLOCK_HEADER_SIZE, cdOffset)
        val count = readU32(header, 0)
        val size = readU64(header, 4)
        val magic = header.copyOfRange(12, 28)
        val version = readU32(header, 28)
        if (!magic.contentEquals(MAGIC_V2) && !magic.contentEquals(MAGIC_V3)) return null
        if (size < SIGN_BLOCK_HEADER_SIZE || size > Int.MAX_VALUE || size > cdOffset) {
            throw HapSignatureFormatException("签名块大小非法: $size")
        }

        val blockOffset = (cdOffset - size).toInt()
        val blocks = readSubBlocks(bytes, blockOffset, count)

        val schemeBytes = blocks[SIGNATURE_SCHEME_BLOCK_ID]
            ?: throw HapSignatureFormatException("签名块中缺少 PKCS7 签名子块")
        val cms = parseCms(schemeBytes, "PKCS7 签名子块")

        val certStore: Store<X509CertificateHolder> = cms.certificates
        val allCerts = certStore.getMatches(null).map { certConverter.getCertificate(it) }
        val signer = cms.signerInfos.signers.firstOrNull()
            ?: throw HapSignatureFormatException("PKCS7 中没有签名者信息")
        // SignerId 在 BC 中实现的是原始类型 Selector，这里强转恢复泛型
        val signerHolder = certStore
            .getMatches(signer.sid as Selector<X509CertificateHolder>)
            .firstOrNull()
        val signerCert = signerHolder?.let { certConverter.getCertificate(it) }
            ?: allCerts.firstOrNull()
            ?: throw HapSignatureFormatException("PKCS7 中没有证书")

        val profileBytes = blocks[PROFILE_BLOCK_ID]
        return HapSignature(
            signBlockVersion = version,
            signerCertificate = signerCert.toInfo(),
            certificateChain = orderChain(signerCert, allCerts).map { it.toInfo() },
            digestAlgorithm = oidName(signer.digestAlgOID),
            signatureAlgorithm = oidName(signer.encryptionAlgOID),
            signTime = readSignTime(signer),
            profile = profileBytes?.let(::parseProfile),
        )
    }

    /** 向后扫描 EOCD（兼容带注释的 zip），返回中央目录偏移 */
    private fun findCentralDirectoryOffset(bytes: ByteArray): Int {
        val lowerBound = maxOf(0, bytes.size - EOCD_MIN_SIZE - ZIP_COMMENT_MAX)
        var i = bytes.size - EOCD_MIN_SIZE
        while (i >= lowerBound) {
            if (bytes[i] == 'P'.code.toByte() && bytes[i + 1] == 'K'.code.toByte() &&
                bytes[i + 2] == 5.toByte() && bytes[i + 3] == 6.toByte()
            ) {
                val cdOffset = readU32(bytes, i + 16)
                if (cdOffset == -1) {
                    throw HapSignatureFormatException("不支持 ZIP64 格式的包")
                }
                return cdOffset
            }
            i--
        }
        throw HapSignatureFormatException("不是有效的 zip 格式（未找到 EOCD）")
    }

    /**
     * 遍历签名块头之后的子块索引表：每项 12 字节 [type u32][length u32][offset u32]，
     * offset 相对签名块起始；返回 type -> 子块内容。
     */
    private fun readSubBlocks(bytes: ByteArray, blockOffset: Int, count: Int): Map<Int, ByteArray> {
        if (count <= 0 || count > 16) {
            throw HapSignatureFormatException("签名块子块数量非法: $count")
        }
        val blocks = HashMap<Int, ByteArray>(count)
        for (i in 0 until count) {
            val entryOffset = blockOffset + i * 12
            val type = readU32(bytes, entryOffset)
            val length = readU32(bytes, entryOffset + 4)
            val valueOffset = readU32(bytes, entryOffset + 8)
            if (length < 0 || valueOffset < 0 || valueOffset.toLong() + length > bytes.size - blockOffset) {
                throw HapSignatureFormatException("子块 #$i 的偏移/长度越界")
            }
            blocks[type] = bytes.copyOfRange(blockOffset + valueOffset, blockOffset + valueOffset + length)
        }
        return blocks
    }

    private fun parseCms(pkcs7: ByteArray, what: String): CMSSignedData = try {
        CMSSignedData(pkcs7)
    } catch (e: Exception) {
        throw HapSignatureFormatException("$what 不是有效的 PKCS7 数据", e)
    }

    /** OID(如 2.16.840.1.101.3.4.2.1)转可读名(如 SHA-256)；未知 OID 原样返回 */
    private fun oidName(oid: String?): String? =
        oid?.let { algorithmNameFinder.getAlgorithmName(ASN1ObjectIdentifier(it)) }

    /** 签名时间属性（CMSAttributes.signingTime），缺失返回 null */
    private fun readSignTime(signer: SignerInformation): Instant? = try {
        signer.signedAttributes?.get(CMSAttributes.signingTime)?.attrValues?.getObjectAt(0)?.let {
            Time.getInstance(it).date.toInstant()
        }
    } catch (e: Exception) {
        null
    }

    /** 签名者在前，其后按 issuer -> subject 串成链；串不上的保持原序附后 */
    private fun orderChain(signer: X509Certificate, all: List<X509Certificate>): List<X509Certificate> {
        if (all.size <= 1) return listOf(signer)
        val rest = all.filter { !it.encoded.contentEquals(signer.encoded) }.toMutableList()
        val ordered = mutableListOf(signer)
        while (rest.isNotEmpty()) {
            val issuer = ordered.last().issuerX500Principal
            val next = rest.indexOfFirst { it.subjectX500Principal == issuer }
            if (next < 0) break
            ordered.add(rest.removeAt(next))
        }
        ordered.addAll(rest)
        return ordered
    }

    /** 解析独立的 profile p7b（与 HAP 内嵌的 profile 同格式），供签名材料校验复用 */
    @JvmStatic
    fun parseProfile(p7b: ByteArray): ProfileInfo? {
        val content = try {
            (parseCms(p7b, "profile p7b").signedContent?.content as? ByteArray)
        } catch (e: HapSignatureFormatException) {
            return null
        } ?: return null
        // 去掉可能的 UTF-8 BOM
        val body = if (content.size >= 3 && content[0] == 0xEF.toByte() &&
            content[1] == 0xBB.toByte() && content[2] == 0xBF.toByte()
        ) content.copyOfRange(3, content.size) else content
        val raw = String(body, Charsets.UTF_8)
        return try {
            val root = lenientJson.parseToJsonElement(raw).jsonObject
            val bundleInfo = root.obj("bundle-info")
            val validity = root.obj("validity")
            val debugInfo = root.obj("debug-info")
            ProfileInfo(
                bundleName = bundleInfo.str("bundle-name"),
                type = root.str("type"),
                appDistributionType = root.str("app-distribution-type"),
                appIdentifier = bundleInfo.str("app-identifier"),
                apl = bundleInfo.str("apl"),
                issuer = root.str("issuer"),
                deviceIds = debugInfo.arr("device-ids"),
                restrictedPermissions = root.obj("permissions").arr("restricted-permissions"),
                notBefore = validity.long("not-before")?.let { Instant.ofEpochSecond(it) },
                notAfter = validity.long("not-after")?.let { Instant.ofEpochSecond(it) },
                rawJson = raw,
            )
        } catch (e: Exception) {
            // JSON 结构异常时至少把原文带回去
            ProfileInfo(rawJson = raw)
        }
    }

    private fun X509Certificate.toInfo(): CertificateInfo = CertificateInfo.of(this)

    private fun JsonObject.obj(key: String): JsonObject =
        (this[key] as? JsonObject) ?: JsonObject(emptyMap())

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.arr(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    private fun readU32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun readU64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) {
            v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        }
        return v
    }
}

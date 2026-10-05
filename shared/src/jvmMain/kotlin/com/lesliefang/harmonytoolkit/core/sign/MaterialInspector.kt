package com.lesliefang.harmonytoolkit.core.sign

import com.lesliefang.harmonytoolkit.core.signature.CertificateInfo
import com.lesliefang.harmonytoolkit.core.signature.HapSignatureParser
import java.io.File
import java.security.KeyStore

/** p12 加载结果：别名列表 + 每个别名的证书摘要 */
data class KeyStoreSummary(
    val aliases: List<AliasInfo>,
) {
    data class AliasInfo(
        val alias: String,
        val certificate: CertificateInfo?,
    )
}

/**
 * 签名材料检查器：给签名前的 GUI 提供"当场校验"能力——
 * p12 输密码后列出别名和证书信息、cer 显示证书摘要、p7b 解析 debug/release 与 bundleName。
 */
object MaterialInspector {

    /** 加载 PKCS12 密钥库；路径不存在或密码错误时抛异常（消息面向用户） */
    @JvmStatic
    fun loadKeyStore(keystorePath: String, storePassword: String): KeyStoreSummary {
        val file = File(keystorePath)
        if (!file.isFile) {
            throw IllegalArgumentException("密钥库文件不存在: $keystorePath")
        }
        val ks = KeyStore.getInstance("PKCS12")
        try {
            file.inputStream().use { ks.load(it, storePassword.toCharArray()) }
        } catch (e: Exception) {
            throw IllegalArgumentException("密钥库加载失败（密码错误或文件损坏）", e)
        }
        val aliases = ks.aliases().toList().map { alias ->
            KeyStoreSummary.AliasInfo(
                alias = alias,
                certificate = (ks.getCertificate(alias) as? java.security.cert.X509Certificate)
                    ?.let { CertificateInfo.of(it) },
            )
        }
        return KeyStoreSummary(aliases = aliases)
    }

    /** 读取 .cer 证书文件（支持 PEM 与 DER） */
    @JvmStatic
    fun loadCertificate(certPath: String): CertificateInfo {
        val file = File(certPath)
        if (!file.isFile) {
            throw IllegalArgumentException("证书文件不存在: $certPath")
        }
        val factory = java.security.cert.CertificateFactory.getInstance("X.509")
        val cert = try {
            file.inputStream().use { factory.generateCertificate(it) }
        } catch (e: Exception) {
            throw IllegalArgumentException("证书解析失败（不是有效的 .cer 文件）", e)
        }
        return CertificateInfo.of(cert as java.security.cert.X509Certificate)
    }

    /** 读取 .p7b profile，解析出 debug/release、bundleName 等摘要 */
    @JvmStatic
    fun inspectProfile(profilePath: String): com.lesliefang.harmonytoolkit.core.signature.ProfileInfo {
        val file = File(profilePath)
        if (!file.isFile) {
            throw IllegalArgumentException("Profile 文件不存在: $profilePath")
        }
        return HapSignatureParser.parseProfile(file.readBytes())
            ?: throw IllegalArgumentException("Profile 解析失败（不是有效的 .p7b 文件）")
    }
}

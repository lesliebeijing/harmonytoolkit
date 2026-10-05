package com.lesliefang.harmonytoolkit.core.repack

import com.lesliefang.harmonytoolkit.core.extract.OpenedPackage
import com.lesliefang.harmonytoolkit.core.log.SessionLog
import com.lesliefang.harmonytoolkit.core.model.PackageKind
import com.lesliefang.harmonytoolkit.core.signature.HapSignatureFormatException
import com.lesliefang.harmonytoolkit.core.signature.HapSignatureParser
import kotlinx.serialization.json.Json
import java.io.File

/** 该包类型暂不支持重打包 */
class UnsupportedRepackException(kind: PackageKind) :
    RuntimeException("暂不支持 ${kind.label} 的重打包（当前支持: HAR/HAP/HSP/APP）")

/** HAR 内嵌的 SDK 签名目录（HarSigner 签名产物） */
private const val CODE_SIGNATURE_DIR = ".CodeSignature"

/**
 * 重打包：把解包缓存目录里的内容（可已被外部工具修改）重新打成与源包同格式的包。
 * 支持 HAR（tar.gz）与 app/hap/hsp（zip）。
 *
 * - HAR：已签名包的 `.CodeSignature` 摘要与文件内容绑定，重打包（尤其改过文件后）
 *   原签名必然失效，打包时整体剔除，仅剔除输出、不改动解包缓存。
 * - zip 系：签名块在条目数据与中央目录之间、不属于任何条目，重打包天然不保留，
 *   产物为未签名包，安装前需重新签名。
 */
object Repackager {

    fun repack(pkg: OpenedPackage, output: File, log: SessionLog) {
        if (pkg.kind != PackageKind.HAR && !pkg.kind.isZip) throw UnsupportedRepackException(pkg.kind)
        val contentDir = File(pkg.workDir, "content")
        require(contentDir.isDirectory) { "解包目录不存在: $contentDir" }
        when {
            pkg.kind == PackageKind.HAR -> repackHar(pkg, contentDir, output, log)
            pkg.kind.isZip -> repackZip(pkg, contentDir, output, log)
            else -> throw UnsupportedRepackException(pkg.kind)
        }
    }

    private fun repackHar(pkg: OpenedPackage, contentDir: File, output: File, log: SessionLog) {
        val signed = File(contentDir, CODE_SIGNATURE_DIR).isDirectory
        if (signed) {
            log.warn("已剔除签名目录 $CODE_SIGNATURE_DIR：重打包后原签名失效，安装前需重新签名")
        }
        val start = System.currentTimeMillis()
        log.info("开始重打包 ${pkg.sourceFile.name} → ${output.absolutePath}")
        val count = TarGzPacker.pack(contentDir, output, exclude = ::isCodeSignaturePath)
        log.info("重打包完成：$count 个条目，耗时 ${System.currentTimeMillis() - start} ms")
    }

    private fun repackZip(pkg: OpenedPackage, contentDir: File, output: File, log: SessionLog) {
        // 安装器依赖 pack.info，缺失或损坏只告警不阻断（重打包本身仍可完成）
        val packInfo = File(contentDir, "pack.info")
        if (!packInfo.isFile) {
            log.warn("缺少 pack.info：重打包产物可能无法安装")
        } else if (!isJson(packInfo.readText())) {
            log.warn("pack.info 不是合法 JSON：重打包产物可能无法安装")
        }
        if (isSigned(pkg.sourceFile)) {
            log.warn("已签名包：重打包不保留签名，安装前需重新签名")
        }
        val start = System.currentTimeMillis()
        log.info("开始重打包 ${pkg.sourceFile.name} → ${output.absolutePath}")
        val count = ZipPacker.pack(contentDir, output, templateZip = pkg.sourceFile)
        log.info("重打包完成：$count 个条目，耗时 ${System.currentTimeMillis() - start} ms")
    }

    /** 签名块存在但结构损坏也视为已签名（重打包同样会使其丢失） */
    private fun isSigned(sourceFile: File): Boolean = try {
        HapSignatureParser.parse(sourceFile) != null
    } catch (e: HapSignatureFormatException) {
        true
    }

    private fun isJson(text: String): Boolean =
        runCatching { lenientJson.parseToJsonElement(text) }.isSuccess

    private val lenientJson = Json { ignoreUnknownKeys = true }

    private fun isCodeSignaturePath(relativePath: String): Boolean =
        relativePath == CODE_SIGNATURE_DIR || relativePath.startsWith("$CODE_SIGNATURE_DIR/")
}

package com.lesliefang.harmonytoolkit.core.sign

import com.lesliefang.harmonytoolkit.core.tools.locateJarOnClasspath
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * hap/hsp/app 包签名：子进程调用官方 hap-sign-tool.jar 的 sign-app（localSign 模式）。
 *
 * hap-sign-tool 是自带 BC/log4j 的 uber jar，为避免与工程 classpath 依赖冲突走子进程
 * （与 sdk-sign-tool-full.jar 的类加载器隔离方案不同，CLI 工具用子进程最直接，
 * 且退出码/输出可控）。签名材料与 HAR 签名完全同构（p12/密码/别名/cer/p7b）。
 */
object AppSigner {

    private const val JAR_NAME = "hap-sign-tool.jar"
    private const val DEFAULT_COMPATIBLE_VERSION = 9

    /**
     * 对 hap/hsp/app 文件签名并输出新文件。
     * compatibleVersion 即 pack.info 的 apiVersion.compatible（签名格式兼容的最低 API 版本）。
     */
    fun sign(input: File, output: File, materials: SigningMaterials, compatibleVersion: Int?) {
        val jar = locateHapSignTool()
        val command = listOf(
            javaBin(),
            "-jar", jar.absolutePath,
            "sign-app",
            "-mode", "localSign",
            "-keyAlias", materials.keyAlias,
            "-keyPwd", materials.keyPassword,
            "-appCertFile", materials.certPath,
            "-profileFile", materials.profilePath,
            "-inFile", input.absolutePath,
            "-signAlg", materials.signAlg,
            "-keystoreFile", materials.keystorePath,
            "-keystorePwd", materials.storePassword,
            "-outFile", output.absolutePath,
            "-compatibleVersion", (compatibleVersion ?: DEFAULT_COMPATIBLE_VERSION).toString(),
        )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val toolOutput = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(5, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            throw RuntimeException("签名失败: hap-sign-tool 执行超时，已终止")
        }
        if (process.exitValue() != 0 || !output.isFile) {
            throw RuntimeException("签名失败: ${extractErrors(toolOutput, process.exitValue())}")
        }
    }

    /** hap-sign-tool 的错误为「ERROR: <码> <描述>」+ 后续异常堆栈行，整体截给用户 */
    private fun extractErrors(toolOutput: String, exitCode: Int): String {
        val lines = toolOutput.lines()
        val errorIndex = lines.indexOfFirst { "ERROR" in it }
        val relevant = if (errorIndex >= 0) lines.drop(errorIndex).take(20) else lines.takeLast(10)
        return relevant.joinToString("\n").ifBlank { "hap-sign-tool 退出码 $exitCode" }
    }

    private fun javaBin(): String {
        val home = System.getProperty("java.home")
        val bin = if (System.getProperty("os.name").lowercase().contains("windows")) "java.exe" else "java"
        return File(home, "bin/$bin").absolutePath
    }

    /** 定位 hap-sign-tool.jar：系统属性 → 运行 classpath（兼容安装包内哈希后缀名）→ 仓库 tools/lib */
    fun locateHapSignTool(): File {
        System.getProperty("harmonytoolkit.hapSignTool")?.let { p ->
            File(p).takeIf { it.isFile }?.let { return it }
        }
        locateJarOnClasspath("hap-sign-tool")?.let { return it }
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .take(6)
            .map { File(it, "tools/lib/$JAR_NAME") }
            .firstOrNull { it.isFile }
            ?.let { return it }
        throw IllegalStateException("未找到 $JAR_NAME（可在 tools/lib 下提供，或用 -Dharmonytoolkit.hapSignTool 指定路径）")
    }
}

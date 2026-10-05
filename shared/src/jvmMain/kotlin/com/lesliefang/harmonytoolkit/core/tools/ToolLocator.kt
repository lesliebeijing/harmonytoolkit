package com.lesliefang.harmonytoolkit.core.tools

import java.io.File

/**
 * 在运行 classpath 上按 jar 名前缀定位文件；找不到返回 null。
 * 安装包（jpackage）内 jar 名带 gradle 哈希后缀（如 hap-sign-tool-e523a817.jar），
 * 因此精确名与前缀名都要匹配。
 */
fun locateJarOnClasspath(namePrefix: String): File? {
    System.getProperty("java.class.path")?.split(File.pathSeparator)?.forEach { entry ->
        val f = File(entry)
        if (!f.isFile || !f.name.endsWith(".jar")) return@forEach
        if (f.name.equals("$namePrefix.jar", ignoreCase = true) ||
            f.name.startsWith("$namePrefix-")
        ) {
            return f
        }
    }
    return null
}

class ToolNotFoundException(toolName: String, searched: List<File>) :
    RuntimeException(
        "找不到工具 $toolName，搜索过: ${searched.joinToString("，")}\n" +
            "可将工具放入工程 tools/<平台>/ 目录，或用系统属性 -Dharmonytoolkit.tools.dir=<目录> / " +
            "环境变量 HARMONY_TOOLKIT_TOOLS 指定（也可放入 ~/.harmonytoolkit/tools/<平台>/）"
    )

/**
 * 定位外部工具（ark_disasm/hdc/hap-sign-tool.jar 等）。查找优先级（后者是前者的兜底）：
 *   1. 系统属性 harmonytoolkit.tools.dir
 *   2. 环境变量 HARMONY_TOOLKIT_TOOLS
 *   3. ~/.harmonytoolkit/tools/<平台>
 *   4. 运行目录 ./tools/<平台>（gradle run 时即工程内 tools/）
 *   5. 随包分发的工具（jar 内资源，构建时由 copyBundledTools 收入），
 *      首次使用时解出到 ~/.harmonytoolkit/bundled/<平台>/ 并加执行权限
 */
class ToolLocator(
    toolsDirOverride: File? = null,
    /** 随包工具解出目录（测试注入用）；默认 ~/.harmonytoolkit/bundled */
    private val bundledHome: File? = null,
) {

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    private val osDir = if (System.getProperty("os.name").lowercase().contains("mac")) "mac" else "windows"

    private val searchRoots: List<File> = buildList {
        toolsDirOverride?.let { add(it) }
        System.getProperty("harmonytoolkit.tools.dir")?.let { add(File(it)) }
        System.getenv("HARMONY_TOOLKIT_TOOLS")?.let { add(File(it)) }
        add(File(System.getProperty("user.home"), ".harmonytoolkit/tools/$osDir"))
        // IDE / gradle run 的工作目录可能在模块子目录，也向上找一级
        add(File("tools/$osDir"))
        add(File("../tools/$osDir"))
    }

    fun locate(toolName: String): File {
        val searched = ArrayList<File>()
        for (root in searchRoots) {
            for (name in exeCandidates(toolName)) {
                val candidate = if (root.name == osDir || root.name == "mac" || root.name == "windows") {
                    File(root, name)
                } else {
                    File(root, "$osDir/$name")
                }
                searched += candidate
                if (candidate.exists()) return candidate
            }
        }
        extractBundled(toolName)?.let { return it }
        throw ToolNotFoundException(toolName, searched)
    }

    /** Windows 上可执行文件允许不带 .exe 后缀：locate("ark_disasm") 也能匹配 ark_disasm.exe */
    private fun exeCandidates(toolName: String): List<String> =
        if (isWindows) listOf(toolName, "$toolName.exe") else listOf(toolName)

    /** classpath 资源路径为 /<平台>/<文件名>，与 copyBundledTools 的拷贝布局一致 */
    private fun extractBundled(toolName: String): File? {
        val home = bundledHome ?: File(System.getProperty("user.home"), ".harmonytoolkit/bundled")
        for (name in exeCandidates(toolName)) {
            val target = File(home, "$osDir/$name")
            if (target.isFile) return target
            val input = ToolLocator::class.java.getResourceAsStream("/$osDir/$name") ?: continue
            input.use { src ->
                target.parentFile?.mkdirs()
                target.outputStream().use { src.copyTo(it) }
            }
            target.setExecutable(true, true)
            return target
        }
        return null
    }
}

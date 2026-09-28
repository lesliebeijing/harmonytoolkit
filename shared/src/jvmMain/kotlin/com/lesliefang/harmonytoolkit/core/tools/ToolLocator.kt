package com.lesliefang.harmonytoolkit.core.tools

import java.io.File

class ToolNotFoundException(toolName: String, searched: List<File>) :
    RuntimeException(
        "找不到工具 $toolName，搜索过: ${searched.joinToString("，")}\n" +
            "可将工具放入工程 tools/<平台>/ 目录，或用系统属性 -Dharmonytoolkit.tools.dir=<目录> / " +
            "环境变量 HARMONY_TOOLKIT_TOOLS 指定（也可放入 ~/.harmonytoolkit/tools/<平台>/）"
    )

/**
 * 定位随工程分发的 SDK 工具（hdc/restool/ark_disasm/hap-sign-tool.jar 等）。
 * 查找优先级（后者是前者的兜底）：
 *   1. 系统属性 harmonytoolkit.tools.dir
 *   2. 环境变量 HARMONY_TOOLKIT_TOOLS
 *   3. ~/.harmonytoolkit/tools/<平台>
 *   4. 运行目录 ./tools/<平台>（gradle run 时即工程内 tools/）
 */
class ToolLocator(toolsDirOverride: File? = null) {

    private val osDir = when {
        System.getProperty("os.name").lowercase().contains("mac") -> "mac"
        else -> "windows"
    }

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
            val candidate = if (root.name == osDir || root.name == "mac" || root.name == "windows") {
                File(root, toolName)
            } else {
                File(root, "$osDir/$toolName")
            }
            searched += candidate
            if (candidate.exists()) return candidate
        }
        throw ToolNotFoundException(toolName, searched)
    }
}

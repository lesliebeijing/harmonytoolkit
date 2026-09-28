package com.lesliefang.harmonytoolkit.core.tools

import com.lesliefang.harmonytoolkit.core.extract.PackageOpener
import com.lesliefang.harmonytoolkit.core.log.SessionLog
import com.lesliefang.harmonytoolkit.core.model.NodeKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** 用真实 hap 里的 modules.abc 验证 ark_disasm 反汇编链路（需要工程 tools/mac 与样本包） */
class AbcDisassemblerTest {

    @Test
    fun disassembleRealAbc() {
        val dir = File(System.getProperty("testPackages") ?: "/Users/lesliefang/Desktop/测试包")
        assumeTrue("样本目录不存在", dir.isDirectory)
        // jvmTest 工作目录在 shared/ 下，用 ToolLocator 判定（含向上一级查找），与运行时一致
        assumeTrue(
            "需要能定位 ark_disasm",
            runCatching { ToolLocator().locate("ark_disasm").isFile }.getOrDefault(false),
        )

        val pkg = runBlocking {
            PackageOpener(File(System.getProperty("java.io.tmpdir"), "harmonytoolkit-test-cache"))
                .open(File(dir, "entry-default-signed.hap"), SessionLog())
        }
        val abc = walk(pkg.root!!).first { it.kind == NodeKind.FILE && it.name.endsWith(".abc") }

        val result = runBlocking { AbcDisassembler().disassemble(abc.sourceFile!!, SessionLog()) }

        assertTrue("反汇编应成功: ${result.exceptionOrNull()}", result.isSuccess)
        val d = result.getOrThrow()
        assertTrue("输出应非空", d.lines.isNotEmpty())
        assertTrue("应含反汇编段落标记", d.text.contains("LITERALS"))
        assertTrue("耗时应有记录", d.durationMs >= 0)
    }

    private fun walk(node: com.lesliefang.harmonytoolkit.core.model.FileNode): List<com.lesliefang.harmonytoolkit.core.model.FileNode> =
        listOf(node) + node.children.flatMap { walk(it) }
}

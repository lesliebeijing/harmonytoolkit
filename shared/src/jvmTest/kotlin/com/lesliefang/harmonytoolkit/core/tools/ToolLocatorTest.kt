package com.lesliefang.harmonytoolkit.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 随包工具解出兜底：文件系统搜索全部落空时从 classpath 资源解出并可执行 */
class ToolLocatorTest {

    @Test
    fun bundledResourceExtractedWhenFilesystemMisses() {
        // 空覆盖目录跳过搜索路径 1-4（相对路径 tools/、../tools/ 对假工具名也必然落空），
        // 逼出第 5 级兜底：从 classpath 的 /<平台>/fake-disasm[.exe] 解出
        val emptyRoot = Files.createTempDirectory("empty-tools").toFile()
        val home = Files.createTempDirectory("bundled-home").toFile()

        val located = ToolLocator(toolsDirOverride = emptyRoot, bundledHome = home).locate("fake-disasm")

        val osDir = if (System.getProperty("os.name").lowercase().contains("mac")) "mac" else "windows"
        val expectedName = if (osDir == "mac") "fake-disasm" else "fake-disasm.exe"
        assertEquals(File(home, "$osDir/$expectedName"), located)
        assertTrue("解出后应可执行", located.canExecute())

        // 二次定位直接命中已解出文件
        val again = ToolLocator(toolsDirOverride = emptyRoot, bundledHome = home).locate("fake-disasm")
        assertEquals(located, again)
    }
}

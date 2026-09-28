package com.lesliefang.harmonytoolkit.core

import com.lesliefang.harmonytoolkit.core.extract.PackageOpener
import com.lesliefang.harmonytoolkit.core.log.SessionLog
import com.lesliefang.harmonytoolkit.core.model.NodeKind
import com.lesliefang.harmonytoolkit.core.model.PackageKind
import com.lesliefang.harmonytoolkit.core.resource.ResourcesIndexParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 用真实鸿蒙包做主线冒烟：解包 → 树 → 嵌套展开 → 缓存命中 → resources.index 解析。
 * 样本目录可用 -PtestPackages=... 或系统属性覆盖；目录不存在时跳过。
 */
class PackageOpenerSmokeTest {

    private val dir = File(
        System.getProperty("testPackages") ?: "/Users/lesliefang/Desktop/测试包"
    )
    private val opener = PackageOpener(
        File(System.getProperty("java.io.tmpdir"), "harmonytoolkit-test-cache")
    )

    private fun requireSamples() = assumeTrue("样本目录不存在: $dir", dir.isDirectory)

    private fun open(name: String, log: SessionLog = SessionLog()) = runBlocking {
        opener.open(File(dir, name), log)
    }

    @Test
    fun openHap_buildsTreeWithKnownEntries() {
        requireSamples()
        val pkg = open("entry-default-signed.hap")
        assertEquals(PackageKind.HAP, pkg.kind)
        val all = walk(pkg.root!!)
        assertTrue("应包含 module.json", all.any { it.name == "module.json" })
        assertTrue("应包含 resources.index", all.any { it.name == "resources.index" })
        // 百分比分母 = 顶层包文件大小
        assertTrue(pkg.totalSize > 0)
        assertTrue(all.all { it.size <= pkg.totalSize })
        // pack.info 元数据
        val meta = pkg.packInfo
        assertTrue("应解析出 pack.info", meta != null)
        assertTrue("bundleName 非空", !meta!!.bundleName.isNullOrBlank())
        assertTrue("versionName 非空", !meta.versionName.isNullOrBlank())
        assertTrue("versionCode 非空", meta.versionCode != null && meta.versionCode > 0)
    }

    @Test
    fun openApp_expandsNestedPackagesExactlyOneLevel() {
        requireSamples()
        val pkg = open("testplugin-default-signed.app")
        assertEquals(PackageKind.APP, pkg.kind)
        val nested = walk(pkg.root!!).filter { it.kind == NodeKind.NESTED_PACKAGE }
        assertTrue("app 内应展开 hap/hsp 嵌套包", nested.isNotEmpty())
        for (node in nested) {
            assertTrue("嵌套包 ${node.path} 应有内容", node.children.isNotEmpty())
            val deeper = walk(node).filter { it.kind == NodeKind.NESTED_PACKAGE && it !== node }
            assertTrue("嵌套包内部不应再展开（只递归一层）", deeper.isEmpty())
        }
    }

    @Test
    fun openHspReleaseTgz_expandsHarAndHsp() {
        requireSamples()
        val pkg = open("hsp-default.tgz")
        assertEquals(PackageKind.HSP_RELEASE_TGZ, pkg.kind)
        val nested = walk(pkg.root!!).filter { it.kind == NodeKind.NESTED_PACKAGE }
        assertTrue("tgz 内应展开出 har/hsp", nested.isNotEmpty())
    }

    @Test
    fun openHar_buildsTree() {
        requireSamples()
        val pkg = open("har.har")
        assertEquals(PackageKind.HAR, pkg.kind)
        assertTrue(walk(pkg.root!!).isNotEmpty())
    }

    @Test
    fun secondOpen_hitsCache() {
        requireSamples()
        open("entry-default-unsigned.hap")
        val log = SessionLog()
        open("entry-default-unsigned.hap", log)
        val text = log.lines.value.joinToString("\n") { it.message }
        assertTrue("第二次打开应命中缓存", text.contains("命中缓存"))
    }

    @Test
    fun resourcesIndex_insideHap_parses() {
        requireSamples()
        val pkg = open("entry-default-signed.hap")
        val indexNode = walk(pkg.root!!).first { it.name == "resources.index" }
        val index = ResourcesIndexParser.parse(indexNode.sourceFile!!.readBytes())
        assertTrue(index.version.startsWith("RestoolV2"))
        assertTrue("应解析出资源条目", index.resources.isNotEmpty())
        assertTrue("应有 base 配置", index.configs.any { it.qualifier == "base" })
        // dump() 可读输出不抛异常
        assertTrue(index.dump().isNotEmpty())
    }

    @Test
    fun unsupportedFile_throws() {
        val e = runCatching {
            runBlocking { opener.open(File("/tmp/whatever.txt"), SessionLog()) }
        }.exceptionOrNull()
        assertTrue(e is com.lesliefang.harmonytoolkit.core.extract.UnsupportedFileException)
    }

    private fun walk(node: com.lesliefang.harmonytoolkit.core.model.FileNode): List<com.lesliefang.harmonytoolkit.core.model.FileNode> =
        listOf(node) + node.children.flatMap { walk(it) }
}

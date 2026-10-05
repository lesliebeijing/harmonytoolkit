package com.lesliefang.harmonytoolkit.core.repack

import com.lesliefang.harmonytoolkit.core.extract.OpenedPackage
import com.lesliefang.harmonytoolkit.core.extract.Unpacker
import com.lesliefang.harmonytoolkit.core.log.SessionLog
import com.lesliefang.harmonytoolkit.core.model.PackageKind
import com.lesliefang.harmonytoolkit.core.signature.HapSignatureParser
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** HAR 重打包：目录 → tar.gz → 解包，内容逐字节一致；未支持的类型明确报错 */
class RepackagerTest {

    @Test
    fun harRepackRoundTripsContent() {
        val workDir = Files.createTempDirectory("har-repack").toFile()
        val contentDir = File(workDir, "content")
        buildHarContent(contentDir)
        val log = SessionLog()
        val output = File(workDir, "repacked.har")

        Repackager.repack(harPackage(workDir), output, log)

        assertTrue(output.isFile)
        val unpacked = File(workDir, "unpacked")
        Unpacker.untargz(output, unpacked)
        // 目录结构一致
        assertEquals(
            relativePaths(contentDir),
            relativePaths(unpacked),
        )
        // 文件内容逐字节一致
        contentDir.walkTopDown().filter { it.isFile }.forEach { f ->
            assertArrayEquals(f.readBytes(), File(unpacked, f.relativeTo(contentDir).path).readBytes())
        }
        // 过程写入了会话日志
        val messages = log.lines.value.map { it.message }
        assertTrue(messages.any { it.startsWith("开始重打包") })
        assertTrue(messages.any { it.startsWith("重打包完成：") })
    }

    @Test
    fun signedHarRepackStripsCodeSignature() {
        val workDir = Files.createTempDirectory("har-repack-signed").toFile()
        val contentDir = File(workDir, "content")
        buildHarContent(contentDir)
        File(contentDir, ".CodeSignature").mkdirs()
        File(contentDir, ".CodeSignature/codeFileList").writeText("[]")
        File(contentDir, ".CodeSignature/signature").writeBytes(byteArrayOf(9, 9))
        val log = SessionLog()
        val output = File(workDir, "repacked.har")

        Repackager.repack(harPackage(workDir), output, log)

        val unpacked = File(workDir, "unpacked")
        Unpacker.untargz(output, unpacked)
        assertTrue("输出不应包含 .CodeSignature", !File(unpacked, ".CodeSignature").exists())
        assertTrue(relativePaths(unpacked).none { it.startsWith(".CodeSignature") })
        // 其余内容原样保留
        assertTrue(File(unpacked, "package/oh-package.json5").isFile)
        // 剔除动作写了告警日志
        assertTrue(log.lines.value.any { it.message.contains("剔除签名目录") })
        // 解包缓存中的签名目录不被删除（树里仍可查看原签名）
        assertTrue(File(contentDir, ".CodeSignature/signature").isFile)
    }

    @Test
    fun zipRepackMirrorsTemplateLayout() {
        val workDir = Files.createTempDirectory("hap-repack").toFile()
        val source = File(workDir, "src.hap")
        writeHapZip(source)
        val contentDir = File(workDir, "content")
        Unpacker.unzip(source, contentDir)
        val output = File(workDir, "repacked.hap")

        Repackager.repack(hapPackage(workDir, source), output, SessionLog())

        ZipFile(output).use { zip ->
            val names = ArrayList<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) names += entries.nextElement().name
            // 条目顺序与压缩方式逐条目镜像原包（Stored/Deflated 混合）
            assertEquals(listOf("module.json", "pack.info", "ets/modules.abc"), names)
            assertEquals(ZipEntry.STORED, zip.getEntry("module.json").method)
            assertEquals(ZipEntry.DEFLATED, zip.getEntry("pack.info").method)
            assertEquals(ZipEntry.STORED, zip.getEntry("ets/modules.abc").method)
            // 内容逐字节一致
            assertEquals("""{"module":"entry"}""", zip.getInputStream(zip.getEntry("module.json")).readBytes().decodeToString())
            assertArrayEquals(byteArrayOf(1, 2, 3), zip.getInputStream(zip.getEntry("ets/modules.abc")).readBytes())
        }
        // 签名块不在任何条目里，重打包产物天然不含签名
        assertNull(HapSignatureParser.parse(output))
    }

    @Test
    fun zipRepackAddsNewAndSkipsDeletedFiles() {
        val workDir = Files.createTempDirectory("hap-repack-mod").toFile()
        val source = File(workDir, "src.hap")
        writeHapZip(source)
        val contentDir = File(workDir, "content")
        Unpacker.unzip(source, contentDir)
        // 新增一个文件、删除一个原条目
        File(contentDir, "resources/index.txt").apply { parentFile.mkdirs() }.writeText("new file")
        assertTrue(File(contentDir, "pack.info").delete())
        val log = SessionLog()
        val output = File(workDir, "repacked.hap")

        Repackager.repack(hapPackage(workDir, source), output, log)

        ZipFile(output).use { zip ->
            val names = ArrayList<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) names += entries.nextElement().name
            // 模板顺序在前、新增文件按路径排序附后；被删除的条目不再出现
            assertEquals(listOf("module.json", "ets/modules.abc", "resources/index.txt"), names)
            // 新增条目默认压缩
            assertEquals(ZipEntry.DEFLATED, zip.getEntry("resources/index.txt").method)
            assertNull(zip.getEntry("pack.info"))
        }
        // 删除 pack.info 触发告警日志
        assertTrue(log.lines.value.any { it.message.contains("缺少 pack.info") })
    }

    @Test
    fun unsupportedKindThrows() {
        val workDir = Files.createTempDirectory("hap-repack").toFile()
        val pkg = OpenedPackage(
            sourceFile = File(workDir, "src.tgz"),
            kind = PackageKind.HSP_RELEASE_TGZ,
            root = null,
            totalSize = 0,
            workDir = workDir,
        )
        try {
            Repackager.repack(pkg, File(workDir, "out.tgz"), SessionLog())
            fail("TGZ 重打包应抛 UnsupportedRepackException")
        } catch (e: UnsupportedRepackException) {
            assertTrue(e.message!!.contains("TGZ"))
        }
    }

    // ---------- 夹具 ----------

    /** 与官方 har 布局一致的内容树：package 主目录 + 空目录（签名目录见专项用例） */
    private fun buildHarContent(dir: File) {
        File(dir, "package/ets").mkdirs()
        File(dir, "package/oh-package.json5").writeText("""{"name":"com.example.lib"}""")
        File(dir, "package/ets/modules.abc").writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        File(dir, "package/empty.dir").mkdirs()
    }

    private fun harPackage(workDir: File) = OpenedPackage(
        sourceFile = File(workDir, "src.har"),
        kind = PackageKind.HAR,
        root = null,
        totalSize = 0,
        workDir = workDir,
    )

    private fun hapPackage(workDir: File, source: File) = OpenedPackage(
        sourceFile = source,
        kind = PackageKind.HAP,
        root = null,
        totalSize = source.length(),
        workDir = workDir,
    )

    /** 模拟官方 hap：无目录条目、自定义乱序、Stored/Deflated 混合 */
    private fun writeHapZip(file: File) {
        ZipArchiveOutputStream(file.outputStream()).use { zip ->
            put(zip, "module.json", """{"module":"entry"}""".toByteArray(), ZipEntry.STORED)
            put(zip, "pack.info", """{"apiVersion":{"compatible":9}}""".toByteArray(), ZipEntry.DEFLATED)
            put(zip, "ets/modules.abc", byteArrayOf(1, 2, 3), ZipEntry.STORED)
        }
    }

    private fun put(zip: ZipArchiveOutputStream, name: String, bytes: ByteArray, method: Int) {
        val entry = ZipArchiveEntry(name)
        entry.method = method
        if (method == ZipEntry.STORED) {
            entry.size = bytes.size.toLong()
            entry.crc = CRC32().apply { update(bytes) }.value
        }
        zip.putArchiveEntry(entry)
        zip.write(bytes)
        zip.closeArchiveEntry()
    }

    /** 目录树快照：相对路径集合（文件与目录都算，用于结构性比对） */
    private fun relativePaths(root: File): Set<String> =
        root.walkTopDown().filter { it != root }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
}

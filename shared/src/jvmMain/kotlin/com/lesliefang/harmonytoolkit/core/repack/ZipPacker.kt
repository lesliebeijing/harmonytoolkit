package com.lesliefang.harmonytoolkit.core.repack

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 目录 → zip（app/hap/hsp）。官方包不含目录条目（unzip -v 全是文件行），
 * 因此只写文件条目。
 *
 * 以原包 zip 为模板镜像布局：条目顺序按原包中央目录顺序、压缩方式
 * （debug 包全 Stored、release 包压缩）逐条目沿用；原包中没有的新增文件按
 * 路径排序附后并默认压缩；原包中有但目录里已删除的条目跳过。
 * 签名块位于条目数据与中央目录之间、不属于任何条目，重打包天然不保留。
 */
object ZipPacker {

    /** 打包并返回写入的条目数（文件条目） */
    fun pack(contentDir: File, output: File, templateZip: File? = null): Int {
        output.parentFile?.mkdirs()
        val template = templateEntries(templateZip)
        val files: Map<String, File> = contentDir.walkTopDown()
            .filter { it.isFile }
            .associateBy { it.relativeTo(contentDir).invariantSeparatorsPath }
        val ordered = template.keys.filter { it in files } +
            files.keys.filter { it !in template }.sorted()

        ZipArchiveOutputStream(BufferedOutputStream(output.outputStream())).use { zip ->
            for (name in ordered) {
                val f = files.getValue(name)
                val entry = ZipArchiveEntry(name)
                entry.time = f.lastModified()
                if (template[name] == ZipEntry.STORED) {
                    // STORED 条目必须在写入前给全 size/crc
                    entry.method = ZipEntry.STORED
                    entry.size = f.length()
                    entry.crc = crc32Of(f)
                }
                zip.putArchiveEntry(entry)
                f.inputStream().use { it.copyTo(zip) }
                zip.closeArchiveEntry()
            }
            zip.finish()
        }
        return ordered.size
    }

    /** 读原包中央目录：条目名（按出现顺序）→ 压缩方式；目录条目与签名块都不在其中 */
    private fun templateEntries(templateZip: File?): LinkedHashMap<String, Int> {
        val map = LinkedHashMap<String, Int>()
        if (templateZip == null || !templateZip.isFile) return map
        ZipFile(templateZip).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory) map[entry.name] = entry.method
            }
        }
        return map
    }

    private fun crc32Of(file: File): Long {
        val crc = CRC32()
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value
    }
}

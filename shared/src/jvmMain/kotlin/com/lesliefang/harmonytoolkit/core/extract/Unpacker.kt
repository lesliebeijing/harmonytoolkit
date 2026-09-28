package com.lesliefang.harmonytoolkit.core.extract

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/** 底层解包：zip（app/hap/hsp）与 tar.gz（har、hsp 发布 tgz），带路径逃逸防护 */
internal object Unpacker {

    class UnsafeEntryException(entryName: String) :
        RuntimeException("压缩包内条目路径不安全，已拒绝解出: $entryName")

    /** 解 zip，返回解出的文件数 */
    fun unzip(zipFile: File, targetDir: File, onEntry: (String) -> Unit = {}): Int {
        var count = 0
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val out = safeTarget(targetDir, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                // zip 里目录条目缺失时补建 mtime 无意义，保留文件时间便于排查
                out.setLastModified(entry.time)
                onEntry(entry.name)
                count++
            }
        }
        return count
    }

    /** 解 tar.gz，返回解出的文件数 */
    fun untargz(tgzFile: File, targetDir: File, onEntry: (String) -> Unit = {}): Int {
        var count = 0
        TarArchiveInputStream(GZIPInputStream(tgzFile.inputStream().buffered(1 shl 16))).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val out = safeTarget(targetDir, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                FileOutputStream(out).use { output -> tar.copyTo(output) }
                out.setLastModified(entry.modTime.time)
                onEntry(entry.name.removePrefix("./"))
                count++
            }
        }
        return count
    }

    /** 防路径逃逸（zip slip）：条目名解析后必须落在目标目录内 */
    private fun safeTarget(targetDir: File, entryName: String): File {
        val root = targetDir.canonicalFile
        val out = File(root, entryName.removePrefix("/")).canonicalFile
        if (out != root && !out.path.startsWith(root.path + File.separator)) {
            throw UnsafeEntryException(entryName)
        }
        return out
    }
}

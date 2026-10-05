package com.lesliefang.harmonytoolkit.core.repack

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedOutputStream
import java.io.File

/** 目录 → tar.gz，条目名为相对路径（package/...），目录单独成条目，与官方 har 布局一致 */
object TarGzPacker {

    /**
     * 打包并返回写入的条目数（文件 + 目录）。
     * [exclude] 收到条目相对路径（如 ".CodeSignature/profile"），返回 true 的条目不写入。
     */
    fun pack(dir: File, output: File, exclude: (relativePath: String) -> Boolean = { false }): Int {
        output.parentFile?.mkdirs()
        var count = 0
        TarArchiveOutputStream(
            GzipCompressorOutputStream(BufferedOutputStream(output.outputStream())),
        ).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
            dir.walkTopDown()
                .filter { it != dir }
                .filter { !exclude(it.relativeTo(dir).invariantSeparatorsPath) }
                .sortedBy { it.relativeTo(dir).invariantSeparatorsPath }
                .forEach { f ->
                    val name = f.relativeTo(dir).invariantSeparatorsPath
                    val entry = TarArchiveEntry(f, name)
                    tar.putArchiveEntry(entry)
                    if (f.isFile) {
                        f.inputStream().use { it.copyTo(tar) }
                    }
                    tar.closeArchiveEntry()
                    count++
                }
            tar.finish()
        }
        return count
    }
}

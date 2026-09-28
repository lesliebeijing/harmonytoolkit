package com.lesliefang.harmonytoolkit.core.extract

import com.lesliefang.harmonytoolkit.core.log.SessionLog
import com.lesliefang.harmonytoolkit.core.model.FileNode
import com.lesliefang.harmonytoolkit.core.model.NodeKind
import com.lesliefang.harmonytoolkit.core.model.PackInfoMeta
import com.lesliefang.harmonytoolkit.core.model.PackInfoParser
import com.lesliefang.harmonytoolkit.core.model.PackageKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class UnsupportedFileException(file: File) :
    RuntimeException("不支持的文件类型: ${file.name}（支持 .app/.hap/.hsp/.har/.tgz/.abc/resources.index）")

/** 一次成功打开的包 */
data class OpenedPackage(
    val sourceFile: File,
    val kind: PackageKind,
    /** 包内容树；单文件模式（abc/resources.index 直接拖入）为 null */
    val root: FileNode?,
    /** 顶层包文件大小，树中大小百分比的分母 */
    val totalSize: Long,
    /** 解包缓存目录（单文件模式为文件所在目录） */
    val workDir: File,
    /** 包根 pack.info 的 app 字段（无则 null） */
    val packInfo: PackInfoMeta? = null,
)

/**
 * 打开/解包一个包：按「规范化路径 + 大小 + 修改时间」哈希缓存解包结果，
 * 命中缓存时免解包直接建树；app 与 hsp 发布 tgz 内第一层的 hap/hsp/har 递归展开一层。
 */
class PackageOpener(private val cacheRoot: File) {

    suspend fun open(file: File, log: SessionLog): OpenedPackage = withContext(Dispatchers.IO) {
        val canonical = file.canonicalFile
        val kind = PackageKind.fromFileName(canonical.name)
            ?: throw UnsupportedFileException(canonical)

        if (kind.isSingleFile) {
            log.info("打开单文件 ${canonical.name} (${kind.description})")
            return@withContext OpenedPackage(canonical, kind, null, canonical.length(), canonical.parentFile)
        }

        val key = cacheKey(canonical)
        val workDir = File(cacheRoot, key)
        val contentDir = File(workDir, "content")
        val nestedDir = File(workDir, "nested")
        val doneMarker = File(workDir, ".ok")

        if (doneMarker.exists()) {
            log.info("命中缓存，直接打开 ${canonical.name}（缓存目录 ${workDir.name}）")
        } else {
            val start = System.currentTimeMillis()
            log.info("开始解包 ${canonical.name}（${kind.description}）")
            workDir.deleteRecursively()
            contentDir.mkdirs()
            val count = unpackByKind(canonical, kind, contentDir, log)
            doneMarker.writeText("ok")
            log.info("解出 $count 个条目，耗时 ${System.currentTimeMillis() - start} ms")
        }

        var root = buildTree(contentDir, basePath = "")
        root = root.copy(name = canonical.name, sourceFile = canonical)
        if (kind.hasNestedPackages) {
            root = expandNested(root, nestedDir, expandable = true, log = log)
        }
        val packInfo = root.children
            .firstOrNull { it.name.equals("pack.info", ignoreCase = true) }
            ?.sourceFile
            ?.let { PackInfoParser.parse(it.readText()) }
        if (packInfo != null) {
            log.info("pack.info: ${packInfo.bundleName} v${packInfo.versionName}(${packInfo.versionCode}) [${packInfo.bundleType}]")
        }
        OpenedPackage(canonical, kind, root, canonical.length(), workDir, packInfo)
    }

    /** log 非空时按时间间隔打解包进度，避免大包长时间无反馈看起来像卡住 */
    private fun unpackByKind(
        file: File,
        kind: PackageKind,
        targetDir: File,
        log: SessionLog? = null,
    ): Int {
        val onEntry: (String) -> Unit
        if (log == null) {
            onEntry = {}
        } else {
            var count = 0
            var lastReport = System.currentTimeMillis()
            onEntry = {
                count++
                val now = System.currentTimeMillis()
                if (now - lastReport >= 1000) {
                    lastReport = now
                    log.info("解包中… 已解出 $count 个条目")
                }
            }
        }
        return when {
            kind.isZip -> Unpacker.unzip(file, targetDir, onEntry)
            kind.isTgz -> Unpacker.untargz(file, targetDir, onEntry)
            else -> error("unreachable: $kind")
        }
    }

    /**
     * 把树中 FILE 节点里可展开的嵌套包（hap/hsp/har）替换为容器节点。
     * 只展开一层：容器内部再出现的包不再处理（[expandable] 为 false 传递给子树）。
     */
    private fun expandNested(node: FileNode, nestedDir: File, expandable: Boolean, log: SessionLog): FileNode {
        if (node.children.isEmpty()) return node
        val newChildren = node.children.map { child ->
            val childKind = if (expandable && child.kind == NodeKind.FILE) nestedKindOf(child) else null
            if (childKind != null) {
                val target = File(nestedDir, cacheKey(child.sourceFile!!, child.path))
                if (!File(target, ".ok").exists()) {
                    target.deleteRecursively()
                    File(target, "content").mkdirs()
                    unpackByKind(child.sourceFile, childKind, File(target, "content"))
                    File(target, ".ok").writeText("ok")
                    log.info("展开嵌套包 ${child.path}（${childKind.label}）")
                } else {
                    log.info("嵌套包 ${child.path} 命中缓存")
                }
                val subRoot = buildTree(File(target, "content"), child.path)
                child.copy(
                    kind = NodeKind.NESTED_PACKAGE,
                    nestedKind = childKind,
                    unpackedDir = File(target, "content"),
                    children = subRoot.children,
                )
            } else if (child.kind == NodeKind.DIRECTORY) {
                expandNested(child, nestedDir, expandable, log)
            } else {
                child
            }
        }
        return node.copy(children = newChildren)
    }

    /** 嵌套展开只针对 hap/hsp/har；tgz 里再嵌 tgz 的场景暂无，按普通文件显示 */
    private fun nestedKindOf(node: FileNode): PackageKind? {
        val kind = PackageKind.fromFileName(node.name) ?: return null
        return when (kind) {
            PackageKind.HAP, PackageKind.HSP, PackageKind.HAR -> kind
            else -> null
        }
    }

    /** 递归扫描目录建树；目录大小为子项汇总，目录排前、名称不区分大小写排序 */
    private fun buildTree(dir: File, basePath: String): FileNode {
        val children = dir.listFiles()
            .orEmpty()
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { f ->
                val childPath = if (basePath.isEmpty()) f.name else "$basePath/${f.name}"
                if (f.isDirectory) {
                    buildTree(f, childPath)
                } else {
                    FileNode(
                        name = f.name,
                        path = childPath,
                        kind = NodeKind.FILE,
                        size = f.length(),
                        sourceFile = f,
                    )
                }
            }
        val size = if (children.isEmpty()) 0L else children.sumOf { it.size }
        return FileNode(
            name = dir.name,
            path = basePath,
            kind = NodeKind.DIRECTORY,
            size = size,
            sourceFile = dir,
            children = children,
        )
    }

    private fun cacheKey(file: File, extra: String = ""): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val raw = "${file.canonicalPath}|${file.length()}|${file.lastModified()}|$extra"
        return digest.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }
}

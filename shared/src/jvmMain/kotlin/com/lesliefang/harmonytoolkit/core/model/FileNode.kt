package com.lesliefang.harmonytoolkit.core.model

import java.io.File

enum class NodeKind { FILE, DIRECTORY, NESTED_PACKAGE }

/**
 * 解包后的虚拟文件树节点（不可变）。
 *
 * - [sourceFile]: FILE 节点为磁盘上的实际文件；NESTED_PACKAGE 节点为原始嵌套包文件（app 内的 hap/hsp、tgz 内的 har）
 * - [unpackedDir]: 仅 NESTED_PACKAGE 有，嵌套包解包目录
 * - [path]: 包内相对路径（如 "libs/x.so"），作为树展开状态的 key；根节点为 ""
 */
data class FileNode(
    val name: String,
    val path: String,
    val kind: NodeKind,
    val size: Long,
    val sourceFile: File? = null,
    val unpackedDir: File? = null,
    val nestedKind: PackageKind? = null,
    val children: List<FileNode> = emptyList(),
) {
    val isContainer: Boolean get() = kind == NodeKind.DIRECTORY || kind == NodeKind.NESTED_PACKAGE
}

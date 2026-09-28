package com.lesliefang.harmonytoolkit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lesliefang.harmonytoolkit.core.extract.OpenedPackage
import com.lesliefang.harmonytoolkit.core.extract.PackageOpener
import com.lesliefang.harmonytoolkit.core.log.SessionLog
import com.lesliefang.harmonytoolkit.core.model.FileNode
import com.lesliefang.harmonytoolkit.core.model.NodeKind
import com.lesliefang.harmonytoolkit.core.model.PackageKind
import com.lesliefang.harmonytoolkit.core.model.ViewerKind
import com.lesliefang.harmonytoolkit.core.model.classifyFile
import com.lesliefang.harmonytoolkit.ui.SearchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

sealed interface TabLoadState {
    data object Loading : TabLoadState
    data class Loaded(val pkg: OpenedPackage) : TabLoadState
    data class Failed(val error: Throwable) : TabLoadState
}

/** 双击文件后内容区要显示的东西 */
sealed interface ViewerRequest {
    data class Image(val file: File) : ViewerRequest
    data class Text(val file: File, val prettyJson: Boolean) : ViewerRequest
    data class ResourcesIndex(val file: File) : ViewerRequest
    data class Abc(val file: File) : ViewerRequest
    data class Placeholder(val file: File, val reason: String) : ViewerRequest
}

/** 一个 tab = 一个包会话（树/选中/查看器/搜索/日志相互隔离） */
class TabState(
    val id: String,
    val file: File,
    val kind: PackageKind,
    val log: SessionLog = SessionLog(),
) {
    /** 内容区搜索（Cmd+F 唤起，文本/abc/resources.index 查看器响应） */
    val search = SearchState()

    var loadState by mutableStateOf<TabLoadState>(TabLoadState.Loading)
        private set
    var selectedNode by mutableStateOf<FileNode?>(null)
    var expandedPaths by mutableStateOf<Set<String>>(emptySet())
        private set
    var viewer by mutableStateOf<ViewerRequest?>(null)

    fun toggleExpanded(node: FileNode) {
        expandedPaths = if (node.path in expandedPaths) expandedPaths - node.path
        else expandedPaths + node.path
    }

    /** 单击选中（右侧信息栏），双击进入查看器 */
    fun openViewer(node: FileNode) {
        val file = node.sourceFile ?: return
        viewer = when (classifyFile(node.name).viewer) {
            ViewerKind.IMAGE -> ViewerRequest.Image(file)
            ViewerKind.JSON -> ViewerRequest.Text(file, prettyJson = true)
            ViewerKind.TEXT -> ViewerRequest.Text(file, prettyJson = false)
            ViewerKind.RESOURCES_INDEX -> ViewerRequest.ResourcesIndex(file)
            ViewerKind.ABC -> ViewerRequest.Abc(file)
            ViewerKind.BINARY -> ViewerRequest.Placeholder(file, "二进制文件不提供预览")
            ViewerKind.UNKNOWN -> ViewerRequest.Placeholder(file, "该文件类型暂不支持预览，后续版本确定")
        }
    }

    fun load(opener: PackageOpener, scope: CoroutineScope) {
        scope.launch {
            loadState = try {
                val pkg = opener.open(file, log)
                val root = pkg.root
                if (pkg.kind.isSingleFile) openViewer(
                    FileNode(file.name, file.name, NodeKind.FILE, file.length(), file)
                ) else if (root != null) {
                    // 默认展开根下第一层目录，进来就能看到包内容
                    expandedPaths = root.children.filter { it.isContainer }.map { it.path }.toSet()
                }
                TabLoadState.Loaded(pkg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("打开失败: ${e.message}")
                TabLoadState.Failed(e)
            }
        }
    }
}

class AppState(private val scope: CoroutineScope) {

    val opener = PackageOpener(File(System.getProperty("user.home"), ".harmonytoolkit/work"))

    var tabs by mutableStateOf<List<TabState>>(emptyList())
        private set
    var activeTabId by mutableStateOf<String?>(null)

    val activeTab: TabState? get() = tabs.firstOrNull { it.id == activeTabId }

    fun openFiles(files: List<File>) {
        files.forEach { openFile(it) }
    }

    fun openFile(file: File) {
        if (PackageKind.fromFileName(file.name) == null) return
        val id = file.canonicalFile.absolutePath
        val existing = tabs.firstOrNull { it.id == id }
        if (existing != null) {
            activeTabId = id
            return
        }
        val tab = TabState(id, file.canonicalFile, PackageKind.fromFileName(file.name)!!)
        tabs = tabs + tab
        activeTabId = id
        tab.load(opener, scope)
    }

    fun closeTab(id: String) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        tabs = tabs.filterNot { it.id == id }
        if (activeTabId == id) {
            activeTabId = tabs.getOrNull(idx.coerceAtMost(tabs.size - 1))?.id
        }
    }
}

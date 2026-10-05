package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesliefang.harmonytoolkit.TabState
import com.lesliefang.harmonytoolkit.core.extract.OpenedPackage
import com.lesliefang.harmonytoolkit.core.model.FileNode
import com.lesliefang.harmonytoolkit.core.model.NodeKind

private class FlatRow(val node: FileNode, val depth: Int)

private fun flatten(root: FileNode, expanded: Set<String>): List<FlatRow> {
    val out = ArrayList<FlatRow>(128)
    fun walk(node: FileNode, depth: Int) {
        for (child in node.children) {
            out += FlatRow(child, depth)
            if (child.isContainer && child.path in expanded) walk(child, depth + 1)
        }
    }
    walk(root, 0)
    return out
}

/** 左侧文件树：名称 / 大小 / 占顶层包百分比；目录单击展开，文件单击选中、双击查看 */
@Composable
fun FileTreeView(tab: TabState, pkg: OpenedPackage) {
    val root = pkg.root ?: return
    val rows = remember(root, tab.expandedPaths) { flatten(root, tab.expandedPaths) }
    val totalSize = pkg.totalSize
    val listState = tab.treeListState

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("文件", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("大小", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                    Text("占比", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        items(rows, key = { it.node.path }) { row ->
                            TreeRow(row, tab, totalSize)
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
            VerticalDivider()
        }
    }
}

/** 面板之间的 1dp 分隔线 */
@Composable
fun VerticalDivider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun TreeRow(row: FlatRow, tab: TabState, totalSize: Long) {
    val node = row.node
    val selected = tab.selectedNode?.path == node.path
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            // key 必须带 tab：两个包的树常有相同路径（如同一 har 的签名前后版本），
            // 仅按 node.path 复用手势协程时，点击会继续操作切走前的旧 tab 状态
            .pointerInput(tab, node.path) {
                if (node.isContainer) {
                    // 目录/嵌套包：单击立即选中并展开收起（不设双击回调，避免单击延迟）
                    detectTapGestures(
                        onTap = {
                            tab.selectedNode = node
                            tab.toggleExpanded(node)
                        },
                    )
                } else {
                    // 文件：单击选中，双击进入查看器
                    detectTapGestures(
                        onTap = { tab.selectedNode = node },
                        onDoubleTap = {
                            tab.selectedNode = node
                            tab.openViewer(node)
                        },
                    )
                }
            }
            .padding(start = (4 + row.depth * 14).dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.isContainer) {
            ChevronIcon(expanded = node.path in tab.expandedPaths)
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            if (node.isContainer) FolderIcon() else FileIcon()
        }
        Text(
            node.name,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (node.isContainer) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(
            formatSize(node.size),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(64.dp),
        )
        Text(
            formatPercent(node.size, totalSize),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}

// ---- 树图标（Canvas 描边绘制，VS Code 风格：细线 chevron + 文件夹/文件轮廓）----

private val TreeIconColor = Color(0xFF5A5A5A)

/** 收起朝右 >，展开朝下 v，细线人字形 */
@Composable
private fun ChevronIcon(expanded: Boolean) {
    Canvas(Modifier.width(12.dp).height(14.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        if (expanded) {
            path.moveTo(2.dp.toPx(), 5.dp.toPx())
            path.lineTo(6.dp.toPx(), 9.dp.toPx())
            path.lineTo(10.dp.toPx(), 5.dp.toPx())
        } else {
            path.moveTo(4.5f.dp.toPx(), 2.5f.dp.toPx())
            path.lineTo(8.5f.dp.toPx(), 7.dp.toPx())
            path.lineTo(4.5f.dp.toPx(), 11.5f.dp.toPx())
        }
        drawPath(path, TreeIconColor, style = stroke)
    }
}

/** 描边文件夹：左侧耳朵 + 主体矩形 */
@Composable
private fun FolderIcon() {
    Canvas(Modifier.size(16.dp)) {
        val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(1.5f.dp.toPx(), 12.5f.dp.toPx())
            lineTo(1.5f.dp.toPx(), 4f.dp.toPx())
            lineTo(5.5f.dp.toPx(), 4f.dp.toPx())
            lineTo(7.5f.dp.toPx(), 6f.dp.toPx())
            lineTo(14.5f.dp.toPx(), 6f.dp.toPx())
            lineTo(14.5f.dp.toPx(), 12.5f.dp.toPx())
            close()
        }
        drawPath(path, TreeIconColor, style = stroke)
    }
}

/** 描边文件：折角文档轮廓 + 折角线 */
@Composable
private fun FileIcon() {
    Canvas(Modifier.size(16.dp)) {
        val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(4f.dp.toPx(), 1.5f.dp.toPx())
            lineTo(10f.dp.toPx(), 1.5f.dp.toPx())
            lineTo(12.5f.dp.toPx(), 4f.dp.toPx())
            lineTo(12.5f.dp.toPx(), 14.5f.dp.toPx())
            lineTo(4f.dp.toPx(), 14.5f.dp.toPx())
            close()
            moveTo(10f.dp.toPx(), 1.5f.dp.toPx())
            lineTo(10f.dp.toPx(), 4f.dp.toPx())
            lineTo(12.5f.dp.toPx(), 4f.dp.toPx())
        }
        drawPath(path, TreeIconColor, style = stroke)
    }
}

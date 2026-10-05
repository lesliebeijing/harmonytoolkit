package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesliefang.harmonytoolkit.TabLoadState
import com.lesliefang.harmonytoolkit.TabState
import com.lesliefang.harmonytoolkit.ui.viewers.ViewerPane
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.VerticalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState

/** tab 内容：上=三栏（树/内容/信息），下=日志；单文件模式只显示内容区 */
@Composable
fun PackageTabContent(tab: TabState) {
    when (val state = tab.loadState) {
        is TabLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    "正在打开 ${tab.file.name}…",
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is TabLoadState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("打开失败", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                Text(
                    state.error.message ?: state.error.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        is TabLoadState.Loaded -> {
            val pkg = state.pkg
            if (pkg.kind.isSingleFile || pkg.root == null) {
                Column(Modifier.fillMaxSize()) {
                    SingleFileHeader(tab, pkg.sourceFile.name, pkg.kind.description)
                    Box(Modifier.fillMaxSize().padding(top = 1.dp)) {
                        ViewerPane(tab)
                    }
                }
            } else {
                ThreePaneLayout(tab, pkg)
            }
        }
    }
}

@Composable
private fun ThreePaneLayout(tab: TabState, pkg: com.lesliefang.harmonytoolkit.core.extract.OpenedPackage) {
    // 日志默认收起成状态条，展开后回到可拖拽的分栏布局。
    // movableContent 让内容区（含 viewer 的反汇编结果等状态）在两种容器结构间移动而不销毁。
    var logExpanded by remember { mutableStateOf(false) }
    val mainSplit = rememberSplitPaneState(initialPositionPercentage = 0.70f)
    val treeSplit = rememberSplitPaneState(initialPositionPercentage = 0.20f)
    val infoSplit = rememberSplitPaneState(initialPositionPercentage = 0.78f)

    val currentTab by rememberUpdatedState(tab)
    val currentPkg by rememberUpdatedState(pkg)
    val panes = remember {
        movableContentOf { ThreePanes(currentTab, currentPkg, treeSplit, infoSplit) }
    }

    if (logExpanded) {
        VerticalSplitPane(splitPaneState = mainSplit) {
            first(200.dp) {
                panes()
            }
            second(120.dp) {
                LogPanel(Modifier.fillMaxSize(), tab, onCollapse = { logExpanded = false })
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                panes()
            }
            LogStatusBar(tab, onClick = { logExpanded = true })
        }
    }
}

@Composable
private fun ThreePanes(
    tab: TabState,
    pkg: com.lesliefang.harmonytoolkit.core.extract.OpenedPackage,
    treeSplit: org.jetbrains.compose.splitpane.SplitPaneState,
    infoSplit: org.jetbrains.compose.splitpane.SplitPaneState,
) {
    HorizontalSplitPane(splitPaneState = treeSplit) {
        first(180.dp) {
            FileTreeView(tab, pkg)
        }
        second(240.dp) {
            HorizontalSplitPane(splitPaneState = infoSplit) {
                first(280.dp) {
                    ViewerPane(tab)
                }
                second(220.dp) {
                    InfoPanel(pkg, tab.log)
                }
            }
        }
    }
}

/** 单文件模式（直接拖入 abc / resources.index）顶部信息条 */
@Composable
private fun SingleFileHeader(tab: TabState, fileName: String, kindDesc: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tab.kind.label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Text(
            fileName,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            kindDesc,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

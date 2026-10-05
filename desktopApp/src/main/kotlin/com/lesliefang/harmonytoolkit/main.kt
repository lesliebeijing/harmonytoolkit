package com.lesliefang.harmonytoolkit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lesliefang.harmonytoolkit.core.model.PackageKind
import com.lesliefang.harmonytoolkit.ui.AboutDialog
import com.lesliefang.harmonytoolkit.ui.PackageTabContent
import com.lesliefang.harmonytoolkit.ui.PasswordToolDialog
import com.lesliefang.harmonytoolkit.ui.TabBar
import com.lesliefang.harmonytoolkit.ui.WelcomeScreen
import kotlinx.coroutines.delay
import java.io.File

fun main() = application {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "鸿蒙开发助手",
        state = rememberWindowState(size = DpSize(1360.dp, 860.dp)),
        onKeyEvent = { event ->
            // Cmd+F 打开当前 tab 内容区的搜索条
            if (event.type == KeyEventType.KeyDown && event.key == Key.F && event.isMetaPressed) {
                state.activeTab?.search?.visible = true
                true
            } else {
                false
            }
        },
    ) {
        HarmonyToolkitApp(state)
    }
}

@Composable
fun HarmonyToolkitApp(state: AppState) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        var dragOver by remember { mutableStateOf(false) }
        var hint by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(hint) {
            if (hint != null) {
                delay(4000)
                hint = null
            }
        }

        Surface(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .dropFilesTarget(
                        onDragOverChange = { dragOver = it },
                        onDrop = { files ->
                            val (supported, rejected) = files.partition {
                                PackageKind.fromFileName(it.name) != null
                            }
                            if (supported.isNotEmpty()) state.openFiles(supported)
                            if (rejected.isNotEmpty()) {
                                hint = "不支持的文件类型: ${rejected.joinToString { it.name }}"
                            }
                        },
                    )
            ) {
                Column(Modifier.fillMaxSize()) {
                    TabBar(state)
                    when (val tab = state.activeTab) {
                        null -> WelcomeScreen(
                            onOpenClick = { state.openFiles(pickPackageFiles()) },
                        )
                        else -> PackageTabContent(tab)
                    }
                }

                if (dragOver) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(3.dp, MaterialTheme.colorScheme.primary)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    ) {
                        Text(
                            "松开以打开",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                hint?.let { message ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                if (state.showPasswordTool) PasswordToolDialog(onDismiss = { state.showPasswordTool = false })
                if (state.showAbout) AboutDialog(onDismiss = { state.showAbout = false })
            }
        }
    }
}

/** 桌面拖放目标：接收拖入的文件列表 */
@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
private fun Modifier.dropFilesTarget(
    onDragOverChange: (Boolean) -> Unit,
    onDrop: (List<File>) -> Unit,
): Modifier {
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnDragOver by rememberUpdatedState(onDragOverChange)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = currentOnDragOver(true)
            override fun onExited(event: DragAndDropEvent) = currentOnDragOver(false)
            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentOnDragOver(false)
                when (val data = event.dragData()) {
                    is DragData.FilesList -> currentOnDrop(data.readFiles().mapNotNull(::droppedPathToFile))
                    else -> Unit
                }
                return true
            }
        }
    }
    return dragAndDropTarget({ true }, target)
}

/** 系统文件选择器（支持多选） */
private fun pickPackageFiles(): List<File> {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "选择包文件", java.awt.FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.isVisible = true
    return dialog.files?.toList().orEmpty()
}

/**
 * macOS 拖入的文件路径是 "file:/Users/..." 形式的 URI（含中文/空格时可能带百分号编码），
 * 必须经 URI 解析成绝对路径，直接 File(raw) 会被当成相对路径拼到工作目录上。
 */
private fun droppedPathToFile(raw: String): File? = runCatching {
    if (raw.startsWith("file:")) File(java.net.URI(raw)) else File(raw)
}.getOrNull()?.takeIf { it.isFile }

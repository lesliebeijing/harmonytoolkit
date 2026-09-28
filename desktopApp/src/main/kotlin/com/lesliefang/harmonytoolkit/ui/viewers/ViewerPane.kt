package com.lesliefang.harmonytoolkit.ui.viewers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.lesliefang.harmonytoolkit.TabState
import com.lesliefang.harmonytoolkit.ViewerRequest

/** 中间内容区：按双击产生的 ViewerRequest 路由到具体查看器 */
@Composable
fun ViewerPane(tab: TabState) {
    when (val request = tab.viewer) {
        null -> EmptyViewer()
        is ViewerRequest.Image -> ImageViewer(request.file)
        is ViewerRequest.Text -> TextViewer(request.file, request.prettyJson, tab.search)
        is ViewerRequest.ResourcesIndex -> ResourcesIndexViewer(request.file, tab.search)
        is ViewerRequest.Abc -> AbcViewer(request.file, tab.log, tab.search)
        is ViewerRequest.Placeholder -> PlaceholderViewer(request)
    }
}

@Composable
private fun EmptyViewer() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "双击左侧文件查看内容",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PlaceholderViewer(request: ViewerRequest.Placeholder) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                request.file.name,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                request.reason,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

package com.lesliefang.harmonytoolkit.ui.viewers

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 图片查看器：等比缩放完整显示 */
@Composable
fun ImageViewer(file: File) {
    val result by produceState<Result<ImageBitmap>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching { file.inputStream().buffered().use { loadImageBitmap(it) } }
        }
    }

    val loaded = result
    when {
        loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        loaded.isFailure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "图片解码失败: ${file.name}",
                color = MaterialTheme.colorScheme.error,
            )
        }
        else -> Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Image(
                bitmap = loaded.getOrThrow(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

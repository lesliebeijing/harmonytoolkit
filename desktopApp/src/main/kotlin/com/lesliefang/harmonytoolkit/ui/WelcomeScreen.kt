package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** 没有 tab 时的空态：拖入引导 */
@Composable
fun WelcomeScreen(onOpenClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("鸿蒙开发助手", style = MaterialTheme.typography.headlineLarge)
        Text(
            "将包文件拖入窗口任意位置即可打开（可多选）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            ".app  .hap  .hsp  .har  .tgz  .abc  resources.index",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Button(onClick = onOpenClick, modifier = Modifier.padding(top = 24.dp)) {
            Text("选择文件…")
        }
    }
}

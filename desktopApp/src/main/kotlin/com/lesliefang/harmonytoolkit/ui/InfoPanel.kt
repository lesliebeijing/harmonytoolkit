package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesliefang.harmonytoolkit.core.extract.OpenedPackage

/** 右侧栏：包基本信息（含 pack.info 元数据）+ 操作按钮（操作下一阶段接入） */
@Composable
fun InfoPanel(pkg: OpenedPackage) {
    val scrollState = rememberScrollState()
    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    InfoCard("包信息") {
                        InfoRow("文件", pkg.sourceFile.name)
                        InfoRow("类型", "${pkg.kind.label} · ${pkg.kind.description}")
                        InfoRow("大小", formatSize(pkg.totalSize))
                        pkg.packInfo?.let { meta ->
                            meta.bundleName?.let { InfoRow("bundleName", it, mono = true) }
                            meta.bundleType?.let { InfoRow("bundleType", it, mono = true) }
                            meta.versionCode?.let { InfoRow("versionCode", it.toString(), mono = true) }
                            meta.versionName?.let { InfoRow("versionName", it, mono = true) }
                        }
                    }

                    InfoCard("操作") {
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text("重打包（下一阶段）", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text("重签名（下一阶段）", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text("推送到设备（下一阶段）", fontSize = 12.sp)
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            value,
            fontSize = 12.sp,
            fontFamily = if (mono) FontFamily.Monospace else null,
            modifier = Modifier.weight(1f),
        )
    }
}

package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesliefang.harmonytoolkit.AppState
import com.lesliefang.harmonytoolkit.TabLoadState

/** 顶部 tab 栏：一个已打开的包一个 tab，当前 tab 高亮，✕ 关闭；右侧为全局工具入口（固定，不随 tabs 滚动） */
@Composable
fun TabBar(state: AppState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(38.dp)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "鸿蒙开发助手",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tab in state.tabs) {
                val active = tab.id == state.activeTabId
                val loading = tab.loadState is TabLoadState.Loading
                // 选中态：亮背景 + 底部主题色指示条 + 文件名加粗，与灰底未选中 tab 拉开对比
                Box(
                    Modifier
                        .height(38.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { state.activeTabId = tab.id },
                ) {
                    Row(
                        Modifier
                            .height(38.dp)
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (loading) "⏳ " else "") + tab.file.name,
                            fontSize = 13.sp,
                            maxLines = 1,
                            fontWeight = if (active) FontWeight.SemiBold else null,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = if (active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "✕",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { state.closeTab(tab.id) }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                        )
                    }
                    if (active) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .width(1.dp)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        BarAction("签名密码加解密") { state.showPasswordTool = true }
        BarAction("关于") { state.showAbout = true }
    }
}

/** tab 栏右侧的工具入口按钮 */
@Composable
private fun BarAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(38.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

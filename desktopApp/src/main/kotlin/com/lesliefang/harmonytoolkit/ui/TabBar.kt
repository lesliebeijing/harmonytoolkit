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

/** 顶部 tab 栏：一个已打开的包一个 tab，当前 tab 高亮，✕ 关闭 */
@Composable
fun TabBar(state: AppState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState()),
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
        for (tab in state.tabs) {
            val active = tab.id == state.activeTabId
            val loading = tab.loadState is TabLoadState.Loading
            Row(
                Modifier
                    .height(38.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { state.activeTabId = tab.id }
                    .padding(start = 12.dp, end = 4.dp),
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
                    (if (loading) "⏳ " else "") + tab.file.name,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
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
        }
    }
}

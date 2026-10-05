package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesliefang.harmonytoolkit.TabState
import com.lesliefang.harmonytoolkit.core.log.LogLine
import com.lesliefang.harmonytoolkit.core.log.LogLevel

/** 底部日志栏：本 tab 会话的操作日志，实时追加并自动滚到底部 */
@Composable
fun LogPanel(modifier: Modifier = Modifier, tab: TabState, onCollapse: () -> Unit) {
    val lines by tab.log.lines.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    Surface(tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("日志", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(
                    "  ${lines.size} 行",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f))
                Text(
                    "收起",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onCollapse)
                        .padding(horizontal = 6.dp),
                )
                Text(
                    "清空",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = { tab.log.clear() })
                        .padding(horizontal = 6.dp),
                )
            }
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), state = listState) {
                    items(lines) { line ->
                        LogLineRow(line)
                    }
                }
            }
        }
    }
}

/** 收起态的日志状态条：一行显示行数与错误提醒，点击展开 */
@Composable
fun LogStatusBar(tab: TabState, onClick: () -> Unit) {
    val lines by tab.log.lines.collectAsState()
    val errorCount = lines.count { it.level == LogLevel.ERROR }
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("日志", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Text(
            "  ${lines.size} 行",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (errorCount > 0) {
            Text(
                "  ⚠ $errorCount 个错误",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Box(Modifier.weight(1f))
        Text(
            "展开 ▲",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogLineRow(line: LogLine) {
    Row(Modifier.fillMaxWidth().padding(vertical = 0.5.dp)) {
        Text(
            line.timeText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(70.dp),
        )
        Text(
            line.level.name,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = levelColor(line.level),
            modifier = Modifier.width(44.dp),
        )
        Text(
            line.message,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.INFO -> MaterialTheme.colorScheme.tertiary
    LogLevel.WARN -> Color(0xFFB8860B)
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
}

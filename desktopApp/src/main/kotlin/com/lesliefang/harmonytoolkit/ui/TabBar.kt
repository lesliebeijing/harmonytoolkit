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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesliefang.harmonytoolkit.AppState
import com.lesliefang.harmonytoolkit.TabLoadState
import kotlin.math.roundToInt

/** 顶部 tab 栏：一个已打开的包一个 tab，当前 tab 高亮，✕ 关闭；右侧为全局工具入口（固定，不随 tabs 滚动） */
@Composable
fun TabBar(state: AppState) {
    // tabs 区域横向滚动。触控板左右滑由 horizontalScroll 原生支持；鼠标滚轮只有纵向增量，在 pointerInput 里翻译成横向
    val tabScroll = rememberScrollState()
    // 每个 tab 与滚动视口的布局坐标，用于把选中 tab 自动滚进视野
    val tabCoords = remember { mutableStateMapOf<String, LayoutCoordinates>() }
    var viewportCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current

    // 选中 tab 变化（新开/切换/关闭）后若不在视野内则滚动到位。
    // 新 tab 的坐标要等布局完成才写入 map，故用 snapshotFlow 等坐标就绪再触发
    LaunchedEffect(Unit) {
        snapshotFlow { state.activeTabId to tabCoords[state.activeTabId] }
            .collect { (id, coords) ->
                val viewport = viewportCoords
                if (id == null || coords == null || viewport == null) return@collect
                if (!coords.isAttached) {
                    tabCoords.remove(id)
                    return@collect
                }
                val tabRect = coords.boundsInWindow()
                val viewportRect = viewport.boundsInWindow()
                val margin = with(density) { 8.dp.toPx() }
                val target = when {
                    tabRect.left < viewportRect.left + margin ->
                        tabScroll.value - (viewportRect.left + margin - tabRect.left)
                    tabRect.right > viewportRect.right - margin ->
                        tabScroll.value + (tabRect.right - viewportRect.right + margin)
                    else -> return@collect
                }
                tabScroll.animateScrollTo(target.roundToInt().coerceIn(0, tabScroll.maxValue))
            }
    }

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
                .onGloballyPositioned { viewportCoords = it }
                .horizontalScroll(tabScroll)
                .pointerInput(Unit) {
                    val notchPx = 40.dp.toPx()
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue
                            // 只翻译纯纵向滚轮（x 分量为 0）；触控板横向滑动和 Shift+滚轮产生的横向增量
                            // 留给 horizontalScroll 原生处理。桌面端 scrollDelta.y 即滚轮刻度
                            //（AWT 约定向下为正），dispatchRawDelta 正值向右滚
                            if (!change.isConsumed &&
                                change.scrollDelta.x == 0f && change.scrollDelta.y != 0f
                            ) {
                                tabScroll.dispatchRawDelta(change.scrollDelta.y * notchPx)
                                change.consume()
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tab in state.tabs) {
                val active = tab.id == state.activeTabId
                val loading = tab.loadState is TabLoadState.Loading
                // 选中态：亮背景 + 底部主题色指示条 + 文件名加粗，与灰底未选中 tab 拉开对比
                Box(
                    Modifier
                        .height(38.dp)
                        .onGloballyPositioned { tabCoords[tab.id] = it }
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

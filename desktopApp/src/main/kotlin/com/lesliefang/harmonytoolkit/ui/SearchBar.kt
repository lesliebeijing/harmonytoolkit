package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 每 tab 独立的搜索状态（Cmd+F 唤起，Esc 关闭） */
class SearchState {
    var visible by mutableStateOf(false)
    var query by mutableStateOf("")

    /** 当前匹配序号（文本/abc 查看器共用）：存这里跨 viewer 重建保留，切 tab 回来不重置 */
    var currentMatch by mutableStateOf(0)
}

/**
 * 内容区顶部的搜索条：输入框（打开时自动聚焦）+ 结果计数 + 上一个/下一个 + 关闭。
 * Enter 下一个、Shift+Enter 上一个、Esc 关闭；onNext/onPrev 为 null 时是纯过滤模式（如 resources.index）。
 */
@Composable
fun SearchBar(
    state: SearchState,
    resultText: String?,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val focusRequester = FocusRequester()
    LaunchedEffect(state.visible) {
        if (state.visible) focusRequester.requestFocus()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = state.query,
            onValueChange = { state.query = it },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .width(200.dp)
                .height(26.dp)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    when {
                        event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                            state.visible = false
                            true
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.Enter && onNext != null -> {
                            if (event.isShiftPressed) onPrevious?.invoke() else onNext.invoke()
                            true
                        }
                        else -> false
                    }
                },
        )
        if (resultText != null) {
            Text(
                resultText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (onNext != null) {
            ArrowButton("↑ 上一处") { onPrevious?.invoke() }
            ArrowButton("↓ 下一处") { onNext.invoke() }
        }
        ArrowButton("✕") { state.visible = false; state.query = "" }
    }
}

@Composable
private fun ArrowButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

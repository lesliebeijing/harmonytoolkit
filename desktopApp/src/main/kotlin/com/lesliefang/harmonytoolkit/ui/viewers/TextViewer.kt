package com.lesliefang.harmonytoolkit.ui.viewers

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesliefang.harmonytoolkit.ui.SearchBar
import com.lesliefang.harmonytoolkit.ui.SearchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import kotlin.math.roundToInt

private const val MAX_TEXT_BYTES = 8L * 1024 * 1024
private const val SMALL_FILE_MAX_LINES = 5000
private const val SMALL_FILE_MAX_CHARS = 1_000_000
private const val SMALL_FILE_MAX_LINE_LENGTH = 2000

private val MatchHighlight = SpanStyle(background = Color(0xFFFFE082), color = Color(0xFF1A1A1A))
private val CurrentMatchHighlight = SpanStyle(background = Color(0xFFFFB300), color = Color(0xFF1A1A1A))

/** 大文件档的一处匹配（行号 + 行内区间） */
internal data class TextMatch(val line: Int, val start: Int, val end: Int)

internal fun findLineMatches(lines: List<String>, query: String): List<TextMatch> {
    if (query.isEmpty()) return emptyList()
    val q = query.lowercase()
    val out = ArrayList<TextMatch>()
    for ((lineIndex, line) in lines.withIndex()) {
        val lower = line.lowercase()
        var from = 0
        while (true) {
            val at = lower.indexOf(q, from)
            if (at < 0) break
            out += TextMatch(lineIndex, at, at + q.length)
            from = at + q.length.coerceAtLeast(1)
        }
    }
    return out
}

/**
 * 文本/JSON 查看器。超限截断到 8MB。
 * 小文件整块只读 TextField（Cmd+A/Cmd+C）；大文件逐行虚拟滚动。
 * 两档均支持搜索（search.visible 时显示搜索条）。
 */
@Composable
fun TextViewer(file: File, prettyJson: Boolean, search: SearchState? = null) {
    val content by produceState<TextContent?>(null, file) {
        value = withContext(Dispatchers.IO) { loadText(file, prettyJson) }
    }

    val c = content
    when {
        c == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        c.isSmall -> SelectableTextViewer(c, search)
        else -> LargeTextViewer(
            lines = c.lines,
            banner = if (c.truncated) "⚠ 文件超过 ${MAX_TEXT_BYTES / 1024 / 1024} MB，仅显示前一部分" else null,
            search = search,
        )
    }
}

/** 小文件：只读 BasicTextField（可聚焦，内置 Cmd+A 全选 / Cmd+C 复制 / 键盘选择）+ 行号列 */
@Composable
private fun SelectableTextViewer(c: TextContent, search: SearchState?) {
    // 搜索条关闭（Esc）后不再高亮，但保留搜索词，重开时输入框还在
    val query = if (search?.visible == true) search.query else ""
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    var fieldValue by remember(c.text) { mutableStateOf(TextFieldValue(c.text)) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val matches = remember(c.text, query) { findOffsetMatches(c.text, query) }
    var currentMatch by remember(matches) { mutableStateOf(0) }

    // 跳转当前匹配：选中 + 滚动到该处所在行
    LaunchedEffect(currentMatch, matches) {
        val m = matches.getOrNull(currentMatch) ?: return@LaunchedEffect
        fieldValue = fieldValue.copy(selection = TextRange(m.first, m.last + 1))
        layout?.let { l ->
            val line = l.getLineForOffset(m.first)
            vScroll.animateScrollTo(l.getLineTop(line).roundToInt().coerceAtLeast(0))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (search != null && search.visible) {
                SearchBar(
                    state = search,
                    resultText = if (query.isEmpty()) null else "${currentMatch + 1}/${matches.size}",
                    onPrevious = if (matches.isEmpty()) null else ({ currentMatch = (currentMatch - 1 + matches.size) % matches.size }),
                    onNext = if (matches.isEmpty()) null else ({ currentMatch = (currentMatch + 1) % matches.size }),
                )
            }
            Row(Modifier.fillMaxSize()) {
                Text(
                    lineNumbersText(c.lines.size),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .verticalScroll(vScroll)
                        .padding(start = 8.dp, top = 8.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(hScroll)
                        .verticalScroll(vScroll)
                ) {
                    BasicTextField(
                        value = fieldValue,
                        // readOnly 下也要回写状态，否则内部状态不更新
                        onValueChange = { fieldValue = it },
                        readOnly = true,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        visualTransformation = if (query.isEmpty() || matches.isEmpty()) {
                            VisualTransformation.None
                        } else {
                            SearchHighlightTransformation(matches, currentMatch)
                        },
                        onTextLayout = { layout = it },
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
                    )
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(vScroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(hScroll),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 12.dp),
        )
    }
}

/** 大文本虚拟滚动渲染：不折行 + 行内可选择 + 双向滚动条 + 搜索高亮跳转；供文本/abc 反汇编共用 */
@Composable
internal fun LargeTextViewer(lines: List<String>, banner: String? = null, search: SearchState? = null) {
    // 搜索条关闭（Esc）后不再高亮，但保留搜索词，重开时输入框还在
    val query = if (search?.visible == true) search.query else ""
    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()

    val matches = remember(lines, query) { findLineMatches(lines, query) }
    val matchByLine = remember(matches) { matches.groupBy { it.line } }
    var currentMatch by remember(matches) { mutableStateOf(0) }

    LaunchedEffect(currentMatch, matches) {
        if (matches.isNotEmpty()) {
            listState.scrollToItem(matches[currentMatch.coerceIn(matches.indices)].line)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (search != null && search.visible) {
                SearchBar(
                    state = search,
                    resultText = if (query.isEmpty()) null else "${currentMatch + 1}/${matches.size}",
                    onPrevious = if (matches.isEmpty()) null else ({ currentMatch = (currentMatch - 1 + matches.size) % matches.size }),
                    onNext = if (matches.isEmpty()) null else ({ currentMatch = (currentMatch + 1) % matches.size }),
                )
            }
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().horizontalScroll(hScroll), state = listState) {
                    if (banner != null) {
                        item {
                            Text(
                                banner,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    itemsIndexed(lines) { index, line ->
                        val lineMatches = matchByLine[index]
                        Row {
                            Text(
                                "${index + 1}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(52.dp).padding(end = 10.dp),
                            )
                            SelectionContainer {
                                if (lineMatches == null) {
                                    Text(
                                        line.ifEmpty { " " },
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        softWrap = false,
                                    )
                                } else {
                                    val annotated = remember(line, lineMatches, currentMatch, matches) {
                                        buildHighlighted(line, lineMatches, matches.getOrNull(currentMatch))
                                    }
                                    Text(
                                        annotated,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(hScroll),
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 12.dp),
                )
            }
        }
    }
}

/** 小文件档的全文 offset 匹配（IntRange 半开区间） */
private fun findOffsetMatches(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val lower = text.lowercase()
    val q = query.lowercase()
    val out = ArrayList<IntRange>()
    var from = 0
    while (true) {
        val at = lower.indexOf(q, from)
        if (at < 0) break
        out += at until (at + q.length)
        from = at + q.length.coerceAtLeast(1)
    }
    return out
}

private fun buildHighlighted(line: String, lineMatches: List<TextMatch>, current: TextMatch?): AnnotatedString =
    buildAnnotatedString {
        append(line)
        for (m in lineMatches) {
            val isCurrent = current != null && current.line == m.line && current.start == m.start
            addStyle(if (isCurrent) CurrentMatchHighlight else MatchHighlight, m.start, m.end)
        }
    }

/** 小文件档搜索高亮：不改文本，只加背景样式 */
private class SearchHighlightTransformation(
    private val matches: List<IntRange>,
    private val current: Int,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        matches.forEachIndexed { i, range ->
            builder.addStyle(if (i == current) CurrentMatchHighlight else MatchHighlight, range.first, range.last + 1)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private data class TextContent(val text: String, val truncated: Boolean) {
    val lines: List<String> = text.lines()
    private val maxLineLength: Int get() = lines.maxOfOrNull { it.length } ?: 0

    /**
     * 小文件走整块只读 TextField（Cmd+A/Cmd+C 可用）。
     * 含超长行（如 minified sourceMaps.map）的归大文件档：TextField 会自动折行导致行号错位，
     * 虚拟列表不折行且有横向滚动。
     */
    val isSmall: Boolean
        get() = lines.size <= SMALL_FILE_MAX_LINES &&
            text.length <= SMALL_FILE_MAX_CHARS &&
            maxLineLength <= SMALL_FILE_MAX_LINE_LENGTH
}

private fun lineNumbersText(count: Int): String = (1..count).joinToString("\n")

private fun loadText(file: File, prettyJson: Boolean): TextContent {
    val cap = MAX_TEXT_BYTES.toInt()
    val text = file.inputStream().use { input ->
        val buffer = ByteArray(minOf(file.length(), cap.toLong()).toInt().coerceAtLeast(0))
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        val raw = String(buffer, 0, read, Charsets.UTF_8)
        if (prettyJson) prettyPrintJson(raw) ?: raw else raw
    }
    return TextContent(text, truncated = file.length() > cap)
}

private val json = Json { prettyPrint = true }

private fun prettyPrintJson(text: String): String? = runCatching {
    json.encodeToString(JsonElement.serializer(), json.parseToJsonElement(text))
}.getOrNull()

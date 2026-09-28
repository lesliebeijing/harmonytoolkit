package com.lesliefang.harmonytoolkit.ui.viewers

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesliefang.harmonytoolkit.core.resource.ResourcesIndex
import com.lesliefang.harmonytoolkit.core.resource.ResourcesIndexParser
import com.lesliefang.harmonytoolkit.ui.SearchBar
import com.lesliefang.harmonytoolkit.ui.SearchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** resources.index 查看器：顶部统计 + 资源列表（类型/名称/id + 各限定词目录下的值）；搜索为资源名/值过滤 */
@Composable
fun ResourcesIndexViewer(file: File, search: SearchState? = null) {
    val parsed by produceState<Result<ResourcesIndex>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching { ResourcesIndexParser.parse(file.readBytes()) }
        }
    }

    val result = parsed
    when {
        result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        result.isFailure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "resources.index 解析失败",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    result.exceptionOrNull()?.message ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        else -> ResourceList(result.getOrThrow(), search)
    }
}

@Composable
private fun ResourceList(index: ResourcesIndex, search: SearchState?) {
    val listState = rememberLazyListState()
    // 搜索条关闭（Esc）后取消过滤，但保留搜索词，重开时输入框还在
    val query = if (search?.visible == true) search.query else ""
    val filtered = remember(index, query) {
        if (query.isEmpty()) index.resources
        else index.resources.filter { res ->
            res.name.contains(query, ignoreCase = true) ||
                res.values.any { v ->
                    (v.arrayValues?.joinToString(" | ") ?: v.value).contains(query, ignoreCase = true)
                }
        }
    }

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (search != null && search.visible) {
                SearchBar(
                    state = search,
                    resultText = if (query.isEmpty()) null else "${filtered.size} 个结果",
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("resources.index", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(16.dp))
                Text(
                    index.version,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "${index.resources.size} 个资源 · ${index.configs.size} 个限定词目录（${index.configs.joinToString { it.qualifier }}）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp), state = listState) {
                items(filtered.size) { i ->
                    val res = filtered[i]
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                res.resType?.name ?: "type${res.entry.rawResType}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                            Text(
                                remember(res.name, query) { highlightName(res.name, query) },
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "0x${res.resId.toString(16)}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        for (v in res.values) {
                            Text(
                                "${v.config.qualifier}: ${v.arrayValues?.joinToString(" | ") ?: v.value}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 1.dp),
                            )
                        }
                    }
                }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

/** 资源名中匹配搜索词的部分黄色高亮 */
private fun highlightName(name: String, query: String) = buildAnnotatedString {
    append(name)
    if (query.isEmpty()) return@buildAnnotatedString
    val lower = name.lowercase()
    val q = query.lowercase()
    var from = 0
    while (true) {
        val at = lower.indexOf(q, from)
        if (at < 0) break
        addStyle(SpanStyle(background = Color(0xFFFFE082), color = Color(0xFF1A1A1A)), at, at + q.length)
        from = at + q.length
    }
}

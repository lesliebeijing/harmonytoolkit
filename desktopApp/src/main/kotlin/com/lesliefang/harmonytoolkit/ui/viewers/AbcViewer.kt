package com.lesliefang.harmonytoolkit.ui.viewers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesliefang.harmonytoolkit.TabState
import com.lesliefang.harmonytoolkit.core.tools.AbcDisassembler
import com.lesliefang.harmonytoolkit.core.tools.DisasmResult
import java.io.File

/** abc 查看器：调 ark_disasm 反汇编后以大文本虚拟滚动展示，工具输出实时进日志栏。
 *  结果按文件缓存到 tab 会话，viewer 重建或切 tab 回来不重复执行，滚动位置同样保留。 */
@Composable
fun AbcViewer(file: File, tab: TabState) {
    val log = tab.log
    val disassembler = remember { AbcDisassembler() }
    val result by produceState<Result<DisasmResult>?>(null, file) {
        val cached = tab.abcCache[file]
        if (cached != null) {
            value = cached
        } else {
            val r = disassembler.disassemble(file, log)
            tab.abcCache[file] = r
            value = r
        }
    }

    val r = result
    when {
        r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    "正在反汇编 ${file.name}…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        r.isFailure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                r.exceptionOrNull()?.message ?: "反汇编失败",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
            )
        }

        else -> {
            val d = r.getOrThrow()
            LargeTextViewer(
                lines = d.lines,
                banner = "ark_disasm 反汇编 · ${d.lines.size} 行 · 耗时 ${d.durationMs} ms" +
                    if (d.truncated) " · ⚠ 输出过大仅显示前一部分" else "",
                search = tab.search,
                listState = tab.listStateOf("abc:${file.path}"),
                hScroll = tab.scrollStateOf("abc-h:${file.path}"),
            )
        }
    }
}

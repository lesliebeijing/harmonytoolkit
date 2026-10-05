package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lesliefang.harmonytoolkit.core.password.HarmonyPasswordTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser

/** 一行一个密码的批量处理结果 */
private data class RowResult(val input: String, val output: String?, val error: String?)

/**
 * 鸿蒙签名密码加解密弹窗：DevEco 工程里配置的签名密码默认本地加密（00000019...），
 * 密钥存于 material 材料；凭 material 目录把明文加密为配置格式，或把配置密文还原为明文。
 */
@Composable
fun PasswordToolDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()

    // 自动签名场景的材料默认在 ~/.ohos/config，存在则预填
    var materialRoot by remember {
        mutableStateOf(
            File(System.getProperty("user.home"), ".ohos/config")
                .takeIf { File(it, "material").isDirectory }?.absolutePath.orEmpty()
        )
    }
    var input by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RowResult>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    fun runBatch(encrypt: Boolean) {
        val root = expandHome(materialRoot.trim())
        val lines = input.lines().filter { it.isNotBlank() }
        busy = true
        results = null
        scope.launch {
            val rows = withContext(Dispatchers.Default) {
                lines.map { line ->
                    try {
                        val output = if (encrypt) HarmonyPasswordTool.encrypt(line, root)
                        else HarmonyPasswordTool.decrypt(line, root)
                        RowResult(line, output, null)
                    } catch (e: Exception) {
                        RowResult(line, null, e.message ?: e.toString())
                    }
                }
            }
            busy = false
            results = rows
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Card(Modifier.width(560.dp)) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("签名密码加解密", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "DevEco 工程中配置的签名密码默认本地加密（00000019... 形式），密钥存于 material 材料。" +
                        "选择 material 所在目录（material 文件夹的父目录，如签名证书目录或 ~/.ohos/config）即可加解密",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = materialRoot,
                        onValueChange = { materialRoot = it },
                        label = { Text("material 目录", fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    OutlinedButton(onClick = {
                        pickDirectory()?.let { materialRoot = it.absolutePath }
                    }) { Text("选择…", fontSize = 11.sp) }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; results = null },
                    label = { Text("输入（每行一个；加密填明文，解密填 00000019... 密文）", fontSize = 12.sp) },
                    textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { runBatch(encrypt = true) },
                        enabled = materialRoot.isNotBlank() && input.isNotBlank() && !busy,
                    ) { Text(if (busy) "处理中…" else "加密", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { runBatch(encrypt = false) },
                        enabled = materialRoot.isNotBlank() && input.isNotBlank() && !busy,
                    ) { Text("解密", fontSize = 12.sp) }
                }

                results?.let { rows ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "结果（${rows.count { it.output != null }}/${rows.size}）",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = {
                                Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(StringSelection(rows.mapNotNull { it.output }.joinToString("\n")), null)
                                copied = true
                            }) { Text(if (copied) "已复制" else "复制结果", fontSize = 11.sp) }
                        }
                        rows.forEach { row ->
                            Column {
                                Text(
                                    row.input,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                )
                                if (row.output != null) {
                                    Text(
                                        row.output,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                    )
                                } else {
                                    Text(
                                        row.error ?: "失败",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { if (!busy) onDismiss() }, enabled = !busy) {
                        Text("关闭", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** 手输的 ~/... 路径展开为用户主目录 */
private fun expandHome(path: String): String =
    if (path == "~") System.getProperty("user.home")
    else path.removePrefix("~/").let { if (it != path) File(System.getProperty("user.home"), it).absolutePath else path }

/** Swing 目录选择器（AWT FileDialog 无跨平台选目录能力） */
private fun pickDirectory(): File? {
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.isMultiSelectionEnabled = false
    return chooser.takeIf { it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
}

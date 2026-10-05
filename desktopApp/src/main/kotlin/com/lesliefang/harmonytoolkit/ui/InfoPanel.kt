package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesliefang.harmonytoolkit.core.extract.OpenedPackage
import com.lesliefang.harmonytoolkit.core.log.SessionLog
import com.lesliefang.harmonytoolkit.core.model.PackageKind
import com.lesliefang.harmonytoolkit.core.repack.Repackager
import com.lesliefang.harmonytoolkit.core.signature.HapSignature
import com.lesliefang.harmonytoolkit.core.signature.HapSignatureFormatException
import com.lesliefang.harmonytoolkit.core.signature.HapSignatureParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 提供操作按钮的包类型；tgz 与单文件（abc/resources.index）暂不提供 */
private val ACTIONABLE_KINDS = setOf(PackageKind.APP, PackageKind.HAP, PackageKind.HSP, PackageKind.HAR)

/** 右侧栏：包基本信息（含 pack.info 元数据）+ 签名信息 + 操作按钮 */
@Composable
fun InfoPanel(pkg: OpenedPackage, log: SessionLog) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var sigState by remember(pkg.sourceFile) { mutableStateOf<SigUiState>(SigUiState.Idle) }
    var repackState by remember(pkg.sourceFile) { mutableStateOf<RepackUiState>(RepackUiState.Idle) }
    var showSignDialog by remember(pkg.sourceFile) { mutableStateOf(false) }
    var zipSigned by remember(pkg.sourceFile) { mutableStateOf(false) }

    // zip 系包的签名块在条目与中央目录之间、树上看不出，静默检测一次源包是否已签名，供重打包警告显示
    LaunchedEffect(pkg.sourceFile) {
        if (pkg.kind.isZip) {
            zipSigned = withContext(Dispatchers.IO) {
                try {
                    HapSignatureParser.parse(pkg.sourceFile) != null
                } catch (e: HapSignatureFormatException) {
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

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
                        // 单文件（abc/resources.index）不解压，无解压路径可显示
                        if (!pkg.kind.isSingleFile) {
                            val contentDir = File(pkg.workDir, "content")
                            InfoRow(
                                "解压路径",
                                contentDir.absolutePath,
                                mono = true,
                                openAction = { openInFileManager(contentDir) },
                            )
                        }
                        pkg.packInfo?.let { meta ->
                            meta.bundleName?.let { InfoRow("bundleName", it, mono = true, copyable = true) }
                            meta.bundleType?.let { InfoRow("bundleType", it, mono = true) }
                            meta.versionCode?.let { InfoRow("versionCode", it.toString(), mono = true) }
                            meta.versionName?.let { InfoRow("versionName", it, mono = true) }
                        }
                    }

                    if (pkg.kind in ACTIONABLE_KINDS) {
                        when (val state = sigState) {
                            is SigUiState.Signed -> SignatureInfoCard(state.sig)
                            is SigUiState.Parsing -> InfoCard("签名信息") {
                                InfoRow("状态", "解析中…")
                            }
                            is SigUiState.Unsigned -> InfoCard("签名信息") {
                                InfoRow("状态", "未签名")
                            }
                            is SigUiState.Failed -> InfoCard("签名信息") {
                                InfoRow("解析失败", state.message)
                            }
                            SigUiState.Idle -> Unit
                        }

                        InfoCard("操作") {
                            // 重打包：HAR 回打 tar.gz，app/hap/hsp 镜像原包布局回打 zip
                            val repackable = pkg.kind == PackageKind.HAR || pkg.kind.isZip
                            OutlinedButton(
                                onClick = {
                                    val output = pickSaveFile(
                                        "保存重打包的包",
                                        suggestedRepackName(pkg),
                                    ) ?: return@OutlinedButton
                                    repackState = RepackUiState.Running
                                    scope.launch {
                                        repackState = withContext(Dispatchers.IO) {
                                            try {
                                                Repackager.repack(pkg, output, log)
                                                RepackUiState.Done(output)
                                            } catch (e: Exception) {
                                                log.error("重打包失败: ${e.message}")
                                                RepackUiState.Failed(e.message ?: e.toString())
                                            }
                                        }
                                    }
                                },
                                enabled = repackable && repackState !is RepackUiState.Running,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (repackable) "重打包" else "重打包（下一阶段）",
                                    fontSize = 12.sp,
                                )
                            }
                            when {
                                // HAR 的签名在包内目录里，重打包时主动剔除
                                repackable && pkg.kind == PackageKind.HAR && hasEmbeddedSignature(pkg) -> Text(
                                    "⚠ 已签名包：重打包会删除签名信息（.CodeSignature），需重新签名",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                // zip 系的签名块不在任何条目里，重打包天然丢失
                                repackable && pkg.kind.isZip && zipSigned -> Text(
                                    "⚠ 已签名包：重打包后签名将丢失，需重新签名",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            // app 仅重打外层 zip，内层 hap/hsp 字节原样带入（嵌套内容的修改暂不生效）
                            if (repackable && pkg.kind == PackageKind.APP) {
                                Text(
                                    "app 包本阶段是“外层重打”，内层 hap/hsp 原样保留",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // 同一套材料：HAR 走 SDK 目录签名（HarSigner），hap/hsp/app 走 hap-sign-tool（AppSigner）
                            OutlinedButton(
                                onClick = { showSignDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("签名", fontSize = 12.sp) }
                            // 签名信息块仅存在于 hap/hsp/app；har 是代码级 SDK 签名，包内无签名可解析
                            if (pkg.kind != PackageKind.HAR) {
                                OutlinedButton(
                                    onClick = {
                                        sigState = SigUiState.Parsing
                                        scope.launch {
                                            sigState = withContext(Dispatchers.IO) {
                                                try {
                                                    HapSignatureParser.parse(pkg.sourceFile)
                                                        ?.let { SigUiState.Signed(it) }
                                                        ?: SigUiState.Unsigned
                                                } catch (e: Exception) {
                                                    SigUiState.Failed(e.message ?: e.toString())
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("获取签名信息", fontSize = 12.sp)
                                }
                            }
                            when (val rs = repackState) {
                                RepackUiState.Running -> InfoRow("重打包", "进行中…")
                                is RepackUiState.Done -> InfoRow(
                                    "重打包已保存",
                                    rs.file.absolutePath,
                                    mono = true,
                                    openAction = { openInFileManager(rs.file.parentFile ?: rs.file) },
                                )
                                is RepackUiState.Failed -> InfoRow("重打包失败", rs.message)
                                RepackUiState.Idle -> Unit
                            }
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
    if (showSignDialog) {
        SignMaterialsDialog(pkg, onDismiss = { showSignDialog = false })
    }
}

/** “获取签名信息”按钮触发的解析状态 */
private sealed interface SigUiState {
    data object Idle : SigUiState
    data object Parsing : SigUiState
    data object Unsigned : SigUiState
    data class Signed(val sig: HapSignature) : SigUiState
    data class Failed(val message: String) : SigUiState
}

/** “重打包”按钮的执行状态 */
private sealed interface RepackUiState {
    data object Idle : RepackUiState
    data object Running : RepackUiState
    data class Done(val file: File) : RepackUiState
    data class Failed(val message: String) : RepackUiState
}

/** 保存对话框的默认文件名：xxx.har → xxx-repack.har */
private fun suggestedRepackName(pkg: OpenedPackage): String {
    val name = pkg.sourceFile.name
    val dot = name.lastIndexOf('.')
    return if (dot > 0) "${name.substring(0, dot)}-repack.${name.substring(dot + 1)}" else "$name-repack"
}

/** HAR 是否内嵌 SDK 签名目录（顶层 .CodeSignature），决定重打包按钮下是否显示警告 */
private fun hasEmbeddedSignature(pkg: OpenedPackage): Boolean =
    pkg.root?.children?.any { it.name.equals(".CodeSignature", ignoreCase = true) && it.isContainer } == true

@Composable
private fun SignatureInfoCard(sig: HapSignature) {
    InfoCard("签名信息") {
        val pf = sig.profile
        InfoRow("类型", pf?.type ?: "（无内嵌 profile）")
        pf?.bundleName?.let { InfoRow("bundleName", it, mono = true, copyable = true) }
        InfoRow("签名证书", sig.signerCertificate.subject)
        InfoRow("颁发者", sig.signerCertificate.issuer)
        InfoRow(
            "SHA-256 指纹",
            sig.signerCertificate.sha256Fingerprint,
            mono = true,
            copyable = true,
        )
        InfoRow(
            "证书有效期",
            "${formatTime(sig.signerCertificate.notBefore)} ~ ${formatTime(sig.signerCertificate.notAfter)}",
        )
        InfoRow("签名时间", sig.signTime?.let { formatTime(it) } ?: "（未知）")
        pf?.appIdentifier?.let { InfoRow("appIdentifier", it, mono = true, copyable = true) }
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
private fun InfoRow(
    label: String,
    value: String,
    mono: Boolean = false,
    copyable: Boolean = false,
    openAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth()) {
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
        if (copyable) {
            CopyButton(value)
        }
        if (openAction != null) {
            OpenButton(openAction)
        }
    }
}

/** 行尾的复制按钮：写入剪切板后短暂显示“已复制”反馈 */
@Composable
private fun CopyButton(value: String) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var feedbackJob by remember { mutableStateOf<Job?>(null) }
    Text(
        if (copied) "已复制" else "复制",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 4.dp)
            .clickable {
                clipboard.setText(AnnotatedString(value))
                feedbackJob?.cancel()
                feedbackJob = scope.launch {
                    copied = true
                    delay(1500)
                    copied = false
                }
            },
    )
}

/** 行尾的“打开”按钮：在系统文件管理器中打开目录 */
@Composable
private fun OpenButton(onClick: () -> Unit) {
    Text(
        "打开",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 4.dp)
            .clickable(onClick = onClick),
    )
}

private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

private fun formatTime(t: Instant): String = timeFormatter.format(t)

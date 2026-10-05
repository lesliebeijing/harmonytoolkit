package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lesliefang.harmonytoolkit.core.extract.OpenedPackage
import com.lesliefang.harmonytoolkit.core.model.PackageKind
import com.lesliefang.harmonytoolkit.core.sign.AppSigner
import com.lesliefang.harmonytoolkit.core.sign.HarSigner
import com.lesliefang.harmonytoolkit.core.sign.KeyStoreSummary
import com.lesliefang.harmonytoolkit.core.sign.MaterialInspector
import com.lesliefang.harmonytoolkit.core.sign.SigningMaterials
import com.lesliefang.harmonytoolkit.core.sign.SigningProfile
import com.lesliefang.harmonytoolkit.core.sign.SigningProfileStore
import com.lesliefang.harmonytoolkit.core.signature.CertificateInfo
import com.lesliefang.harmonytoolkit.core.signature.ProfileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 签名材料弹窗：GUI 收集 p12/密码/别名/cer/p7b/算法，
 * 当场校验（p12 列别名、cer 显示指纹、p7b 标注 debug/release），
 * 确认后执行签名并持久化非敏感字段到 signing.json。
 */
@Composable
fun SignMaterialsDialog(pkg: OpenedPackage, onDismiss: () -> Unit) {
    val store = remember { SigningProfileStore() }
    val initial = remember { store.lastProfile() }
    val scope = rememberCoroutineScope()

    var keystorePath by remember { mutableStateOf(initial?.keystorePath.orEmpty()) }
    var storePassword by remember { mutableStateOf("") }
    var keystoreSummary by remember { mutableStateOf<KeyStoreSummary?>(null) }
    var keystoreError by remember { mutableStateOf<String?>(null) }
    var keyAlias by remember { mutableStateOf(initial?.keyAlias.orEmpty()) }
    var keyPassword by remember { mutableStateOf("") }
    var certPath by remember { mutableStateOf(initial?.certPath.orEmpty()) }
    var certInfo by remember { mutableStateOf<CertificateInfo?>(null) }
    var certError by remember { mutableStateOf<String?>(null) }
    var profilePath by remember { mutableStateOf(initial?.profilePath.orEmpty()) }
    var profileInfo by remember { mutableStateOf<ProfileInfo?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }

    var busy by remember { mutableStateOf(false) }
    var successFile by remember { mutableStateOf<File?>(null) }
    var failureMessage by remember { mutableStateOf<String?>(null) }

    // 路径就绪即自动校验 cer/p7b（选择或预填后都生效）
    LaunchedEffect(certPath) {
        certInfo = null
        certError = null
        if (certPath.isNotEmpty()) {
            try {
                certInfo = MaterialInspector.loadCertificate(certPath)
            } catch (e: Exception) {
                certError = e.message
            }
        }
    }
    LaunchedEffect(profilePath) {
        profileInfo = null
        profileError = null
        if (profilePath.isNotEmpty()) {
            try {
                profileInfo = MaterialInspector.inspectProfile(profilePath)
            } catch (e: Exception) {
                profileError = e.message
            }
        }
    }

    val ready = keystoreSummary != null && keyAlias.isNotEmpty() && keyPassword.isNotEmpty() &&
        certInfo != null && profileInfo != null

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Card(Modifier.width(560.dp)) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("签名材料", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)

                PathRow(
                    label = "Store file (*.p12)",
                    path = keystorePath,
                    summary = keystoreSummary?.aliases?.firstOrNull()?.certificate
                        ?.let { shortSubject(it) },
                    error = keystoreError,
                    onPick = {
                        pickFile("选择密钥库 (.p12)")?.let {
                            keystorePath = it
                            keystoreSummary = null
                            keystoreError = null
                        }
                    },
                )
                PasswordRow(
                    label = "Store password",
                    value = storePassword,
                    onChange = { storePassword = it },
                    trailing = {
                        OutlinedButton(
                            onClick = {
                                try {
                                    keystoreSummary = MaterialInspector.loadKeyStore(keystorePath, storePassword)
                                    keystoreError = null
                                    val aliases = keystoreSummary!!.aliases.map { it.alias }
                                    if (keyAlias.isEmpty() || keyAlias !in aliases) {
                                        keyAlias = aliases.firstOrNull().orEmpty()
                                    }
                                } catch (e: Exception) {
                                    keystoreSummary = null
                                    keystoreError = e.message
                                }
                            },
                            enabled = keystorePath.isNotEmpty() && storePassword.isNotEmpty(),
                        ) { Text("加载", fontSize = 11.sp) }
                    },
                )

                DropdownRow(
                    label = "Key alias",
                    options = keystoreSummary?.aliases?.map { it.alias } ?: emptyList(),
                    selected = keyAlias,
                    enabled = keystoreSummary != null,
                    onSelect = { keyAlias = it },
                    hint = "先加载密钥库",
                )
                PasswordRow(label = "Key password", value = keyPassword, onChange = { keyPassword = it }, trailing = {})

                PathRow(
                    label = "Certificate (*.cer)",
                    path = certPath,
                    summary = certInfo?.let {
                        "${shortSubject(it)}\nSHA-256: ${it.sha256Fingerprint}"
                    },
                    error = certError,
                    onPick = {
                        pickFile("选择证书 (.cer)")?.let { certPath = it }
                    },
                )
                PathRow(
                    label = "Profile (*.p7b)",
                    path = profilePath,
                    summary = profileInfo?.let {
                        "type=${it.type ?: "?"} · bundle=${it.bundleName ?: "?"}"
                    },
                    error = profileError,
                    onPick = {
                        pickFile("选择 Profile (.p7b)")?.let { profilePath = it }
                    },
                )
                // SDK 签名官方仅支持 SHA256withECDSA，固定展示不可编辑
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Sign alg",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(140.dp),
                    )
                    Text(
                        SigningMaterials.DEFAULT_SIGN_ALG,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when {
                    busy -> Text("签名中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    successFile != null -> Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "已签名并保存到: ",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "打开",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    successFile?.parentFile?.let(::openInFileManager)
                                    onDismiss()
                                },
                            )
                        }
                        Text(
                            successFile!!.absolutePath,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    failureMessage != null -> Text(
                        "签名失败: $failureMessage",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { if (!busy) onDismiss() }, enabled = !busy) {
                        Text(if (successFile != null) "关闭" else "取消", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val output = pickSaveFile(
                                "保存签名后的包",
                                suggestedOutputName(pkg),
                            ) ?: return@Button
                            busy = true
                            // 清掉上次结果，防止旧的成功状态掩盖本次失败
                            successFile = null
                            failureMessage = null
                            scope.launch {
                                val error = withContext(Dispatchers.IO) {
                                    try {
                                        val materials = SigningMaterials(
                                            keystorePath = keystorePath,
                                            storePassword = storePassword,
                                            keyAlias = keyAlias,
                                            keyPassword = keyPassword,
                                            certPath = certPath,
                                            profilePath = profilePath,
                                            // SDK 签名官方仅支持 SHA256withECDSA
                                            signAlg = SigningMaterials.DEFAULT_SIGN_ALG,
                                        )
                                        if (pkg.kind == PackageKind.HAR) {
                                            HarSigner.signAndRepack(File(pkg.workDir, "content"), materials, output)
                                        } else {
                                            AppSigner.sign(
                                                input = pkg.sourceFile,
                                                output = output,
                                                materials = materials,
                                                compatibleVersion = pkg.packInfo?.compatibleApiVersion?.toInt(),
                                            )
                                        }
                                        store.save(
                                            SigningProfile(
                                                keystorePath = keystorePath,
                                                certPath = certPath,
                                                profilePath = profilePath,
                                                keyAlias = keyAlias,
                                                signAlg = SigningMaterials.DEFAULT_SIGN_ALG,
                                            ),
                                        )
                                        null
                                    } catch (e: Exception) {
                                        e.message ?: e.toString()
                                    }
                                }
                                busy = false
                                if (error == null) successFile = output else failureMessage = error
                            }
                        },
                        enabled = ready && !busy,
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("签名", fontSize = 12.sp) }
                }
            }
        }
    }
}

private fun suggestedOutputName(pkg: OpenedPackage): String {
    val name = pkg.sourceFile.name
    val dot = name.lastIndexOf('.')
    return if (dot > 0) "${name.substring(0, dot)}-signed.${name.substring(dot + 1)}" else "$name-signed"
}

private fun shortSubject(info: CertificateInfo): String {
    // RFC2253 顺序为 O=..,CN=..，这里取 CN 段展示
    val cn = info.subject.split(",").firstOrNull { it.trim().startsWith("CN=") }?.trim()
    return cn?.removePrefix("CN=") ?: info.subject
}

@Composable
private fun PathRow(
    label: String,
    path: String,
    summary: String?,
    error: String?,
    onPick: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(140.dp),
            )
            Text(
                if (path.isEmpty()) "（未选择）" else path,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = if (path.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                maxLines = 2,
            )
            OutlinedButton(onClick = onPick) { Text("选择…", fontSize = 11.sp) }
        }
        when {
            error != null -> Text(error, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            summary != null -> Text(
                summary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PasswordRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            modifier = Modifier.weight(1f).padding(end = 6.dp),
        )
        trailing()
    }
}

@Composable
private fun DropdownRow(
    label: String,
    options: List<String>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    hint: String,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Box {
            OutlinedButton(onClick = { if (options.isNotEmpty()) expanded = true }, enabled = enabled) {
                Text(if (selected.isEmpty()) hint.ifEmpty { "（未选择）" } else selected, fontSize = 12.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 12.sp) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

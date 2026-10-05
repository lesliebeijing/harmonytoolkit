package com.lesliefang.harmonytoolkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** 与 desktopApp/build.gradle.kts 的 packageVersion 保持一致 */
private const val APP_VERSION = "1.0.0"

/** 关于弹窗：应用名、版本与功能概览 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.width(360.dp)) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("鸿蒙开发助手", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "HarmonyOS 应用包工具箱",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "版本 $APP_VERSION",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("作者：lesliefang", fontSize = 12.sp)
                    Text("Email: fl_beijing@126.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("头条 @青岛房老师", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("知乎 @青岛房老师", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("微信公众号 @KotlinFang", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("关闭", fontSize = 12.sp) }
                }
            }
        }
    }
}

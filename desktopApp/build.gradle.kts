import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// 发版 CI 用 -PpackageVersion=<去 v 的 tag> 注入；本地开发默认 1.0.0
val appVersion = (project.findProperty("packageVersion") as? String)?.trim().orEmpty().ifEmpty { "1.0.0" }

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.compose.components.splitpane)

    implementation(libs.compose.uiToolingPreview)
}

// 版本号写入资源供 AboutDialog 运行时读取，与安装包版本保持同源；
// 版本作为任务输入，换 -PpackageVersion 重跑时才会重新生成
val generateVersionResource = tasks.register("generateVersionResource") {
    val version = appVersion
    inputs.property("version", version)
    val output = layout.buildDirectory.file("generated/appVersion/app-version.txt")
    outputs.file(output)
    doLast {
        output.get().asFile.apply { parentFile.mkdirs() }.writeText(version)
    }
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/appVersion"))
}

tasks.named("processResources") { dependsOn(generateVersionResource) }

// 签名依赖（bouncycastle 等）的 META-INF/*.SF|*.RSA|*.DSA|*.EC 并入胖 jar 后，
// 签名与新 manifest 不再匹配，java -jar 时 JVM 校验失败直接 SecurityException；
// jar 级签名对本应用无意义（hap 签名走密钥材料，与此无关），打包后剔除，等价 Maven Shade 的默认行为
tasks.matching { it.name == "packageUberJarForCurrentOS" }.configureEach {
    doLast {
        val jarFile = outputs.files.singleFile
        val strippedFile = jarFile.resolveSibling(jarFile.name + ".tmp")
        var stripped = 0
        ZipOutputStream(strippedFile.outputStream().buffered()).use { zos ->
            ZipFile(jarFile).use { zip ->
                for (entry in zip.entries()) {
                    val ext = entry.name.substringAfterLast('.', "").uppercase()
                    if (entry.name.startsWith("META-INF/") && ext in setOf("SF", "RSA", "DSA", "EC")) {
                        stripped++
                        continue
                    }
                    zos.putNextEntry(ZipEntry(entry))
                    zip.getInputStream(entry).copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
        if (!jarFile.delete()) throw GradleException("剔除签名文件失败：无法删除 ${jarFile.name}")
        if (!strippedFile.renameTo(jarFile)) throw GradleException("剔除签名文件失败：无法回写 ${jarFile.name}")
        logger.lifecycle("uber jar 已剔除 $stripped 个签名文件（META-INF/*.SF|*.RSA|*.DSA|*.EC）")
    }
}

compose.desktop {
    application {
        mainClass = "com.lesliefang.harmonytoolkit.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.lesliefang.harmonytoolkit"
            packageVersion = appVersion
        }
    }
}

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

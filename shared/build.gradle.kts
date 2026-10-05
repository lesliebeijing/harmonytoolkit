plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// HAR 签名用的华为 SDK 签名工具（uber jar，运行时由隔离类加载器使用，不参与编译）
val sdkSignToolJar = files(rootProject.projectDir.resolve("tools/lib/sdk-sign-tool-full.jar"))

// hap/hsp/app 签名用的官方 hap-sign-tool（uber jar，运行时由 AppSigner 子进程调用）
val hapSignToolJar = files(rootProject.projectDir.resolve("tools/lib/hap-sign-tool.jar"))

// 随包分发的平台工具：构建时把当前平台的 ark_disasm 收进 jar 资源，
// 运行时 ToolLocator 文件系统找不到就从 classpath 解出（构建机平台与产物平台一致）
val bundledOsDir = if (System.getProperty("os.name").lowercase().contains("mac")) "mac" else "windows"

val copyBundledTools = tasks.register<Copy>("copyBundledTools") {
    into(layout.buildDirectory.dir("generated/bundledTools"))
    from(rootProject.projectDir.resolve("tools/$bundledOsDir")) {
        include("ark_disasm*")
        into(bundledOsDir)
    }
}

// 资源目录从任务输出推导，processResources 自动依赖 copyBundledTools
val bundledToolsOutput = copyBundledTools.map { it.destinationDir }

kotlin {
    jvm()


    sourceSets {
        jvmMain {
            resources.srcDir(bundledToolsOutput)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.commons.compress)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.bouncycastle.bcpkix)
            runtimeOnly(sdkSignToolJar)
            runtimeOnly(hapSignToolJar)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            runtimeOnly(sdkSignToolJar)
            runtimeOnly(hapSignToolJar)
        }
    }
}
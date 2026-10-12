rootProject.name = "harmonytoolkit"

pluginManagement {
    repositories {
        // 例外：阿里云对 org.jetbrains.compose 新版本的 jar 同步经常滞后/不完整（元数据有、jar 404），
        // 而 Gradle 不会跨仓库回源下载 jar（元数据命中哪个仓库，jar 就锁定该仓库），
        // 所以 compose 插件直接走 Maven Central，其余仍国内镜像优先
        mavenCentral {
            mavenContent {
                includeGroupAndSubgroups("org.jetbrains.compose")
            }
        }
        // 国内镜像在前，官方源在后兜底（仅当镜像缺包时才会回源）
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 固定使用此处声明的仓库，忽略全局 init 脚本注入的项目级仓库，保证解析顺序确定
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // 同 pluginManagement：阿里云对 compose 新版本 jar 同步不完整，元数据命中镜像后 jar 404 无法
        // 回源兜底，compose 构件（含 components-splitpane 等）直接走 Maven Central
        mavenCentral {
            mavenContent {
                includeGroupAndSubgroups("org.jetbrains.compose")
            }
        }
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google") {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":desktopApp")
include(":shared")

package com.lesliefang.harmonytoolkit.core.model

/** 顶层可拖入的包/文件类型 */
enum class PackageKind(val label: String, val description: String) {
    APP("APP", "应用发布包 (zip)"),
    HAP("HAP", "Ability 包 (zip)"),
    HSP("HSP", "动态共享包 (zip)"),
    HAR("HAR", "静态共享包 (tar.gz)"),
    HSP_RELEASE_TGZ("TGZ", "HSP 发布包 (tar.gz, 内含 har + hsp)"),
    ABC_FILE("ABC", "ArkTS 字节码文件"),
    RESOURCES_INDEX("INDEX", "resources.index 资源索引文件");

    /** zip 容器格式（app/hap/hsp） */
    val isZip: Boolean get() = this == APP || this == HAP || this == HSP

    /** tar.gz 格式（har 与 hsp 发布 tgz） */
    val isTgz: Boolean get() = this == HAR || this == HSP_RELEASE_TGZ

    /** 单文件模式：无树形列表，直接进入内容查看 */
    val isSingleFile: Boolean get() = this == ABC_FILE || this == RESOURCES_INDEX

    /** 内部第一层的 hap/hsp/har 需要递归展开一层 */
    val hasNestedPackages: Boolean get() = this == APP || this == HSP_RELEASE_TGZ

    companion object {
        fun fromFileName(name: String): PackageKind? {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".app") -> APP
                lower.endsWith(".hap") -> HAP
                lower.endsWith(".hsp") -> HSP
                lower.endsWith(".har") -> HAR
                lower.endsWith(".tgz") || lower.endsWith(".tar.gz") -> HSP_RELEASE_TGZ
                lower.endsWith(".abc") -> ABC_FILE
                lower == "resources.index" || lower.endsWith(".index") -> RESOURCES_INDEX
                else -> null
            }
        }
    }
}

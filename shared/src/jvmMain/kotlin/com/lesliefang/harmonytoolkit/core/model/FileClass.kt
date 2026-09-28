package com.lesliefang.harmonytoolkit.core.model

import java.util.Locale

/** 内容区查看器路由目标 */
enum class ViewerKind { IMAGE, JSON, TEXT, RESOURCES_INDEX, ABC, BINARY, UNKNOWN }

data class FileClass(val viewer: ViewerKind, val typeLabel: String)

/** 按文件名决定双击后的查看方式，新类型在 [classifyFile] 里加一条即可 */
fun classifyFile(name: String): FileClass {
    val lower = name.lowercase(Locale.ROOT)
    return when {
        lower == "resources.index" || lower.endsWith(".index") ->
            FileClass(ViewerKind.RESOURCES_INDEX, "资源索引")
        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".bmp") || lower.endsWith(".webp") ->
            FileClass(ViewerKind.IMAGE, "图片")
        lower.endsWith(".abc") ->
            FileClass(ViewerKind.ABC, "ArkTS 字节码")
        lower.endsWith(".so") || lower.endsWith(".bin") ->
            FileClass(ViewerKind.BINARY, "二进制")
        // 鸿蒙包内不带 .json 后缀的 json 文件（.pages.info 不是 json，不特殊处理）
        lower == "pack.info" || lower.endsWith(".pack.info") ->
            FileClass(ViewerKind.JSON, "JSON")
        lower.endsWith(".json") || lower.endsWith(".map") ->
            FileClass(ViewerKind.JSON, "JSON")
        // json5 不是严格 JSON，按纯文本展示
        lower.endsWith(".json5") ->
            FileClass(ViewerKind.TEXT, "JSON5")
        // 声明文件
        lower.endsWith(".d.ts") || lower.endsWith(".d.ets") ->
            FileClass(ViewerKind.TEXT, "声明文件")
        lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".xml") ||
            lower.endsWith(".html") || lower.endsWith(".js") || lower.endsWith(".ts") ||
            lower.endsWith(".ets") || lower.endsWith(".css") || lower.endsWith(".csv") ||
            lower.endsWith(".properties") ->
            FileClass(ViewerKind.TEXT, "文本")
        else -> FileClass(ViewerKind.UNKNOWN, "未识别")
    }
}

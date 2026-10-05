package com.lesliefang.harmonytoolkit.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** pack.info 中 app 字段的包元信息 */
data class PackInfoMeta(
    val bundleName: String?,
    val bundleType: String?,
    val versionCode: Long?,
    val versionName: String?,
    /** 首个 module 的 apiVersion.compatible，即 hap 签名所需的 compatibleVersion */
    val compatibleApiVersion: Long? = null,
)

/** 解析包根目录下 pack.info 的 "app" 字段；不是合法 JSON 或无该字段时返回 null */
object PackInfoParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): PackInfoMeta? = runCatching {
        val summary = json.parseToJsonElement(text).jsonObject["summary"]?.jsonObject ?: return null
        val app = summary["app"]?.jsonObject ?: return null
        val version = app["version"]?.jsonObject
        val compatible = (summary["modules"] as? JsonArray)
            ?.firstOrNull()
            ?.runCatching { jsonObject["apiVersion"]?.jsonObject?.get("compatible")?.jsonPrimitive?.longOrNull }
            ?.getOrNull()
        PackInfoMeta(
            bundleName = app["bundleName"]?.jsonPrimitive?.content,
            bundleType = app["bundleType"]?.jsonPrimitive?.content,
            versionCode = version?.get("code")?.jsonPrimitive?.longOrNull,
            versionName = version?.get("name")?.jsonPrimitive?.content,
            compatibleApiVersion = compatible,
        )
    }.getOrNull()
}

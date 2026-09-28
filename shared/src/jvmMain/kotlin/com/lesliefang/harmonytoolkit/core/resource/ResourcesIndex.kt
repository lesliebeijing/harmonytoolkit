package com.lesliefang.harmonytoolkit.core.resource


/**
 * 鸿蒙 HAP 包中 resources.index (RestoolV2 格式) 的解析器。
 *
 * DevEco Studio 5.x+ / HarmonyOS NEXT 打包出的 HAP 内 resources.index 均为该格式
 * (文件头 128 字节版本串以 "RestoolV2" 开头)。格式依据 OpenHarmony 开源实现:
 *   - global_resource_management/frameworks/resmgr/src/hap_parser_v2.cpp (读取端, 权威)
 *   - global_resource_management/frameworks/resmgr/include/res_desc.h  (结构体定义)
 *   - developtools_global_resource (restool, 写入端)
 *
 * 文件布局(所有多字节整数均为小端序):
 *
 *   ResIndexHeader (140B)
 *     char     version[128]        例 "RestoolV2 6.1.0.003"
 *     uint32   length              文件总大小
 *     uint32   keyCount            限定词配置(KEYS 块)个数
 *     uint32   dataBlockOffset     DATA 块在文件中的偏移
 *
 *   keyCount × KeyInfo                          —— 每个 = 一个资源限定目录(base/en_US/dark...)
 *     char     tag[4]               "KEYS"
 *     uint32   resConfigId          配置 id (0=base, 1,2,3...)
 *     uint32   keyParamsCount
 *     keyParamsCount × KeyParam (8B)
 *       uint32 type                 ResKeyType
 *       uint32 value
 *
 *   IdsHeader (16B)
 *     char     tag[4]               "IDSS"
 *     uint32   length               IDSS 块大小(不含本头)
 *     uint32   typeCount            资源类型分组数
 *     uint32   idCount              资源条目总数
 *
 *   typeCount × TypeInfo 分组
 *     uint32   type                 ResType
 *     uint32   length               本组大小(含 TypeInfo 头 12B)
 *     uint32   count                本组条目数
 *     count × ResItem
 *       uint32   resId               资源 id, 如 0x01000001
 *       uint32   offset              指向 DATA 区该资源的 ResInfo
 *       uint32   length              资源名长度
 *       byte[]   name                资源名 (UTF-8)
 *
 *   DATA 块 (位于 dataBlockOffset)
 *     DataHander (12B)
 *       char     tag[4]             "DATA"
 *       uint32   length             后续 ResInfo/ConfigItem 表的总大小
 *       uint32   idCount
 *     idCount × { ResInfo (12B) + valueCount × ConfigItem (8B) }
 *       ResInfo:  uint32 resId / uint32 length / uint32 valueCount
 *       ConfigItem: uint32 resCfgId(对应 KeyInfo.resConfigId) / uint32 offset(值的偏移)
 *
 *   值数据 (ConfigItem.offset 指向)
 *     普通类型:  uint16 len + UTF-8 字节 (string/color "#FFFFFF"/float "50fp"/
 *               media 与 profile 为 HAP 内路径, 如 "entry/resources/base/media/x.png")
 *     数组类型(strarray/intarray/plural/theme/pattern):
 *               uint16 totalLen + 重复的 { uint16 len + 内容 + 1 字节分隔符 },
 *               累计读取字节数(含分隔符)须等于 totalLen
 */
object ResourcesIndexParser {

    private const val VERSION_LEN = 128
    private const val HEADER_LEN = 140


    /** 解析 resources.index 字节内容(HAP 中可直接读出的 bytes) */
    fun parse(bytes: ByteArray): ResourcesIndex {
        val buf = Buf(bytes)

        // ---- ResIndexHeader ----
        val versionBytes = buf.bytes(VERSION_LEN)
        val versionEnd = versionBytes.indexOfFirst { it == 0.toByte() }.let { if (it < 0) VERSION_LEN else it }
        val version = String(versionBytes, 0, versionEnd, Charsets.UTF_8)
        require(version.startsWith("RestoolV2")) {
            "不支持的 resources.index 版本: \"$version\" (仅支持 RestoolV2; 旧版 Restool 2.x 为 V1 格式, 结构不同)"
        }
        val fileSize = buf.u32()
        val keyCount = buf.u32().toInt()
        val dataBlockOffset = buf.u32().toInt()
        require(fileSize.toInt() == bytes.size) {
            "文件头 length($fileSize) 与实际文件大小(${bytes.size}) 不一致, 文件可能不完整"
        }

        // ---- KeyInfo × keyCount ----
        val configs = ArrayList<ResConfig>(keyCount)
        repeat(keyCount) {
            require(buf.str(4) == "KEYS") { "KEYS 块 tag 错误 @${buf.pos - 4}" }
            val resConfigId = buf.u32()
            val keyParamsCount = buf.u32().toInt()
            val params = ArrayList<ResKeyParam>(keyParamsCount)
            repeat(keyParamsCount) {
                val type = buf.u32().toInt()
                val value = buf.u32()
                params += ResKeyParam(type, value)
            }
            configs += ResConfig(resConfigId, params)
        }

        // ---- IdsHeader ----
        require(buf.str(4) == "IDSS") { "IDSS 块 tag 错误 @${buf.pos - 4}" }
        @Suppress("UNUSED_VARIABLE") val idsLength = buf.u32() // IDSS 块大小, 解析时不依赖
        val typeCount = buf.u32().toInt()
        val idCount = buf.u32().toInt()

        // ---- TypeInfo 分组 + ResItem ----
        val entries = ArrayList<IdEntry>(idCount)
        repeat(typeCount) {
            val rawType = buf.u32().toInt()
            @Suppress("UNUSED_VARIABLE") val typeLength = buf.u32() // 本组大小, 解析时不依赖
            val count = buf.u32().toInt()
            repeat(count) {
                val resId = buf.u32()
                val dataOffset = buf.u32().toInt()
                val nameLen = buf.u32().toInt()
                entries += IdEntry(ResType.of(rawType), rawType, resId, buf.str(nameLen), dataOffset)
            }
        }

        // ---- DATA 块: ResInfo + ConfigItem, 随后读取各配置下的值 ----
        buf.seek(dataBlockOffset)
        require(buf.str(4) == "DATA") { "DATA 块 tag 错误 @${buf.pos - 4}" }
        @Suppress("UNUSED_VARIABLE") val dataLength = buf.u32()
        val dataIdCount = buf.u32().toInt()

        val configById = configs.associateBy { it.resConfigId }
        val resources = ArrayList<ResourceEntry>(dataIdCount)
        repeat(dataIdCount) {
            val resId = buf.u32()
            @Suppress("UNUSED_VARIABLE") val resInfoLen = buf.u32()
            val valueCount = buf.u32().toInt()
            val found = entries.firstOrNull { it.resId == resId }
                ?: throw IllegalStateException("DATA 区出现未知资源 id 0x${resId.toString(16)}")
            val values = ArrayList<ResValue>(valueCount)
            repeat(valueCount) {
                val resCfgId = buf.u32()
                val valueOffset = buf.u32().toInt()
                val config = configById[resCfgId]
                    ?: throw IllegalStateException("资源 0x${resId.toString(16)} 引用了未知配置 id $resCfgId")
                values += readValue(Buf(bytes), valueOffset, found.rawResType, config)
            }
            resources += ResourceEntry(found, values)
        }

        return ResourcesIndex(version, fileSize, dataBlockOffset, configs, entries, resources)
    }

    /** strarray/intarray/plural/theme/pattern 的值为字符串数组, 其余为单个字符串 */
    private fun isArrayType(rawType: Int): Boolean =
        rawType == ResType.STRINGARRAY.id || rawType == ResType.INTARRAY.id ||
            rawType == ResType.THEME.id || rawType == ResType.PLURALS.id || rawType == ResType.PATTERN.id

    private fun readValue(buf: Buf, offset: Int, rawType: Int, config: ResConfig): ResValue {
        buf.seek(offset)
        return if (isArrayType(rawType)) {
            val totalLen = buf.u16()
            val start = buf.pos
            val list = ArrayList<String>()
            if (totalLen > 0) {
                while (true) {
                    list += readU16String(buf)
                    buf.skip(1) // 条目间分隔符字节
                    val consumed = buf.pos - start
                    if (consumed == totalLen) break
                    require(consumed < totalLen) { "数组资源越界 @${buf.pos}" }
                }
            }
            ResValue(config, offset, list.joinToString(","), list)
        } else {
            ResValue(config, offset, readU16String(buf), null)
        }
    }

    private fun readU16String(buf: Buf): String {
        val len = buf.u16()
        return buf.str(len)
    }

    /** 小端序、带越界检查的读取游标 */
    private class Buf(private val b: ByteArray) {
        var pos: Int = 0

        fun seek(pos: Int) {
            require(pos in 0..b.size) { "非法偏移 $pos" }
            this.pos = pos
        }

        fun skip(n: Int) {
            require(pos + n <= b.size) { "越界读取 @$pos +$n > ${b.size}" }
            pos += n
        }

        fun u16(): Int {
            require(pos + 2 <= b.size) { "越界读取 @$pos" }
            val v = (b[pos].toInt() and 0xFF) or ((b[pos + 1].toInt() and 0xFF) shl 8)
            pos += 2
            return v
        }

        fun u32(): UInt {
            require(pos + 4 <= b.size) { "越界读取 @$pos" }
            var v = 0L
            for (i in 3 downTo 0) v = (v shl 8) or (b[pos + i].toLong() and 0xFF)
            pos += 4
            return v.toUInt()
        }

        fun bytes(n: Int): ByteArray {
            require(pos + n <= b.size) { "越界读取 @$pos" }
            val r = b.copyOfRange(pos, pos + n)
            pos += n
            return r
        }

        fun str(n: Int): String {
            val raw = bytes(n)
            return String(raw, Charsets.UTF_8)
        }
    }
}

/** 限定词类型, 对应 res_common.h 中的 KeyType */
enum class ResKeyType(val id: Int) {
    LANGUAGES(0), REGION(1), SCREEN_DENSITY(2), DIRECTION(3), DEVICETYPE(4),
    SCRIPT(5), COLORMODE(6), MCC(7), MNC(8), INPUTDEVICE(10);

    companion object {
        fun of(id: Int): ResKeyType? = entries.firstOrNull { it.id == id }
    }
}

/** 资源类型, 对应 res_common.h 中的 ResType; name 为 restool 使用的资源目录/显示名 */
enum class ResType(val id: Int, val dirName: String) {
    VALUES(0, "values"), ANIMATION(1, "animation"), DRAWABLE(2, "drawable"),
    LAYOUT(3, "layout"), MENU(4, "menu"), MIPMAP(5, "mipmap"), RAW(6, "rawfile"),
    XML(7, "xml"), INTEGER(8, "integer"), STRING(9, "string"),
    STRINGARRAY(10, "strarray"), INTARRAY(11, "intarray"), BOOLEAN(12, "boolean"),
    DIMEN(13, "dimen"), COLOR(14, "color"), ID(15, "id"), THEME(16, "theme"),
    PLURALS(17, "plural"), FLOAT(18, "float"), MEDIA(19, "media"),
    PROF(20, "profile"), SVG(21, "svg"), PATTERN(22, "pattern"), SYMBOL(23, "symbol");

    companion object {
        fun of(id: Int): ResType? = entries.firstOrNull { it.id == id }
    }
}

/** KeyParam: 一个限定词键值对 */
data class ResKeyParam(val rawType: Int, val value: UInt) {
    val type: ResKeyType? get() = ResKeyType.of(rawType)

    /**
     * 还原为资源目录限定词中的字符串形式:
     * 语言/地区/脚本 = 打包进 uint32 的 ASCII("en"、"US"、"Hans");
     * 颜色模式 dark/light; 屏幕密度 sdpi..xxxldpi; 设备类型; mcc/mnc 数字等。
     */
    fun toQualifierString(): String = when (type) {
        ResKeyType.LANGUAGES, ResKeyType.REGION, ResKeyType.SCRIPT -> {
            // 大端 ASCII 打包进 uint32: 从最高字节起跳过 0
            val v = value.toInt()
            val sb = StringBuilder()
            for (shift in 24 downTo 0 step 8) {
                val c = (v ushr shift) and 0xFF
                if (c != 0) sb.append(c.toChar())
            }
            sb.toString()
        }
        ResKeyType.DIRECTION -> if (value == 0U) "vertical" else "horizontal"
        ResKeyType.COLORMODE -> if (value == 0U) "dark" else "light"
        ResKeyType.SCREEN_DENSITY -> when (value.toInt()) {
            120 -> "sdpi"; 160 -> "mdpi"; 240 -> "ldpi"; 320 -> "xldpi"; 480 -> "xxldpi"; 640 -> "xxxldpi"
            else -> value.toString()
        }
        ResKeyType.DEVICETYPE -> when (value.toInt()) {
            0 -> "phone"; 1 -> "tablet"; 2 -> "car"; 3 -> "pad"; 4 -> "tv"; 6 -> "wearable"; 7 -> "2in1"
            else -> value.toString()
        }
        ResKeyType.INPUTDEVICE -> "pointingdevice"
        ResKeyType.MCC -> "mcc$value"
        ResKeyType.MNC -> "mnc$value"
        null -> value.toString()
    }

    override fun toString(): String = "[type:${type?.name ?: rawType}, value:${toQualifierString()}]"
}

/** 一个限定词配置(KEYS 块), 对应一个资源限定目录 */
data class ResConfig(val resConfigId: UInt, val keyParams: List<ResKeyParam>) {
    /** 还原目录名: 无限定词为 "base", 否则如 "en_US"、"dark"、"zh_CN_dark" */
    val qualifier: String
        get() = if (keyParams.isEmpty()) "base"
        else keyParams.joinToString("_") { it.toQualifierString() }
}

/** IDSS 区的一个资源条目 */
data class IdEntry(
    val resType: ResType?,
    val rawResType: Int,
    val resId: UInt,
    val name: String,
    /** 在 DATA 区中 ResInfo 的文件偏移 */
    val dataOffset: Int,
)

/** 某限定词配置下的一条资源值 */
data class ResValue(
    val config: ResConfig,
    /** 值数据在文件中的偏移 */
    val valueOffset: Int,
    /** 字符串形式的值; 数组类型为逗号拼接 */
    val value: String,
    /** 数组类型(strarray/intarray/plural/theme/pattern)的原始各项, 非数组为 null */
    val arrayValues: List<String>?,
)

/** 一个资源(跨全部限定词配置) */
data class ResourceEntry(val entry: IdEntry, val values: List<ResValue>) {
    val resId: UInt get() = entry.resId
    val name: String get() = entry.name
    val resType: ResType? get() = entry.resType

    /** 指定限定词目录(如 "base"/"dark"/"en_US")下的值 */
    fun valueFor(qualifier: String): String? =
        values.firstOrNull { it.config.qualifier == qualifier }?.value
}

/** 解析结果 */
data class ResourcesIndex(
    val version: String,
    val fileSize: UInt,
    val dataBlockOffset: Int,
    /** 全部限定词配置(KEYS) */
    val configs: List<ResConfig>,
    /** IDSS 区全部条目(按类型分组后的扁平列表) */
    val entries: List<IdEntry>,
    /** 全部资源及其在各配置下的值 */
    val resources: List<ResourceEntry>,
) {
    fun findByName(name: String): ResourceEntry? = resources.firstOrNull { it.name == name }
    fun findById(resId: UInt): ResourceEntry? = resources.firstOrNull { it.resId == resId }

    /** 人类可读的全部条目清单 */
    fun dump(): String = buildString {
        appendLine("resources.index version=$version fileSize=$fileSize")
        appendLine("configs: ${configs.joinToString { "${it.resConfigId}=${it.qualifier}" }}")
        for (res in resources) {
            appendLine(
                "0x${res.resId.toString(16)} [${res.resType?.name ?: "type${res.entry.rawResType}"}] ${res.name}"
            )
            for (v in res.values) {
                val valueDesc = if (v.arrayValues != null) v.arrayValues.joinToString(" | ") else v.value
                appendLine("    ${v.config.qualifier}: $valueDesc")
            }
        }
    }
}

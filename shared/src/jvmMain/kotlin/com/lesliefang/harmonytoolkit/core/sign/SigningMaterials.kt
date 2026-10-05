package com.lesliefang.harmonytoolkit.core.sign

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 一次签名所需的完整材料（含密码，仅存在于内存中，不持久化） */
data class SigningMaterials(
    val keystorePath: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val certPath: String,
    val profilePath: String,
    val signAlg: String = DEFAULT_SIGN_ALG,
) {
    companion object {
        const val DEFAULT_SIGN_ALG = "SHA256withECDSA"
    }
}

/** 持久化的非敏感材料字段（路径/别名/算法），密码每次由用户输入 */
@Serializable
data class SigningProfile(
    val name: String = "默认",
    val keystorePath: String = "",
    val certPath: String = "",
    val profilePath: String = "",
    val keyAlias: String = "",
    val signAlg: String = SigningMaterials.DEFAULT_SIGN_ALG,
) {
    fun toMaterials(storePassword: String, keyPassword: String): SigningMaterials = SigningMaterials(
        keystorePath = keystorePath,
        storePassword = storePassword,
        keyAlias = keyAlias,
        keyPassword = keyPassword,
        certPath = certPath,
        profilePath = profilePath,
        signAlg = signAlg,
    )
}

/**
 * 签名材料持久化：~/.harmonytoolkit/signing.json，
 * 按 name 去重的材料列表 + lastUsed 标记，只存非敏感字段。
 */
class SigningProfileStore(private val configFile: File = defaultConfigFile()) {

    @Serializable
    data class Config(
        val profiles: List<SigningProfile> = emptyList(),
        val lastUsed: String? = null,
    )

    fun load(): Config = try {
        if (configFile.isFile) json.decodeFromString(Config.serializer(), configFile.readText()) else Config()
    } catch (e: Exception) {
        // 配置损坏不致命，当作没有配置
        Config()
    }

    fun lastProfile(): SigningProfile? {
        val config = load()
        return config.profiles.firstOrNull { it.name == config.lastUsed }
            ?: config.profiles.firstOrNull()
    }

    /** 按 name 覆盖保存并标记为 lastUsed */
    fun save(profile: SigningProfile) {
        val config = load()
        val updated = Config(
            profiles = (config.profiles.filterNot { it.name == profile.name } + profile),
            lastUsed = profile.name,
        )
        configFile.parentFile.mkdirs()
        configFile.writeText(json.encodeToString(Config.serializer(), updated))
    }

    companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

        fun defaultConfigFile(): File =
            File(File(System.getProperty("user.home"), ".harmonytoolkit"), "signing.json")
    }
}

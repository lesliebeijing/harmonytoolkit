package com.lesliefang.harmonytoolkit.core.password

import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.security.DrbgParameters
import java.security.GeneralSecurityException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 鸿蒙签名密码加解密（移植自 DevEco 反编译的 passwordtool 源码，已修复其中两处问题：
 * OsTypeUtil.isMac 误判导致 .DS_Store 不被过滤、saveComponents 目录名拼错导致自动生成的
 * 材料无法通过自身校验）。
 *
 * DevEco 对工程里配置的签名密码默认本地加密（00000019... 形式 hex 串），密钥派生自
 * material 材料目录（material 文件夹与 .p12 同级，自动签名场景在 ~/.ohos/config）。
 *
 * 密文格式与 DevEco 完全兼容：[4B 大端长度][12B IV][AES-GCM-128 密文+tag] 转 hex（大写）。
 */
object HarmonyPasswordTool {

    /** materialRoot: material 文件夹的父目录（如签名证书所在目录或 ~/.ohos/config）。材料缺失时自动生成一套 */
    fun encrypt(plainPassword: String, materialRoot: String): String {
        require(plainPassword.isNotEmpty()) { "密码不能为空" }
        return translate("加密") {
            AesGcmKit.encrypt2Hex(MaterialKeyStore.readKey(materialRoot, createIfMissing = true), plainPassword)
        }
    }

    /** materialRoot 同上；hexCipher 为工程配置里的加密密码（00000019... 形式） */
    fun decrypt(hexCipher: String, materialRoot: String): String {
        require(hexCipher.isNotBlank()) { "密文不能为空" }
        return translate("解密") {
            AesGcmKit.decryptHex2Str(MaterialKeyStore.readKey(materialRoot, createIfMissing = false), hexCipher.trim())
        }
    }

    private inline fun <T> translate(op: String, block: () -> T): T = try {
        block()
    } catch (e: CryptoException) {
        // GCM tag 校验失败 = 密码与材料不匹配
        throw IllegalStateException("${op}失败：密码与 material 材料不匹配", e)
    } catch (e: GeneralSecurityException) {
        throw IllegalStateException("${op}失败: ${e.message}", e)
    }
}

private class CryptoException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 头 4 字节大端长度 + 12 字节 IV + AES-GCM-128(密文+16B tag)，与 DevEco 密文布局一致 */
private object AesGcmKit {
    private const val IV_LENGTH = 12
    private const val MIN_ENCRYPTED_LENGTH = 32

    fun encrypt(key: ByteArray, msg: ByteArray): ByteArray = try {
        val iv = ByteArray(IV_LENGTH).also(DRBG::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val content = cipher.doFinal(msg)
        ByteBuffer.allocate(4 + IV_LENGTH + content.size)
            .putInt(content.size)
            .put(iv)
            .put(content)
            .array()
    } catch (e: GeneralSecurityException) {
        throw CryptoException("Encrypt failed", e)
    }

    fun decrypt(key: ByteArray, msg: ByteArray): ByteArray {
        if (msg.size < MIN_ENCRYPTED_LENGTH) throw CryptoException("Illegal data")
        return try {
            val bodyLength = ByteBuffer.wrap(msg, 0, 4).int
            val ivLength = msg.size - 4 - bodyLength
            val iv = msg.copyOfRange(4, 4 + ivLength)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(msg, 4 + ivLength, bodyLength)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Decrypt failed", e)
        }
    }

    fun encrypt2Hex(key: ByteArray, msg: String): String =
        BytesUtil.bytes2Hex(encrypt(key, msg.toByteArray(Charsets.UTF_8)))

    fun decryptHex2Str(key: ByteArray, hex: String): String {
        if (!BytesUtil.isHexBytes(hex)) throw CryptoException("Illegal hex data")
        return String(decrypt(key, BytesUtil.hex2Bytes(hex)), Charsets.UTF_8)
    }
}

private object BytesUtil {
    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()
    private val HEX_PATTERN = Regex("^([0-9A-Fa-f]{2})+$")

    fun bytes2Hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            append(HEX_CHARS[v ushr 4])
            append(HEX_CHARS[v and 0xF])
        }
    }

    fun isHexBytes(hex: String): Boolean = hex.isEmpty() || HEX_PATTERN.matches(hex)

    fun hex2Bytes(hex: String): ByteArray {
        if (hex.isEmpty()) return ByteArray(0)
        require(isHexBytes(hex)) { "Illegal hex string!" }
        return ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) or Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }

    fun xor(a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size) { "size of xor input should be same!" }
        return ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }

    /** 注意是 UTF-8 解码成字符串再取 chars，不是每字节一字符（PBKDF2 口令即由此而来） */
    fun bytes2Chars(bytes: ByteArray): CharArray = String(bytes, Charsets.UTF_8).toCharArray()
}

/** material 材料的密钥存取（原 KeyManager）：fd/<i>/ 文件×3 组件 + ac/ salt + ce/ 加密工作密钥 */
private object MaterialKeyStore {
    private val COMPONENT_DIR_NAMES = arrayOf("fd", "ac", "ce")

    // DevEco 内置的第三分量，参与 XOR 后再 PBKDF2 派生 rootKey，不可更改
    private val BUILT_IN_COMPONENT = byteArrayOf(
        49, -13, 9, 115, -42, -81, 91, -72, -45, -66, -79, 88, 101, -125, -64, 119,
    )

    private const val KEY_LENGTH = 16
    private const val PBKDF2_ITERATIONS = 10000

    fun readKey(materialRoot: String, createIfMissing: Boolean): ByteArray {
        require(materialRoot.isNotEmpty()) { "请先选择 material 材料目录" }
        val rootDir = File(materialRoot, "material")
        val files = rootDir.listFiles()
        if (!rootDir.isDirectory || files == null || files.isEmpty()) {
            // 材料不存在：仅加密场景允许自动生成（与 DevEco readOrCreateKey 一致）
            if (createIfMissing) return generateKey(MaterialFiles.createDirs(rootDir.toPath()).toFile())
            throw IllegalStateException(
                "所选目录下没有 material 材料（应选择 material 文件夹所在的目录，" +
                    "如签名证书目录或 ~/.ohos/config）",
            )
        }
        // 有材料但组件不全（fd/ac/ce）说明材料损坏，不允许覆盖重建
        val names = files.mapTo(mutableSetOf()) { it.name }
        check(COMPONENT_DIR_NAMES.all { it in names }) { "Component in the [material] dir has been damaged!" }
        return readKeyFiles(rootDir)
    }

    private fun generateKey(rootDir: File): ByteArray = try {
        val components = (0 until 3).map { randomBytes() }
        val salt = randomBytes()
        val rootKey = deriveRootKey(components, salt)
        val workKey = randomBytes()
        val workKeyMaterial = AesGcmKit.encrypt(rootKey, workKey)
        components.forEachIndexed { i, c -> writeKeyFile(rootDir, "fd/$i", c) }
        writeKeyFile(rootDir, "ac", salt)
        writeKeyFile(rootDir, "ce", workKeyMaterial)
        workKey
    } catch (e: Exception) {
        throw IllegalStateException("生成 material 失败: ${e.message}", e)
    }

    private fun readKeyFiles(rootDir: File): ByteArray = try {
        val fdDir = File(rootDir, "fd")
        check(fdDir.isDirectory) { "Material not found!" }
        // 无条件过滤 .DS_Store：原版仅 mac 过滤（且 isMac 误判），材料从 mac 拷到 windows 同样会挂
        val componentDirs = fdDir.listFiles()
            ?.filter { it.name != ".DS_Store" }
            ?: throw IllegalStateException("Components state is illegal!")
        check(componentDirs.size == 3) { "Components state is illegal!" }
        val rootKey = deriveRootKey(componentDirs.map(::readSingleFile), readSingleFile(File(rootDir, "ac")))
        AesGcmKit.decrypt(rootKey, readSingleFile(File(rootDir, "ce")))
    } catch (e: CryptoException) {
        throw IllegalStateException("Read Material failed!", e)
    } catch (e: java.io.IOException) {
        throw IllegalStateException("Read Material failed!", e)
    }

    /** 每个组件目录下必须恰好一个文件（.DS_Store 可能在任意一层出现，统一剔除） */
    private fun readSingleFile(dir: File): ByteArray {
        val files = dir.listFiles()?.filter { it.name != ".DS_Store" }
        check(files != null && files.size == 1) { "Sub component state is illegal!" }
        return files[0].readBytes()
    }

    private fun deriveRootKey(components: List<ByteArray>, salt: ByteArray): ByteArray {
        val full = components + BUILT_IN_COMPONENT
        check(full.size >= 2 && full.all { it.size == KEY_LENGTH }) { "Components length is not same!" }
        val password = BytesUtil.bytes2Chars(full.reduce(BytesUtil::xor))
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 128)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun randomBytes(): ByteArray = ByteArray(KEY_LENGTH).also(DRBG::nextBytes)

    /** 组件写到 dir/<uuid>（fd/<i>/<uuid>、ac/<uuid>、ce/<uuid>，与 DevEco 生成的布局一致） */
    private fun writeKeyFile(rootDir: File, dirName: String, bytes: ByteArray) {
        val file = File(rootDir, dirName).resolve(UUID.randomUUID().toString().replace("-", ""))
        MaterialFiles.write(file.toPath(), bytes)
    }
}

/** 材料目录 rwx------、文件 rw-------（Windows 无 POSIX 属性则跳过），并自动补齐父目录 */
private object MaterialFiles {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    fun createDirs(path: Path): Path =
        if (isWindows) Files.createDirectories(path)
        else Files.createDirectories(
            path,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
        )

    fun write(file: Path, bytes: ByteArray) {
        file.parent?.takeIf { Files.notExists(it) }?.let(::createDirs)
        if (isWindows) {
            Files.newOutputStream(file, CREATE, WRITE, TRUNCATE_EXISTING).use { it.write(bytes) }
        } else {
            Files.newByteChannel(
                file,
                setOf(CREATE, WRITE, TRUNCATE_EXISTING),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
            ).use { it.write(ByteBuffer.wrap(bytes)) }
        }
    }
}

/** DRBG（128 位、RESEED_ONLY）安全随机源，与 DevEco 一致 */
private object DRBG {
    val random: SecureRandom = try {
        SecureRandom.getInstance(
            "DRBG",
            DrbgParameters.instantiation(128, DrbgParameters.Capability.RESEED_ONLY, null),
        )
    } catch (e: NoSuchAlgorithmException) {
        throw UnsupportedOperationException(e)
    }

    fun nextBytes(bytes: ByteArray) = random.nextBytes(bytes)
}

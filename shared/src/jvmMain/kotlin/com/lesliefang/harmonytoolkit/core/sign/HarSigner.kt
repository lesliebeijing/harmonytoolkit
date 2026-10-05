package com.lesliefang.harmonytoolkit.core.sign

import com.lesliefang.harmonytoolkit.core.repack.TarGzPacker
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Files

/**
 * HAR（SDK/代码）签名执行器：解包目录 → 隔离类加载调用
 * com.huawei.hmos.security.pki.sdk.sign.SdkSigner#signSdkLocal（入口会先删除已有
 * .CodeSignature）→ 重打包为 tar.gz。
 *
 * sdk-sign-tool-full.jar 是自带 BC/log4j 的 uber jar，为避免与工程 classpath 上的
 * BouncyCastle 版本冲突，用 child-first 类加载器隔离 org.bouncycastle.* 与
 * org.apache.logging.*，其余类走父加载器。
 */
object HarSigner {

    private const val SIGNER_CLASS = "com.huawei.hmos.security.pki.sdk.sign.SdkSigner"
    private const val LOCAL_PARAM_CLASS = "com.huawei.hmos.security.pki.sdk.entity.SignSdkLocalParam"
    private const val IN_FILE_FORM_CLASS = "com.huawei.hmos.security.pki.sdk.entity.InFileForm"
    private const val JAR_NAME = "sdk-sign-tool-full.jar"

    /** 对已解包的 har 内容目录签名并重打包；contentDir 保持只读（复制到临时目录操作） */
    fun signAndRepack(contentDir: File, materials: SigningMaterials, outputHar: File) {
        require(contentDir.isDirectory) { "解包目录不存在: $contentDir" }
        val workDir = Files.createTempDirectory("harmonytoolkit-sign").toFile()
        try {
            contentDir.copyRecursively(workDir, overwrite = true)
            signDir(workDir, materials)
            TarGzPacker.pack(workDir, outputHar)
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** 就地签名目录：成功后目录内出现 .CodeSignature/{codeFileList, profile, signature} */
    fun signDir(dir: File, materials: SigningMaterials) {
        val jar = locateSdkSignTool()
        val loader = IsolatedJarClassLoader(jar, HarSigner::class.java.classLoader)
        val failure: Throwable? = try {
            val paramClass = loader.loadClass(LOCAL_PARAM_CLASS)
            val formClass = loader.loadClass(IN_FILE_FORM_CLASS)
            val param = paramClass.getDeclaredConstructor().newInstance()
            paramClass.getMethod("setInFile", String::class.java).invoke(param, dir.absolutePath)
            paramClass.getMethod("setInFileForm", formClass).invoke(
                param,
                java.lang.Enum.valueOf(formClass.asSubclass(Enum::class.java), "DIR"),
            )
            paramClass.getMethod("setProfileFile", String::class.java).invoke(param, materials.profilePath)
            paramClass.getMethod("setSignCertFile", String::class.java).invoke(param, materials.certPath)
            paramClass.getMethod("setKeyStoreFile", String::class.java).invoke(param, materials.keystorePath)
            paramClass.getMethod("setKeyStorePwd", CharArray::class.java)
                .invoke(param, materials.storePassword.toCharArray())
            paramClass.getMethod("setKeyAlias", String::class.java).invoke(param, materials.keyAlias)
            paramClass.getMethod("setKeyPwd", CharArray::class.java)
                .invoke(param, materials.keyPassword.toCharArray())
            paramClass.getMethod("setSignAlg", String::class.java).invoke(param, materials.signAlg)
            loader.loadClass(SIGNER_CLASS).getDeclaredConstructor().newInstance().let { signer ->
                signer.javaClass.getMethod("signSdkLocal", paramClass).invoke(signer, param)
            }
            null
        } catch (e: InvocationTargetException) {
            e.targetException
        } catch (e: Throwable) {
            e
        }
        failure?.let {
            throw RuntimeException("HAR 签名失败: ${it.message ?: it.javaClass.simpleName}", it)
        }
    }

    /** 定位 sdk-sign-tool-full.jar：系统属性 → 运行 classpath → 仓库 tools/lib */
    fun locateSdkSignTool(): File {
        System.getProperty("harmonytoolkit.sdkSignTool")?.let { p ->
            File(p).takeIf { it.isFile }?.let { return it }
        }
        // Gradle run / test 场景：jar 作为文件依赖出现在 classpath 上
        System.getProperty("java.class.path")?.split(File.pathSeparator)?.forEach { entry ->
            val f = File(entry)
            if (f.isFile && f.name.equals(JAR_NAME, ignoreCase = true)) return f
        }
        // 开发场景：从工作目录向上找仓库的 tools/lib
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .take(6)
            .map { File(it, "tools/lib/$JAR_NAME") }
            .firstOrNull { it.isFile }
            ?.let { return it }
        throw IllegalStateException("未找到 $JAR_NAME（可在 tools/lib 下提供，或用 -Dharmonytoolkit.sdkSignTool 指定路径）")
    }

    /** child-first 隔离 uber jar 内置的 BC/log4j，防止与工程依赖的版本互相覆盖 */
    private class IsolatedJarClassLoader(jar: File, parent: ClassLoader) :
        URLClassLoader(arrayOf(jar.toURI().toURL()), parent) {

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            findLoadedClass(name)?.let { return it }
            if (CHILD_FIRST_PREFIXES.any { name.startsWith(it) }) {
                try {
                    val c = findClass(name)
                    if (resolve) resolveClass(c)
                    return c
                } catch (_: ClassNotFoundException) {
                    // jar 内没有时退回父加载器
                }
            }
            return super.loadClass(name, resolve)
        }

        companion object {
            private val CHILD_FIRST_PREFIXES =
                listOf("org.bouncycastle.", "org.apache.logging.log4j.")
        }
    }
}

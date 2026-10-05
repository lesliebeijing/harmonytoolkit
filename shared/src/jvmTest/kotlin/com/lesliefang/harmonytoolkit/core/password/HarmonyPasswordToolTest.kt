package com.lesliefang.harmonytoolkit.core.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HarmonyPasswordToolTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 本机 DevEco 自动签名生成的真实材料（无 DevEco 环境的机器跳过真实链路测试） */
    private fun requireRealMaterial(): File {
        val root = File(System.getProperty("user.home"), ".ohos/config")
        assumeTrue("本机无 DevEco material，跳过真实链路测试", File(root, "material/fd").isDirectory)
        return root
    }

    @Test
    fun `空目录加密自动生成材料并可解密`() {
        val root = tmp.newFolder("certs")
        val cipher = HarmonyPasswordTool.encrypt("abc123!@#", root.absolutePath)
        assertTrue("密文应为 000000 开头的 hex 串", cipher.startsWith("000000"))
        assertEquals("abc123!@#", HarmonyPasswordTool.decrypt(cipher, root.absolutePath))

        // 生成的材料布局与 DevEco 一致：fd/0,1,2 各一文件 + ac 一文件 + ce 一文件
        val material = File(root, "material")
        listOf("fd/0", "fd/1", "fd/2", "ac", "ce").forEach {
            val files = File(material, it).listFiles { f -> f.name != ".DS_Store" }
            assertEquals("material/$it 应恰好一个文件", 1, files?.size)
        }
    }

    @Test
    fun `同一材料两次加密产生不同密文`() {
        val root = tmp.newFolder("certs").absolutePath
        val a = HarmonyPasswordTool.encrypt("secret", root)
        val b = HarmonyPasswordTool.encrypt("secret", root)
        assertNotEquals(a, b)
        assertEquals("secret", HarmonyPasswordTool.decrypt(b, root))
    }

    @Test
    fun `中文密码 roundtrip`() {
        val root = tmp.newFolder("certs").absolutePath
        val cipher = HarmonyPasswordTool.encrypt("鸿蒙密码123", root)
        assertEquals("鸿蒙密码123", HarmonyPasswordTool.decrypt(cipher, root))
    }

    /** 材料从 mac 拷到 windows 也会带 .DS_Store，读取须无条件过滤 */
    @Test
    fun `material 内混入 DS_Store 不影响加解密`() {
        val root = tmp.newFolder("certs")
        val cipher = HarmonyPasswordTool.encrypt("with-ds-store", root.absolutePath)
        File(root, "material/fd/.DS_Store").writeText("junk")
        File(root, "material/.DS_Store").writeText("junk")
        File(root, "material/ac/.DS_Store").writeText("junk")
        assertEquals("with-ds-store", HarmonyPasswordTool.decrypt(cipher, root.absolutePath))
    }

    @Test
    fun `解密时目录无材料直接报错且不生成文件`() {
        val empty = tmp.newFolder("empty").absolutePath
        try {
            HarmonyPasswordTool.decrypt("00000019ABCDEF", empty)
            fail("应报缺少 material 的错误")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("material"))
        }
        assertFalse(File(empty, "material").exists())
    }

    @Test
    fun `损坏的材料组件不被覆盖重建`() {
        val root = tmp.newFolder("certs")
        val material = File(root, "material/fd").apply { mkdirs() }
        File(material, "only-one-component").writeText("junk")
        try {
            HarmonyPasswordTool.encrypt("pw", root.absolutePath)
            fail("应报材料损坏")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("damaged"))
        }
    }

    @Test
    fun `空参数被拒绝`() {
        try {
            HarmonyPasswordTool.encrypt("pw", "")
            fail("应拒绝空材料目录")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!, e.message!!.contains("material"))
        }
        try {
            HarmonyPasswordTool.encrypt("", "/tmp")
            fail("应拒绝空密码")
        } catch (e: IllegalArgumentException) {
            assertEquals("密码不能为空", e.message)
        }
        try {
            HarmonyPasswordTool.decrypt("  ", "/tmp")
            fail("应拒绝空密文")
        } catch (e: IllegalArgumentException) {
            assertEquals("密文不能为空", e.message)
        }
    }

    @Test
    fun `真实 DevEco 材料 roundtrip`() {
        val root = requireRealMaterial()
        val cipher = HarmonyPasswordTool.encrypt("RealCheck123", root.absolutePath)
        assertEquals("RealCheck123", HarmonyPasswordTool.decrypt(cipher, root.absolutePath))
    }

    @Test
    fun `不同材料互解失败`() {
        val rootA = tmp.newFolder("certsA").absolutePath
        val cipher = HarmonyPasswordTool.encrypt("secret", rootA)
        val rootB = tmp.newFolder("certsB").absolutePath
        HarmonyPasswordTool.encrypt("other", rootB) // 生成另一套材料
        try {
            HarmonyPasswordTool.decrypt(cipher, rootB)
            fail("另一套材料应解密失败")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("不匹配"))
        }
    }
}

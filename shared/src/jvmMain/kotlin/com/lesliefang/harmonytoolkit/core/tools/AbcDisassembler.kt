package com.lesliefang.harmonytoolkit.core.tools

import com.lesliefang.harmonytoolkit.core.log.SessionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** abc 反汇编结果 */
data class DisasmResult(val text: String, val durationMs: Long, val truncated: Boolean) {
    val lines: List<String> = text.lines()
}

/** 用 ark_disasm 反汇编 abc 字节码；ark_disasm 输出写文件而非 stdout，这里落临时文件后读回 */
class AbcDisassembler(
    private val locator: ToolLocator = ToolLocator(),
    private val maxOutputBytes: Long = 8L * 1024 * 1024,
) {

    suspend fun disassemble(abcFile: File, log: SessionLog): Result<DisasmResult> {
        val runner = ToolRunner(locator, log)
        val out = File.createTempFile("ark_disasm", ".pa")
        return try {
            val r = runner.run("ark_disasm", listOf(abcFile.absolutePath, out.absolutePath))
            if (!r.isSuccess) {
                Result.failure(RuntimeException("ark_disasm 执行失败（退出码 ${r.exitCode}），详见日志"))
            } else {
                withContext(Dispatchers.IO) { readCapped(out, r.durationMs) }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            withContext(Dispatchers.IO) { out.delete() }
        }
    }

    private fun readCapped(out: File, durationMs: Long): Result<DisasmResult> {
        val cap = maxOutputBytes.toInt()
        val text = out.inputStream().use { input ->
            val buffer = ByteArray(minOf(out.length(), cap.toLong()).toInt())
            var read = 0
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            String(buffer, 0, read, Charsets.UTF_8)
        }
        return Result.success(DisasmResult(text, durationMs, truncated = out.length() > cap))
    }
}

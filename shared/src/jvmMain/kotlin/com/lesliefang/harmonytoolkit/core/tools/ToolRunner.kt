package com.lesliefang.harmonytoolkit.core.tools

import com.lesliefang.harmonytoolkit.core.log.SessionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 执行外部工具（ark_disasm/hap-sign-tool.jar/restool/hdc...），
 * 输出合并 stdout/stderr 后逐行实时写入会话日志。
 * jar 工具用应用自带 JVM 启动子进程，用户无需安装 JDK。
 */
class ToolRunner(
    private val locator: ToolLocator,
    private val log: SessionLog,
) {

    data class Result(val exitCode: Int, val durationMs: Long) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    suspend fun run(
        toolName: String,
        args: List<String> = emptyList(),
        cwd: File? = null,
        timeoutMinutes: Long = 10,
    ): Result = withContext(Dispatchers.IO) {
        val toolFile = locator.locate(toolName)
        val command = buildList {
            if (toolFile.extension.equals("jar", ignoreCase = true)) {
                add(javaBin())
                add("-jar")
                add(toolFile.absolutePath)
            } else {
                add(toolFile.absolutePath)
            }
            addAll(args)
        }
        log.info("\$ ${command.joinToString(" ")}")

        val start = System.currentTimeMillis()
        val process = ProcessBuilder(command)
            .apply {
                redirectErrorStream(true)
                cwd?.let { directory(it) }
            }
            .start()

        val reader = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) log.info(line)
            }
        }
        reader.isDaemon = true
        reader.start()

        val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            log.error("工具执行超时（${timeoutMinutes}min），已终止: $toolName")
        }
        val duration = System.currentTimeMillis() - start
        val exit = process.exitValue()
        if (exit == 0) {
            log.info("完成，耗时 $duration ms")
        } else {
            log.error("退出码 $exit，耗时 $duration ms")
        }
        Result(exit, duration)
    }

    private fun javaBin(): String {
        val home = System.getProperty("java.home")
        val bin = if (System.getProperty("os.name").lowercase().contains("windows")) "java.exe" else "java"
        return File(home, "bin/$bin").absolutePath
    }
}

package com.lesliefang.harmonytoolkit.core.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class LogLevel { INFO, WARN, ERROR }

data class LogLine(val time: LocalTime, val level: LogLevel, val message: String) {
    val timeText: String = time.format(TIME_FORMAT)

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}

/** 每个 tab（包会话）独立的日志缓冲，供底部日志面板订阅 */
class SessionLog(private val capacity: Int = 5000) {

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    private val buffer = ArrayList<LogLine>(256)

    fun info(message: String) = append(LogLevel.INFO, message)
    fun warn(message: String) = append(LogLevel.WARN, message)
    fun error(message: String) = append(LogLevel.ERROR, message)

    @Synchronized
    private fun append(level: LogLevel, message: String) {
        buffer += LogLine(LocalTime.now(), level, message)
        if (buffer.size > capacity) buffer.subList(0, buffer.size - capacity).clear()
        _lines.value = buffer.toList()
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _lines.value = emptyList()
    }
}

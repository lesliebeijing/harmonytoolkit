package com.lesliefang.harmonytoolkit.ui

import java.util.Locale

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb)
    return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0)
}

fun formatPercent(part: Long, total: Long): String {
    if (total <= 0L || part <= 0L) return "0%"
    val pct = part * 100.0 / total
    return when {
        pct < 0.1 -> "<0.1%"
        pct >= 99.95 -> "100%"
        else -> String.format(Locale.ROOT, "%.1f%%", pct)
    }
}

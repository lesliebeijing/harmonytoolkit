package com.lesliefang.harmonytoolkit.ui

import java.awt.Desktop
import java.awt.FileDialog
import java.io.File

/** AWT 原生打开文件对话框（macOS/Windows/Linux 通用）；取消返回 null */
internal fun pickFile(title: String): String? {
    val dialog = FileDialog(null as java.awt.Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(dir, file).absolutePath
}

/** AWT 原生保存文件对话框；取消返回 null */
internal fun pickSaveFile(title: String, defaultName: String): File? {
    val dialog = FileDialog(null as java.awt.Frame?, title, FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(dir, file)
}

/** 用系统文件管理器打开目录；失败静默（目录不存在或系统拒绝时无副作用） */
internal fun openInFileManager(dir: File) {
    try {
        if (dir.isDirectory) {
            Desktop.getDesktop().open(dir)
        }
    } catch (_: Exception) {
    }
}

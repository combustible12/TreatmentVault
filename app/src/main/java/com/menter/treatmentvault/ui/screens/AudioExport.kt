package com.menter.treatmentvault.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.menter.treatmentvault.data.CustomEntryEntity
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object AudioExport {
    private fun source(context: Context, entry: CustomEntryEntity): File {
        val name = richBodyAudioNames(entry.content).firstOrNull()
            ?: error("没有关联音频")
        return attachmentFile(context, name).also {
            require(it.isFile && it.length() > 0L) { "录音文件不存在或为空" }
        }
    }

    fun copyOne(context: Context, entry: CustomEntryEntity, output: OutputStream) {
        source(context, entry).inputStream().use { it.copyTo(output) }
    }

    fun writeZip(context: Context, entries: List<CustomEntryEntity>, output: OutputStream, includeNotes: Boolean) {
        require(entries.isNotEmpty()) { "请先选择录音" }
        val files = entries.map { it to source(context, it) }
        ZipOutputStream(output).use { zip ->
            files.forEachIndexed { index, pair ->
                val entry = pair.first
                val file = pair.second
                val base = (index + 1).toString() + "_" + entry.title.ifBlank { "录音" }
                    .replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(45)
                zip.putNextEntry(ZipEntry(base + "." + file.extension.ifBlank { "m4a" }))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                if (includeNotes) {
                    val notes = richBodyPlainText(entry.content)
                        .replace(Regex("\\[\\[audiomark:[^\\]]+]]"), "").trim()
                    val markers = audioMarkersFromContent(entry.content)
                    val content = buildString {
                        appendLine("标题：" + entry.title)
                        appendLine("日期：" + entry.date)
                        if (notes.isNotBlank()) {
                            appendLine("文字备注：")
                            appendLine(notes)
                        }
                        if (markers.isNotEmpty()) {
                            appendLine("时间标记：")
                            markers.forEach { appendLine(it.positionMs.toString() + "ms " + it.label) }
                        }
                    }
                    zip.putNextEntry(ZipEntry(base + ".txt"))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
    }

    fun share(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension.equals("zip", true)) "application/zip" else "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

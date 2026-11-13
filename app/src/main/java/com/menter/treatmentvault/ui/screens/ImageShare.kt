package com.menter.treatmentvault.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

internal fun shareableImageFiles(files: List<File>): List<File> = files
    .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
    .filter { it.isFile && it.length() > 0L }

internal fun toggleSharedImage(
    selectedPaths: Set<String>,
    file: File,
): Set<String> = file.absolutePath.let { path ->
    if (path in selectedPaths) selectedPaths - path else selectedPaths + path
}

internal fun toggleAllSharedImages(
    files: List<File>,
    selectedPaths: Set<String>,
): Set<String> {
    val all = shareableImageFiles(files).mapTo(linkedSetOf()) { it.absolutePath }
    return if (all.isNotEmpty() && all.all(selectedPaths::contains)) emptySet() else all
}

internal fun imageShareIntent(context: Context, files: List<File>): Intent {
    val valid = shareableImageFiles(files)
    require(valid.isNotEmpty()) { "没有可分享的图片" }
    val uris = ArrayList(valid.map {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it)
    })
    return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, uris.size > 1)
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        clipData = ClipData.newUri(context.contentResolver, "治疗资料图片", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
    }
}

@Composable
internal fun ImageShareSelectionDialog(
    files: List<File>,
    initialFile: File,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val available = remember(files) { shareableImageFiles(files) }
    var selectedPaths by remember(available, initialFile.absolutePath) {
        mutableStateOf(setOf(initialFile.absolutePath).filterTo(linkedSetOf()) { path ->
            available.any { it.absolutePath == path }
        })
    }
    var error by remember { mutableStateOf<String?>(null) }
    val allSelected = available.isNotEmpty() && available.all { it.absolutePath in selectedPaths }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择要分享的图片") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("已选 ${selectedPaths.size}/${available.size} 张", modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedPaths = toggleAllSharedImages(available, selectedPaths) }) {
                        Text(if (allSelected) "取消全选" else "全选")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(available, key = { _, file -> file.absolutePath }) { index, file ->
                        val selected = file.absolutePath in selectedPaths
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedPaths = toggleSharedImage(selectedPaths, file)
                            },
                            color = if (selected) Color(0xFFE3E3E3) else Color(0xFFF5F5F5),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    modifier = Modifier.size(58.dp),
                                    contentScale = ContentScale.Crop,
                                )
                                Text("第 ${index + 1} 张", modifier = Modifier.weight(1f))
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { selectedPaths = toggleSharedImage(selectedPaths, file) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF8A8A8A),
                                        checkmarkColor = Color.White,
                                        uncheckedColor = Color(0xFF8A8A8A),
                                    ),
                                )
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedPaths.isNotEmpty(),
                onClick = {
                    val selected = available.filter { it.absolutePath in selectedPaths }
                    runCatching {
                        context.startActivity(Intent.createChooser(imageShareIntent(context, selected), "分享图片"))
                    }.onSuccess { onDismiss() }
                        .onFailure { error = "图片分享失败：${it.message ?: "请稍后重试"}" }
                },
            ) { Text("分享（${selectedPaths.size}）") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

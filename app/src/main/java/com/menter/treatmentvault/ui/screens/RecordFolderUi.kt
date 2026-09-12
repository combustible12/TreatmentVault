package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.data.CategoryEntity

internal fun folderPathFor(categoryKey: String, categories: List<CategoryEntity>): List<CategoryEntity> {
    val byKey = categories.associateBy { it.key }
    val reversed = mutableListOf<CategoryEntity>()
    val visited = mutableSetOf<String>()
    var current = byKey[categoryKey]
    while (current != null && visited.add(current.key)) {
        reversed += current
        current = current.parentId?.let(byKey::get)
    }
    return reversed.asReversed()
}

internal fun folderPathText(categoryKey: String, categories: List<CategoryEntity>): String =
    folderPathFor(categoryKey, categories).joinToString("  ›  ") { it.name }

@Composable
internal fun RecordFolderOwnershipDialog(
    item: UnifiedRecordItem,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onFoldersChange: ((Set<String>) -> Unit)? = null,
    onSelectForMove: (() -> Unit)? = null,
) {
    val availableFolders = remember(categories) {
        categories.filter { isFolderCategory(it) && !it.hidden }
            .sortedBy { folderPathText(it.key, categories).lowercase() }
    }
    val validCurrent = remember(item.folderKeys, categories) {
        item.folderKeys.filter { key -> categories.any { it.key == key } }.toSet()
    }
    val currentFolders = remember(validCurrent, categories) {
        categories.filter { it.key in validCurrent }
            .sortedBy { folderPathText(it.key, categories).lowercase() }
    }
    val requiredPrimary = if (item.ownerType == "note") item.categoryKey else null
    var managing by remember(item.key) { mutableStateOf(false) }
    var selected by remember(item.key, item.folderKeys) { mutableStateOf(item.folderKeys) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("归属文件夹", style = MaterialTheme.typography.titleSmall)
                if (!managing) {
                    if (currentFolders.isEmpty()) {
                        Text("未找到有效文件夹归属", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        currentFolders.forEach { folder ->
                            val path = folderPathText(folder.key, categories).ifBlank { folder.name }
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                                    if (path != folder.name) {
                                        Text(path, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    if (onFoldersChange != null) {
                        TextButton(onClick = { managing = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("管理归属文件夹")
                        }
                    }
                } else {
                    Text(
                        if (requiredPrimary != null) "当前笔记所在的主文件夹会保留；可以再勾选其他文件夹。"
                        else "同一条记录可以同时出现在多个文件夹；至少保留一个归属。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(availableFolders, key = { it.key }) { folder ->
                            val checked = folder.key in selected || folder.key == requiredPrimary
                            val locked = folder.key == requiredPrimary
                            ListItem(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !locked) {
                                    selected = when {
                                        folder.key !in selected -> selected + folder.key
                                        item.ownerType != "note" && selected.size <= 1 -> selected
                                        else -> selected - folder.key
                                    }
                                },
                                headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    val path = folderPathText(folder.key, categories)
                                    if (path != folder.name) Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = {
                                    Checkbox(
                                        checked = checked,
                                        enabled = !locked,
                                        onCheckedChange = { next ->
                                            selected = when {
                                                next -> selected + folder.key
                                                item.ownerType != "note" && selected.size <= 1 -> selected
                                                else -> selected - folder.key
                                            }
                                        },
                                    )
                                },
                                trailingContent = { if (locked) Text("主文件夹", style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (managing && onFoldersChange != null) {
                TextButton(
                    enabled = item.ownerType == "note" || selected.isNotEmpty(),
                    onClick = {
                        val next = if (requiredPrimary != null) selected + requiredPrimary else selected
                        onFoldersChange(next)
                        onDismiss()
                    },
                ) { Text("保存归属") }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = when {
            managing -> ({ TextButton(onClick = { managing = false; selected = item.folderKeys }) { Text("返回") } })
            onSelectForMove != null -> ({ TextButton(onClick = onSelectForMove) { Text("选择并移动") } })
            else -> null
        },
    )
}

@Composable
internal fun RenameCategoryDialog(
    category: CategoryEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(category.key) { mutableStateOf(category.name) }
    val clean = name.trim()
    val duplicate = categories.any {
        it.key != category.key && it.parentId == category.parentId && it.name.equals(clean, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = { if (duplicate) Text("同一级已经有同名文件夹") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = clean.isNotBlank() && !duplicate && clean != category.name,
                onClick = { onRename(clean) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

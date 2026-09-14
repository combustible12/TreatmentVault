package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CategoryEntity
import com.menter.treatmentvault.data.RecordStateEntity
import kotlinx.coroutines.launch

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

internal fun toggleFolderSelection(selected: Set<String>, folderKey: String): Set<String> =
    if (folderKey in selected) selected - folderKey else selected + folderKey

@Composable
internal fun RecordFolderOwnershipDialog(
    item: UnifiedRecordItem,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onFoldersChange: ((Set<String>) -> Unit)? = null,
    onSelectForMove: (() -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val scope = rememberCoroutineScope()
    var currentState by remember(item.key) { mutableStateOf<RecordStateEntity?>(null) }
    var renaming by remember(item.key) { mutableStateOf(false) }
    var renameText by remember(item.key) { mutableStateOf("") }

    LaunchedEffect(item.key) {
        currentState = dao.recordState(item.ownerType, item.ownerId)
            ?: RecordStateEntity(ownerType = item.ownerType, ownerId = item.ownerId)
        renameText = recordDisplayTitle(item, currentState!!)
    }

    val displayTitle = currentState?.let { recordDisplayTitle(item, it) } ?: item.title
    val cleanRename = renameText.trim()

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
    val currentPrimary = if (item.ownerType == "note") item.categoryKey else null
    var managing by remember(item.key) { mutableStateOf(false) }
    var selected by remember(item.key, item.folderKeys) { mutableStateOf(item.folderKeys) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (renaming) "重命名" else displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (renaming) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("显示名称") },
                        singleLine = true,
                    )
                    Text("只修改列表里的显示名称，不改变体温、金额、日期等原始记录字段。", style = MaterialTheme.typography.bodySmall)
                } else {
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { renameText = displayTitle; renaming = true },
                            modifier = Modifier.weight(1f),
                        ) { Text("重命名") }
                        if (onFoldersChange != null) {
                            TextButton(onClick = { managing = true }, modifier = Modifier.weight(1f)) {
                                Text("管理归属")
                            }
                        }
                    }
                    currentState?.let { state ->
                        FilledTonalButton(
                            onClick = {
                                val next = state.copy(
                                    pinned = !state.pinned,
                                    updatedAt = System.currentTimeMillis(),
                                )
                                scope.launch {
                                    dao.saveRecordState(next)
                                    currentState = next
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.pinned) "取消置顶" else "置顶")
                        }
                    }
                } else {
                    Text(
                        "可以自由调整归属文件夹；编辑时可以先取消旧归属，但保存前至少选择一个。取消当前文件夹并勾选新文件夹后，会真正移动，不会暗中保留旧归属。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(availableFolders, key = { it.key }) { folder ->
                            val checked = folder.key in selected
                            ListItem(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selected = toggleFolderSelection(selected, folder.key)
                                },
                                headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    val path = folderPathText(folder.key, categories)
                                    if (path != folder.name) Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { next ->
                                            selected = if (next) selected + folder.key else selected - folder.key
                                        },
                                    )
                                },
                                trailingContent = {
                                    if (folder.key == currentPrimary) Text("当前归属", style = MaterialTheme.typography.labelSmall)
                                },
                            )
                        }
                    }
                }
                }
            }
        },
        confirmButton = {
            if (renaming) {
                TextButton(
                    enabled = cleanRename.isNotBlank(),
                    onClick = {
                        val base = currentState ?: RecordStateEntity(ownerType = item.ownerType, ownerId = item.ownerId)
                        val next = base.copy(
                            customTitle = cleanRename.takeUnless { it == item.title },
                            updatedAt = System.currentTimeMillis(),
                        )
                        scope.launch {
                            dao.saveRecordState(next)
                            currentState = next
                            renaming = false
                        }
                    },
                ) { Text("保存名称") }
            } else if (managing && onFoldersChange != null) {
                TextButton(
                    enabled = selected.isNotEmpty(),
                    onClick = {
                        onFoldersChange(selected)
                        onDismiss()
                    },
                ) { Text("保存归属") }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = when {
            renaming -> ({ TextButton(onClick = { renaming = false; renameText = displayTitle }) { Text("返回") } })
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

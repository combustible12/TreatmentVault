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
import com.menter.treatmentvault.data.AppDao
import com.menter.treatmentvault.data.CategoryEntity
import com.menter.treatmentvault.data.CustomEntryEntity

suspend fun moveNotesToCategory(
    dao: AppDao,
    allNotes: List<CustomEntryEntity>,
    ids: Set<Long>,
    destinationCategoryId: String,
) {
    if (ids.isEmpty()) return
    val now = System.currentTimeMillis()
    allNotes.filter { it.id in ids }.forEachIndexed { index, note ->
        if (note.categoryId != destinationCategoryId) {
            dao.updateCustomEntry(note.copy(categoryId = destinationCategoryId, updatedAt = now + index))
        }
    }
}

private fun categoryPath(category: CategoryEntity, categories: List<CategoryEntity>): String {
    val map = categories.associateBy { it.key }
    val names = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    var current: CategoryEntity? = category
    while (current != null && seen.add(current.key)) {
        names += current.name
        current = current.parentId?.let { map[it] }
    }
    return names.asReversed().joinToString(" / ")
}

@Composable
fun MoveNotesDialog(
    categories: List<CategoryEntity>,
    count: Int,
    currentCategoryId: String? = null,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) {
    val targets = remember(categories, currentCategoryId) {
        categories
            .filter { isFolderCategory(it) && !it.hidden && it.key != currentCategoryId }
            .sortedBy { categoryPath(it, categories).lowercase() }
    }
    var destination by remember(targets) { mutableStateOf(targets.firstOrNull()?.key) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动 $count 条笔记") },
        text = {
            if (targets.isEmpty()) {
                Text("目前没有其他可用文件夹。可以先新建一个文件夹。")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                    items(targets, key = { it.key }) { category ->
                        val path = categoryPath(category, categories)
                        ListItem(
                            modifier = Modifier.fillMaxWidth().clickable { destination = category.key },
                            headlineContent = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                if (path != category.name) Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = destination == category.key,
                                    onClick = { destination = category.key },
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { destination?.let(onMove) }, enabled = destination != null) {
                Text("移动")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun CollectExistingNotesDialog(
    notes: List<CustomEntryEntity>,
    destinationName: String,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var selected by remember(notes) { mutableStateOf<Set<Long>>(emptySet()) }
    val allSelected = notes.isNotEmpty() && selected.size == notes.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收纳已有笔记") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "从“待整理”中选择笔记，移动到“$destinationName”。移动不会复制，也不会改动正文、图片、收藏或置顶状态。",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (notes.isEmpty()) {
                    Text("待整理里暂时没有可收纳的笔记。", modifier = Modifier.padding(vertical = 18.dp))
                } else {
                    TextButton(
                        onClick = {
                            selected = if (allSelected) emptySet() else notes.map { it.id }.toSet()
                        }
                    ) {
                        Text(if (allSelected) "取消全选" else "全选 ${notes.size} 条")
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(notes, key = { it.id }) { note ->
                            val plain = richBodyPlainText(note.content)
                            val title = note.title.ifBlank {
                                plain.lineSequence().firstOrNull().orEmpty().take(42).ifBlank { "未命名笔记" }
                            }
                            ListItem(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selected = if (note.id in selected) selected - note.id else selected + note.id
                                },
                                headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    if (plain.isNotBlank()) Text(plain, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = {
                                    Checkbox(
                                        checked = note.id in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) selected + note.id else selected - note.id
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) {
                Text("移入 $destinationName")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

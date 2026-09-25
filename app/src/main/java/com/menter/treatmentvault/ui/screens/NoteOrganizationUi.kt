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

suspend fun moveFoldersToCategory(
    dao: AppDao,
    categories: List<CategoryEntity>,
    folderKeys: Set<String>,
    destinationCategoryId: String,
) {
    if (folderKeys.isEmpty()) return
    val byKey = categories.associateBy { it.key }
    val baseOrder = categories.filter { it.parentId == destinationCategoryId }.maxOfOrNull { it.sortOrder } ?: 0
    val now = System.currentTimeMillis()

    fun wouldCreateCycle(folderKey: String): Boolean {
        var cursor: String? = destinationCategoryId
        val seen = mutableSetOf<String>()
        while (cursor != null && seen.add(cursor)) {
            if (cursor == folderKey) return true
            cursor = byKey[cursor]?.parentId
        }
        return false
    }

    folderKeys.mapNotNull { byKey[it] }
        .filter { isFolderCategory(it) && it.key != destinationCategoryId && !wouldCreateCycle(it.key) }
        .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.name })
        .forEachIndexed { index, folder ->
            dao.updateCategory(
                folder.copy(
                    parentId = destinationCategoryId,
                    sortOrder = baseOrder + ((index + 1) * 10),
                    updatedAt = now + index,
                )
            )
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

private fun collectableFolders(
    categories: List<CategoryEntity>,
    destinationCategoryId: String,
): List<CategoryEntity> {
    val byKey = categories.associateBy { it.key }
    val ancestorKeys = mutableSetOf<String>()
    var cursor = byKey[destinationCategoryId]?.parentId
    while (cursor != null && ancestorKeys.add(cursor)) {
        cursor = byKey[cursor]?.parentId
    }
    return categories
        .filter {
            isFolderCategory(it) &&
                !it.hidden &&
                it.key != destinationCategoryId &&
                it.parentId != destinationCategoryId &&
                it.key !in ancestorKeys
        }
        .sortedBy { categoryPath(it, categories).lowercase() }
}

@Composable
fun MoveRecordsDialog(
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
        title = { Text("移动 $count 条记录") },
        text = {
            if (targets.isEmpty()) {
                Text("目前没有其他可用文件夹。可以先新建一个文件夹。")
            } else {
                FastScrollLazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
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
            TextButton(onClick = { destination?.let(onMove) }, enabled = destination != null) { Text("移动") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun MoveNotesDialog(
    categories: List<CategoryEntity>,
    count: Int,
    currentCategoryId: String? = null,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) = MoveRecordsDialog(categories, count, currentCategoryId, onDismiss, onMove)

@Composable
fun CollectExistingContentDialog(
    records: List<UnifiedRecordItem>,
    categories: List<CategoryEntity>,
    destinationCategoryId: String,
    destinationName: String,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>, Set<String>) -> Unit,
) {
    val folders = remember(categories, destinationCategoryId) {
        collectableFolders(categories, destinationCategoryId)
    }
    var section by remember { mutableStateOf("records") }
    var selectedRecords by remember(records) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolders by remember(folders) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedCount = selectedRecords.size + selectedFolders.size
    val currentAllSelected = when (section) {
        "folders" -> folders.isNotEmpty() && selectedFolders.size == folders.size
        else -> records.isNotEmpty() && selectedRecords.size == records.size
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收纳已有") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "把已有记录或已有文件夹移动到“$destinationName”。检查、治疗等专用记录也能移动，类型和原字段都会保留。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = section == "records",
                        onClick = { section = "records" },
                        label = { Text("记录 ${records.size}") },
                    )
                    FilterChip(
                        selected = section == "folders",
                        onClick = { section = "folders" },
                        label = { Text("文件夹 ${folders.size}") },
                    )
                }

                val currentCount = if (section == "folders") folders.size else records.size
                if (currentCount == 0) {
                    Text(
                        if (section == "folders") "目前没有可收纳的其他文件夹。" else "目前没有可收纳的其他记录。",
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    TextButton(
                        onClick = {
                            if (section == "folders") {
                                selectedFolders = if (currentAllSelected) emptySet() else folders.map { it.key }.toSet()
                            } else {
                                selectedRecords = if (currentAllSelected) emptySet() else records.map { it.key }.toSet()
                            }
                        }
                    ) { Text(if (currentAllSelected) "取消全选" else "全选 $currentCount 项") }

                    FastScrollLazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp)) {
                        if (section == "folders") {
                            items(folders, key = { it.key }) { folder ->
                                val path = categoryPath(folder, categories)
                                ListItem(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedFolders = if (folder.key in selectedFolders) selectedFolders - folder.key else selectedFolders + folder.key
                                    },
                                    headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = {
                                        if (path != folder.name) Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    leadingContent = {
                                        Checkbox(
                                            checked = folder.key in selectedFolders,
                                            onCheckedChange = { checked ->
                                                selectedFolders = if (checked) selectedFolders + folder.key else selectedFolders - folder.key
                                            },
                                        )
                                    },
                                )
                            }
                        } else {
                            items(records, key = { it.key }) { record ->
                                val source = categories.firstOrNull { it.key == record.categoryKey }
                                ListItem(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedRecords = if (record.key in selectedRecords) selectedRecords - record.key else selectedRecords + record.key
                                    },
                                    headlineContent = { Text(record.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = {
                                        Column {
                                            source?.let { Text(categoryPath(it, categories), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                            if (record.preview.isNotBlank()) Text(record.preview, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    },
                                    leadingContent = {
                                        Checkbox(
                                            checked = record.key in selectedRecords,
                                            onCheckedChange = { checked ->
                                                selectedRecords = if (checked) selectedRecords + record.key else selectedRecords - record.key
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedRecords, selectedFolders) },
                enabled = selectedCount > 0,
            ) { Text(if (selectedCount > 0) "收纳 $selectedCount 项" else "收纳") }
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
                Text("选择已有笔记，移动到“$destinationName”。", style = MaterialTheme.typography.bodySmall)
                if (notes.isEmpty()) {
                    Text("暂时没有可收纳的笔记。", modifier = Modifier.padding(vertical = 18.dp))
                } else {
                    TextButton(onClick = { selected = if (allSelected) emptySet() else notes.map { it.id }.toSet() }) {
                        Text(if (allSelected) "取消全选" else "全选 ${notes.size} 条")
                    }
                    FastScrollLazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
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
                                supportingContent = { if (plain.isNotBlank()) Text(plain, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
            TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) { Text("移入 $destinationName") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CustomEntryEntity
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotebookCategoryScreen(categoryId: String, back: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val category by dao.categories().map { list -> list.firstOrNull { it.key == categoryId } }.collectAsState(initial = null)
    val entries by dao.customEntries(categoryId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val prefs = remember { app.getSharedPreferences("category_view_preferences", 0) }

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomEntryEntity?>(null) }
    var deleting by remember { mutableStateOf<CustomEntryEntity?>(null) }
    var search by remember { mutableStateOf("") }
    var viewMode by remember(categoryId) {
        mutableStateOf(prefs.getString("view_$categoryId", "compact") ?: "compact")
    }

    val filteredEntries = remember(entries, search) {
        val q = search.trim()
        if (q.isBlank()) entries else entries.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.content.contains(q, ignoreCase = true) ||
                it.tags.contains(q, ignoreCase = true) ||
                it.date.contains(q, ignoreCase = true)
        }
    }

    fun setViewMode(mode: String) {
        viewMode = mode
        prefs.edit().putString("view_$categoryId", mode).apply()
    }

    Page(category?.name ?: "记录", back) {
        Text(
            "这里按笔记来用：点任意记录即可继续编辑，不是录入后就锁死。",
            style = MaterialTheme.typography.bodySmall,
        )

        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.EditNote, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("新建记录")
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索标题、正文、标签、日期") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "清空")
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = viewMode == "compact",
                onClick = { setViewMode("compact") },
                label = { Text("条目") },
                leadingIcon = { Icon(Icons.Default.ViewList, contentDescription = null) },
            )
            FilterChip(
                selected = viewMode == "grid",
                onClick = { setViewMode("grid") },
                label = { Text("宫格") },
                leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
            )
            FilterChip(
                selected = viewMode == "detail",
                onClick = { setViewMode("detail") },
                label = { Text("详情") },
                leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) },
            )
        }

        Text(
            if (search.isBlank()) "共 ${entries.size} 条" else "找到 ${filteredEntries.size} / ${entries.size} 条",
            style = MaterialTheme.typography.bodySmall,
        )

        if (filteredEntries.isEmpty()) {
            Text(if (entries.isEmpty()) "暂无记录" else "没有匹配的记录")
        } else {
            when (viewMode) {
                "grid" -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(filteredEntries, key = { it.id }) { entry ->
                        NotebookGridCard(entry, onEdit = { editing = entry }, onDelete = { deleting = entry })
                    }
                }
                "detail" -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        NotebookDetailCard(entry, onEdit = { editing = entry }, onDelete = { deleting = entry })
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        NotebookCompactCard(entry, onEdit = { editing = entry }, onDelete = { deleting = entry })
                    }
                }
            }
        }
    }

    if (adding) {
        NotebookEntryEditor(
            title = "新建记录",
            categoryId = categoryId,
            initial = null,
            onDismiss = { adding = false },
            onSave = { value ->
                scope.launch { dao.saveCustomEntry(value) }
                adding = false
            },
        )
    }

    editing?.let { entry ->
        NotebookEntryEditor(
            title = "编辑记录",
            categoryId = categoryId,
            initial = entry,
            onDismiss = { editing = null },
            onSave = { value ->
                scope.launch { dao.updateCustomEntry(value) }
                editing = null
            },
        )
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条记录？") },
            text = { Text(entry.title.ifBlank { entry.content.take(50) }) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.deleteCustomEntry(entry) }
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun NotebookCompactCard(entry: CustomEntryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.title.ifBlank { entry.content.lineSequence().firstOrNull().orEmpty().take(38) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.date.isNotBlank()) Text(entry.date, style = MaterialTheme.typography.labelSmall)
                if (entry.content.isNotBlank()) Text(entry.content, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                if (entry.tags.isNotBlank()) Text("# ${entry.tags}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
            NotebookEntryMenu(onEdit, onDelete)
        }
    }
}

@Composable
private fun NotebookGridCard(entry: CustomEntryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    entry.title.ifBlank { entry.content.lineSequence().firstOrNull().orEmpty().take(22) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                NotebookEntryMenu(onEdit, onDelete)
            }
            if (entry.date.isNotBlank()) Text(entry.date, style = MaterialTheme.typography.labelSmall)
            if (entry.content.isNotBlank()) Text(entry.content, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            if (entry.tags.isNotBlank()) Text("# ${entry.tags}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun NotebookDetailCard(entry: CustomEntryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    entry.title.ifBlank { entry.content.take(32) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                NotebookEntryMenu(onEdit, onDelete)
            }
            if (entry.date.isNotBlank()) Text(entry.date, style = MaterialTheme.typography.bodySmall)
            if (entry.content.isNotBlank()) Text(entry.content)
            if (entry.tags.isNotBlank()) Text("标签：${entry.tags}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NotebookEntryMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "记录操作")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("编辑") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookEntryEditor(
    title: String,
    categoryId: String,
    initial: CustomEntryEntity?,
    onDismiss: () -> Unit,
    onSave: (CustomEntryEntity) -> Unit,
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var date by remember(initial?.id) { mutableStateOf(initial?.date ?: today) }
    var entryTitle by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var tags by remember(initial?.id) { mutableStateOf(initial?.tags.orEmpty()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "关闭")
                            }
                        },
                        actions = {
                            TextButton(
                                enabled = entryTitle.isNotBlank() || content.isNotBlank(),
                                onClick = {
                                    onSave(
                                        CustomEntryEntity(
                                            id = initial?.id ?: 0,
                                            categoryId = categoryId,
                                            date = date.trim(),
                                            title = entryTitle.trim(),
                                            content = content.trim(),
                                            tags = tags.trim(),
                                            updatedAt = System.currentTimeMillis(),
                                        )
                                    )
                                },
                            ) { Text("保存") }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = entryTitle,
                        onValueChange = { entryTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("标题") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("日期") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
                        label = { Text("正文") },
                        minLines = 14,
                    )
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("标签（逗号分隔）") },
                    )
                    Text("点已有记录也会进入这个编辑页，可以反复修改。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

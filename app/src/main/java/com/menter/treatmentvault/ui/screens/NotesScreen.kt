package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CategoryEntity
import com.menter.treatmentvault.data.CustomEntryEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun noteTitle(note: CustomEntryEntity): String =
    note.title.ifBlank { note.content.lineSequence().firstOrNull().orEmpty().take(48).ifBlank { "未命名笔记" } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllNotesScreen(back: () -> Unit, go: (String) -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val categories by dao.categories().collectAsState(initial = emptyList())
    val notes by dao.allCustomEntriesFlow().collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val categoryMap = remember(categories) { categories.associateBy { it.key } }
    val customCategories = remember(categories) { categories.filterNot { it.isSystem }.sortedBy { it.sortOrder } }
    val filtered = remember(notes, query, categoryFilter) {
        val q = query.trim()
        notes.filter { note ->
            (categoryFilter == null || note.categoryId == categoryFilter) &&
                (q.isBlank() || note.title.contains(q, true) || note.content.contains(q, true) || note.tags.contains(q, true) || note.date.contains(q, true))
        }.sortedByDescending { it.updatedAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全部笔记") },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { go("note/new/inbox") }) {
                Icon(Icons.Default.Add, contentDescription = "新建笔记")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜索笔记") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "清空") }
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("全部") })
                    }
                    items(customCategories, key = { it.key }) { category ->
                        FilterChip(
                            selected = categoryFilter == category.key,
                            onClick = { categoryFilter = if (categoryFilter == category.key) null else category.key },
                            label = { Text(category.name) },
                            leadingIcon = { Icon(categoryIcon(category.iconKey), contentDescription = null) },
                        )
                    }
                }
            }
            item { Text("${filtered.size} 条笔记", style = MaterialTheme.typography.bodySmall) }
            if (filtered.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (notes.isEmpty()) "还没有笔记" else "没有匹配的笔记", style = MaterialTheme.typography.titleSmall)
                            if (notes.isEmpty()) Text("直接点右下角 + 开始记，不用先设计数据库。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { note ->
                    val category = categoryMap[note.categoryId]
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { go("note/${note.id}") },
                        headlineContent = { Text(noteTitle(note), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(category?.name ?: "笔记", style = MaterialTheme.typography.labelSmall)
                                if (note.content.isNotBlank()) Text(note.content.replace("\n", " "), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (note.tags.isNotBlank()) Text("# ${note.tags}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        leadingContent = { Icon(categoryIcon(category?.iconKey ?: "note"), contentDescription = null) },
                        trailingContent = { if (note.date.isNotBlank()) Text(note.date, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(noteId: Long, initialCategoryId: String?, back: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val categories by dao.categories().collectAsState(initial = emptyList())
    val noteFlow = remember(noteId) { if (noteId > 0) dao.customEntry(noteId) else flowOf(null) }
    val existing by noteFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    var loadedId by remember(noteId) { mutableStateOf<Long?>(null) }
    var title by rememberSaveable(noteId) { mutableStateOf("") }
    var content by rememberSaveable(noteId) { mutableStateOf("") }
    var date by rememberSaveable(noteId) { mutableStateOf(today) }
    var tags by rememberSaveable(noteId) { mutableStateOf("") }
    var categoryId by rememberSaveable(noteId, initialCategoryId) { mutableStateOf(initialCategoryId.orEmpty()) }
    var categoryMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingFiles by remember(noteId) { mutableStateOf<List<String>>(emptyList()) }

    val customCategories = remember(categories) { categories.filterNot { it.isSystem }.sortedBy { it.sortOrder } }

    LaunchedEffect(categories) {
        if (categories.none { it.key == "inbox" }) {
            dao.saveCategory(CategoryEntity(key = "inbox", name = "待整理", iconKey = "note", sortOrder = 5, isSystem = false, smartEnabled = false))
        }
        if (noteId == 0L && categoryId.isBlank()) categoryId = initialCategoryId ?: customCategories.firstOrNull()?.key ?: "inbox"
    }

    LaunchedEffect(existing) {
        val note = existing ?: return@LaunchedEffect
        if (loadedId != note.id) {
            loadedId = note.id
            title = note.title
            content = note.content
            date = note.date
            tags = note.tags
            categoryId = note.categoryId
        }
    }

    val selectedCategory = customCategories.firstOrNull { it.key == categoryId }
    val canSave = title.isNotBlank() || content.isNotBlank()

    fun saveAndBack() {
        if (!canSave) return
        val safeCategory = categoryId.ifBlank { customCategories.firstOrNull()?.key ?: "inbox" }
        val value = CustomEntryEntity(
            id = existing?.id ?: 0,
            categoryId = safeCategory,
            date = date.trim(),
            title = title.trim(),
            content = content.trim(),
            tags = tags.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        scope.launch {
            val savedId = if (value.id == 0L) dao.saveCustomEntry(value) else {
                dao.updateCustomEntry(value)
                value.id
            }
            savePendingAttachments(dao, "note", savedId, pendingFiles)
            back()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId > 0) "编辑笔记" else "新建笔记") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, contentDescription = "删除") }
                    TextButton(onClick = { saveAndBack() }, enabled = canSave) { Text("保存") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("标题") }, singleLine = true, textStyle = MaterialTheme.typography.titleLarge)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(categoryIcon(selectedCategory?.iconKey ?: "note"), contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(selectedCategory?.name ?: "选择分类", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            customCategories.forEach { category ->
                                DropdownMenuItem(text = { Text(category.name) }, leadingIcon = { Icon(categoryIcon(category.iconKey), contentDescription = null) }, onClick = { categoryId = category.key; categoryMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(value = date, onValueChange = { date = it }, modifier = Modifier.weight(1f), label = { Text("日期") }, singleLine = true)
                }
            }
            item {
                OutlinedTextField(value = tags, onValueChange = { tags = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("标签，例如：化疗, 便秘, 待问") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) })
            }
            item {
                OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp), placeholder = { Text("直接写。先记下来，之后再整理。") }, minLines = 12)
            }
            item {
                ImageAttachmentSection(dao = dao, ownerType = "note", ownerId = existing?.id ?: 0L, pendingFiles = pendingFiles, onPendingFilesChange = { pendingFiles = it })
            }
            item { Text("标题、正文、分类、标签和图片之后都可以重新打开修改。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条笔记？") },
            text = { Text(noteTitle(existing ?: CustomEntryEntity(categoryId = categoryId))) },
            confirmButton = {
                TextButton(onClick = {
                    val note = existing ?: return@TextButton
                    scope.launch {
                        dao.deleteCustomEntry(note)
                        confirmDelete = false
                        back()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

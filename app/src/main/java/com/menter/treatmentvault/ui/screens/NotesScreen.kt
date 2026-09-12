package com.menter.treatmentvault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.menter.treatmentvault.data.RecordStateEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun noteTitle(note: CustomEntryEntity): String =
    note.title.ifBlank { richBodyPlainText(note.content).lineSequence().firstOrNull().orEmpty().take(48).ifBlank { "未命名笔记" } }

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
    var confirmTrash by remember { mutableStateOf(false) }
    var pendingFiles by remember(noteId) { mutableStateOf<List<String>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }

    val folderCategories = remember(categories, categoryId) {
        categories
            .filter { isFolderCategory(it) && (!it.hidden || it.key == categoryId) }
            .sortedWith(compareBy({ it.parentId != null }, { it.sortOrder }, { it.name }))
    }

    LaunchedEffect(categories) {
        if (categories.none { it.key == "inbox" }) {
            dao.saveCategory(CategoryEntity(key = "inbox", name = "待整理", iconKey = "note", sortOrder = 5, isSystem = false, smartEnabled = false))
        }
        if (noteId == 0L && categoryId.isBlank()) {
            categoryId = initialCategoryId ?: folderCategories.firstOrNull { it.key == "inbox" }?.key ?: folderCategories.firstOrNull()?.key ?: "inbox"
        }
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

    val selectedCategory = folderCategories.firstOrNull { it.key == categoryId }
        ?: categories.firstOrNull { it.key == categoryId }
    val canSave = title.isNotBlank() || richBodyPlainText(content).isNotBlank() || pendingFiles.isNotEmpty()

    fun saveAndBack() {
        if (saving) return
        if (!canSave) { back(); return }
        saving = true
        val safeCategory = categoryId.ifBlank {
            folderCategories.firstOrNull { it.key == "inbox" }?.key ?: folderCategories.firstOrNull()?.key ?: "inbox"
        }
        val value = CustomEntryEntity(
            id = existing?.id ?: 0,
            categoryId = safeCategory,
            date = date.trim(),
            title = title.trim(),
            content = content,
            tags = tags.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        scope.launch {
            val savedId = if (value.id == 0L) dao.saveCustomEntry(value) else { dao.updateCustomEntry(value); value.id }
            savePendingAttachments(dao, "note", savedId, pendingFiles)
            saving = false
            back()
        }
    }

    BackHandler { saveAndBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId > 0) "编辑记录" else "新建记录") },
                navigationIcon = { IconButton(onClick = { saveAndBack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "返回并保存") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Default.DeleteOutline, contentDescription = "移到回收站") }
                    if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else TextButton(onClick = { saveAndBack() }, enabled = canSave) { Text("完成") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("标题") }, singleLine = true, textStyle = MaterialTheme.typography.titleLarge) }
            item {
                RichNoteBodyEditor(
                    dao = dao,
                    ownerType = "note",
                    ownerId = existing?.id ?: 0L,
                    value = content,
                    onValueChange = { content = it },
                    pendingFiles = pendingFiles,
                    onPendingFilesChange = { pendingFiles = it },
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(categoryIcon(selectedCategory?.iconKey ?: "note"), contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(selectedCategory?.name ?: "选择文件夹", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            folderCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    leadingIcon = { Icon(categoryIcon(category.iconKey), contentDescription = null) },
                                    onClick = { categoryId = category.key; categoryMenu = false },
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = date, onValueChange = { date = it }, modifier = Modifier.weight(1f), label = { Text("日期") }, singleLine = true)
                }
            }
            item { OutlinedTextField(value = tags, onValueChange = { tags = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("标签，例如：化疗, 便秘, 待问") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) }) }
            item { Text("返回时会自动保存；删除的内容先进入回收站。", style = MaterialTheme.typography.bodySmall) }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (confirmTrash) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            title = { Text("移到回收站？") },
            text = { Text(noteTitle(existing ?: CustomEntryEntity(categoryId = categoryId)) + "\n之后可以在“全部记录 → 回收站”恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val note = existing ?: return@TextButton
                    scope.launch {
                        dao.saveRecordState(RecordStateEntity(ownerType = "note", ownerId = note.id, trashedAt = System.currentTimeMillis()))
                        confirmTrash = false
                        back()
                    }
                }) { Text("移到回收站") }
            },
            dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text("取消") } },
        )
    }
}

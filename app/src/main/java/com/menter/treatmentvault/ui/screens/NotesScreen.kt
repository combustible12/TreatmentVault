package com.menter.treatmentvault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CategoryEntity
import com.menter.treatmentvault.data.CustomEntryEntity
import com.menter.treatmentvault.data.RecordStateEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(noteId: Long, initialCategoryId: String?, inDialog: Boolean = false, back: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val lifecycleOwner = LocalLifecycleOwner.current
    val categories by dao.categories().collectAsState(initial = emptyList())
    val noteFlow = remember(noteId) { if (noteId > 0) dao.customEntry(noteId) else flowOf(null) }
    val existing by noteFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val saveMutex = remember(noteId) { Mutex() }

    var loadedId by rememberSaveable(noteId) { mutableStateOf<Long?>(null) }
    var initialized by rememberSaveable(noteId) { mutableStateOf(noteId == 0L) }
    var persistedId by rememberSaveable(noteId) { mutableLongStateOf(noteId.coerceAtLeast(0L)) }
    var title by rememberSaveable(noteId) { mutableStateOf("") }
    var content by rememberSaveable(noteId) { mutableStateOf("") }
    var date by rememberSaveable(noteId) { mutableStateOf(today) }
    var tags by rememberSaveable(noteId) { mutableStateOf("") }
    var categoryId by rememberSaveable(noteId, initialCategoryId) { mutableStateOf(initialCategoryId.orEmpty()) }
    var createdAtMs by rememberSaveable(noteId) { mutableLongStateOf(System.currentTimeMillis()) }
    var categoryMenu by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var pendingFiles by rememberSaveable(noteId, stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })) { mutableStateOf<List<String>>(emptyList()) }
    var removedFiles by rememberSaveable(noteId, stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf<Set<String>>(emptySet()) }
    var saving by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var revision by rememberSaveable(noteId) { mutableLongStateOf(0L) }
    var savedRevision by rememberSaveable(noteId) { mutableLongStateOf(0L) }

    var saveFailed by remember(noteId) { mutableStateOf(false) }

    fun markDirty() {
        revision += 1L
        saveFailed = false
    }
    val folderCategories = remember(categories, categoryId) { categories.filter { isFolderCategory(it) && (!it.hidden || it.key == categoryId) }.sortedWith(compareBy({ it.parentId != null }, { it.sortOrder }, { it.name })) }

    LaunchedEffect(categories) {
        if (categories.none { it.key == "inbox" }) dao.saveCategory(CategoryEntity(key = "inbox", name = "待整理", iconKey = "note", sortOrder = 5, isSystem = false, smartEnabled = false))
        if (noteId == 0L && categoryId.isBlank()) categoryId = initialCategoryId ?: folderCategories.firstOrNull { it.key == "inbox" }?.key ?: folderCategories.firstOrNull()?.key ?: "inbox"
    }
    LaunchedEffect(existing) {
        val note = existing ?: return@LaunchedEffect
        if (loadedId != note.id) {
            loadedId = note.id; persistedId = note.id; title = note.title; content = note.content; date = note.date; tags = note.tags; categoryId = note.categoryId
            createdAtMs = note.createdAt.takeIf { it > 0L } ?: note.updatedAt
            pendingFiles = emptyList(); removedFiles = emptySet(); initialized = true; revision = 0L; savedRevision = 0L
        }
    }

    val selectedCategory = folderCategories.firstOrNull { it.key == categoryId } ?: categories.firstOrNull { it.key == categoryId }
    fun hasUnsavedChanges() = revision != savedRevision && shouldPersistNote(persistedId, title, content, pendingFiles, tags)

    suspend fun persistLatest(): Boolean = saveMutex.withLock {
        if (!initialized || !hasUnsavedChanges()) {
            return@withLock true
        }
        val targetRevision = revision
        val pendingSnapshot = pendingFiles
        val removedSnapshot = removedFiles
        val value = CustomEntryEntity(
            id = persistedId,
            categoryId = categoryId.ifBlank { "inbox" },
            date = date.trim(),
            title = title.trim(),
            content = content,
            tags = tags.trim(),
            createdAt = createdAtMs,
            updatedAt = existing?.updatedAt ?: createdAtMs,
        )
        saving = true
        saveFailed = false
        try {
            if (value.id == 0L) persistedId = dao.saveCustomEntry(value)
            else dao.updateCustomEntry(value)
            reconcileRichAttachments(context, dao, "note", persistedId, value.content, removedSnapshot)
            pendingFiles = pendingFiles - pendingSnapshot.toSet()
            removedFiles = removedFiles - removedSnapshot
            savedRevision = targetRevision
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.e("NoteEditor", "Unable to save note", error)
            saveFailed = true
            false
        } finally {
            saving = false
        }
    }

    val needsSave = hasUnsavedChanges()
    LaunchedEffect(revision, initialized) {
        if (initialized && needsSave) {
            delay(600)
            // A later keystroke can restart the debounce, but must not cancel a database write.
            scope.launch { persistLatest() }
        }
    }
    val persistOnStop by rememberUpdatedState(newValue = { scope.launch { persistLatest() } })
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) persistOnStop() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun saveAndBack(moveToTrash: Boolean = false) {
        if (leaving) return
        leaving = true
        scope.launch {
            try {
                do {
                    if (!persistLatest()) return@launch
                } while (hasUnsavedChanges())
                if (moveToTrash && persistedId > 0L) {
                    dao.saveRecordState(RecordStateEntity(ownerType = "note", ownerId = persistedId, trashedAt = System.currentTimeMillis()))
                }
                back()
            } finally {
                leaving = false
            }
        }
    }

    val editor: @Composable () -> Unit = {
        // Keep the scrolling viewport above the keyboard, including long text blocks.
        Scaffold(modifier = Modifier.fillMaxSize().imePadding(), topBar = {
            TopAppBar(
                title = { Text(if (noteId > 0 || persistedId > 0) "编辑记录" else "新建记录") },
                navigationIcon = { IconButton(onClick = { saveAndBack() }) { Icon(Icons.Default.ArrowBack, "返回并保存") } },
                actions = {
                    if (persistedId > 0L) IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Default.DeleteOutline, "移到回收站") }
                    if (saveFailed) {
                        TextButton(onClick = { scope.launch { persistLatest() } }, enabled = !leaving) { Text("重新保存") }
                    } else if (initialized) {
                        val status = when {
                            saving -> "保存中"
                            needsSave -> "待保存"
                            persistedId > 0L -> "已保存"
                            else -> "空白笔记"
                        }
                        Text(status, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        }) { padding ->
            if (!initialized) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else FastScrollLazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                        if (title.isBlank()) Text("标题", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(value = title, onValueChange = { if (title != it) { title = it; markDirty() } }, modifier = Modifier.fillMaxWidth(), singleLine = false, textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface))
                    }
                }
                item {
                    RichNoteBodyGridEditor(dao = dao, ownerType = "note", ownerId = persistedId, sessionKey = "note-editor-$noteId", value = content,
                        onValueChange = { if (content != it) { content = it; markDirty() } }, pendingFiles = pendingFiles,
                        onPendingFilesChange = { if (pendingFiles != it) { pendingFiles = it; markDirty() } }, removedFiles = removedFiles,
                        onRemovedFilesChange = { if (removedFiles != it) { removedFiles = it; markDirty() } })
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            TextButton(onClick = { categoryMenu = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                Icon(categoryIcon(selectedCategory?.iconKey ?: "note"), null, Modifier.size(20.dp)); Spacer(Modifier.width(5.dp))
                                Text(selectedCategory?.name ?: "选择文件夹", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                folderCategories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, leadingIcon = { Icon(categoryIcon(category.iconKey), null) }, onClick = { if (categoryId != category.key) { categoryId = category.key; markDirty() }; categoryMenu = false }) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextField(value = date, onValueChange = { if (date != it) { date = it; markDirty() } }, modifier = Modifier.width(150.dp), singleLine = true,
                            placeholder = { Text("日期") }, colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, disabledContainerColor = MaterialTheme.colorScheme.surface, focusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant, unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tag, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp))
                        BasicTextField(value = tags, onValueChange = { if (tags != it) { tags = it; markDirty() } }, modifier = Modifier.weight(1f), singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), decorationBox = { inner -> if (tags.isBlank()) Text("添加标签", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() })
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }

        if (confirmTrash) AlertDialog(onDismissRequest = { confirmTrash = false }, title = { Text("移到回收站？") }, text = {
            val currentTitle = title.ifBlank { richBodyPlainText(content).lineSequence().firstOrNull().orEmpty().take(48).ifBlank { "未命名记录" } }
            Text(currentTitle + "\n之后可以在“全部记录 → 回收站”恢复。")
        }, confirmButton = {
            TextButton(onClick = { confirmTrash = false; saveAndBack(moveToTrash = true) }) { Text("移到回收站") }
        }, dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text("取消") } })
    }

    if (inDialog) {
        EditorWindow(onDismissRequest = { saveAndBack() }) { editor() }
    } else {
        BackHandler { saveAndBack() }
        editor()
    }
}

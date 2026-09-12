package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.menter.treatmentvault.data.RecordStateEntity
import kotlinx.coroutines.launch

private enum class RecordShelf(val label: String) {
    ALL("全部"), PINNED("置顶"), FAVORITE("收藏"), ARCHIVED("归档"), TRASH("回收站")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AllRecordsScreen(back: () -> Unit, go: (String) -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val categories by dao.categories().collectAsState(emptyList())
    val supplies by dao.supplies().collectAsState(emptyList())
    val exams by dao.exams().collectAsState(emptyList())
    val plans by dao.plans().collectAsState(emptyList())
    val records by dao.records().collectAsState(emptyList())
    val symptoms by dao.symptoms().collectAsState(emptyList())
    val temperatures by dao.temperatures().collectAsState(emptyList())
    val questions by dao.questions().collectAsState(emptyList())
    val expenses by dao.expenses().collectAsState(emptyList())
    val timeline by dao.timeline().collectAsState(emptyList())
    val notes by dao.allCustomEntriesFlow().collectAsState(emptyList())
    val states by dao.recordStates().collectAsState(emptyList())
    val recordFolders by dao.recordFolders().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var shelfName by rememberSaveable { mutableStateOf(RecordShelf.ALL.name) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var moveKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val shelf = runCatching { RecordShelf.valueOf(shelfName) }.getOrDefault(RecordShelf.ALL)
    val stateMap = remember(states) { states.associateBy { "${it.ownerType}:${it.ownerId}" } }
    val allItems = remember(categories, supplies, exams, plans, records, symptoms, temperatures, questions, expenses, timeline, notes, recordFolders) {
        buildUnifiedRecords(categories, supplies, exams, plans, records, symptoms, temperatures, questions, expenses, timeline, notes, recordFolders)
    }
    val filtered = remember(allItems, stateMap, query, shelf) {
        val q = query.trim()
        allItems.filter { item ->
            val state = recordStateFor(item, stateMap)
            val shelfMatch = when (shelf) {
                RecordShelf.ALL -> state.trashedAt == null && !state.archived
                RecordShelf.PINNED -> state.trashedAt == null && !state.archived && state.pinned
                RecordShelf.FAVORITE -> state.trashedAt == null && !state.archived && state.favorite
                RecordShelf.ARCHIVED -> state.trashedAt == null && state.archived
                RecordShelf.TRASH -> state.trashedAt != null
            }
            shelfMatch && (q.isBlank() || listOf(item.title, item.preview, item.section, item.date).any { it.contains(q, true) })
        }.sortedWith(
            compareByDescending<UnifiedRecordItem> { recordStateFor(it, stateMap).pinned && shelf == RecordShelf.ALL }
                .thenByDescending { it.updatedAt }
        )
    }

    fun toggle(item: UnifiedRecordItem) {
        selectedKeys = if (item.key in selectedKeys) selectedKeys - item.key else selectedKeys + item.key
    }

    Scaffold(
        topBar = {
            if (selectedKeys.isNotEmpty()) {
                TopAppBar(
                    title = { Text("已选 ${selectedKeys.size} 条记录") },
                    navigationIcon = { IconButton(onClick = { selectedKeys = emptySet() }) { Icon(Icons.Default.Close, contentDescription = "取消选择") } },
                    actions = { IconButton(onClick = { moveKeys = selectedKeys }) { Icon(Icons.Default.FolderOpen, contentDescription = "移动到") } },
                )
            } else {
                TopAppBar(
                    title = { Text("全部记录") },
                    navigationIcon = { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
                )
            }
        },
        floatingActionButton = {
            if (shelf != RecordShelf.TRASH && selectedKeys.isEmpty()) {
                FloatingActionButton(onClick = { go("note/new/inbox") }) { Icon(Icons.Default.Add, contentDescription = "新建笔记") }
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
                    placeholder = { Text("搜索所有记录") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "清空") } },
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RecordShelf.entries, key = { it.name }) { item ->
                        FilterChip(
                            selected = shelf == item,
                            onClick = { shelfName = item.name; selectedKeys = emptySet() },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
            if (selectedKeys.isEmpty()) {
                item { Text("${filtered.size} 条记录 · 长按任意记录可多选移动", style = MaterialTheme.typography.bodySmall) }
            }
            if (filtered.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (query.isBlank()) "这里还没有记录" else "没有匹配内容", style = MaterialTheme.typography.titleSmall)
                            if (shelf == RecordShelf.TRASH) Text("删除的内容会先放在这里，可恢复。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.key }) { item ->
                    val state = recordStateFor(item, stateMap)
                    UnifiedRecordRow(
                        item = item,
                        state = state,
                        shelf = shelf,
                        selectionMode = selectedKeys.isNotEmpty(),
                        selected = item.key in selectedKeys,
                        onOpen = { if (selectedKeys.isNotEmpty()) toggle(item) else go(item.route) },
                        onLongSelect = { if (shelf != RecordShelf.TRASH) toggle(item) },
                        onMoveRequested = { moveKeys = setOf(item.key) },
                        onStateChange = { next -> scope.launch { dao.saveRecordState(next.copy(updatedAt = System.currentTimeMillis())) } },
                        onDeleteForever = { scope.launch { permanentlyDeleteRecord(dao, item) } },
                    )
                }
            }
        }
    }

    if (moveKeys.isNotEmpty()) {
        MoveRecordsDialog(
            categories = categories,
            count = moveKeys.size,
            onDismiss = { moveKeys = emptySet() },
            onMove = { destination ->
                scope.launch {
                    moveUnifiedRecordsToFolder(dao, notes, allItems, moveKeys, destination)
                    selectedKeys = selectedKeys - moveKeys
                    moveKeys = emptySet()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnifiedRecordRow(
    item: UnifiedRecordItem,
    state: RecordStateEntity,
    shelf: RecordShelf,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongSelect: () -> Unit,
    onMoveRequested: () -> Unit,
    onStateChange: (RecordStateEntity) -> Unit,
    onDeleteForever: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selectable = shelf != RecordShelf.TRASH

    ListItem(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onOpen,
            onLongClick = { if (selectable) onLongSelect() },
        ),
        headlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.pinned && shelf != RecordShelf.TRASH) Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.section, style = MaterialTheme.typography.labelSmall)
                if (item.preview.isNotBlank()) Text(item.preview, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.date.isNotBlank()) Text(item.date, style = MaterialTheme.typography.labelSmall)
            }
        },
        leadingContent = { Icon(categoryIcon(item.iconKey), contentDescription = null) },
        trailingContent = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onOpen() })
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (shelf == RecordShelf.TRASH) {
                            DropdownMenuItem(
                                text = { Text("恢复") },
                                leadingIcon = { Icon(Icons.Default.RestoreFromTrash, contentDescription = null) },
                                onClick = { menuOpen = false; onStateChange(state.copy(trashedAt = null, archived = false)) },
                            )
                            DropdownMenuItem(
                                text = { Text("永久删除") },
                                leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("移动到文件夹") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                onClick = { menuOpen = false; onMoveRequested() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.pinned) "取消置顶" else "置顶") },
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                onClick = { menuOpen = false; onStateChange(state.copy(pinned = !state.pinned)) },
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.favorite) "取消收藏" else "收藏") },
                                leadingIcon = { Icon(if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null) },
                                onClick = { menuOpen = false; onStateChange(state.copy(favorite = !state.favorite)) },
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.archived) "取消归档" else "归档") },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                onClick = { menuOpen = false; onStateChange(state.copy(archived = !state.archived)) },
                            )
                            DropdownMenuItem(
                                text = { Text("移到回收站") },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                onClick = { menuOpen = false; onStateChange(state.copy(trashedAt = System.currentTimeMillis(), archived = false, pinned = false)) },
                            )
                        }
                    }
                }
            }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("永久删除这条记录？") },
            text = { Text("永久删除后无法从回收站恢复。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteForever() }) { Text("永久删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

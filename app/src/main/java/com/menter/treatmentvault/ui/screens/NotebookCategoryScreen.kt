package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CustomEntryEntity
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class NoteSortMode(val label: String) {
    UPDATED("最近编辑"),
    DATE("记录日期"),
    TITLE("标题"),
}

private fun notePlainText(note: CustomEntryEntity): String = richBodyPlainText(note.content)

private fun noteDisplayTitle(note: CustomEntryEntity): String {
    val plain = notePlainText(note)
    return note.title.ifBlank { plain.lineSequence().firstOrNull().orEmpty().take(48).ifBlank { "未命名笔记" } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotebookCategoryScreen(categoryId: String, back: () -> Unit, go: (String) -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val category by dao.categories().map { list -> list.firstOrNull { it.key == categoryId } }.collectAsState(initial = null)
    val categories by dao.categories().collectAsState(initial = emptyList())
    val entries by dao.customEntries(categoryId).collectAsState(initial = emptyList())
    val allNotes by dao.allCustomEntriesFlow().collectAsState(initial = emptyList())
    val states by dao.recordStates().collectAsState(initial = emptyList())
    val stateMap = remember(states) { states.associateBy { "${it.ownerType}:${it.ownerId}" } }
    val scope = rememberCoroutineScope()
    val prefs = remember { app.getSharedPreferences("category_view_preferences", 0) }

    var search by remember { mutableStateOf("") }
    var viewMode by remember(categoryId) { mutableStateOf(prefs.getString("view_$categoryId", "compact") ?: "compact") }
    var sortName by remember(categoryId) { mutableStateOf(prefs.getString("sort_$categoryId", NoteSortMode.UPDATED.name) ?: NoteSortMode.UPDATED.name) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var selectedIds by remember(categoryId) { mutableStateOf<Set<Long>>(emptySet()) }
    var showMove by remember { mutableStateOf(false) }
    var showCollect by remember { mutableStateOf(false) }

    val sortMode = runCatching { NoteSortMode.valueOf(sortName) }.getOrDefault(NoteSortMode.UPDATED)
    val activeEntries = remember(entries, stateMap) {
        entries.filter { note ->
            val state = stateMap["note:${note.id}"]
            state?.trashedAt == null && state?.archived != true
        }
    }
    val filtered = remember(activeEntries, search, sortMode) {
        val q = search.trim()
        val matched = if (q.isBlank()) activeEntries else activeEntries.filter { note ->
            note.title.contains(q, true) ||
                notePlainText(note).contains(q, true) ||
                note.tags.contains(q, true) ||
                note.date.contains(q, true)
        }
        when (sortMode) {
            NoteSortMode.UPDATED -> matched.sortedByDescending { it.updatedAt }
            NoteSortMode.DATE -> matched.sortedWith(compareByDescending<CustomEntryEntity> { it.date }.thenByDescending { it.updatedAt })
            NoteSortMode.TITLE -> matched.sortedBy { noteDisplayTitle(it).lowercase() }
        }
    }
    val inboxCandidates = remember(allNotes, stateMap, categoryId) {
        if (categoryId == "inbox") emptyList() else allNotes.filter { note ->
            if (note.categoryId != "inbox") return@filter false
            val state = stateMap["note:${note.id}"]
            state?.trashedAt == null && state?.archived != true
        }.sortedByDescending { it.updatedAt }
    }

    fun setView(mode: String) {
        viewMode = mode
        prefs.edit().putString("view_$categoryId", mode).apply()
    }

    fun setSort(mode: NoteSortMode) {
        sortName = mode.name
        prefs.edit().putString("sort_$categoryId", mode.name).apply()
        sortMenuOpen = false
    }

    fun toggle(noteId: Long) {
        selectedIds = if (noteId in selectedIds) selectedIds - noteId else selectedIds + noteId
    }

    Scaffold(
        topBar = {
            if (selectedIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text("已选 ${selectedIds.size} 条") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMove = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "移动到")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(category?.name ?: "记录") },
                    navigationIcon = {
                        IconButton(onClick = back) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { go("note/new/$categoryId") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.EditNote, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("新建笔记")
                }
                if (categoryId != "inbox") {
                    OutlinedButton(onClick = { showCollect = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("收纳已有")
                    }
                }
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

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewMode == "compact",
                    onClick = { setView("compact") },
                    label = { Text("条目") },
                    leadingIcon = { Icon(Icons.Default.ViewList, contentDescription = null) },
                )
                FilterChip(
                    selected = viewMode == "grid",
                    onClick = { setView("grid") },
                    label = { Text("宫格") },
                    leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                )
                FilterChip(
                    selected = viewMode == "detail",
                    onClick = { setView("detail") },
                    label = { Text("详情") },
                    leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) },
                )
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "排序")
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        NoteSortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                trailingIcon = {
                                    if (sortMode == mode) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                onClick = { setSort(mode) },
                            )
                        }
                    }
                }
            }

            Text(
                if (search.isBlank()) "共 ${activeEntries.size} 条 · ${sortMode.label}" else "找到 ${filtered.size} / ${activeEntries.size} 条",
                style = MaterialTheme.typography.bodySmall,
            )

            if (filtered.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (activeEntries.isEmpty()) "这个文件夹还是空的" else "没有匹配的笔记", style = MaterialTheme.typography.titleSmall)
                        if (activeEntries.isEmpty() && categoryId != "inbox") {
                            Text(
                                if (inboxCandidates.isEmpty()) "可以直接新建笔记；以后“待整理”里有内容时，也可以从这里批量收进来。"
                                else "可以新建，也可以把“待整理”里的 ${inboxCandidates.size} 条笔记批量收进来。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            } else when (viewMode) {
                "grid" -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(filtered, key = { it.id }) { note ->
                        NoteGridCard(
                            note = note,
                            selected = note.id in selectedIds,
                            selectionMode = selectedIds.isNotEmpty(),
                            onClick = { if (selectedIds.isNotEmpty()) toggle(note.id) else go("note/${note.id}") },
                            onLongClick = { toggle(note.id) },
                        )
                    }
                }
                "detail" -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { note ->
                        NoteDetailCard(
                            note = note,
                            selected = note.id in selectedIds,
                            selectionMode = selectedIds.isNotEmpty(),
                            onClick = { if (selectedIds.isNotEmpty()) toggle(note.id) else go("note/${note.id}") },
                            onLongClick = { toggle(note.id) },
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.id }) { note ->
                        NoteCompactCard(
                            note = note,
                            selected = note.id in selectedIds,
                            selectionMode = selectedIds.isNotEmpty(),
                            onClick = { if (selectedIds.isNotEmpty()) toggle(note.id) else go("note/${note.id}") },
                            onLongClick = { toggle(note.id) },
                        )
                    }
                }
            }
        }
    }

    if (showCollect) {
        CollectExistingNotesDialog(
            notes = inboxCandidates,
            destinationName = category?.name ?: "当前文件夹",
            onDismiss = { showCollect = false },
            onConfirm = { ids ->
                scope.launch {
                    moveNotesToCategory(dao, allNotes, ids, categoryId)
                    showCollect = false
                }
            },
        )
    }

    if (showMove && selectedIds.isNotEmpty()) {
        MoveNotesDialog(
            categories = categories,
            count = selectedIds.size,
            currentCategoryId = categoryId,
            onDismiss = { showMove = false },
            onMove = { destination ->
                scope.launch {
                    moveNotesToCategory(dao, allNotes, selectedIds, destination)
                    selectedIds = emptySet()
                    showMove = false
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCompactCard(
    note: CustomEntryEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val plain = notePlainText(note)
    ListItem(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        headlineContent = { Text(noteDisplayTitle(note), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (note.date.isNotBlank()) Text(note.date, style = MaterialTheme.typography.labelSmall)
                if (plain.isNotBlank()) Text(plain, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (note.tags.isNotBlank()) Text("# ${note.tags}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteGridCard(
    note: CustomEntryEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val plain = notePlainText(note)
    Card(
        Modifier.fillMaxWidth().heightIn(min = 130.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    noteDisplayTitle(note),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            if (note.date.isNotBlank()) Text(note.date, style = MaterialTheme.typography.labelSmall)
            if (plain.isNotBlank()) Text(plain, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            if (note.tags.isNotBlank()) Text("# ${note.tags}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteDetailCard(
    note: CustomEntryEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val plain = notePlainText(note)
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(noteDisplayTitle(note), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            if (note.date.isNotBlank()) Text(note.date, style = MaterialTheme.typography.labelSmall)
            if (plain.isNotBlank()) Text(plain)
            if (note.tags.isNotBlank()) Text("标签：${note.tags}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

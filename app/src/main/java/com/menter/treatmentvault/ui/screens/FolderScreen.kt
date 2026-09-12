package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CategoryEntity
import com.menter.treatmentvault.data.RecordStateEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderScreen(categoryId: String, back: () -> Unit, go: (String) -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val categories by dao.categories().collectAsState(initial = emptyList())
    val supplies by dao.supplies().collectAsState(initial = emptyList())
    val exams by dao.exams().collectAsState(initial = emptyList())
    val plans by dao.plans().collectAsState(initial = emptyList())
    val treatmentRecords by dao.records().collectAsState(initial = emptyList())
    val symptoms by dao.symptoms().collectAsState(initial = emptyList())
    val temperatures by dao.temperatures().collectAsState(initial = emptyList())
    val questions by dao.questions().collectAsState(initial = emptyList())
    val expenses by dao.expenses().collectAsState(initial = emptyList())
    val timeline by dao.timeline().collectAsState(initial = emptyList())
    val allNotes by dao.allCustomEntriesFlow().collectAsState(initial = emptyList())
    val states by dao.recordStates().collectAsState(initial = emptyList())
    val recordFolders by dao.recordFolders().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val folder = categories.firstOrNull { it.key == categoryId }
    val stateMap = remember(states) { states.associateBy { "${it.ownerType}:${it.ownerId}" } }
    val allItems = remember(categories, supplies, exams, plans, treatmentRecords, symptoms, temperatures, questions, expenses, timeline, allNotes, recordFolders) {
        buildUnifiedRecords(categories, supplies, exams, plans, treatmentRecords, symptoms, temperatures, questions, expenses, timeline, allNotes, recordFolders)
    }
    val activeItems = remember(allItems, stateMap) {
        allItems.filter { item ->
            val state = recordStateFor(item, stateMap)
            state.trashedAt == null && !state.archived
        }
    }
    val childFolders = remember(categories, categoryId) {
        categories.filter { it.parentId == categoryId && isFolderCategory(it) && !it.hidden }
            .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.name })
    }
    val directItems = remember(activeItems, categoryId) {
        activeItems.filter { categoryId in it.folderKeys }
            .sortedWith(compareByDescending<UnifiedRecordItem> { isTodo(recordStateFor(it, stateMap)) && !recordStateFor(it, stateMap).completed }.thenByDescending { it.updatedAt })
    }
    val collectableRecords = remember(activeItems, categoryId) {
        activeItems.filter { categoryId !in it.folderKeys }.sortedByDescending { it.updatedAt }
    }

    var query by remember(categoryId) { mutableStateOf("") }
    var selectedKeys by remember(categoryId) { mutableStateOf<Set<String>>(emptySet()) }
    var showMove by remember { mutableStateOf(false) }
    var showCollect by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var ownershipItem by remember { mutableStateOf<UnifiedRecordItem?>(null) }
    var renameFolder by remember { mutableStateOf<CategoryEntity?>(null) }

    val q = query.trim()
    val visibleChildren = remember(childFolders, q) {
        if (q.isBlank()) childFolders else childFolders.filter { it.name.contains(q, true) || it.smartKeywords.contains(q, true) }
    }
    val visibleItems = remember(directItems, q) {
        if (q.isBlank()) directItems else directItems.filter { item ->
            listOf(item.title, item.preview, item.date, item.section).any { it.contains(q, true) }
        }
    }

    fun toggle(item: UnifiedRecordItem) {
        selectedKeys = if (item.key in selectedKeys) selectedKeys - item.key else selectedKeys + item.key
    }

    fun saveState(next: RecordStateEntity) {
        scope.launch { dao.saveRecordState(next.copy(updatedAt = System.currentTimeMillis())) }
    }

    if (folder == null) {
        Page("文件夹不存在", back) { Text("这个文件夹可能已经被删除或恢复了默认设置。") }
        return
    }

    Scaffold(
        topBar = {
            if (selectedKeys.isNotEmpty()) {
                TopAppBar(
                    title = { Text("已选 ${selectedKeys.size} 条") },
                    navigationIcon = { IconButton(onClick = { selectedKeys = emptySet() }) { Icon(Icons.Default.Close, contentDescription = "取消选择") } },
                    actions = { IconButton(onClick = { showMove = true }) { Icon(Icons.Default.FolderOpen, contentDescription = "移动到") } },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            folder.name,
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { renameFolder = folder },
                            ),
                        )
                    },
                    navigationIcon = { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
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
            }

            item {
                OutlinedButton(onClick = { showNewFolder = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("新建子文件夹")
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜索当前文件夹") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "清空") } },
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }

            if (visibleChildren.isNotEmpty()) {
                item { Text("子文件夹", style = MaterialTheme.typography.titleMedium) }
                items(visibleChildren, key = { "folder:${it.key}" }) { child ->
                    val recordCount = activeItems.count { child.key in it.folderKeys }
                    val subfolderCount = categories.count { it.parentId == child.key && isFolderCategory(it) && !it.hidden }
                    FolderColorCard(
                        folder = child,
                        supporting = buildString {
                            if (subfolderCount > 0) append("$subfolderCount 个子文件夹")
                            if (recordCount > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("$recordCount 条记录")
                            }
                            if (isEmpty()) append("空文件夹")
                        },
                        onClick = { go("folder/${child.key}") },
                        onLongClick = { renameFolder = child },
                    )
                }
            }

            if (visibleItems.isNotEmpty()) {
                item { Text("笔记与记录", style = MaterialTheme.typography.titleMedium) }
                items(visibleItems, key = { it.key }) { item ->
                    val state = recordStateFor(item, stateMap)
                    FolderRecordCard(
                        item = item,
                        state = state,
                        selectionMode = selectedKeys.isNotEmpty(),
                        selected = item.key in selectedKeys,
                        onClick = { if (selectedKeys.isNotEmpty()) toggle(item) else go(item.route) },
                        onLongClick = {
                            if (selectedKeys.isNotEmpty()) toggle(item)
                            else ownershipItem = item
                        },
                        onStateChange = ::saveState,
                    )
                }
            }

            if (visibleChildren.isEmpty() && visibleItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (q.isBlank()) "这个文件夹还是空的" else "没有匹配内容", style = MaterialTheme.typography.titleMedium)
                            if (q.isBlank()) {
                                Text(
                                    if (categoryId == "inbox") "可以直接新建笔记，之后再移动到其他文件夹。"
                                    else "可以新建笔记、新建子文件夹，或从其他位置收纳已有记录和文件夹。",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    ownershipItem?.let { item ->
        RecordFolderOwnershipDialog(
            item = item,
            categories = categories,
            onDismiss = { ownershipItem = null },
            onFoldersChange = { folders -> scope.launch { setUnifiedRecordFolders(dao, item, folders) } },
            onSelectForMove = {
                toggle(item)
                ownershipItem = null
            },
        )
    }

    renameFolder?.let { target ->
        RenameCategoryDialog(
            category = target,
            categories = categories,
            onDismiss = { renameFolder = null },
            onRename = { newName ->
                scope.launch { dao.saveCategory(target.copy(name = newName)) }
                renameFolder = null
            },
        )
    }

    if (showCollect) {
        CollectExistingContentDialog(
            records = collectableRecords,
            categories = categories,
            destinationCategoryId = categoryId,
            destinationName = folder.name,
            onDismiss = { showCollect = false },
            onConfirm = { recordKeys, folderKeys ->
                scope.launch {
                    moveUnifiedRecordsToFolder(dao, allNotes, allItems, recordKeys, categoryId)
                    moveFoldersToCategory(dao, categories, folderKeys, categoryId)
                    showCollect = false
                }
            },
        )
    }

    if (showMove && selectedKeys.isNotEmpty()) {
        MoveRecordsDialog(
            categories = categories,
            count = selectedKeys.size,
            currentCategoryId = categoryId,
            onDismiss = { showMove = false },
            onMove = { destination ->
                scope.launch {
                    moveUnifiedRecordsToFolder(dao, allNotes, allItems, selectedKeys, destination)
                    selectedKeys = emptySet()
                    showMove = false
                }
            },
        )
    }

    if (showNewFolder) {
        val duplicate = categories.any { it.parentId == categoryId && it.name.equals(newFolderName.trim(), true) }
        AlertDialog(
            onDismissRequest = { showNewFolder = false; newFolderName = "" },
            title = { Text("在“${folder.name}”中新建文件夹") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = { if (duplicate) Text("同一级已经有同名文件夹") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newFolderName.isNotBlank() && !duplicate,
                    onClick = {
                        val name = newFolderName.trim()
                        val order = (categories.filter { it.parentId == categoryId }.maxOfOrNull { it.sortOrder } ?: 0) + 10
                        scope.launch {
                            dao.saveCategory(
                                CategoryEntity(
                                    key = "custom_${System.currentTimeMillis()}",
                                    name = name,
                                    iconKey = "folder",
                                    sortOrder = order,
                                    isSystem = false,
                                    smartEnabled = true,
                                    smartKeywords = name,
                                    parentId = categoryId,
                                )
                            )
                        }
                        newFolderName = ""
                        showNewFolder = false
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false; newFolderName = "" }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderColorCard(
    folder: CategoryEntity,
    supporting: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(categoryIcon(folder.iconKey), contentDescription = null, modifier = Modifier.size(23.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (supporting.isNotBlank()) Text(supporting, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRecordCard(
    item: UnifiedRecordItem,
    state: RecordStateEntity,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStateChange: (RecordStateEntity) -> Unit,
) {
    var menuOpen by remember(item.key) { mutableStateOf(false) }
    val contentColor = recordContentColor(state)
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = recordBackgroundColor(state, MaterialTheme.colorScheme.surfaceContainerHigh)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(categoryIcon(item.iconKey), contentDescription = null, modifier = Modifier.size(23.dp), tint = contentColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isTodo(state)) {
                        Checkbox(checked = state.completed, onCheckedChange = { onStateChange(state.copy(completed = it)) })
                    }
                    Text(item.title, color = contentColor, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (state.pinned) Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
                    if (state.favorite) Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
                }
                if (item.date.isNotBlank()) Text(item.date, color = contentColor, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = contentColor) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isTodo(state)) "切换为普通条目" else "切换为待办事项") },
                            leadingIcon = { Icon(if (isTodo(state)) Icons.Default.Notes else Icons.Default.CheckBox, contentDescription = null) },
                            onClick = { menuOpen = false; onStateChange(toggleTodoDisplay(state)) },
                        )
                    }
                }
            }
        }
    }
}

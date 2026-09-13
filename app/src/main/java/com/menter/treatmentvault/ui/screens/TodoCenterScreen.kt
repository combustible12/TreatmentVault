package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CategoryEntity
import com.menter.treatmentvault.data.RecordStateEntity
import kotlinx.coroutines.launch

private enum class TodoShelf(val label: String) {
    PENDING("未完成"), ALL("全部"), DONE("已完成")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoCenterScreen(back: () -> Unit, go: (String) -> Unit) {
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
    val listState = rememberLazyListState()

    var query by rememberSaveable { mutableStateOf("") }
    var shelfName by rememberSaveable { mutableStateOf(TodoShelf.PENDING.name) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var ownershipItem by remember { mutableStateOf<UnifiedRecordItem?>(null) }
    val shelf = runCatching { TodoShelf.valueOf(shelfName) }.getOrDefault(TodoShelf.PENDING)

    val stateMap = remember(states) { states.associateBy { "${it.ownerType}:${it.ownerId}" } }
    val allItems = remember(categories, supplies, exams, plans, records, symptoms, temperatures, questions, expenses, timeline, notes, recordFolders) {
        buildUnifiedRecords(categories, supplies, exams, plans, records, symptoms, temperatures, questions, expenses, timeline, notes, recordFolders)
    }
    val todos = remember(allItems, stateMap) {
        allItems.filter { item ->
            val state = recordStateFor(item, stateMap)
            state.trashedAt == null && !state.archived && isTodo(state)
        }
    }
    val pendingCount = remember(todos, stateMap) { todos.count { !recordStateFor(it, stateMap).completed } }
    val doneCount = remember(todos, stateMap) { todos.count { recordStateFor(it, stateMap).completed } }
    val categoryCounts = remember(todos, categories) {
        categories.filterNot { it.hidden }.mapNotNull { category ->
            val count = todos.count { category.key in it.folderKeys }
            if (count > 0) category to count else null
        }.sortedBy { it.first.sortOrder }
    }
    LaunchedEffect(categoryCounts, selectedCategory) {
        if (selectedCategory != null && categoryCounts.none { it.first.key == selectedCategory }) selectedCategory = null
    }

    val filtered = remember(todos, stateMap, query, shelf, selectedCategory) {
        val q = query.trim()
        todos.filter { item ->
            val state = recordStateFor(item, stateMap)
            val shelfMatch = when (shelf) {
                TodoShelf.PENDING -> !state.completed
                TodoShelf.ALL -> true
                TodoShelf.DONE -> state.completed
            }
            val categoryMatch = selectedCategory == null || selectedCategory in item.folderKeys
            val queryMatch = q.isBlank() || listOf(recordDisplayTitle(item, state), item.preview, item.section, item.date)
                .any { it.contains(q, ignoreCase = true) }
            shelfMatch && categoryMatch && queryMatch
        }.sortedWith(
            compareBy<UnifiedRecordItem> { recordStateFor(it, stateMap).completed }
                .thenByDescending { it.updatedAt }
        )
    }

    fun saveState(next: RecordStateEntity) {
        scope.launch { dao.saveRecordState(next.copy(updatedAt = System.currentTimeMillis())) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "全部待办",
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onDoubleClick = { scope.launch { listState.animateScrollToItem(0) } },
                        ),
                    )
                },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TodoSummaryStat("待完成", pendingCount, Icons.Default.RadioButtonUnchecked)
                        VerticalDivider(Modifier.height(42.dp))
                        TodoSummaryStat("已完成", doneCount, Icons.Default.CheckCircle)
                        VerticalDivider(Modifier.height(42.dp))
                        TodoSummaryStat("全部", todos.size, Icons.Default.Checklist)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜索待办") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "清空") } },
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TodoShelf.entries, key = { it.name }) { option ->
                        val count = when (option) {
                            TodoShelf.PENDING -> pendingCount
                            TodoShelf.ALL -> todos.size
                            TodoShelf.DONE -> doneCount
                        }
                        FilterChip(
                            selected = shelf == option,
                            onClick = { shelfName = option.name },
                            label = { Text("${option.label} $count") },
                            leadingIcon = if (shelf == option) {{ Icon(Icons.Default.Done, null, Modifier.size(18.dp)) }} else null,
                        )
                    }
                }
            }
            if (categoryCounts.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("全部类目") },
                            )
                        }
                        items(categoryCounts, key = { it.first.key }) { (category, count) ->
                            FilterChip(
                                selected = selectedCategory == category.key,
                                onClick = { selectedCategory = if (selectedCategory == category.key) null else category.key },
                                label = { Text("${category.name} $count") },
                                leadingIcon = { Icon(categoryIcon(category.iconKey), null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    if (filtered.isEmpty()) "没有符合条件的待办" else "${filtered.size} 条 · 勾选完成后不会删除，只会进入已完成区",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (todos.isEmpty()) "还没有待办" else "当前筛选下没有待办", style = MaterialTheme.typography.titleSmall)
                            Text(if (todos.isEmpty()) "可以在任意记录的更多菜单里切换为待办事项。" else "换一个状态、类目或搜索词试试。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.key }) { item ->
                    val state = recordStateFor(item, stateMap)
                    TodoCenterRow(
                        item = item,
                        state = state,
                        categoryNames = item.folderKeys.mapNotNull { key -> categories.firstOrNull { it.key == key }?.name },
                        onOpen = { go(item.route) },
                        onLongPress = { ownershipItem = item },
                        onStateChange = ::saveState,
                    )
                }
            }
        }
    }

    ownershipItem?.let { item ->
        RecordFolderOwnershipDialog(
            item = item,
            categories = categories,
            onDismiss = { ownershipItem = null },
            onFoldersChange = { folders -> scope.launch { setUnifiedRecordFolders(dao, item, folders) } },
        )
    }
}

@Composable
private fun TodoSummaryStat(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoCenterRow(
    item: UnifiedRecordItem,
    state: RecordStateEntity,
    categoryNames: List<String>,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onStateChange: (RecordStateEntity) -> Unit,
) {
    var menuOpen by remember(item.key) { mutableStateOf(false) }
    val contentColor = recordContentColor(state)

    ListItem(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        colors = ListItemDefaults.colors(containerColor = recordBackgroundColor(state)),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Checkbox(
                        checked = state.completed,
                        onCheckedChange = { onStateChange(state.copy(completed = it)) },
                        modifier = Modifier.size(48.dp),
                    )
                }
                Text(recordDisplayTitle(item, state), maxLines = 2, overflow = TextOverflow.Ellipsis, color = contentColor)
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (categoryNames.isNotEmpty()) Text(categoryNames.distinct().joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = contentColor)
                if (item.preview.isNotBlank()) Text(item.preview, maxLines = 2, overflow = TextOverflow.Ellipsis, color = contentColor)
                if (item.date.isNotBlank()) Text(item.date, style = MaterialTheme.typography.labelSmall, color = contentColor)
            }
        },
        leadingContent = { Icon(categoryIcon(item.iconKey), contentDescription = null, tint = contentColor) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordThumbnail(item.thumbnailFileName, size = 44.dp)
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = contentColor) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("切换为普通条目") },
                            leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                            onClick = { menuOpen = false; onStateChange(toggleTodoDisplay(state)) },
                        )
                    }
                }
            }
        },
    )
}

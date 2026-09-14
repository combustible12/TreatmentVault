package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

private fun flattenHomeCategories(categories: List<CategoryEntity>): List<Pair<CategoryEntity, Int>> {
    val visible = categories.filterNot { it.hidden }
    val byParent = visible.groupBy { it.parentId }
    val result = mutableListOf<Pair<CategoryEntity, Int>>()
    fun visit(parent: String?, depth: Int) {
        byParent[parent].orEmpty().sortedBy { it.sortOrder }.forEach { category ->
            result += category to depth
            visit(category.key, depth + 1)
        }
    }
    visit(null, 0)
    val included = result.map { it.first.key }.toSet()
    visible.filterNot { it.key in included }.sortedBy { it.sortOrder }.forEach { result += it to 0 }
    return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(go: (String) -> Unit) {
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var query by rememberSaveable { mutableStateOf("") }
    var showAddCategory by rememberSaveable { mutableStateOf(false) }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var newCategoryParentId by rememberSaveable { mutableStateOf<String?>(null) }
    var parentMenuOpen by remember { mutableStateOf(false) }
    var ownershipItem by remember { mutableStateOf<UnifiedRecordItem?>(null) }
    var renameCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(categories) {
        if (categories.none { it.key == "inbox" }) {
            dao.saveCategory(CategoryEntity(key = "inbox", name = "待整理", iconKey = "note", sortOrder = 5, isSystem = false, smartEnabled = false))
        }
    }

    val visibleCategories = remember(categories) { flattenHomeCategories(categories) }
    val customCategories = remember(categories) { categories.filterNot { it.isSystem }.sortedBy { it.sortOrder } }
    val allRecords = remember(categories, supplies, exams, plans, records, symptoms, temperatures, questions, expenses, timeline, notes, recordFolders) {
        buildUnifiedRecords(categories, supplies, exams, plans, records, symptoms, temperatures, questions, expenses, timeline, notes, recordFolders)
    }
    val stateMap = remember(states) { states.associateBy { "${it.ownerType}:${it.ownerId}" } }
    val activeRecords = remember(allRecords, stateMap) {
        allRecords.filter {
            val state = recordStateFor(it, stateMap)
            state.trashedAt == null && !state.archived
        }
    }
    val pinnedRecords = remember(activeRecords, stateMap) { activeRecords.filter { recordStateFor(it, stateMap).pinned }.take(5) }
    val todoRecords = remember(activeRecords, stateMap) { activeRecords.filter { isTodo(recordStateFor(it, stateMap)) } }
    val pendingTodoCount = remember(todoRecords, stateMap) { todoRecords.count { !recordStateFor(it, stateMap).completed } }
    val completedTodoCount = remember(todoRecords, stateMap) { todoRecords.count { recordStateFor(it, stateMap).completed } }
    val recentRecords = remember(activeRecords) { activeRecords.take(7) }
    val q = query.trim()
    val recordSearch = remember(activeRecords, stateMap, q) {
        if (q.isBlank()) emptyList() else activeRecords.filter { item ->
            val state = recordStateFor(item, stateMap)
            listOf(recordDisplayTitle(item, state), item.preview, item.section, item.date).any { it.contains(q, true) }
        }
    }
    val categorySearch = remember(categories, q) {
        if (q.isBlank()) emptyList() else categories.filterNot { it.hidden }.filter { it.name.contains(q, true) || it.smartKeywords.contains(q, true) }
    }

    fun open(route: String) {
        if (route.isBlank()) return
        scope.launch {
            if (drawerState.isOpen) drawerState.close()
            go(route)
        }
    }

    fun categoryRoute(category: CategoryEntity): String = categoryOpenRoute(category)
    fun countFor(category: CategoryEntity): Int = allRecords.count { category.key in it.folderKeys }
    fun saveState(next: RecordStateEntity) {
        scope.launch { dao.saveRecordState(next.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun createCategory() {
        val name = newCategoryName.trim()
        if (name.isBlank()) return
        val duplicate = categories.any { it.parentId == newCategoryParentId && it.name.equals(name, true) }
        if (duplicate) return
        val key = "category_${System.currentTimeMillis()}"
        val order = (categories.filter { it.parentId == newCategoryParentId }.maxOfOrNull { it.sortOrder } ?: 0) + 10
        scope.launch {
            dao.saveCategory(CategoryEntity(key = key, name = name, iconKey = "folder", sortOrder = order, isSystem = false, smartEnabled = true, smartKeywords = name, parentId = newCategoryParentId))
            newCategoryName = ""
            newCategoryParentId = null
            showAddCategory = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(18.dp))
                    Text("治疗笔记", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
                    Text("所有内容都是记录，分类只是查看方式", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    NavigationDrawerItem(icon = { Icon(Icons.Default.Notes, contentDescription = null) }, label = { Text("全部记录") }, selected = false, onClick = { open("notes") })
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                        label = {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("全部待办")
                                if (pendingTodoCount > 0) Text(pendingTodoCount.toString(), style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        selected = false,
                        onClick = { open("todos") },
                    )
                    NavigationDrawerItem(icon = { Icon(Icons.Default.Mic, contentDescription = null) }, label = { Text("智能录入") }, selected = false, onClick = { open("smart_input") })
                    NavigationDrawerItem(icon = { Icon(Icons.Default.AccountTree, contentDescription = null) }, label = { Text("资料树") }, selected = false, onClick = { open("structure") })
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("分类", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                        IconButton(onClick = { showAddCategory = true }) { Icon(Icons.Default.Add, contentDescription = "新建类目") }
                    }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(visibleCategories, key = { it.first.key }) { (category, depth) ->
                            NavigationDrawerItem(
                                icon = { Icon(categoryIcon(category.iconKey), contentDescription = null) },
                                label = {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            (if (depth > 0) "  ".repeat(depth) else "") + category.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.combinedClickable(
                                                onClick = {},
                                                onLongClick = { renameCategory = category },
                                            ),
                                        )
                                        val count = countFor(category)
                                        if (count > 0) Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                selected = false,
                                onClick = { open(categoryRoute(category)) },
                            )
                        }
                    }
                    HorizontalDivider()
                    NavigationDrawerItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text("类目管理") }, selected = false, onClick = { open("categories") })
                    NavigationDrawerItem(icon = { Icon(Icons.Default.Backup, null) }, label = { Text("备份与恢复") }, selected = false, onClick = { open("settings") })
                    NavigationDrawerItem(icon = { Icon(Icons.Default.SystemUpdateAlt, null) }, label = { Text("检查更新") }, selected = false, onClick = { open("update") })
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "治疗笔记",
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onDoubleClick = { scope.launch { listState.animateScrollToItem(0) } },
                            ),
                        )
                    },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "菜单") } },
                    actions = { IconButton(onClick = { go("structure") }) { Icon(Icons.Default.AccountTree, contentDescription = "资料树") } },
                )
            },
            floatingActionButton = {
                Box {
                    FloatingActionButton(onClick = { addMenuOpen = true }) { Icon(Icons.Default.Add, contentDescription = "新建") }
                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("新建笔记") },
                            leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                            onClick = { addMenuOpen = false; go("note/new/inbox") },
                        )
                        DropdownMenuItem(
                            text = { Text("新建类目") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = { addMenuOpen = false; showAddCategory = true },
                        )
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                state = listState,
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
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "清空") } },
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }
                if (q.isNotBlank()) {
                    item { Text("搜索结果 · ${recordSearch.size + categorySearch.size}", style = MaterialTheme.typography.titleSmall) }
                    if (categorySearch.isNotEmpty()) {
                        item { Text("分类", style = MaterialTheme.typography.labelLarge) }
                    }
                    items(categorySearch, key = { "category:${it.key}" }) { category ->
                        HomeCategoryCard(
                            category = category,
                            depth = 0,
                            supporting = "文件夹 · 打开后查看该分类全部记录",
                            onClick = { go(categoryRoute(category)) },
                            onLongClick = { renameCategory = category },
                        )
                    }
                    if (recordSearch.isNotEmpty()) {
                        item { Text("记录", style = MaterialTheme.typography.labelLarge) }
                    }
                    items(recordSearch, key = { it.key }) { item ->
                        val state = recordStateFor(item, stateMap)
                        RecordListItem(
                            item = item,
                            state = state,
                            onClick = { go(item.route) },
                            onLongClick = { ownershipItem = item },
                            onStateChange = ::saveState,
                        )
                    }
                    if (recordSearch.isEmpty() && categorySearch.isEmpty()) item { Text("没有找到匹配内容", modifier = Modifier.padding(vertical = 20.dp)) }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { go("note/new/inbox") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(6.dp)); Text("新建记录") }
                                FilledTonalButton(onClick = { go("smart_input") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("智能录入") }
                            }
                            FilledTonalButton(
                                onClick = { go("todos") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Checklist, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("全部待办 · $pendingTodoCount 未完成")
                                if (completedTodoCount > 0) {
                                    Spacer(Modifier.weight(1f))
                                    Text("$completedTodoCount 已完成", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    if (pinnedRecords.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("置顶", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { go("notes") }) { Text("管理") }
                            }
                        }
                        items(pinnedRecords, key = { "pinned:${it.key}" }) { item ->
                            RecordListItem(item, recordStateFor(item, stateMap), { go(item.route) }, { ownershipItem = item }, ::saveState)
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("最近记录", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { go("notes") }) { Text("全部") }
                        }
                    }
                    if (recentRecords.isEmpty()) {
                        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("还没有记录", style = MaterialTheme.typography.titleSmall); Text("先随手记下来，不用先决定放哪里。", style = MaterialTheme.typography.bodySmall) } } }
                    } else {
                        items(recentRecords, key = { "recent:${it.key}" }) { item ->
                            RecordListItem(item, recordStateFor(item, stateMap), { go(item.route) }, { ownershipItem = item }, ::saveState)
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("分类", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                            TextButton(onClick = { showAddCategory = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("新建类目") }
                        }
                    }
                    items(visibleCategories, key = { it.first.key }) { (category, depth) ->
                        val count = countFor(category)
                        HomeCategoryCard(
                            category = category,
                            depth = depth,
                            supporting = if (count == 0) "文件夹 · 暂无记录" else "文件夹 · $count 条记录",
                            onClick = { go(categoryRoute(category)) },
                            onLongClick = { renameCategory = category },
                        )
                    }
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

    renameCategory?.let { target ->
        RenameCategoryDialog(
            category = target,
            categories = categories,
            onDismiss = { renameCategory = null },
            onRename = { newName ->
                scope.launch { dao.saveCategory(target.copy(name = newName)) }
                renameCategory = null
            },
        )
    }

    if (showAddCategory) {
        val cleanName = newCategoryName.trim()
        val duplicate = categories.any { it.parentId == newCategoryParentId && it.name.equals(cleanName, true) }
        val parentName = customCategories.firstOrNull { it.key == newCategoryParentId }?.name
        AlertDialog(
            onDismissRequest = { showAddCategory = false; newCategoryName = ""; newCategoryParentId = null; parentMenuOpen = false },
            title = { Text("新建类目") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = newCategoryName, onValueChange = { newCategoryName = it }, label = { Text("类目名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = duplicate)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { parentMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (parentName == null) "顶级类目" else "上级：$parentName"); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = parentMenuOpen, onDismissRequest = { parentMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("顶级类目") }, onClick = { newCategoryParentId = null; parentMenuOpen = false })
                            customCategories.filterNot { it.key == "inbox" }.forEach { category ->
                                DropdownMenuItem(text = { Text(category.name) }, onClick = { newCategoryParentId = category.key; parentMenuOpen = false })
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { createCategory() }, enabled = cleanName.isNotBlank() && !duplicate) { Text("创建") } },
            dismissButton = { TextButton(onClick = { showAddCategory = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeCategoryCard(
    category: CategoryEntity,
    depth: Int,
    supporting: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 12).dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ) {
                Box(Modifier.padding(9.dp)) { Icon(categoryIcon(category.iconKey), contentDescription = "文件夹") }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(supporting, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordListItem(
    item: UnifiedRecordItem,
    state: RecordStateEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStateChange: (RecordStateEntity) -> Unit,
) {
    var menuOpen by remember(item.key) { mutableStateOf(false) }
    val contentColor = recordContentColor(state)

    ListItem(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(containerColor = recordBackgroundColor(state)),
        headlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isTodo(state)) {
                    Checkbox(checked = state.completed, onCheckedChange = { onStateChange(state.copy(completed = it)) })
                }
                if (state.pinned) Icon(Icons.Default.PushPin, null, modifier = Modifier.size(16.dp), tint = contentColor)
                Text(recordDisplayTitle(item, state), maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor)
            }
        },
        supportingContent = {
            Column {
                Text(item.section, style = MaterialTheme.typography.labelSmall, color = contentColor)
                if (item.preview.isNotBlank()) Text(item.preview, maxLines = 2, overflow = TextOverflow.Ellipsis, color = contentColor)
            }
        },
        leadingContent = { Icon(categoryIcon(item.iconKey), null, tint = contentColor) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                RecordThumbnail(item.thumbnailFileName, size = 48.dp)
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = contentColor) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isTodo(state)) "切换为普通条目" else "切换为待办事项") },
                            leadingIcon = { Icon(if (isTodo(state)) Icons.Default.Notes else Icons.Default.CheckBox, contentDescription = null) },
                            onClick = { menuOpen = false; onStateChange(toggleTodoDisplay(state)) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.pinned) "取消置顶" else "置顶") },
                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                            onClick = { menuOpen = false; onStateChange(state.copy(pinned = !state.pinned)) },
                        )
                    }
                }
            }
        },
    )
}

package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.menter.treatmentvault.data.*
import kotlinx.coroutines.launch

private data class HomeSearchHit(
    val title: String,
    val subtitle: String,
    val section: String,
    val route: String,
    val iconKey: String,
    val updatedAt: Long,
)

private fun previewText(value: String, max: Int = 90): String {
    val clean = value.replace("\n", " ").trim()
    return if (clean.length > max) clean.take(max) + "…" else clean
}

private fun matchesHomeQuery(query: String, vararg values: Any?): Boolean =
    values.any { it?.toString()?.contains(query, ignoreCase = true) == true }

private fun flattenVisibleCategories(categories: List<CategoryEntity>): List<Pair<CategoryEntity, Int>> {
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

private fun buildHomeSearchResults(
    query: String,
    categories: List<CategoryEntity>,
    supplies: List<SupplyEntity>,
    exams: List<ExamEntity>,
    plans: List<TreatmentPlanEntity>,
    records: List<TreatmentRecordEntity>,
    symptoms: List<SymptomEntity>,
    temperatures: List<TemperatureEntity>,
    questions: List<QuestionEntity>,
    expenses: List<ExpenseEntity>,
    timeline: List<TimelineEntity>,
    customEntries: List<CustomEntryEntity>,
): List<HomeSearchHit> {
    val q = query.trim()
    if (q.isBlank()) return emptyList()
    val categoryMap = categories.associateBy { it.key }

    return buildList {
        customEntries.forEach { item ->
            val category = categoryMap[item.categoryId]
            val categoryName = category?.name ?: "笔记"
            if (matchesHomeQuery(q, categoryName, item.date, item.title, item.content, item.tags)) {
                add(
                    HomeSearchHit(
                        title = item.title.ifBlank { previewText(item.content, 42).ifBlank { "未命名笔记" } },
                        subtitle = previewText(listOf(item.date, item.content, item.tags).filter { it.isNotBlank() }.joinToString(" · ")),
                        section = categoryName,
                        route = "note/${item.id}",
                        iconKey = category?.iconKey ?: "note",
                        updatedAt = item.updatedAt,
                    )
                )
            }
        }
        categories.forEach { category ->
            if (matchesHomeQuery(q, category.name, category.smartKeywords)) {
                add(
                    HomeSearchHit(
                        title = category.name,
                        subtitle = if (category.isSystem) "打开资料分类" else "打开笔记分类",
                        section = "分类",
                        route = if (category.isSystem) category.route else "custom/${category.key}",
                        iconKey = category.iconKey,
                        updatedAt = category.updatedAt,
                    )
                )
            }
        }
        supplies.forEach { item ->
            if (matchesHomeQuery(q, item.name, item.stages, item.purpose, item.necessity, item.purchaseStatus, item.usage, item.notes)) {
                add(HomeSearchHit(item.name, previewText(listOf(item.purchaseStatus, item.purpose, item.notes).filter { it.isNotBlank() }.joinToString(" · ")), "药品用品", "supplies", "medication", item.updatedAt))
            }
        }
        exams.forEach { item ->
            if (matchesHomeQuery(q, item.name, item.date, item.hospital, item.type, item.summary, item.keyData, item.doctorExplanation, item.notes)) {
                add(HomeSearchHit(item.name, previewText(listOf(item.date, item.hospital, item.summary).filter { it.isNotBlank() }.joinToString(" · ")), "检查报告", "exams", "description", item.updatedAt))
            }
        }
        plans.forEach { item ->
            if (matchesHomeQuery(q, item.name, item.category, item.hospital, item.doctor, item.date, item.medicines, item.dose, item.cycle, item.sessions, item.startDate, item.expectedEnd, item.explanation, item.notes)) {
                add(HomeSearchHit(item.name, previewText(listOf(item.date, item.doctor, item.explanation).filter { it.isNotBlank() }.joinToString(" · ")), "治疗方案", "treatment", "medical", item.updatedAt))
            }
        }
        records.forEach { item ->
            if (matchesHomeQuery(q, item.date, item.type, item.hospital, item.doctor, item.content, item.medicines, item.sideEffects, item.condition, item.notes)) {
                add(HomeSearchHit(listOf(item.type, item.date).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "治疗记录" }, previewText(item.content.ifBlank { item.notes }), "治疗记录", "treatment", "medical", item.updatedAt))
            }
        }
        symptoms.forEach { item ->
            if (matchesHomeQuery(q, item.date, item.temperature, item.weight, item.appetite, item.nausea, item.vomiting, item.diarrhea, item.constipation, item.bowelCount, item.urinaryFrequency, item.dysuria, item.pain, item.fatigue, item.bleeding, item.other, item.notes)) {
                add(HomeSearchHit("症状记录 ${item.date}", previewText(listOf(item.constipation, item.urinaryFrequency, item.pain, item.other, item.notes).filter { it.isNotBlank() }.joinToString(" · ")), "症状记录", "symptoms", "heart", item.updatedAt))
            }
        }
        temperatures.forEach { item ->
            if (matchesHomeQuery(q, item.date, item.time, item.temperature, item.measurementSite, item.notes)) {
                add(HomeSearchHit("${item.temperature}℃", previewText(listOf(item.date, item.time, item.measurementSite, item.notes).filter { it.isNotBlank() }.joinToString(" · ")), "体温记录", "temperature", "thermostat", item.updatedAt))
            }
        }
        questions.forEach { item ->
            if (matchesHomeQuery(q, item.question, item.date, item.hospital, item.doctor, item.answer, item.status, item.followUp, item.notes)) {
                add(HomeSearchHit(item.question, previewText(listOf(item.status, item.date, item.doctor, item.answer).filter { it.isNotBlank() }.joinToString(" · ")), "医生问诊", "questions", "question", item.updatedAt))
            }
        }
        expenses.forEach { item ->
            if (matchesHomeQuery(q, item.date, item.type, item.amount, item.merchant, item.insuranceAmount, item.selfPayAmount, item.reimbursedAmount, item.notes)) {
                add(HomeSearchHit("${item.type} ¥${item.amount}", previewText(listOf(item.date, item.merchant, item.notes).filter { it.isNotBlank() }.joinToString(" · ")), "费用记录", "expenses", "payments", item.updatedAt))
            }
        }
        timeline.forEach { item ->
            if (matchesHomeQuery(q, item.date, item.type, item.title, item.content, item.sourceType)) {
                add(HomeSearchHit(item.title.ifBlank { item.type }, previewText(listOf(item.date, item.content).filter { it.isNotBlank() }.joinToString(" · ")), "总时间轴", "timeline", "timeline", item.updatedAt))
            }
        }
    }.sortedByDescending { it.updatedAt }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(go: (String) -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val categories by dao.categories().collectAsState(initial = emptyList())
    val supplies by dao.supplies().collectAsState(initial = emptyList())
    val exams by dao.exams().collectAsState(initial = emptyList())
    val plans by dao.plans().collectAsState(initial = emptyList())
    val records by dao.records().collectAsState(initial = emptyList())
    val symptoms by dao.symptoms().collectAsState(initial = emptyList())
    val temperatures by dao.temperatures().collectAsState(initial = emptyList())
    val questions by dao.questions().collectAsState(initial = emptyList())
    val expenses by dao.expenses().collectAsState(initial = emptyList())
    val timeline by dao.timeline().collectAsState(initial = emptyList())
    val customEntries by dao.allCustomEntriesFlow().collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(categories) {
        if (categories.none { it.key == "inbox" }) {
            dao.saveCategory(
                CategoryEntity(
                    key = "inbox",
                    name = "待整理",
                    iconKey = "note",
                    sortOrder = 5,
                    isSystem = false,
                    smartEnabled = false,
                    smartKeywords = "",
                )
            )
        }
    }

    val visibleCategories = remember(categories) { flattenVisibleCategories(categories) }
    val recentNotes = remember(customEntries) { customEntries.sortedByDescending { it.updatedAt }.take(6) }
    val customCounts = remember(customEntries) { customEntries.groupingBy { it.categoryId }.eachCount() }
    val searchResults = remember(
        query, categories, supplies, exams, plans, records, symptoms,
        temperatures, questions, expenses, timeline, customEntries,
    ) {
        buildHomeSearchResults(query, categories, supplies, exams, plans, records, symptoms, temperatures, questions, expenses, timeline, customEntries)
    }

    fun open(route: String) {
        if (route.isBlank()) return
        scope.launch {
            if (drawerState.isOpen) drawerState.close()
            go(route)
        }
    }

    fun countFor(category: CategoryEntity): Int = when (category.key) {
        "timeline" -> timeline.size
        "exams" -> exams.size
        "treatment" -> plans.size + records.size
        "supplies" -> supplies.size
        "symptoms" -> symptoms.size
        "temperature" -> temperatures.size
        "questions" -> questions.size
        "expenses" -> expenses.size
        else -> customCounts[category.key] ?: 0
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(18.dp))
                    Text("治疗笔记", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
                    Text("像笔记一样记，分类在后面帮你整理", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Notes, contentDescription = null) },
                        label = { Text("全部笔记") },
                        selected = false,
                        onClick = { open("notes") },
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                        label = { Text("智能录入") },
                        selected = false,
                        onClick = { open("smart_input") },
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                        label = { Text("资料树") },
                        selected = false,
                        onClick = { open("structure") },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("分类", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(visibleCategories, key = { it.first.key }) { pair ->
                            val category = pair.first
                            val depth = pair.second
                            val route = if (category.isSystem) category.route else "custom/${category.key}"
                            NavigationDrawerItem(
                                icon = { Icon(categoryIcon(category.iconKey), contentDescription = null) },
                                label = {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text((if (depth > 0) "  ".repeat(depth) else "") + category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val count = countFor(category)
                                        if (count > 0) Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                selected = false,
                                onClick = { open(route) },
                            )
                        }
                    }

                    HorizontalDivider()
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("类目管理") },
                        selected = false,
                        onClick = { open("categories") },
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Backup, contentDescription = null) },
                        label = { Text("备份与恢复") },
                        selected = false,
                        onClick = { open("settings") },
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.SystemUpdateAlt, contentDescription = null) },
                        label = { Text("检查更新") },
                        selected = false,
                        onClick = { open("update") },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("治疗笔记") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    actions = {
                        IconButton(onClick = { go("structure") }) {
                            Icon(Icons.Default.AccountTree, contentDescription = "资料树")
                        }
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
                        placeholder = { Text("搜索全部资料") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "清空")
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }

                if (query.isNotBlank()) {
                    item {
                        Text("搜索结果 · ${searchResults.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (searchResults.isEmpty()) {
                        item { Text("没有找到匹配内容", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 18.dp)) }
                    } else {
                        items(searchResults, key = { "${it.route}-${it.title}-${it.updatedAt}" }) { hit ->
                            ListItem(
                                modifier = Modifier.fillMaxWidth().clickable { open(hit.route) },
                                headlineContent = { Text(hit.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Column {
                                        Text(hit.section, style = MaterialTheme.typography.labelSmall)
                                        if (hit.subtitle.isNotBlank()) Text(hit.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                leadingContent = { Icon(categoryIcon(hit.iconKey), contentDescription = null) },
                                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                            )
                        }
                    }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { go("note/new/inbox") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.EditNote, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("新建笔记")
                            }
                            FilledTonalButton(onClick = { go("smart_input") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("智能录入")
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("最近笔记", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { go("notes") }) { Text("全部") }
                        }
                    }
                    if (recentNotes.isEmpty()) {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("还没有笔记", style = MaterialTheme.typography.titleSmall)
                                    Text("先随手记下来，不用先想该放哪里。", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        items(recentNotes, key = { it.id }) { note ->
                            val category = categories.firstOrNull { it.key == note.categoryId }
                            ListItem(
                                modifier = Modifier.fillMaxWidth().clickable { go("note/${note.id}") },
                                headlineContent = {
                                    Text(note.title.ifBlank { previewText(note.content, 42).ifBlank { "未命名笔记" } }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Column {
                                        Text(category?.name ?: "笔记", style = MaterialTheme.typography.labelSmall)
                                        if (note.content.isNotBlank()) Text(previewText(note.content), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                leadingContent = { Icon(categoryIcon(category?.iconKey ?: "note"), contentDescription = null) },
                            )
                        }
                    }
                    item {
                        Text("分类", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    items(visibleCategories, key = { it.first.key }) { pair ->
                        val category = pair.first
                        val depth = pair.second
                        val route = if (category.isSystem) category.route else "custom/${category.key}"
                        ListItem(
                            modifier = Modifier.fillMaxWidth().clickable { if (route.isNotBlank()) go(route) },
                            headlineContent = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                val count = countFor(category)
                                Text(if (count == 0) "暂无记录" else "$count 条", style = MaterialTheme.typography.bodySmall)
                            },
                            leadingContent = {
                                Row {
                                    if (depth > 0) Spacer(Modifier.width((depth * 18).dp))
                                    Icon(categoryIcon(category.iconKey), contentDescription = null)
                                }
                            },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

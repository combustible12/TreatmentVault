package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.*
import kotlinx.coroutines.launch
import kotlin.math.abs

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
        categories.forEach { category ->
            if (matchesHomeQuery(q, category.name, category.smartKeywords)) {
                add(
                    HomeSearchHit(
                        title = category.name,
                        subtitle = if (category.hidden) "已隐藏类目" else "打开类目",
                        section = "类目",
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

        customEntries.forEach { item ->
            val category = categoryMap[item.categoryId]
            val categoryName = category?.name ?: "自定义记录"
            if (matchesHomeQuery(q, categoryName, item.date, item.title, item.content, item.tags)) {
                add(
                    HomeSearchHit(
                        title = item.title.ifBlank { previewText(item.content, 40).ifBlank { "未命名记录" } },
                        subtitle = previewText(listOf(item.date, item.content, item.tags).filter { it.isNotBlank() }.joinToString(" · ")),
                        section = categoryName,
                        route = "custom/${item.categoryId}",
                        iconKey = category?.iconKey ?: "folder",
                        updatedAt = item.updatedAt,
                    )
                )
            }
        }
    }.sortedByDescending { it.updatedAt }
}

private suspend fun persistVisibleHomeOrder(
    allCategories: List<CategoryEntity>,
    orderedVisible: List<CategoryEntity>,
    save: suspend (CategoryEntity) -> Unit,
) {
    val slots = allCategories.filterNot { it.hidden }.map { it.sortOrder }.sorted()
    val now = System.currentTimeMillis()
    orderedVisible.forEachIndexed { index, category ->
        save(category.copy(sortOrder = slots.getOrElse(index) { index * 10 }, updatedAt = now + index))
    }
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
    val haptic = LocalHapticFeedback.current
    var query by rememberSaveable { mutableStateOf("") }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var localVisible by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }

    LaunchedEffect(categories, draggingKey) {
        if (draggingKey == null) localVisible = categories.filterNot { it.hidden }
    }
    val latestVisible by rememberUpdatedState(localVisible)
    val latestCategories by rememberUpdatedState(categories)

    val searchResults = buildHomeSearchResults(
        query, categories, supplies, exams, plans, records, symptoms,
        temperatures, questions, expenses, timeline, customEntries,
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("治疗资料库") },
                actions = {
                    IconButton(onClick = { go("structure") }) {
                        Icon(Icons.Default.AccountTree, contentDescription = "资料树")
                    }
                    IconButton(onClick = { go("categories") }) {
                        Icon(Icons.Default.Settings, contentDescription = "类目管理")
                    }
                    IconButton(onClick = { go("update") }) {
                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = "检查更新")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                singleLine = true,
                label = { Text("搜索全部资料") },
                placeholder = { Text("标题、正文、标签、医生、医院、药品、费用……") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清空搜索")
                        }
                    }
                },
            )

            if (query.isNotBlank()) {
                Text(
                    "全局搜索 · ${searchResults.size} 条结果",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )

                if (searchResults.isEmpty()) {
                    Text("没有找到匹配内容", modifier = Modifier.padding(14.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(searchResults) { hit ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (hit.route.isNotBlank()) go(hit.route)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(categoryIcon(hit.iconKey), contentDescription = null)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(hit.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(hit.section, style = MaterialTheme.typography.labelSmall)
                                        if (hit.subtitle.isNotBlank()) {
                                            Text(hit.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    "长按卡片即可拖动排序；右上角树状图可看全部资料结构",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(horizontal = 12.dp).weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(localVisible, key = { it.key }) { category ->
                        val route = if (category.isSystem) category.route else "custom/${category.key}"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .pointerInput(category.key) {
                                    var dx = 0f
                                    var dy = 0f
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dx = 0f
                                            dy = 0f
                                            draggingKey = category.key
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragCancel = {
                                            draggingKey = null
                                            localVisible = latestCategories.filterNot { it.hidden }
                                        },
                                        onDragEnd = {
                                            val visibleSnapshot = latestVisible
                                            val allSnapshot = latestCategories
                                            draggingKey = null
                                            scope.launch {
                                                persistVisibleHomeOrder(allSnapshot, visibleSnapshot) { dao.updateCategory(it) }
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dx += dragAmount.x
                                            dy += dragAmount.y
                                            val threshold = 54.dp.toPx()
                                            if (abs(dx) >= threshold || abs(dy) >= threshold) {
                                                val current = latestVisible.toMutableList()
                                                val from = current.indexOfFirst { it.key == category.key }
                                                val step = if (abs(dx) > abs(dy)) {
                                                    if (dx > 0f) 1 else -1
                                                } else {
                                                    if (dy > 0f) 2 else -2
                                                }
                                                val to = from + step
                                                if (from >= 0 && to in current.indices) {
                                                    val moved = current.removeAt(from)
                                                    current.add(to, moved)
                                                    localVisible = current
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                dx = 0f
                                                dy = 0f
                                            }
                                        },
                                    )
                                }
                                .clickable(enabled = draggingKey == null) {
                                    if (route.isNotBlank()) go(route)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(categoryIcon(category.iconKey), contentDescription = null)
                                Text(category.name, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

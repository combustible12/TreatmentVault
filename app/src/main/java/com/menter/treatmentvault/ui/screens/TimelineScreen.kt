package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private data class TimelineDisplayItem(
    val key: String,
    val date: String,
    val type: String,
    val title: String,
    val content: String,
    val updatedAt: Long,
    val route: String? = null,
    val source: TimelineEntity? = null,
)

private fun dateForTimeline(date: String, updatedAt: Long): String =
    date.trim().ifBlank { Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() }

private fun timelineSortTime(date: String, fallback: Long): Long = runCatching {
    LocalDate.parse(date.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrDefault(fallback)

private fun timelinePreview(vararg values: String): String = values
    .map { richBodyPlainText(it) }
    .firstOrNull { it.isNotBlank() }
    .orEmpty()
    .replace("\n", " ")
    .let { if (it.length > 110) it.take(110) + "…" else it }

@Composable
fun TimelineScreen(back: () -> Unit, go: (String) -> Unit, initialEditId: Long = 0L) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val timeline by dao.timeline().collectAsState(emptyList())
    val exams by dao.exams().collectAsState(emptyList())
    val plans by dao.plans().collectAsState(emptyList())
    val records by dao.records().collectAsState(emptyList())
    val symptoms by dao.symptoms().collectAsState(emptyList())
    val temperatures by dao.temperatures().collectAsState(emptyList())
    val questions by dao.questions().collectAsState(emptyList())
    val expenses by dao.expenses().collectAsState(emptyList())
    val notes by dao.allCustomEntriesFlow().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimelineEntity?>(null) }
    var openedInitial by remember(initialEditId) { mutableStateOf(false) }

    LaunchedEffect(initialEditId, timeline) {
        if (!openedInitial && initialEditId > 0L) {
            timeline.firstOrNull { it.id == initialEditId }?.let {
                editing = it
                editorOpen = true
                openedInitial = true
            }
        }
    }

    val items = remember(timeline, exams, plans, records, symptoms, temperatures, questions, expenses, notes) {
        buildList {
            timeline.forEach { value ->
                add(TimelineDisplayItem("timeline-${value.id}", dateForTimeline(value.date, value.updatedAt), value.type.ifBlank { "记录" }, value.title.ifBlank { "时间线记录" }, value.content, value.updatedAt, source = value))
            }
            exams.forEach { value -> add(TimelineDisplayItem("exam-${value.id}", dateForTimeline(value.date, value.updatedAt), "检查", value.name, timelinePreview(value.summary, value.notes), value.updatedAt, "exams/${value.id}")) }
            plans.forEach { value -> add(TimelineDisplayItem("plan-${value.id}", dateForTimeline(value.date.ifBlank { value.startDate }, value.updatedAt), "治疗方案", value.name, timelinePreview(value.explanation, value.notes), value.updatedAt, "treatment/plan/${value.id}")) }
            records.forEach { value -> add(TimelineDisplayItem("record-${value.id}", dateForTimeline(value.date, value.updatedAt), value.type.ifBlank { "治疗记录" }, value.type.ifBlank { "治疗记录" }, timelinePreview(value.content, value.notes), value.updatedAt, "treatment/record/${value.id}")) }
            symptoms.forEach { value ->
                val structured = listOf(value.nausea, value.vomiting, value.diarrhea, value.constipation, value.urinaryFrequency, value.dysuria, value.pain, value.fatigue, value.bleeding, value.other).firstOrNull { it.isNotBlank() }.orEmpty()
                add(TimelineDisplayItem("symptom-${value.id}", dateForTimeline(value.date, value.updatedAt), "症状", "症状记录", timelinePreview(value.notes, structured), value.updatedAt, "symptoms/${value.id}"))
            }
            temperatures.forEach { value -> add(TimelineDisplayItem("temp-${value.id}", dateForTimeline(value.date, value.updatedAt), "体温", "${value.temperature}℃ ${value.time}", timelinePreview(value.notes), value.updatedAt, "temperature/${value.id}")) }
            questions.forEach { value -> add(TimelineDisplayItem("question-${value.id}", dateForTimeline(value.date, value.updatedAt), "问诊", value.question, timelinePreview(value.answer, value.notes), value.updatedAt, "questions/${value.id}")) }
            expenses.forEach { value -> add(TimelineDisplayItem("expense-${value.id}", dateForTimeline(value.date, value.updatedAt), "费用", "${value.type} ¥${value.amount}", timelinePreview(value.notes, value.merchant), value.updatedAt, "expenses/${value.id}")) }
            notes.forEach { value -> add(TimelineDisplayItem("note-${value.id}", dateForTimeline(value.date, value.updatedAt), "笔记", value.title.ifBlank { "未命名笔记" }, value.content, value.updatedAt, "note/${value.id}")) }
        }.sortedWith(compareByDescending<TimelineDisplayItem> { timelineSortTime(it.date, it.updatedAt) }.thenByDescending { it.updatedAt })
    }

    Page("总时间轴", back) {
        Text("其他资料会自动汇总到这里；也可以直接按笔记方式记录一件事。", style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("记录一件事")
        }

        if (items.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("时间线还没有内容", style = MaterialTheme.typography.titleMedium)
                    Text("点上面的“记录一件事”就能开始。", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                items(items, key = { it.key }) { value ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(value.date, modifier = Modifier.width(94.dp), style = MaterialTheme.typography.bodySmall)
                        Card(
                            modifier = Modifier.weight(1f).clickable {
                                if (value.source != null) { editing = value.source; editorOpen = true }
                                else if (!value.route.isNullOrBlank()) go(value.route)
                            }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(value.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(value.type, style = MaterialTheme.typography.labelMedium)
                                    val plain = richBodyPlainText(value.content)
                                    if (plain.isNotBlank()) Text(plain, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                if (value.source == null && !value.route.isNullOrBlank()) Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        val initial = editing
        var title by remember(initial?.id, editorOpen) { mutableStateOf(initial?.title.orEmpty()) }
        var body by remember(initial?.id, editorOpen) { mutableStateOf(initial?.content.orEmpty()) }
        var date by remember(initial?.id, editorOpen) { mutableStateOf(initial?.date?.ifBlank { LocalDate.now().toString() } ?: LocalDate.now().toString()) }
        var type by remember(initial?.id, editorOpen) { mutableStateOf(initial?.type.orEmpty().ifBlank { "记录" }) }
        var pendingFiles by remember(initial?.id, editorOpen) { mutableStateOf<List<String>>(emptyList()) }

        FullScreenEditorDialog(
            title = if (initial == null) "新增时间线记录" else "编辑时间线记录",
            onDismiss = { editorOpen = false },
            canSave = title.isNotBlank() || richBodyPlainText(body).isNotBlank() || pendingFiles.isNotEmpty(),
            onSave = {
                if (title.isBlank() && richBodyPlainText(body).isBlank() && pendingFiles.isEmpty()) return@FullScreenEditorDialog
                scope.launch {
                    val value = TimelineEntity(
                        id = initial?.id ?: 0L,
                        date = date.trim(),
                        type = type.trim().ifBlank { "记录" },
                        title = title.trim().ifBlank { "时间线记录" },
                        content = body,
                        sourceType = initial?.sourceType.orEmpty().ifBlank { "manual" },
                        sourceId = initial?.sourceId,
                        important = initial?.important ?: false,
                        updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveTimeline(value) else { dao.updateTimeline(value); value.id }
                    savePendingAttachments(dao, "timeline", id, pendingFiles)
                    editorOpen = false
                }
            },
        ) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Field(title, { title = it }, "标题") }
                item {
                    RichNoteBodyEditor(
                        dao = dao, ownerType = "timeline", ownerId = initial?.id ?: 0L,
                        value = body, onValueChange = { body = it },
                        pendingFiles = pendingFiles, onPendingFilesChange = { pendingFiles = it },
                    )
                }
                item { Field(date, { date = it }, "日期") }
                item { Field(type, { type = it }, "类型（选填）") }
            }
        }
    }
}
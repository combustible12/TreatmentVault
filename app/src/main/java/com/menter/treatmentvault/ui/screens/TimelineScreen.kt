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

private fun timelinePreview(vararg values: String): String =
    values.firstOrNull { it.isNotBlank() }.orEmpty().replace("\n", " ").let { if (it.length > 110) it.take(110) + "…" else it }

@Composable
fun TimelineScreen(back: () -> Unit, go: (String) -> Unit) {
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

    val items = remember(timeline, exams, plans, records, symptoms, temperatures, questions, expenses, notes) {
        buildList {
            timeline.forEach { value ->
                add(
                    TimelineDisplayItem(
                        key = "timeline-${value.id}",
                        date = dateForTimeline(value.date, value.updatedAt),
                        type = value.type.ifBlank { "记录" },
                        title = value.title.ifBlank { "时间线记录" },
                        content = value.content,
                        updatedAt = value.updatedAt,
                        source = value,
                    )
                )
            }
            exams.forEach { value ->
                add(TimelineDisplayItem("exam-${value.id}", dateForTimeline(value.date, value.updatedAt), "检查", value.name, timelinePreview(value.summary, value.notes), value.updatedAt, "exams"))
            }
            plans.forEach { value ->
                add(TimelineDisplayItem("plan-${value.id}", dateForTimeline(value.date.ifBlank { value.startDate }, value.updatedAt), "治疗方案", value.name, timelinePreview(value.explanation, value.notes), value.updatedAt, "treatment"))
            }
            records.forEach { value ->
                add(TimelineDisplayItem("record-${value.id}", dateForTimeline(value.date, value.updatedAt), value.type.ifBlank { "治疗记录" }, value.type.ifBlank { "治疗记录" }, timelinePreview(value.content, value.notes), value.updatedAt, "treatment"))
            }
            symptoms.forEach { value ->
                val content = listOf(value.nausea, value.vomiting, value.diarrhea, value.constipation, value.urinaryFrequency, value.dysuria, value.pain, value.fatigue, value.bleeding, value.other, value.notes).firstOrNull { it.isNotBlank() }.orEmpty()
                add(TimelineDisplayItem("symptom-${value.id}", dateForTimeline(value.date, value.updatedAt), "症状", "症状记录", content, value.updatedAt, "symptoms"))
            }
            temperatures.forEach { value ->
                add(TimelineDisplayItem("temp-${value.id}", dateForTimeline(value.date, value.updatedAt), "体温", "${value.temperature}℃ ${value.time}", value.notes, value.updatedAt, "temperature"))
            }
            questions.forEach { value ->
                add(TimelineDisplayItem("question-${value.id}", dateForTimeline(value.date, value.updatedAt), "问诊", value.question, timelinePreview(value.answer, value.notes), value.updatedAt, "questions"))
            }
            expenses.forEach { value ->
                add(TimelineDisplayItem("expense-${value.id}", dateForTimeline(value.date, value.updatedAt), "费用", "${value.type} ¥${value.amount}", timelinePreview(value.merchant, value.notes), value.updatedAt, "expenses"))
            }
            notes.forEach { value ->
                add(TimelineDisplayItem("note-${value.id}", dateForTimeline(value.date, value.updatedAt), "笔记", value.title.ifBlank { "未命名笔记" }, value.content, value.updatedAt, "note/${value.id}"))
            }
        }.sortedWith(compareByDescending<TimelineDisplayItem> { timelineSortTime(it.date, it.updatedAt) }.thenByDescending { it.updatedAt })
    }

    Page("总时间轴", back) {
        Text("检查、治疗、症状、体温、问诊、费用和普通笔记会自动汇总到这里；也可以直接记一件事。", style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(
            onClick = { editing = null; editorOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("记录一件事")
        }

        if (items.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("时间线还没有内容", style = MaterialTheme.typography.titleMedium)
                    Text("你可以点上面的“记录一件事”，以后其他资料也会自动出现在这里。", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp, bottom = 24.dp)) {
                items(items, key = { it.key }) { value ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(value.date, modifier = Modifier.width(94.dp), style = MaterialTheme.typography.bodySmall)
                        Card(
                            modifier = Modifier.weight(1f).clickable {
                                if (value.source != null) {
                                    editing = value.source
                                    editorOpen = true
                                } else if (!value.route.isNullOrBlank()) {
                                    go(value.route)
                                }
                            }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(value.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(value.type, style = MaterialTheme.typography.labelMedium)
                                    if (value.content.isNotBlank()) Text(value.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
            canSave = title.isNotBlank(),
            onSave = {
                if (title.isBlank()) return@FullScreenEditorDialog
                scope.launch {
                    val value = TimelineEntity(
                        id = initial?.id ?: 0L,
                        date = date.trim(),
                        type = type.trim().ifBlank { "记录" },
                        title = title.trim(),
                        content = body.trim(),
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
            Field(title, { title = it }, "标题")
            NoteBodyField(body, { body = it }, "正文")
            ImageAttachmentSection(dao, "timeline", initial?.id ?: 0L, pendingFiles) { pendingFiles = it }
            Field(date, { date = it }, "日期")
            Field(type, { type = it }, "类型（选填）")
        }
    }
}

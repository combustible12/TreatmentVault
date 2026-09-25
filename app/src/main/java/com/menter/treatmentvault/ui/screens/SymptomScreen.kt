package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.SymptomEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SymptomScreen(back: () -> Unit, initialEditId: Long = 0L) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = (context.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.symptoms().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SymptomEntity?>(null) }
    var openedInitial by remember(initialEditId) { mutableStateOf(false) }

    LaunchedEffect(initialEditId, list) {
        if (!openedInitial && initialEditId > 0L) {
            list.firstOrNull { it.id.toLong() == initialEditId }?.let {
                editing = it
                editorOpen = true
                openedInitial = true
            }
        }
    }

    Page("症状记录", back) {
        Text("先像记笔记一样写发生了什么，需要时再展开体温、疼痛、排便等细项。", style = MaterialTheme.typography.bodySmall)
        FastScrollFastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(value.date.ifBlank { "症状记录" }, style = MaterialTheme.typography.titleMedium)
                        val body = richBodyPlainText(value.notes)
                        val structured = listOf(value.nausea, value.vomiting, value.diarrhea, value.constipation, value.urinaryFrequency, value.dysuria, value.pain, value.fatigue, value.bleeding, value.other).firstOrNull { it.isNotBlank() }.orEmpty()
                        val preview = body.ifBlank { structured }
                        if (preview.isNotBlank()) Text(preview, maxLines = 3)
                    }
                }
            }
        }
        Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("记录症状") }
    }

    if (editorOpen) {
        val initial = editing
        val today = remember(editorOpen) { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        var date by remember(initial?.id, editorOpen) { mutableStateOf(initial?.date?.ifBlank { today } ?: today) }
        var temp by remember(initial?.id, editorOpen) { mutableStateOf(initial?.temperature?.toString().orEmpty()) }
        var weight by remember(initial?.id, editorOpen) { mutableStateOf(initial?.weight?.toString().orEmpty()) }
        var appetite by remember(initial?.id, editorOpen) { mutableStateOf(initial?.appetite.orEmpty()) }
        var nausea by remember(initial?.id, editorOpen) { mutableStateOf(initial?.nausea.orEmpty()) }
        var vomiting by remember(initial?.id, editorOpen) { mutableStateOf(initial?.vomiting.orEmpty()) }
        var diarrhea by remember(initial?.id, editorOpen) { mutableStateOf(initial?.diarrhea.orEmpty()) }
        var constipation by remember(initial?.id, editorOpen) { mutableStateOf(initial?.constipation.orEmpty()) }
        var urinaryFrequency by remember(initial?.id, editorOpen) { mutableStateOf(initial?.urinaryFrequency.orEmpty()) }
        var dysuria by remember(initial?.id, editorOpen) { mutableStateOf(initial?.dysuria.orEmpty()) }
        var pain by remember(initial?.id, editorOpen) { mutableStateOf(initial?.pain.orEmpty()) }
        var fatigue by remember(initial?.id, editorOpen) { mutableStateOf(initial?.fatigue.orEmpty()) }
        var bleeding by remember(initial?.id, editorOpen) { mutableStateOf(initial?.bleeding.orEmpty()) }
        var other by remember(initial?.id, editorOpen) { mutableStateOf(initial?.other.orEmpty()) }
        var notes by remember(initial?.id, editorOpen) { mutableStateOf(initial?.notes.orEmpty()) }
        var pendingFiles by remember(initial?.id, editorOpen) { mutableStateOf<List<String>>(emptyList()) }
        var removedFiles by remember(initial?.id, editorOpen) { mutableStateOf<Set<String>>(emptySet()) }
        var detailsOpen by remember(initial?.id, editorOpen) { mutableStateOf(false) }

        FullScreenEditorDialog(
            title = if (initial == null) "记录症状" else "编辑症状",
            onDismiss = { editorOpen = false },
            canSave = richBodyHasContent(notes) || pendingFiles.isNotEmpty() || date.isNotBlank(),
            hasPersistedRecord = initial != null,
            onSave = {
                if (initial == null && !richBodyHasContent(notes) && pendingFiles.isEmpty() && date.isBlank()) {
                    editorOpen = false
                    return@FullScreenEditorDialog
                }
                scope.launch {
                    val value = SymptomEntity(
                        id = initial?.id ?: 0,
                        date = date.trim(),
                        temperature = temp.toDoubleOrNull(),
                        weight = weight.toDoubleOrNull(),
                        appetite = appetite.trim(),
                        nausea = nausea.trim(),
                        vomiting = vomiting.trim(),
                        diarrhea = diarrhea.trim(),
                        constipation = constipation.trim(),
                        bowelCount = initial?.bowelCount,
                        urinaryFrequency = urinaryFrequency.trim(),
                        dysuria = dysuria.trim(),
                        pain = pain.trim(),
                        fatigue = fatigue.trim(),
                        bleeding = bleeding.trim(),
                        other = other.trim(),
                        notes = notes,
                        updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveSymptom(value) else { dao.updateSymptom(value); value.id }
                    reconcileRichAttachments(context, dao, "symptom", id, notes, removedFiles)
                    pendingFiles = emptyList()
                    removedFiles = emptySet()
                    editorOpen = false
                }
            },
        ) {
            FastScrollFastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    RichNoteBodyEditor(
                        dao = dao,
                        ownerType = "symptom",
                        ownerId = initial?.id ?: 0L,
                        value = notes,
                        onValueChange = { notes = it },
                        pendingFiles = pendingFiles,
                        onPendingFilesChange = { pendingFiles = it },
                        removedFiles = removedFiles,
                        onRemovedFilesChange = { removedFiles = it },
                        sessionKey = "symptom-${initial?.id ?: "new"}",
                    )
                }
                item {
                    TextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (detailsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (detailsOpen) "收起症状细项" else "更多症状信息（选填）")
                    }
                }
                if (detailsOpen) {
                    item { Field(date, { date = it }, "日期") }
                    item { Field(temp, { temp = it }, "体温") }
                    item { Field(weight, { weight = it }, "体重") }
                    item { Field(appetite, { appetite = it }, "食欲") }
                    item { Field(nausea, { nausea = it }, "恶心") }
                    item { Field(vomiting, { vomiting = it }, "呕吐") }
                    item { Field(diarrhea, { diarrhea = it }, "腹泻") }
                    item { Field(constipation, { constipation = it }, "便秘") }
                    item { Field(urinaryFrequency, { urinaryFrequency = it }, "尿频/尿急") }
                    item { Field(dysuria, { dysuria = it }, "排尿不适") }
                    item { Field(pain, { pain = it }, "疼痛") }
                    item { Field(fatigue, { fatigue = it }, "乏力") }
                    item { Field(bleeding, { bleeding = it }, "出血") }
                    item { Field(other, { other = it }, "其他") }
                }
            }
        }
    }
}

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
import com.menter.treatmentvault.data.QuestionEntity
import kotlinx.coroutines.launch

@Composable
fun QuestionScreen(back: () -> Unit, initialEditId: Long = 0L) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = (context.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.questions().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<QuestionEntity?>(null) }
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

    Page("医生问诊", back) {
        Text("问题当标题，下面直接记医生回答、照片和补充内容。医院、医生、状态等放到更多信息。", style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(value.question, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(value.status, value.date, value.doctor).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        val body = richBodyPlainText(value.answer)
                        if (body.isNotBlank()) Text(body, maxLines = 3)
                    }
                }
            }
        }
        Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("新增待问") }
    }

    if (editorOpen) {
        val initial = editing
        var question by remember(initial?.id, editorOpen) { mutableStateOf(initial?.question.orEmpty()) }
        var date by remember(initial?.id, editorOpen) { mutableStateOf(initial?.date.orEmpty()) }
        var hospital by remember(initial?.id, editorOpen) { mutableStateOf(initial?.hospital.orEmpty()) }
        var doctor by remember(initial?.id, editorOpen) { mutableStateOf(initial?.doctor.orEmpty()) }
        var answer by remember(initial?.id, editorOpen) { mutableStateOf(initial?.answer.orEmpty()) }
        var status by remember(initial?.id, editorOpen) { mutableStateOf(initial?.status ?: "待问") }
        var followUp by remember(initial?.id, editorOpen) { mutableStateOf(initial?.followUp.orEmpty()) }
        var notes by remember(initial?.id, editorOpen) { mutableStateOf(initial?.notes.orEmpty()) }
        var pendingFiles by remember(initial?.id, editorOpen) { mutableStateOf<List<String>>(emptyList()) }
        var removedFiles by remember(initial?.id, editorOpen) { mutableStateOf<Set<String>>(emptySet()) }
        var detailsOpen by remember(initial?.id, editorOpen) { mutableStateOf(false) }

        FullScreenEditorDialog(
            title = if (initial == null) "新增问题" else "编辑问诊",
            onDismiss = { editorOpen = false },
            canSave = question.isNotBlank() || richBodyHasContent(answer) || pendingFiles.isNotEmpty(),
            hasPersistedRecord = initial != null,
            onSave = {
                if (initial == null && question.isBlank() && !richBodyHasContent(answer) && pendingFiles.isEmpty()) {
                    editorOpen = false
                    return@FullScreenEditorDialog
                }
                scope.launch {
                    val value = QuestionEntity(
                        id = initial?.id ?: 0,
                        question = question.trim().ifBlank { "未命名问题" },
                        date = date.trim(),
                        hospital = hospital.trim(),
                        doctor = doctor.trim(),
                        answer = answer,
                        status = status.trim().ifBlank { "待问" },
                        followUp = followUp.trim(),
                        notes = notes.trim(),
                        updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveQuestion(value) else { dao.updateQuestion(value); value.id }
                    reconcileRichAttachments(context, dao, "question", id, answer, removedFiles)
                    pendingFiles = emptyList()
                    removedFiles = emptySet()
                    editorOpen = false
                }
            },
        ) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Field(question, { question = it }, "问题/标题") }
                item {
                    RichNoteBodyEditor(
                        dao = dao,
                        ownerType = "question",
                        ownerId = initial?.id ?: 0L,
                        value = answer,
                        onValueChange = { answer = it },
                        pendingFiles = pendingFiles,
                        onPendingFilesChange = { pendingFiles = it },
                        removedFiles = removedFiles,
                        onRemovedFilesChange = { removedFiles = it },
                        label = "回答与记录",
                        sessionKey = "question-${initial?.id ?: "new"}",
                    )
                }
                item {
                    TextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (detailsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (detailsOpen) "收起问诊信息" else "更多问诊信息（选填）")
                    }
                }
                if (detailsOpen) {
                    item { Field(date, { date = it }, "日期") }
                    item { Field(hospital, { hospital = it }, "医院") }
                    item { Field(doctor, { doctor = it }, "医生") }
                    item { Field(status, { status = it }, "状态（待问/已问）") }
                    item { Field(followUp, { followUp = it }, "后续要问") }
                    item { Field(notes, { notes = it }, "补充备注") }
                }
            }
        }
    }
}

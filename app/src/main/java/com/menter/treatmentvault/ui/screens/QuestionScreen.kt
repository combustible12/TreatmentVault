package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.QuestionEntity
import kotlinx.coroutines.launch

@Composable
fun QuestionScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.questions().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<QuestionEntity?>(null) }

    Page("医生问诊", back) {
        Text("点任意记录即可继续编辑和添加图片。", style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(value.question, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(value.status, value.date, value.doctor).filter { it.isNotBlank() }.joinToString(" · "))
                        if (value.answer.isNotBlank()) Text("答：${value.answer}")
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

        FullScreenEditorDialog(
            title = if (initial == null) "新增问题" else "编辑问诊",
            onDismiss = { editorOpen = false },
            canSave = question.isNotBlank(),
            onSave = {
                if (question.isBlank()) return@FullScreenEditorDialog
                scope.launch {
                    val value = QuestionEntity(
                        id = initial?.id ?: 0, question = question.trim(), date = date.trim(), hospital = hospital.trim(), doctor = doctor.trim(),
                        answer = answer.trim(), status = status.trim().ifBlank { "待问" }, followUp = followUp.trim(), notes = notes.trim(), updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveQuestion(value) else { dao.updateQuestion(value); value.id }
                    savePendingAttachments(dao, "question", id, pendingFiles)
                    editorOpen = false
                }
            },
        ) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Field(question, { question = it }, "问题") }
                item { Field(date, { date = it }, "日期") }
                item { Field(hospital, { hospital = it }, "医院") }
                item { Field(doctor, { doctor = it }, "医生") }
                item { Field(status, { status = it }, "状态（待问/已问）") }
                item { Field(answer, { answer = it }, "医生回答") }
                item { Field(followUp, { followUp = it }, "后续要问") }
                item { Field(notes, { notes = it }, "备注") }
                item { ImageAttachmentSection(dao, "question", initial?.id ?: 0L, pendingFiles) { pendingFiles = it } }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

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
import com.menter.treatmentvault.data.ExamEntity
import kotlinx.coroutines.launch

@Composable
fun ExamScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.exams().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExamEntity?>(null) }

    Page("检查报告", back) {
        Text("点任意记录即可继续编辑和添加图片。", style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(value.name, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(value.date, value.type, value.hospital).filter { it.isNotBlank() }.joinToString(" · "))
                        if (value.summary.isNotBlank()) Text(value.summary)
                    }
                }
            }
        }
        Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("新增检查") }
    }

    if (editorOpen) {
        val initial = editing
        var name by remember(initial?.id, editorOpen) { mutableStateOf(initial?.name.orEmpty()) }
        var date by remember(initial?.id, editorOpen) { mutableStateOf(initial?.date.orEmpty()) }
        var hospital by remember(initial?.id, editorOpen) { mutableStateOf(initial?.hospital.orEmpty()) }
        var type by remember(initial?.id, editorOpen) { mutableStateOf(initial?.type ?: "其他") }
        var summary by remember(initial?.id, editorOpen) { mutableStateOf(initial?.summary.orEmpty()) }
        var keyData by remember(initial?.id, editorOpen) { mutableStateOf(initial?.keyData.orEmpty()) }
        var doctorExplanation by remember(initial?.id, editorOpen) { mutableStateOf(initial?.doctorExplanation.orEmpty()) }
        var notes by remember(initial?.id, editorOpen) { mutableStateOf(initial?.notes.orEmpty()) }
        var pendingFiles by remember(initial?.id, editorOpen) { mutableStateOf<List<String>>(emptyList()) }

        FullScreenEditorDialog(
            title = if (initial == null) "新增检查" else "编辑检查",
            onDismiss = { editorOpen = false },
            canSave = name.isNotBlank(),
            onSave = {
                if (name.isBlank()) return@FullScreenEditorDialog
                scope.launch {
                    val value = ExamEntity(
                        id = initial?.id ?: 0,
                        name = name.trim(), date = date.trim(), hospital = hospital.trim(), type = type.trim().ifBlank { "其他" },
                        summary = summary.trim(), keyData = keyData.trim(), doctorExplanation = doctorExplanation.trim(), notes = notes.trim(),
                        attachmentPath = initial?.attachmentPath.orEmpty(), updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveExam(value) else { dao.updateExam(value); value.id }
                    savePendingAttachments(dao, "exam", id, pendingFiles)
                    editorOpen = false
                }
            },
        ) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Field(name, { name = it }, "检查名称") }
                item { Field(date, { date = it }, "日期 YYYY-MM-DD") }
                item { Field(hospital, { hospital = it }, "医院") }
                item { Field(type, { type = it }, "类型") }
                item { Field(summary, { summary = it }, "结果摘要") }
                item { Field(keyData, { keyData = it }, "关键数据") }
                item { Field(doctorExplanation, { doctorExplanation = it }, "医生解释") }
                item { Field(notes, { notes = it }, "备注") }
                item { ImageAttachmentSection(dao, "exam", initial?.id ?: 0L, pendingFiles) { pendingFiles = it } }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

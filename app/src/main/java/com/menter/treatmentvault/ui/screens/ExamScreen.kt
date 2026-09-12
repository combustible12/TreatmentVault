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
        Text("这里按笔记来用：标题、正文、图片是主要内容；其他检查信息都只是选填。", style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(value.name, style = MaterialTheme.typography.titleMedium)
                        val meta = listOf(value.date, value.type, value.hospital).filter { it.isNotBlank() && it != "其他" }.joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)
                        val body = value.summary.ifBlank { value.notes }
                        if (body.isNotBlank()) Text(body, maxLines = 3)
                    }
                }
            }
        }
        Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("新增检查笔记") }
    }

    if (editorOpen) {
        val initial = editing
        var name by remember(initial?.id, editorOpen) { mutableStateOf(initial?.name.orEmpty()) }
        var date by remember(initial?.id, editorOpen) { mutableStateOf(initial?.date.orEmpty()) }
        var hospital by remember(initial?.id, editorOpen) { mutableStateOf(initial?.hospital.orEmpty()) }
        var type by remember(initial?.id, editorOpen) { mutableStateOf(initial?.type ?: "其他") }
        var body by remember(initial?.id, editorOpen) { mutableStateOf(initial?.summary.orEmpty()) }
        var keyData by remember(initial?.id, editorOpen) { mutableStateOf(initial?.keyData.orEmpty()) }
        var doctorExplanation by remember(initial?.id, editorOpen) { mutableStateOf(initial?.doctorExplanation.orEmpty()) }
        var notes by remember(initial?.id, editorOpen) { mutableStateOf(initial?.notes.orEmpty()) }
        var pendingFiles by remember(initial?.id, editorOpen) { mutableStateOf<List<String>>(emptyList()) }
        var detailsOpen by remember(initial?.id, editorOpen) {
            mutableStateOf(
                !initial?.date.isNullOrBlank() || !initial?.hospital.isNullOrBlank() ||
                    (initial?.type.orEmpty().isNotBlank() && initial?.type != "其他") ||
                    !initial?.keyData.isNullOrBlank() || !initial?.doctorExplanation.isNullOrBlank() || !initial?.notes.isNullOrBlank()
            )
        }

        FullScreenEditorDialog(
            title = if (initial == null) "新增检查笔记" else "编辑检查笔记",
            onDismiss = { editorOpen = false },
            canSave = name.isNotBlank(),
            onSave = {
                if (name.isBlank()) return@FullScreenEditorDialog
                scope.launch {
                    val value = ExamEntity(
                        id = initial?.id ?: 0,
                        name = name.trim(),
                        date = date.trim(),
                        hospital = hospital.trim(),
                        type = type.trim().ifBlank { "其他" },
                        summary = body.trim(),
                        keyData = keyData.trim(),
                        doctorExplanation = doctorExplanation.trim(),
                        notes = notes.trim(),
                        attachmentPath = initial?.attachmentPath.orEmpty(),
                        updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveExam(value) else { dao.updateExam(value); value.id }
                    savePendingAttachments(dao, "exam", id, pendingFiles)
                    editorOpen = false
                }
            },
        ) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Field(name, { name = it }, "标题") }
                item { NoteBodyField(body, { body = it }, "正文") }
                item { ImageAttachmentSection(dao, "exam", initial?.id ?: 0L, pendingFiles) { pendingFiles = it } }
                item {
                    TextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (detailsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (detailsOpen) "收起检查信息" else "更多检查信息（全部选填）")
                    }
                }
                if (detailsOpen) {
                    item { Field(date, { date = it }, "日期（选填）") }
                    item { Field(hospital, { hospital = it }, "医院（选填）") }
                    item { Field(type, { type = it }, "类型（选填）") }
                    item { Field(keyData, { keyData = it }, "关键数据（选填）") }
                    item { Field(doctorExplanation, { doctorExplanation = it }, "医生解释（选填）") }
                    item { Field(notes, { notes = it }, "补充备注（选填）") }
                }
            }
        }
    }
}

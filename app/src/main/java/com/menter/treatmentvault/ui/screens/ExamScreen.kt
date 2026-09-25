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
fun ExamScreen(back: () -> Unit, initialEditId: Long = 0L) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = (context.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.exams().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExamEntity?>(null) }
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

    if (initialEditId <= 0L) {
        Page("检查报告", back) {
            Text("按笔记来记：标题 + 可穿插的文字/图片。日期、医院、类型等都只是选填。", style = MaterialTheme.typography.bodySmall)
            FastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { value ->
                    Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(value.name, style = MaterialTheme.typography.titleMedium)
                            val meta = listOf(value.date, value.type, value.hospital).filter { it.isNotBlank() && it != "其他" }.joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)
                            val body = richBodyPlainText(value.summary.ifBlank { value.notes })
                            if (body.isNotBlank()) Text(body, maxLines = 3)
                        }
                    }
                }
            }
            Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("新增检查笔记") }
        }
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
        var removedFiles by remember(initial?.id, editorOpen) { mutableStateOf<Set<String>>(emptySet()) }
        var detailsOpen by remember(initial?.id, editorOpen) {
            mutableStateOf(
                !initial?.date.isNullOrBlank() || !initial?.hospital.isNullOrBlank() ||
                    (initial?.type.orEmpty().isNotBlank() && initial?.type != "其他") ||
                    !initial?.keyData.isNullOrBlank() || !initial?.doctorExplanation.isNullOrBlank() || !initial?.notes.isNullOrBlank()
            )
        }

        FullScreenEditorDialog(
            title = if (initial == null) "新增检查笔记" else "编辑检查笔记",
            onDismiss = {
                editorOpen = false
                if (initialEditId > 0L) back()
            },
            canSave = name.isNotBlank() || richBodyHasContent(body) || pendingFiles.isNotEmpty(),
            hasPersistedRecord = initial != null,
            onSave = {
                if (initial == null && name.isBlank() && !richBodyHasContent(body) && pendingFiles.isEmpty()) {
                    editorOpen = false
                    return@FullScreenEditorDialog
                }
                scope.launch {
                    val value = ExamEntity(
                        id = initial?.id ?: 0,
                        name = name.trim().ifBlank { "未命名检查" },
                        date = date.trim(),
                        hospital = hospital.trim(),
                        type = type.trim().ifBlank { "其他" },
                        summary = body,
                        keyData = keyData.trim(),
                        doctorExplanation = doctorExplanation.trim(),
                        notes = notes.trim(),
                        attachmentPath = initial?.attachmentPath.orEmpty(),
                        updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveExam(value) else { dao.updateExam(value); value.id }
                    reconcileRichAttachments(context, dao, "exam", id, body, removedFiles)
                    pendingFiles = emptyList()
                    removedFiles = emptySet()
                    editorOpen = false
                    if (initialEditId > 0L) back()
                }
            },
        ) {
            FastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Field(name, { name = it }, "标题") }
                item {
                    RichNoteBodyEditor(
                        dao = dao,
                        ownerType = "exam",
                        ownerId = initial?.id ?: 0L,
                        value = body,
                        onValueChange = { body = it },
                        pendingFiles = pendingFiles,
                        onPendingFilesChange = { pendingFiles = it },
                        removedFiles = removedFiles,
                        onRemovedFilesChange = { removedFiles = it },
                        sessionKey = "exam-${initial?.id ?: "new"}",
                    )
                }
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

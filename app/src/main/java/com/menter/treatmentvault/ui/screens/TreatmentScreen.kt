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
import com.menter.treatmentvault.data.TreatmentPlanEntity
import com.menter.treatmentvault.data.TreatmentRecordEntity
import kotlinx.coroutines.launch

@Composable
fun TreatmentScreen(back: () -> Unit, initialPlanId: Long = 0L, initialRecordId: Long = 0L) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = (context.applicationContext as TreatmentVaultApplication).database.dao()
    val plans by dao.plans().collectAsState(emptyList())
    val records by dao.records().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(if (initialRecordId > 0L) 1 else 0) }
    var editingPlan by remember { mutableStateOf<TreatmentPlanEntity?>(null) }
    var editingRecord by remember { mutableStateOf<TreatmentRecordEntity?>(null) }
    var planEditorOpen by remember { mutableStateOf(false) }
    var recordEditorOpen by remember { mutableStateOf(false) }
    var openedInitial by remember(initialPlanId, initialRecordId) { mutableStateOf(false) }

    LaunchedEffect(initialPlanId, initialRecordId, plans, records) {
        if (!openedInitial) {
            when {
                initialPlanId > 0L -> plans.firstOrNull { it.id.toLong() == initialPlanId }?.let {
                    tab = 0; editingPlan = it; planEditorOpen = true; openedInitial = true
                }
                initialRecordId > 0L -> records.firstOrNull { it.id.toLong() == initialRecordId }?.let {
                    tab = 1; editingRecord = it; recordEditorOpen = true; openedInitial = true
                }
            }
        }
    }

    Page("治疗方案与记录", back) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("方案") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("治疗记录") })
        }
        Text("正文和图片按笔记方式记录；药物、剂量、医生等细节需要时再展开填。", style = MaterialTheme.typography.bodySmall)
        FastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 0) {
                items(plans, key = { it.id }) { value ->
                    Card(Modifier.fillMaxWidth().clickable { editingPlan = value; planEditorOpen = true }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(value.name, style = MaterialTheme.typography.titleMedium)
                            Text(listOf(value.category, value.date, value.doctor).filter { it.isNotBlank() }.joinToString(" · "))
                            val body = richBodyPlainText(value.explanation)
                            if (body.isNotBlank()) Text(body, maxLines = 3)
                        }
                    }
                }
            } else {
                items(records, key = { it.id }) { value ->
                    Card(Modifier.fillMaxWidth().clickable { editingRecord = value; recordEditorOpen = true }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(listOf(value.date, value.type).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "治疗记录" }, style = MaterialTheme.typography.titleMedium)
                            val body = richBodyPlainText(value.content)
                            if (body.isNotBlank()) Text(body, maxLines = 3)
                            if (value.sideEffects.isNotBlank()) Text("反应：${value.sideEffects}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Button(onClick = { if (tab == 0) { editingPlan = null; planEditorOpen = true } else { editingRecord = null; recordEditorOpen = true } }, modifier = Modifier.fillMaxWidth()) {
            Text(if (tab == 0) "新增治疗方案" else "新增治疗记录")
        }
    }

    if (planEditorOpen) {
        val initial = editingPlan
        var name by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.name.orEmpty()) }
        var category by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.category ?: "其他") }
        var hospital by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.hospital.orEmpty()) }
        var doctor by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.doctor.orEmpty()) }
        var date by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.date.orEmpty()) }
        var medicines by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.medicines.orEmpty()) }
        var dose by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.dose.orEmpty()) }
        var cycle by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.cycle.orEmpty()) }
        var sessions by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.sessions.orEmpty()) }
        var explanation by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.explanation.orEmpty()) }
        var notes by remember(initial?.id, planEditorOpen) { mutableStateOf(initial?.notes.orEmpty()) }
        var pendingFiles by remember(initial?.id, planEditorOpen) { mutableStateOf<List<String>>(emptyList()) }
        var removedFiles by remember(initial?.id, planEditorOpen) { mutableStateOf<Set<String>>(emptySet()) }
        var detailsOpen by remember(initial?.id, planEditorOpen) { mutableStateOf(false) }

        FullScreenEditorDialog(
            title = if (initial == null) "新增治疗方案" else "编辑治疗方案",
            onDismiss = { planEditorOpen = false },
            canSave = name.isNotBlank() || richBodyHasContent(explanation) || pendingFiles.isNotEmpty(),
            hasPersistedRecord = initial != null,
            onSave = {
                if (initial == null && name.isBlank() && !richBodyHasContent(explanation) && pendingFiles.isEmpty()) { planEditorOpen = false; return@FullScreenEditorDialog }
                scope.launch {
                    val value = TreatmentPlanEntity(
                        id = initial?.id ?: 0,
                        name = name.trim().ifBlank { "未命名治疗方案" },
                        category = category.trim().ifBlank { "其他" },
                        hospital = hospital.trim(), doctor = doctor.trim(), date = date.trim(), medicines = medicines.trim(),
                        dose = dose.trim(), cycle = cycle.trim(), sessions = sessions.trim(),
                        startDate = initial?.startDate.orEmpty(), expectedEnd = initial?.expectedEnd.orEmpty(),
                        explanation = explanation, notes = notes.trim(), updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.savePlan(value) else { dao.updatePlan(value); value.id }
                    reconcileRichAttachments(context, dao, "plan", id, explanation, removedFiles)
                    pendingFiles = emptyList(); removedFiles = emptySet(); planEditorOpen = false
                }
            },
        ) {
            FastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Field(name, { name = it }, "标题") }
                item {
                    RichNoteBodyEditor(
                        dao = dao, ownerType = "plan", ownerId = initial?.id ?: 0L,
                        value = explanation, onValueChange = { explanation = it },
                        pendingFiles = pendingFiles, onPendingFilesChange = { pendingFiles = it },
                        removedFiles = removedFiles, onRemovedFilesChange = { removedFiles = it },
                        sessionKey = "plan-${initial?.id ?: "new"}",
                    )
                }
                item {
                    TextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (detailsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(6.dp)); Text(if (detailsOpen) "收起治疗信息" else "更多治疗信息（全部选填）")
                    }
                }
                if (detailsOpen) {
                    item { Field(category, { category = it }, "类型") }; item { Field(hospital, { hospital = it }, "医院") }
                    item { Field(doctor, { doctor = it }, "医生") }; item { Field(date, { date = it }, "日期") }
                    item { Field(medicines, { medicines = it }, "药物") }; item { Field(dose, { dose = it }, "剂量") }
                    item { Field(cycle, { cycle = it }, "周期") }; item { Field(sessions, { sessions = it }, "次数") }
                    item { Field(notes, { notes = it }, "补充备注") }
                }
            }
        }
    }

    if (recordEditorOpen) {
        val initial = editingRecord
        var date by remember(initial?.id, recordEditorOpen) { mutableStateOf(initial?.date.orEmpty()) }
        var type by remember(initial?.id, recordEditorOpen) { mutableStateOf(initial?.type.orEmpty()) }
        var hospital by remember(initial?.id, recordEditorOpen) { mutableStateOf(initial?.hospital.orEmpty()) }
        var doctor by remember(initial?.id, recordEditorOpen) { mutableStateOf(initial?.doctor.orEmpty()) }
        var content by remember(initial?.id, recordEditorOpen) { mutableStateOf(initial?.content.orEmpty()) }
        var medicines by remember(initial?.id, recordEditorOpen) { mutableStateOf(initial?.medicines.orEmpty()) }
        var sideEffects by remember(initial?.id, recordEditorOpen) { mutableStateOf(initial?.sideEffects.orEmpty()) }
        var condition by remember(initial?.id, recordEditorOpen) { mutableStateOf(initial?.condition.orEmpty()) }
        var notes by remember(initial?.id, recordEditorOpen) { mutableStateOf(initial?.notes.orEmpty()) }
        var pendingFiles by remember(initial?.id, recordEditorOpen) { mutableStateOf<List<String>>(emptyList()) }
        var removedFiles by remember(initial?.id, recordEditorOpen) { mutableStateOf<Set<String>>(emptySet()) }
        var detailsOpen by remember(initial?.id, recordEditorOpen) { mutableStateOf(false) }

        FullScreenEditorDialog(
            title = if (initial == null) "新增治疗记录" else "编辑治疗记录",
            onDismiss = { recordEditorOpen = false },
            canSave = richBodyHasContent(content) || pendingFiles.isNotEmpty() || date.isNotBlank() || type.isNotBlank(),
            hasPersistedRecord = initial != null,
            onSave = {
                if (initial == null && !richBodyHasContent(content) && pendingFiles.isEmpty() && date.isBlank() && type.isBlank()) { recordEditorOpen = false; return@FullScreenEditorDialog }
                scope.launch {
                    val value = TreatmentRecordEntity(
                        id = initial?.id ?: 0, date = date.trim(), type = type.trim().ifBlank { "治疗记录" },
                        hospital = hospital.trim(), doctor = doctor.trim(), content = content, medicines = medicines.trim(),
                        sideEffects = sideEffects.trim(), condition = condition.trim(), notes = notes.trim(), updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveRecord(value) else { dao.updateRecord(value); value.id }
                    reconcileRichAttachments(context, dao, "record", id, content, removedFiles)
                    pendingFiles = emptyList(); removedFiles = emptySet(); recordEditorOpen = false
                }
            },
        ) {
            FastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    RichNoteBodyEditor(
                        dao = dao, ownerType = "record", ownerId = initial?.id ?: 0L,
                        value = content, onValueChange = { content = it },
                        pendingFiles = pendingFiles, onPendingFilesChange = { pendingFiles = it },
                        removedFiles = removedFiles, onRemovedFilesChange = { removedFiles = it },
                        sessionKey = "record-${initial?.id ?: "new"}",
                    )
                }
                item {
                    TextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (detailsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(6.dp)); Text(if (detailsOpen) "收起治疗信息" else "更多治疗信息（全部选填）")
                    }
                }
                if (detailsOpen) {
                    item { Field(date, { date = it }, "日期") }; item { Field(type, { type = it }, "治疗类型") }
                    item { Field(hospital, { hospital = it }, "医院") }; item { Field(doctor, { doctor = it }, "医生") }
                    item { Field(medicines, { medicines = it }, "药物") }; item { Field(sideEffects, { sideEffects = it }, "副作用/反应") }
                    item { Field(condition, { condition = it }, "当时情况") }; item { Field(notes, { notes = it }, "补充备注") }
                }
            }
        }
    }
}

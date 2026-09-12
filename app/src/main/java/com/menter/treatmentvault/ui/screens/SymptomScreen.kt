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
import com.menter.treatmentvault.data.SymptomEntity
import kotlinx.coroutines.launch

@Composable
fun SymptomScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.symptoms().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SymptomEntity?>(null) }

    Page("症状记录", back) {
        Text("点任意记录即可继续编辑和添加图片。", style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(value.date, style = MaterialTheme.typography.titleMedium)
                        Text("体温 ${value.temperature ?: "-"}℃ · 体重 ${value.weight ?: "-"}kg")
                        val summary = listOf(value.nausea, value.diarrhea, value.constipation, value.urinaryFrequency, value.pain, value.fatigue, value.bleeding, value.other).filter { it.isNotBlank() }.joinToString(" · ")
                        if (summary.isNotBlank()) Text(summary)
                    }
                }
            }
        }
        Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("记录症状") }
    }

    if (editorOpen) {
        val initial = editing
        var date by remember(initial?.id, editorOpen) { mutableStateOf(initial?.date.orEmpty()) }
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

        FullScreenEditorDialog(
            title = if (initial == null) "记录症状" else "编辑症状",
            onDismiss = { editorOpen = false },
            canSave = date.isNotBlank(),
            onSave = {
                if (date.isBlank()) return@FullScreenEditorDialog
                scope.launch {
                    val value = SymptomEntity(
                        id = initial?.id ?: 0, date = date.trim(), temperature = temp.toDoubleOrNull(), weight = weight.toDoubleOrNull(),
                        appetite = appetite.trim(), nausea = nausea.trim(), vomiting = vomiting.trim(), diarrhea = diarrhea.trim(), constipation = constipation.trim(),
                        bowelCount = initial?.bowelCount, urinaryFrequency = urinaryFrequency.trim(), dysuria = dysuria.trim(), pain = pain.trim(), fatigue = fatigue.trim(),
                        bleeding = bleeding.trim(), other = other.trim(), notes = notes.trim(), updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveSymptom(value) else { dao.updateSymptom(value); value.id }
                    savePendingAttachments(dao, "symptom", id, pendingFiles)
                    editorOpen = false
                }
            },
        ) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                item { Field(notes, { notes = it }, "备注") }
                item { ImageAttachmentSection(dao, "symptom", initial?.id ?: 0L, pendingFiles) { pendingFiles = it } }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

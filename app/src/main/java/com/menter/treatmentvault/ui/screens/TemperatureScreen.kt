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
import com.menter.treatmentvault.data.TemperatureEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TemperatureScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.temperatures().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TemperatureEntity?>(null) }

    Page("体温记录", back) {
        list.firstOrNull()?.let { latest ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("最近一次", style = MaterialTheme.typography.labelLarge)
                    Text("${latest.temperature} ℃", style = MaterialTheme.typography.headlineMedium)
                    Text("${latest.date} ${latest.time} · ${latest.measurementSite}")
                }
            }
        }
        Text("体温是结构化数据，但备注仍然可以像笔记一样插文字和图片。", style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${value.temperature} ℃", style = MaterialTheme.typography.titleMedium)
                        Text("${value.date} ${value.time} · ${value.measurementSite}")
                        val plain = richBodyPlainText(value.notes)
                        if (plain.isNotBlank()) Text(plain, maxLines = 3)
                    }
                }
            }
        }
        Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("记录体温") }
    }

    if (editorOpen) {
        val initial = editing
        val now = remember(editorOpen) { Date() }
        var date by remember(initial?.id, editorOpen) { mutableStateOf(initial?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)) }
        var time by remember(initial?.id, editorOpen) { mutableStateOf(initial?.time ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)) }
        var temp by remember(initial?.id, editorOpen) { mutableStateOf(initial?.temperature?.toString().orEmpty()) }
        var site by remember(initial?.id, editorOpen) { mutableStateOf(initial?.measurementSite ?: "腋下") }
        var notes by remember(initial?.id, editorOpen) { mutableStateOf(initial?.notes.orEmpty()) }
        var pendingFiles by remember(initial?.id, editorOpen) { mutableStateOf<List<String>>(emptyList()) }
        var detailsOpen by remember(initial?.id, editorOpen) { mutableStateOf(false) }

        FullScreenEditorDialog(
            title = if (initial == null) "记录体温" else "编辑体温",
            onDismiss = { editorOpen = false },
            canSave = temp.toDoubleOrNull() != null,
            onSave = {
                val number = temp.toDoubleOrNull() ?: return@FullScreenEditorDialog
                scope.launch {
                    val value = TemperatureEntity(
                        id = initial?.id ?: 0,
                        date = date.trim(),
                        time = time.trim(),
                        temperature = number,
                        measurementSite = site.trim().ifBlank { "腋下" },
                        notes = notes,
                        updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveTemperature(value) else { dao.updateTemperature(value); value.id }
                    savePendingAttachments(dao, "temperature", id, pendingFiles)
                    editorOpen = false
                }
            },
        ) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Field(temp, { temp = it }, "体温 ℃") }
                item {
                    RichNoteBodyEditor(
                        dao = dao, ownerType = "temperature", ownerId = initial?.id ?: 0L,
                        value = notes, onValueChange = { notes = it },
                        pendingFiles = pendingFiles, onPendingFilesChange = { pendingFiles = it },
                        label = "备注",
                    )
                }
                item {
                    TextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (detailsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (detailsOpen) "收起测量信息" else "更多测量信息")
                    }
                }
                if (detailsOpen) {
                    item { Field(date, { date = it }, "日期") }
                    item { Field(time, { time = it }, "时间") }
                    item { Field(site, { site = it }, "测量部位") }
                }
            }
        }
    }
}

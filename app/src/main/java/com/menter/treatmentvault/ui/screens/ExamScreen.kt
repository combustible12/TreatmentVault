package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    var add by remember { mutableStateOf(false) }

    Page("检查报告", back) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(list) { value ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(value.name, style = MaterialTheme.typography.titleMedium)
                        Text("${value.date} · ${value.type} · ${value.hospital}")
                        if (value.summary.isNotBlank()) Text(value.summary)
                    }
                }
            }
        }
        Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) {
            Text("新增检查")
        }
    }

    if (add) {
        var name by remember { mutableStateOf("") }
        var date by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("其他") }
        var summary by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch {
                            dao.saveExam(ExamEntity(name = name, date = date, type = type, summary = summary))
                            add = false
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } },
            title = { Text("新增检查") },
            text = {
                Column {
                    Field(name, { name = it }, "检查名称")
                    Field(date, { date = it }, "日期 YYYY-MM-DD")
                    Field(type, { type = it }, "类型")
                    Field(summary, { summary = it }, "结果摘要")
                }
            },
        )
    }
}

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
import com.menter.treatmentvault.data.QuestionEntity
import kotlinx.coroutines.launch

@Composable
fun QuestionScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.questions().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf(false) }

    Page("医生问诊", back) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(list) { value ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(value.question, style = MaterialTheme.typography.titleMedium)
                        Text("${value.status} · ${value.date} ${value.doctor}")
                        if (value.answer.isNotBlank()) Text("答：${value.answer}")
                    }
                }
            }
        }
        Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) {
            Text("新增待问")
        }

        if (add) {
            var question by remember { mutableStateOf("") }
            var date by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { add = false },
                confirmButton = {
                    TextButton(onClick = {
                        if (question.isNotBlank()) {
                            scope.launch {
                                dao.saveQuestion(QuestionEntity(question = question, date = date))
                                add = false
                            }
                        }
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } },
                title = { Text("新增问题") },
                text = {
                    Column {
                        Field(question, { question = it }, "问题")
                        Field(date, { date = it }, "日期")
                    }
                },
            )
        }
    }
}

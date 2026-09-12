package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import com.menter.treatmentvault.data.SymptomEntity
import kotlinx.coroutines.launch

@Composable
fun SymptomScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.symptoms().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf(false) }

    Page("症状记录", back) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(list) { value ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(value.date, style = MaterialTheme.typography.titleMedium)
                        Text("体温 ${value.temperature ?: "-"}℃ · 体重 ${value.weight ?: "-"}kg")
                        Text(
                            listOf(value.nausea, value.diarrhea, value.constipation, value.pain, value.fatigue)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                        )
                    }
                }
            }
        }
        Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) {
            Text("记录今天症状")
        }

        if (add) {
            var date by remember { mutableStateOf("") }
            var temp by remember { mutableStateOf("") }
            var notes by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { add = false },
                confirmButton = {
                    TextButton(onClick = {
                        if (date.isNotBlank()) {
                            scope.launch {
                                dao.saveSymptom(
                                    SymptomEntity(
                                        date = date,
                                        temperature = temp.toDoubleOrNull(),
                                        notes = notes,
                                    )
                                )
                                add = false
                            }
                        }
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } },
                title = { Text("症状记录") },
                text = {
                    Column {
                        Field(date, { date = it }, "日期")
                        Field(temp, { temp = it }, "体温")
                        Field(notes, { notes = it }, "备注")
                    }
                },
            )
        }
    }
}

package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
    var add by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf("") }
    var site by remember { mutableStateOf("腋下") }
    var notes by remember { mutableStateOf("") }

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
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list) { value ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${value.temperature} ℃", style = MaterialTheme.typography.titleMedium)
                        Text("${value.date} ${value.time} · ${value.measurementSite}")
                        if (value.notes.isNotBlank()) Text(value.notes)
                    }
                }
            }
        }
        Button(
            onClick = {
                val now = Date()
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                temp = ""
                site = "腋下"
                notes = ""
                add = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("记录体温") }
    }

    if (add) {
        AlertDialog(
            onDismissRequest = { add = false },
            confirmButton = {
                TextButton(onClick = {
                    val number = temp.toDoubleOrNull()
                    if (number != null && date.isNotBlank() && time.isNotBlank()) {
                        scope.launch {
                            dao.saveTemperature(
                                TemperatureEntity(
                                    date = date,
                                    time = time,
                                    temperature = number,
                                    measurementSite = site,
                                    notes = notes,
                                )
                            )
                            add = false
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } },
            title = { Text("记录体温") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(temp, { temp = it }, "体温 ℃")
                    Field(date, { date = it }, "日期")
                    Field(time, { time = it }, "时间")
                    Field(site, { site = it }, "测量部位")
                    Field(notes, { notes = it }, "备注")
                }
            },
        )
    }
}

package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import com.menter.treatmentvault.data.SupplyEntity
import kotlinx.coroutines.launch

@Composable
fun SupplyScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.supplies().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf(false) }

    Page("药品与用品", back) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("待购", "已购", "暂不购买").forEach { status ->
                AssistChip(onClick = {}, label = { Text("$status ${list.count { it.purchaseStatus == status }}") })
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list) { value ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(value.name, style = MaterialTheme.typography.titleMedium)
                        Text("${value.purchaseStatus} · ${value.necessity}")
                        if (value.purpose.isNotBlank()) Text(value.purpose)
                        if (value.stages.isNotBlank()) Text(value.stages, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("新增用品")
        }
    }

    if (add) {
        var name by remember { mutableStateOf("") }
        var purpose by remember { mutableStateOf("") }
        var stages by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch {
                            dao.saveSupply(SupplyEntity(name = name, purpose = purpose, stages = stages))
                            add = false
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } },
            title = { Text("新增用品") },
            text = {
                Column {
                    Field(name, { name = it }, "名称")
                    Field(stages, { stages = it }, "使用阶段")
                    Field(purpose, { purpose = it }, "作用")
                }
            },
        )
    }
}

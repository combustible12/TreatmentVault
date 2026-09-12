package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication

@Composable
fun TreatmentScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val plans by dao.plans().collectAsState(emptyList())
    val records by dao.records().collectAsState(emptyList())
    var tab by remember { mutableIntStateOf(0) }

    Page("治疗方案与记录", back) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("方案") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("治疗记录") })
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (tab == 0) {
                items(plans) { value ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(value.name, style = MaterialTheme.typography.titleMedium)
                            Text("${value.category} · ${value.date}")
                            Text(value.explanation)
                        }
                    }
                }
            } else {
                items(records) { value ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("${value.date} ${value.type}", style = MaterialTheme.typography.titleMedium)
                            Text(value.content)
                            if (value.sideEffects.isNotBlank()) Text("反应：${value.sideEffects}")
                        }
                    }
                }
            }
        }
        Text(
            "第一版优先通过更新包导入完整治疗方案/记录；手工编辑表单后续补强。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication

@Composable
fun TimelineScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.timeline().collectAsState(emptyList())
    Page("总时间轴", back) {
        LazyColumn {
            items(list) { value ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(value.date, modifier = Modifier.width(92.dp))
                    Card(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(value.title, style = MaterialTheme.typography.titleMedium)
                            Text(value.type)
                            if (value.content.isNotBlank()) Text(value.content)
                        }
                    }
                }
            }
        }
    }
}

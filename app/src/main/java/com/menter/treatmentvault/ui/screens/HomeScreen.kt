package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(go: (String) -> Unit) {
    val cards = listOf(
        Triple("总时间轴", "timeline", Icons.Default.Timeline),
        Triple("检查报告", "exams", Icons.Default.Description),
        Triple("治疗方案与记录", "treatment", Icons.Default.MedicalServices),
        Triple("药品用品", "supplies", Icons.Default.Medication),
        Triple("症状记录", "symptoms", Icons.Default.MonitorHeart),
        Triple("体温记录", "temperature", Icons.Default.Thermostat),
        Triple("医生问诊", "questions", Icons.Default.QuestionAnswer),
        Triple("费用记录", "expenses", Icons.Default.Payments),
        Triple("导入更新包", "import", Icons.Default.UploadFile),
        Triple("备份与恢复", "settings", Icons.Default.Backup),
    )
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("治疗资料库") }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(cards) { (title, route, icon) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable { go(route) }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(icon, contentDescription = null)
                        Text(title, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

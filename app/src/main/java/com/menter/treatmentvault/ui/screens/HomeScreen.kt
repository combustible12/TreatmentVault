package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun HomeScreen(go:(String)->Unit){ val cards=listOf(
    Triple("总时间轴","timeline",Icons.Default.Timeline),Triple("检查报告","exams",Icons.Default.Description),Triple("治疗方案与记录","treatment",Icons.Default.MedicalServices),Triple("药品用品","supplies",Icons.Default.Medication),Triple("症状记录","symptoms",Icons.Default.MonitorHeart),Triple("医生问诊","questions",Icons.Default.QuestionAnswer),Triple("费用记录","expenses",Icons.Default.Payments),Triple("导入更新包","import",Icons.Default.UploadFile),Triple("备份与恢复","settings",Icons.Default.Backup))
    Scaffold(topBar={CenterAlignedTopAppBar(title={Text("治疗资料库")})}){p-> LazyVerticalGrid(GridCells.Fixed(2),Modifier.padding(p).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(cards){(t,r,i)->Card(Modifier.fillMaxWidth().height(110.dp).clickable{go(r)}){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(i,null);Text(t,style=MaterialTheme.typography.titleMedium)}}}}}
}

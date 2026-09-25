package com.menter.treatmentvault.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.*
import com.menter.treatmentvault.smartinput.SmartCategory
import com.menter.treatmentvault.smartinput.SmartInputParser
import com.menter.treatmentvault.smartinput.SmartParsedItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SmartInputScreen(back: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val db = app.database
    val dao = db.dao()
    val categories by dao.categories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var raw by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<List<SmartParsedItem>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf("") }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val speech = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (speech.isNotBlank()) {
                raw = if (raw.isBlank()) speech else "$raw\n$speech"
                parsed = emptyList(); message = ""
            }
        }
    }
    fun nowDate() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    fun nowTime() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    Page("智能录入", back) {
        Text("可以直接粘贴、输入，或多次点语音继续追加。自定义类目也会按你设置的关键词参与分类。", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = raw, onValueChange = { raw = it; parsed = emptyList(); message = "" }, label = { Text("把大段内容放这里") }, modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp), minLines = 8)
        Button(onClick = {
            voiceError = ""
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "继续说，识别结果会追加到原文")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            runCatching { voiceLauncher.launch(intent) }.onFailure { voiceError = "当前手机没有可用的系统语音识别服务，仍可直接粘贴或输入。" }
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Mic, null); Text("语音输入（可重复追加）") }
        if (voiceError.isNotBlank()) Text(voiceError, style = MaterialTheme.typography.bodySmall)

        Button(onClick = {
            parsed = SmartInputParser.parse(raw, nowDate(), nowTime(), categories)
            message = if (parsed.isEmpty()) "没有识别到可整理内容" else "已整理 ${parsed.size} 条，请先看预览"
        }, enabled = raw.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("自动整理并预览") }

        if (parsed.isNotEmpty()) {
            val counts = parsed.groupingBy { it.displayLabel }.eachCount()
            Text(counts.entries.joinToString(" · ") { "${it.key} ${it.value}" }, style = MaterialTheme.typography.titleSmall)
            FastScrollLazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(parsed) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.displayLabel, style = MaterialTheme.typography.labelLarge)
                            Text(item.text)
                            val extra = buildList { add(item.date); item.time?.let { add(it) }; item.temperature?.let { add("${it}℃") }; item.amount?.let { add("¥${"%.2f".format(it)}") } }
                            Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(onClick = {
                scope.launch {
                    val saveTime = nowTime(); var saved = 0
                    db.withTransaction {
                        dao.saveTimeline(TimelineEntity(date = nowDate(), type = "智能录入原文", title = "智能录入 $saveTime", content = raw))
                        for (item in parsed) {
                            when (item.category) {
                                SmartCategory.TEMPERATURE -> item.temperature?.let { dao.saveTemperature(TemperatureEntity(date=item.date,time=item.time?:saveTime,temperature=it,measurementSite=inferMeasurementSite(item.text),notes=item.text)); saved++ }
                                SmartCategory.QUESTION -> { dao.saveQuestion(QuestionEntity(question=item.text.trim().trimEnd('？','?'),date=item.date,status="待问",notes="来自智能录入")); saved++ }
                                SmartCategory.EXPENSE -> item.amount?.let { dao.saveExpense(ExpenseEntity(date=item.date,type=inferExpenseType(item.text),amount=it,selfPayAmount=it,notes=item.text)); saved++ }
                                SmartCategory.EXAM -> { dao.saveExam(ExamEntity(name=inferExamName(item.text),date=item.date,type=inferExamType(item.text),summary=item.text,notes="来自智能录入")); saved++ }
                                SmartCategory.TREATMENT -> { dao.saveRecord(TreatmentRecordEntity(date=item.date,type=inferTreatmentType(item.text),content=item.text,notes="来自智能录入")); saved++ }
                                SmartCategory.SYMPTOM -> { dao.saveSymptom(SymptomEntity(date=item.date,temperature=item.temperature,urinaryFrequency=if(item.text.contains("尿频")||item.text.contains("尿急"))"有" else "",dysuria=if(item.text.contains("尿痛"))"有" else "",pain=if(item.text.contains("痛")||item.text.contains("疼")) item.text else "",fatigue=if(item.text.contains("乏力")||item.text.contains("疲劳"))"有" else "",bleeding=if(item.text.contains("出血"))"有" else "",notes=item.text)); saved++ }
                                SmartCategory.CUSTOM -> item.customCategoryId?.let { id -> dao.saveCustomEntry(CustomEntryEntity(categoryId=id,date=item.date,title=item.customCategoryName.orEmpty(),content=item.text)); saved++ }
                                SmartCategory.SUPPLY, SmartCategory.OTHER -> { dao.saveTimeline(TimelineEntity(date=item.date,type="待确认",title=item.displayLabel,content=item.text)); saved++ }
                            }
                        }
                    }
                    message = "已保存 $saved 条；原文也已完整保留。无法确定的内容进入待确认。"
                }
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Text("确认并全部保存") }
        }
        if (message.isNotBlank()) Text(message)
        if (raw.isNotBlank()) TextButton(onClick = { raw="";parsed=emptyList();message="" }) { Text("清空输入") }
    }
}

private fun inferMeasurementSite(text:String)=when{ text.contains("口温")||text.contains("口腔")->"口腔";text.contains("耳温")->"耳温";text.contains("额温")||text.contains("额头")->"额温";else->"腋下" }
private fun inferExamName(text:String)=when{ text.contains("PET",true)->"PET-CT";text.contains("MRI",true)||text.contains("磁共振")->"MRI/磁共振";text.contains("CT",true)->"CT";text.contains("免疫组化")->"免疫组化";text.contains("病理")||text.contains("活检")->"病理/活检";text.contains("心电图")->"心电图";text.contains("彩超")->"彩超";text.contains("血常规")||text.contains("抽血")->"血液检查";else->"检查记录" }
private fun inferExamType(text:String)=when{ text.contains("病理")||text.contains("活检")||text.contains("免疫组化")->"病理";text.contains("MRI",true)||text.contains("磁共振")->"MRI";text.contains("PET",true)->"PET-CT";text.contains("CT",true)->"CT";text.contains("心电图")->"心电图";text.contains("彩超")->"超声";text.contains("血")||text.contains("抽血")->"化验";else->"其他" }
private fun inferTreatmentType(text:String)=when{ text.contains("放疗")||text.contains("定位")->"放疗";text.contains("化疗")->"化疗";text.contains("手术")->"手术";else->"治疗" }
private fun inferExpenseType(text:String)=when{ text.contains("挂号")->"挂号";text.contains("检查")||text.contains("MRI",true)||text.contains("CT",true)->"检查";text.contains("住院")||text.contains("押金")->"住院";text.contains("药")->"药品";else->"其他" }

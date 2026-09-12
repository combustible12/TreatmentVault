package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CategoryEntity
import com.menter.treatmentvault.data.CustomEntryEntity
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun categoryIcon(key: String): ImageVector = when (key) {
    "mic" -> Icons.Default.Mic
    "timeline" -> Icons.Default.Timeline
    "description" -> Icons.Default.Description
    "medical" -> Icons.Default.MedicalServices
    "medication" -> Icons.Default.Medication
    "heart" -> Icons.Default.MonitorHeart
    "thermostat" -> Icons.Default.Thermostat
    "question" -> Icons.Default.QuestionAnswer
    "payments" -> Icons.Default.Payments
    "upload" -> Icons.Default.UploadFile
    "backup" -> Icons.Default.Backup
    "favorite" -> Icons.Default.Favorite
    "star" -> Icons.Default.Star
    "science" -> Icons.Default.Science
    "event" -> Icons.Default.Event
    "note" -> Icons.Default.Notes
    "shield" -> Icons.Default.HealthAndSafety
    else -> Icons.Default.Folder
}

private val selectableIcons = listOf(
    "folder" to "文件夹", "note" to "笔记", "favorite" to "爱心", "star" to "星标",
    "science" to "检查", "medical" to "医疗", "medication" to "药品", "event" to "日程",
    "payments" to "费用", "shield" to "健康",
)

@Composable
fun CategoryManagementScreen(back: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val categories by dao.categories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }

    Page("类目管理", back) {
        Text("系统类目可以排序和隐藏；自定义类目还可以改名、换图标和删除。子分类/标签结构已预留。", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("新增自定义类目")
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.key }) { category ->
                val index = categories.indexOfFirst { it.key == category.key }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(categoryIcon(category.iconKey), contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text(category.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (category.isSystem) "系统类目" else "自定义类目", style = MaterialTheme.typography.bodySmall)
                                if (!category.isSystem && category.smartEnabled) {
                                    Text("智能关键词：${category.smartKeywords.ifBlank { category.name }}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = {
                                scope.launch { dao.updateCategory(category.copy(hidden = !category.hidden, updatedAt = System.currentTimeMillis())) }
                            }) {
                                Icon(if (category.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "隐藏/显示")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    if (index > 0) scope.launch {
                                        val prev = categories[index - 1]
                                        dao.updateCategory(category.copy(sortOrder = prev.sortOrder, updatedAt = System.currentTimeMillis()))
                                        dao.updateCategory(prev.copy(sortOrder = category.sortOrder, updatedAt = System.currentTimeMillis()))
                                    }
                                },
                                enabled = index > 0,
                            ) { Text("上移") }
                            TextButton(
                                onClick = {
                                    if (index in 0 until categories.lastIndex) scope.launch {
                                        val next = categories[index + 1]
                                        dao.updateCategory(category.copy(sortOrder = next.sortOrder, updatedAt = System.currentTimeMillis()))
                                        dao.updateCategory(next.copy(sortOrder = category.sortOrder, updatedAt = System.currentTimeMillis()))
                                    }
                                },
                                enabled = index in 0 until categories.lastIndex,
                            ) { Text("下移") }
                            if (!category.isSystem) {
                                TextButton(onClick = { editing = category }) { Text("编辑") }
                                TextButton(onClick = { deleting = category }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        CategoryEditDialog(
            title = "新增类目",
            initial = null,
            onDismiss = { adding = false },
            onSave = { name, icon, keywords ->
                scope.launch {
                    val maxOrder = categories.maxOfOrNull { it.sortOrder } ?: 0
                    dao.saveCategory(
                        CategoryEntity(
                            key = "custom_${System.currentTimeMillis()}",
                            name = name,
                            iconKey = icon,
                            sortOrder = maxOrder + 10,
                            isSystem = false,
                            smartEnabled = true,
                            smartKeywords = keywords.ifBlank { name },
                        )
                    )
                }
                adding = false
            },
        )
    }

    editing?.let { category ->
        CategoryEditDialog(
            title = "编辑类目",
            initial = category,
            onDismiss = { editing = null },
            onSave = { name, icon, keywords ->
                scope.launch {
                    dao.updateCategory(
                        category.copy(
                            name = name,
                            iconKey = icon,
                            smartKeywords = keywords.ifBlank { name },
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
                editing = null
            },
        )
    }

    deleting?.let { category ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除“${category.name}”？") },
            text = { Text("该自定义类目下的内容也会删除。系统默认类目不会提供这个删除入口。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        dao.clearCustomEntriesForCategory(category.key)
                        dao.deleteCategory(category)
                    }
                    deleting = null
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun CategoryEditDialog(
    title: String,
    initial: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember(initial?.key) { mutableStateOf(initial?.name.orEmpty()) }
    var icon by remember(initial?.key) { mutableStateOf(initial?.iconKey ?: "folder") }
    var keywords by remember(initial?.key) { mutableStateOf(initial?.smartKeywords.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("类目名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(keywords, { keywords = it }, label = { Text("智能分类关键词（逗号分隔）") }, modifier = Modifier.fillMaxWidth())
                Text("选择图标")
                selectableIcons.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (key, label) ->
                            FilterChip(
                                selected = icon == key,
                                onClick = { icon = key },
                                label = { Text(label) },
                                leadingIcon = { Icon(categoryIcon(key), contentDescription = null) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), icon, keywords.trim()) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun CustomCategoryScreen(categoryId: String, back: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val category by dao.categories().map { list -> list.firstOrNull { it.key == categoryId } }.collectAsState(initial = null)
    val entries by dao.customEntries(categoryId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf(false) }

    Page(category?.name ?: "自定义类目", back) {
        Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) { Text("新增记录") }
        if (entries.isEmpty()) Text("暂无记录。智能录入识别到这个类目后，也会自动存到这里。")
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.title.ifBlank { entry.content.take(28) }, style = MaterialTheme.typography.titleMedium)
                        if (entry.date.isNotBlank()) Text(entry.date, style = MaterialTheme.typography.bodySmall)
                        Text(entry.content)
                        if (entry.tags.isNotBlank()) Text("标签：${entry.tags}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { scope.launch { dao.deleteCustomEntry(entry) } }) { Text("删除") }
                    }
                }
            }
        }
    }

    if (add) {
        var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text("新增记录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(date, { date = it }, label = { Text("日期") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(content, { content = it }, label = { Text("内容") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                    OutlinedTextField(tags, { tags = it }, label = { Text("标签（逗号分隔，预留能力）") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.saveCustomEntry(CustomEntryEntity(categoryId = categoryId, date = date, title = title, content = content, tags = tags)) }
                    add = false
                }, enabled = content.isNotBlank() || title.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } },
        )
    }
}

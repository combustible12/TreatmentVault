package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.AppDao
import com.menter.treatmentvault.data.CategoryEntity
import com.menter.treatmentvault.data.CustomEntryEntity
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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

private val defaultSystemOrder = listOf(
    "smart_input", "timeline", "exams", "treatment", "supplies", "symptoms",
    "temperature", "questions", "expenses", "import", "settings",
)

private suspend fun persistCategoryOrder(dao: AppDao, ordered: List<CategoryEntity>) {
    val now = System.currentTimeMillis()
    ordered.forEachIndexed { index, category ->
        dao.updateCategory(
            category.copy(
                sortOrder = index * 10,
                updatedAt = now + index,
            )
        )
    }
}

@Composable
fun CategoryManagementScreen(back: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val categories by dao.categories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var localOrder by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(categories, draggingKey) {
        if (draggingKey == null) localOrder = categories
    }
    val latestOrder by rememberUpdatedState(localOrder)

    Page("类目管理", back) {
        Text(
            "长按左侧拖动手柄即可排序；点击自定义类目可直接编辑。系统类目可排序和隐藏，默认不提供删除。",
            style = MaterialTheme.typography.bodySmall,
        )

        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("新增自定义类目")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        categories.filter { it.hidden }.forEach {
                            dao.updateCategory(it.copy(hidden = false, updatedAt = System.currentTimeMillis()))
                        }
                    }
                },
                enabled = categories.any { it.hidden },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Text("显示全部")
            }
            OutlinedButton(
                onClick = {
                    val system = localOrder
                        .filter { it.isSystem }
                        .sortedBy { key ->
                            defaultSystemOrder.indexOf(key.key).let { if (it < 0) Int.MAX_VALUE else it }
                        }
                    val custom = localOrder.filterNot { it.isSystem }
                    val reset = system + custom
                    localOrder = reset
                    scope.launch { persistCategoryOrder(dao, reset) }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Text("恢复默认")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(localOrder, key = { it.key }) { category ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !category.isSystem) { editing = category },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = "长按拖动排序",
                            modifier = Modifier.pointerInput(category.key) {
                                var accumulator = 0f
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        accumulator = 0f
                                        draggingKey = category.key
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        localOrder = categories
                                    },
                                    onDragEnd = {
                                        val snapshot = latestOrder
                                        draggingKey = null
                                        scope.launch { persistCategoryOrder(dao, snapshot) }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulator += dragAmount.y
                                        val threshold = 48.dp.toPx()
                                        if (abs(accumulator) >= threshold) {
                                            val current = latestOrder.toMutableList()
                                            val from = current.indexOfFirst { it.key == category.key }
                                            val to = from + if (accumulator > 0f) 1 else -1
                                            if (from >= 0 && to in current.indices) {
                                                val moved = current.removeAt(from)
                                                current.add(to, moved)
                                                localOrder = current
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            accumulator = 0f
                                        }
                                    },
                                )
                            },
                        )

                        Icon(categoryIcon(category.iconKey), contentDescription = null)

                        Column(Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (category.isSystem) "系统类目" else "自定义类目 · 点卡片编辑",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!category.isSystem && category.smartEnabled) {
                                Text(
                                    "智能关键词：${category.smartKeywords.ifBlank { category.name }}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (category.hidden) {
                                Text("已从首页隐藏", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    dao.updateCategory(
                                        category.copy(
                                            hidden = !category.hidden,
                                            updatedAt = System.currentTimeMillis(),
                                        )
                                    )
                                }
                            }
                        ) {
                            Icon(
                                if (category.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (category.hidden) "显示" else "隐藏",
                            )
                        }

                        if (!category.isSystem) {
                            Column {
                                IconButton(onClick = { menuFor = category.key }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                                }
                                DropdownMenu(
                                    expanded = menuFor == category.key,
                                    onDismissRequest = { menuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("编辑") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = {
                                            menuFor = null
                                            editing = category
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                        onClick = {
                                            menuFor = null
                                            deleting = category
                                        },
                                    )
                                }
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

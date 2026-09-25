package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.AppDao
import com.menter.treatmentvault.data.CategoryEntity
import kotlinx.coroutines.launch
import kotlin.math.abs

private val editableCategoryIcons = listOf(
    "folder" to "文件夹", "note" to "笔记", "favorite" to "爱心", "star" to "星标",
    "science" to "检查", "medical" to "医疗", "medication" to "药品", "event" to "日程",
    "payments" to "费用", "shield" to "健康", "description" to "报告", "timeline" to "时间线",
    "thermostat" to "体温", "question" to "问诊", "upload" to "导入", "backup" to "备份",
)

private fun defaultBuiltInCategories(now: Long = System.currentTimeMillis()) = listOf(
    CategoryEntity("smart_input", "智能录入", "smart_input", "mic", 0, false, true, false, "", null, now),
    CategoryEntity("timeline", "总时间轴", "timeline", "timeline", 10, false, true, false, "", null, now + 1),
    CategoryEntity("exams", "检查报告", "exams", "description", 20, false, true, true, "MRI,磁共振,CT,PET,病理,活检,免疫组化,彩超,心电图,抽血,血常规,尿检,检查,报告", null, now + 2),
    CategoryEntity("treatment", "治疗方案与记录", "treatment", "medical", 30, false, true, true, "化疗,放疗,手术,治疗,定位,输液港,PICC,方案,周期,复查,评估", null, now + 3),
    CategoryEntity("supplies", "药品用品", "supplies", "medication", 40, false, true, true, "买,购买,准备,用品,喷雾,体温计,蛋白粉,营养粉,乳果糖,益生菌,比亚芬,康复新液,药店", null, now + 4),
    CategoryEntity("symptoms", "症状记录", "symptoms", "heart", 50, false, true, true, "尿频,尿急,尿痛,恶心,呕吐,腹泻,便秘,出血,疼,痛,乏力,疲劳,没胃口,食欲,发热,发烧,分泌物,头晕,腹胀", null, now + 5),
    CategoryEntity("temperature", "体温记录", "temperature", "thermostat", 60, false, true, true, "体温,发热,发烧,℃,度", null, now + 6),
    CategoryEntity("questions", "医生问诊", "questions", "question", 70, false, true, true, "要问,问医生,请问,想问,需要问,？,?", null, now + 7),
    CategoryEntity("expenses", "费用记录", "expenses", "payments", 80, false, true, true, "花了,费用,挂号,缴费,交了,支付,自费,报销,元,¥,￥", null, now + 8),
    CategoryEntity("import", "导入更新包", "import", "upload", 90, false, true, false, "", null, now + 9),
    CategoryEntity("settings", "备份与恢复", "settings", "backup", 100, false, true, false, "", null, now + 10),
)

private suspend fun saveEditableCategoryOrder(dao: AppDao, ordered: List<CategoryEntity>) {
    val now = System.currentTimeMillis()
    ordered.forEachIndexed { index, category ->
        dao.updateCategory(category.copy(sortOrder = index * 10, updatedAt = now + index))
    }
}

private fun descendantCategoryKeys(rootKey: String, allCategories: List<CategoryEntity>): Set<String> {
    val result = mutableSetOf<String>()
    fun visit(parentKey: String) {
        allCategories.filter { it.parentId == parentKey }.forEach { child ->
            if (result.add(child.key)) visit(child.key)
        }
    }
    visit(rootKey)
    return result
}

private suspend fun deleteCategoryWithoutDeletingRecords(dao: AppDao, category: CategoryEntity) {
    val allCategories = dao.allCategories()
    val notes = dao.allCustomEntries().filter { it.categoryId == category.key }

    if (notes.isNotEmpty()) {
        var fallback = allCategories.firstOrNull { it.key == "inbox" && it.key != category.key }
            ?: allCategories.firstOrNull { !it.isSystem && !it.hidden && it.key != category.key }
            ?: allCategories.firstOrNull { !it.isSystem && it.key != category.key }
        if (fallback == null) {
            fallback = CategoryEntity(
                key = if (category.key == "inbox") "uncategorized_${System.currentTimeMillis()}" else "inbox",
                name = if (category.key == "inbox") "未分类" else "待整理",
                iconKey = "note",
                sortOrder = (allCategories.maxOfOrNull { it.sortOrder } ?: 0) + 10,
                isSystem = false,
                smartEnabled = false,
            )
            dao.saveCategory(fallback)
        }
        val target = requireNotNull(fallback)
        val now = System.currentTimeMillis()
        notes.forEachIndexed { index, note ->
            dao.updateCustomEntry(note.copy(categoryId = target.key, updatedAt = now + index))
        }
    }

    allCategories.filter { it.parentId == category.key }.forEach {
        dao.updateCategory(it.copy(parentId = null, updatedAt = System.currentTimeMillis()))
    }
    dao.deleteCategory(category)
}

@Composable
fun EditableCategoryManagementScreen(back: () -> Unit) {
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
    var confirmRestore by remember { mutableStateOf(false) }

    LaunchedEffect(categories, draggingKey) {
        if (draggingKey == null) localOrder = categories
    }
    val latestOrder by rememberUpdatedState(localOrder)

    Page("类目管理", back) {
        Text(
            "所有类目都可以编辑、排序、隐藏或删除。删除类目只删除分类入口，不会删除里面的记录。",
            style = MaterialTheme.typography.bodySmall,
        )

        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("新增类目")
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Spacer(Modifier.width(4.dp))
                Text("显示全部")
            }
            OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("恢复默认")
            }
        }

        FastScrollLazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(localOrder, key = { it.key }) { category ->
                Card(modifier = Modifier.fillMaxWidth().clickable { editing = category }) {
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
                                        scope.launch { saveEditableCategoryOrder(dao, snapshot) }
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
                            Text(category.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (category.isSystem) "默认类目" else "自建类目") + " · 点卡片编辑",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (category.smartEnabled) {
                                Text(
                                    "智能关键词：${category.smartKeywords.ifBlank { category.name }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (category.hidden) Text("已从首页隐藏", style = MaterialTheme.typography.labelSmall)
                        }

                        IconButton(onClick = {
                            scope.launch {
                                dao.updateCategory(category.copy(hidden = !category.hidden, updatedAt = System.currentTimeMillis()))
                            }
                        }) {
                            Icon(
                                if (category.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (category.hidden) "显示" else "隐藏",
                            )
                        }

                        Column {
                            IconButton(onClick = { menuFor = category.key }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(expanded = menuFor == category.key, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(
                                    text = { Text("编辑") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { menuFor = null; editing = category },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除类目") },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                    onClick = { menuFor = null; deleting = category },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        EditableCategoryDialog(
            title = "新增类目",
            initial = null,
            allCategories = categories,
            onDismiss = { adding = false },
            onSave = { name, icon, keywords, parentId ->
                scope.launch {
                    dao.saveCategory(
                        CategoryEntity(
                            key = "custom_${System.currentTimeMillis()}",
                            name = name,
                            iconKey = icon,
                            sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: 0) + 10,
                            isSystem = false,
                            smartEnabled = true,
                            smartKeywords = keywords.ifBlank { name },
                            parentId = parentId,
                        )
                    )
                }
                adding = false
            },
        )
    }

    editing?.let { category ->
        EditableCategoryDialog(
            title = "编辑类目",
            initial = category,
            allCategories = categories,
            onDismiss = { editing = null },
            onSave = { name, icon, keywords, parentId ->
                scope.launch {
                    dao.updateCategory(
                        category.copy(
                            name = name,
                            iconKey = icon,
                            smartKeywords = keywords,
                            parentId = parentId,
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
            text = {
                Text(
                    if (category.isSystem)
                        "只会删除这个默认分类入口。已有检查、治疗、症状等记录不会删除，仍可从全部记录、搜索和时间线找到；需要时可点“恢复默认”把入口恢复回来。"
                    else
                        "只删除这个类目，不删除其中的笔记。类目里的笔记会自动移动到“待整理”或其他可见类目。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { deleteCategoryWithoutDeletingRecords(dao, category) }
                    deleting = null
                }) { Text("删除类目") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("恢复默认类目？") },
            text = { Text("会重新建立被删掉的默认类目，并把默认类目的名称、图标、关键词、显示状态和顺序恢复为初始值。你自己新建的类目和任何记录都不会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { defaultBuiltInCategories().forEach { dao.saveCategory(it) } }
                    confirmRestore = false
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun EditableCategoryDialog(
    title: String,
    initial: CategoryEntity?,
    allCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?) -> Unit,
) {
    var name by remember(initial?.key) { mutableStateOf(initial?.name.orEmpty()) }
    var icon by remember(initial?.key) { mutableStateOf(initial?.iconKey ?: "folder") }
    var keywords by remember(initial?.key) { mutableStateOf(initial?.smartKeywords.orEmpty()) }
    var parentId by remember(initial?.key) { mutableStateOf(initial?.parentId) }
    var parentMenu by remember { mutableStateOf(false) }

    val parentName = allCategories.firstOrNull { it.key == parentId }?.name
    val blockedParentKeys = initial?.let { descendantCategoryKeys(it.key, allCategories) + it.key }.orEmpty()
    val parentCandidates = allCategories.filter { it.key !in blockedParentKeys }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FastScrollLazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("类目名称") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(keywords, { keywords = it }, label = { Text("智能分类关键词（逗号分隔）") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { parentMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AccountTree, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(parentName?.let { "上级：$it" } ?: "顶级类目", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = parentMenu, onDismissRequest = { parentMenu = false }) {
                            DropdownMenuItem(text = { Text("顶级类目") }, onClick = { parentId = null; parentMenu = false })
                            parentCandidates.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    leadingIcon = { Icon(categoryIcon(category.iconKey), contentDescription = null) },
                                    onClick = { parentId = category.key; parentMenu = false },
                                )
                            }
                        }
                    }
                }
                item { Text("选择图标") }
                editableCategoryIcons.chunked(2).forEach { row ->
                    item {
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), icon, keywords.trim(), parentId) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

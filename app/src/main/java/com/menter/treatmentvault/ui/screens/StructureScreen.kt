package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CategoryEntity
import com.menter.treatmentvault.data.CustomEntryEntity

private val nonDataCategoryKeys = setOf("smart_input", "import", "settings")

@Composable
fun StructureScreen(back: () -> Unit, go: (String) -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()

    val categories by dao.categories().collectAsState(initial = emptyList())
    val supplies by dao.supplies().collectAsState(initial = emptyList())
    val exams by dao.exams().collectAsState(initial = emptyList())
    val plans by dao.plans().collectAsState(initial = emptyList())
    val records by dao.records().collectAsState(initial = emptyList())
    val symptoms by dao.symptoms().collectAsState(initial = emptyList())
    val temperatures by dao.temperatures().collectAsState(initial = emptyList())
    val questions by dao.questions().collectAsState(initial = emptyList())
    val expenses by dao.expenses().collectAsState(initial = emptyList())
    val timeline by dao.timeline().collectAsState(initial = emptyList())
    val customEntries by dao.allCustomEntriesFlow().collectAsState(initial = emptyList())

    val dataCategories = remember(categories) {
        categories.filter { !it.hidden && (!it.isSystem || it.key !in nonDataCategoryKeys) }
    }
    val flattened = remember(dataCategories) {
        val byParent = dataCategories.groupBy { it.parentId }
        buildList<Pair<CategoryEntity, Int>> {
            fun visit(parent: String?, depth: Int) {
                byParent[parent].orEmpty().sortedBy { it.sortOrder }.forEach { category ->
                    add(category to depth)
                    visit(category.key, depth + 1)
                }
            }
            visit(null, 0)
            val included = map { it.first.key }.toSet()
            dataCategories.filterNot { it.key in included }.sortedBy { it.sortOrder }.forEach { add(it to 0) }
        }
    }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun children(category: CategoryEntity): List<String> = when (category.key) {
        "timeline" -> timeline.map { "${it.date} · ${it.title}" }
        "exams" -> exams.map { "${it.date} · ${it.name}" } + customEntries.filter { it.categoryId == category.key }.map { entryLabel(it) }
        "treatment" -> plans.map { "方案 · ${it.name}" } + records.map {
            "${it.date} · ${it.type}${if (it.content.isBlank()) "" else " · ${it.content.take(30)}"}"
        } + customEntries.filter { it.categoryId == category.key }.map { entryLabel(it) }
        "supplies" -> supplies.map { it.name } + customEntries.filter { it.categoryId == category.key }.map { entryLabel(it) }
        "symptoms" -> symptoms.map {
            val detail = it.notes.ifBlank { it.other.ifBlank { "症状记录" } }
            "${it.date} · ${detail.take(36)}"
        } + customEntries.filter { it.categoryId == category.key }.map { entryLabel(it) }
        "temperature" -> temperatures.map { "${it.date} ${it.time} · ${it.temperature}℃" } + customEntries.filter { it.categoryId == category.key }.map { entryLabel(it) }
        "questions" -> questions.map { "${it.status} · ${it.question.take(42)}" } + customEntries.filter { it.categoryId == category.key }.map { entryLabel(it) }
        "expenses" -> expenses.map { "${it.date} · ${it.type} · ¥${it.amount}" } + customEntries.filter { it.categoryId == category.key }.map { entryLabel(it) }
        else -> customEntries.filter { it.categoryId == category.key }.map { entryLabel(it) }
    }

    Page("资料树", back) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.AccountTree, contentDescription = null)
            Text("治疗资料库 → 文件夹 → 子文件夹 → 记录", style = MaterialTheme.typography.titleSmall)
        }
        Text(
            "这里按真实文件夹层级显示。展开文件夹看直属记录，点“进入”打开对应文件夹。",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { expanded = dataCategories.map { it.key }.toSet() }) { Text("全部展开") }
            OutlinedButton(onClick = { expanded = emptySet() }) { Text("全部收起") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.AccountTree, contentDescription = null)
                Column {
                    Text("治疗资料库", style = MaterialTheme.typography.titleMedium)
                    Text("${dataCategories.size} 个文件夹/资料入口", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(flattened, key = { it.first.key }) { (category, depth) ->
                val childItems = children(category)
                val childFolderCount = dataCategories.count { it.parentId == category.key }
                val isExpanded = category.key in expanded
                Card(modifier = Modifier.fillMaxWidth().padding(start = (depth * 18).dp)) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = if (isExpanded) expanded - category.key else expanded + category.key }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, contentDescription = if (isExpanded) "收起" else "展开")
                            Icon(categoryIcon(category.iconKey), contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text(category.name, style = MaterialTheme.typography.titleSmall)
                                Text("$childFolderCount 个子文件夹 · ${childItems.size} 条直属记录", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { val route = categoryOpenRoute(category); if (route.isNotBlank()) go(route) }) { Text("进入") }
                        }

                        if (isExpanded) {
                            if (childItems.isEmpty()) {
                                Text("└─ 暂无直属记录", modifier = Modifier.padding(start = 44.dp, end = 12.dp, bottom = 10.dp), style = MaterialTheme.typography.bodySmall)
                            } else {
                                childItems.take(80).forEachIndexed { index, label ->
                                    Text(
                                        text = if (index == childItems.lastIndex) "└─ $label" else "├─ $label",
                                        modifier = Modifier.padding(start = 44.dp, end = 12.dp, bottom = 6.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (childItems.size > 80) {
                                    Text("└─ 还有 ${childItems.size - 80} 条，进入文件夹查看", modifier = Modifier.padding(start = 44.dp, end = 12.dp, bottom = 10.dp), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun entryLabel(entry: CustomEntryEntity): String {
    val title = entry.title.ifBlank {
        richBodyPlainText(entry.content).lineSequence().firstOrNull().orEmpty().take(40).ifBlank { "未命名记录" }
    }
    return if (entry.date.isBlank()) title else "${entry.date} · $title"
}

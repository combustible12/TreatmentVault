package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CustomEntryEntity
import kotlinx.coroutines.flow.map

@Composable
fun NotebookCategoryScreen(categoryId: String, back: () -> Unit, go: (String) -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val category by dao.categories().map { list -> list.firstOrNull { it.key == categoryId } }.collectAsState(initial = null)
    val entries by dao.customEntries(categoryId).collectAsState(initial = emptyList())
    val prefs = remember { app.getSharedPreferences("category_view_preferences", 0) }
    var search by remember { mutableStateOf("") }
    var viewMode by remember(categoryId) { mutableStateOf(prefs.getString("view_$categoryId", "compact") ?: "compact") }

    val filtered = remember(entries, search) {
        val q = search.trim()
        if (q.isBlank()) entries else entries.filter {
            it.title.contains(q, true) || it.content.contains(q, true) || it.tags.contains(q, true) || it.date.contains(q, true)
        }
    }

    fun setView(mode: String) {
        viewMode = mode
        prefs.edit().putString("view_$categoryId", mode).apply()
    }

    Page(category?.name ?: "记录", back) {
        Button(onClick = { go("note/new/$categoryId") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.EditNote, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("新建笔记")
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索标题、正文、标签、日期") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, contentDescription = "清空") } },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = viewMode == "compact", onClick = { setView("compact") }, label = { Text("条目") }, leadingIcon = { Icon(Icons.Default.ViewList, contentDescription = null) })
            FilterChip(selected = viewMode == "grid", onClick = { setView("grid") }, label = { Text("宫格") }, leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) })
            FilterChip(selected = viewMode == "detail", onClick = { setView("detail") }, label = { Text("详情") }, leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) })
        }
        Text(if (search.isBlank()) "共 ${entries.size} 条" else "找到 ${filtered.size} / ${entries.size} 条", style = MaterialTheme.typography.bodySmall)

        if (filtered.isEmpty()) {
            Text(if (entries.isEmpty()) "暂无记录" else "没有匹配的记录")
        } else when (viewMode) {
            "grid" -> LazyVerticalGrid(
                columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(filtered, key = { it.id }) { note -> NoteGridCard(note) { go("note/${note.id}") } }
            }
            "detail" -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { note -> NoteDetailCard(note) { go("note/${note.id}") } }
            }
            else -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.id }) { note -> NoteCompactCard(note) { go("note/${note.id}") } }
            }
        }
    }
}

@Composable
private fun NoteCompactCard(note: CustomEntryEntity, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        headlineContent = { Text(note.title.ifBlank { note.content.lineSequence().firstOrNull().orEmpty().take(40).ifBlank { "未命名笔记" } }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (note.date.isNotBlank()) Text(note.date, style = MaterialTheme.typography.labelSmall)
                if (note.content.isNotBlank()) Text(note.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (note.tags.isNotBlank()) Text("# ${note.tags}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
    )
}

@Composable
private fun NoteGridCard(note: CustomEntryEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().heightIn(min = 130.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(note.title.ifBlank { note.content.take(28).ifBlank { "未命名笔记" } }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (note.date.isNotBlank()) Text(note.date, style = MaterialTheme.typography.labelSmall)
            if (note.content.isNotBlank()) Text(note.content, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            if (note.tags.isNotBlank()) Text("# ${note.tags}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun NoteDetailCard(note: CustomEntryEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(note.title.ifBlank { note.content.take(36).ifBlank { "未命名笔记" } }, style = MaterialTheme.typography.titleMedium)
            if (note.date.isNotBlank()) Text(note.date, style = MaterialTheme.typography.labelSmall)
            if (note.content.isNotBlank()) Text(note.content)
            if (note.tags.isNotBlank()) Text("标签：${note.tags}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

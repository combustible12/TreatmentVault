package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.data.CategoryEntity

internal fun folderPathFor(categoryKey: String, categories: List<CategoryEntity>): List<CategoryEntity> {
    val byKey = categories.associateBy { it.key }
    val reversed = mutableListOf<CategoryEntity>()
    val visited = mutableSetOf<String>()
    var current = byKey[categoryKey]
    while (current != null && visited.add(current.key)) {
        reversed += current
        current = current.parentId?.let(byKey::get)
    }
    return reversed.asReversed()
}

@Composable
internal fun RecordFolderOwnershipDialog(
    item: UnifiedRecordItem,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSelectForMove: (() -> Unit)? = null,
) {
    val path = remember(item.categoryKey, categories) { folderPathFor(item.categoryKey, categories) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("归属文件夹", style = MaterialTheme.typography.titleSmall)
                if (path.isEmpty()) {
                    Text("未找到有效文件夹归属", style = MaterialTheme.typography.bodyMedium)
                } else {
                    path.forEachIndexed { index, folder ->
                        Text(
                            text = buildString {
                                repeat(index) { append("  ") }
                                if (index > 0) append("↳ ")
                                append(folder.name)
                            },
                            style = if (index == path.lastIndex) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (path.size > 1) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            path.joinToString("  ›  ") { it.name },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = onSelectForMove?.let { action ->
            { TextButton(onClick = action) { Text("选择并移动") } }
        },
    )
}

@Composable
internal fun RenameCategoryDialog(
    category: CategoryEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(category.key) { mutableStateOf(category.name) }
    val clean = name.trim()
    val duplicate = categories.any {
        it.key != category.key && it.parentId == category.parentId && it.name.equals(clean, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = { if (duplicate) Text("同一级已经有同名文件夹") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = clean.isNotBlank() && !duplicate && clean != category.name,
                onClick = { onRename(clean) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

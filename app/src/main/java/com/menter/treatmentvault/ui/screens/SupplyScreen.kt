package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.SupplyEntity
import kotlinx.coroutines.launch

@Composable
fun SupplyScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.supplies().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SupplyEntity?>(null) }

    Page("药品与用品", back) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("待购", "已购", "暂不购买").forEach { status ->
                AssistChip(onClick = {}, label = { Text("$status ${list.count { it.purchaseStatus == status }}") })
            }
        }
        Text("点任意记录即可继续编辑和添加图片。", style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(value.name, style = MaterialTheme.typography.titleMedium)
                        Text("${value.purchaseStatus} · ${value.necessity}")
                        if (value.purpose.isNotBlank()) Text(value.purpose)
                        if (value.stages.isNotBlank()) Text(value.stages, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("新增用品")
        }
    }

    if (editorOpen) {
        val initial = editing
        var name by remember(initial?.id, editorOpen) { mutableStateOf(initial?.name.orEmpty()) }
        var stages by remember(initial?.id, editorOpen) { mutableStateOf(initial?.stages.orEmpty()) }
        var purpose by remember(initial?.id, editorOpen) { mutableStateOf(initial?.purpose.orEmpty()) }
        var necessity by remember(initial?.id, editorOpen) { mutableStateOf(initial?.necessity ?: "按需购买") }
        var purchaseStatus by remember(initial?.id, editorOpen) { mutableStateOf(initial?.purchaseStatus ?: "待购") }
        var quantity by remember(initial?.id, editorOpen) { mutableStateOf(initial?.quantity?.toString().orEmpty()) }
        var price by remember(initial?.id, editorOpen) { mutableStateOf(initial?.price?.toString().orEmpty()) }
        var usage by remember(initial?.id, editorOpen) { mutableStateOf(initial?.usage.orEmpty()) }
        var notes by remember(initial?.id, editorOpen) { mutableStateOf(initial?.notes.orEmpty()) }
        var pendingFiles by remember(initial?.id, editorOpen) { mutableStateOf<List<String>>(emptyList()) }

        FullScreenEditorDialog(
            title = if (initial == null) "新增用品" else "编辑用品",
            onDismiss = { editorOpen = false },
            canSave = name.isNotBlank(),
            onSave = {
                if (name.isBlank()) return@FullScreenEditorDialog
                scope.launch {
                    val value = SupplyEntity(
                        id = initial?.id ?: 0,
                        name = name.trim(), stages = stages.trim(), purpose = purpose.trim(), necessity = necessity.trim().ifBlank { "按需购买" },
                        purchaseStatus = purchaseStatus.trim().ifBlank { "待购" }, quantity = quantity.toIntOrNull(), price = price.toDoubleOrNull(),
                        purchaseUrl = initial?.purchaseUrl.orEmpty(), usage = usage.trim(), doctorConfirmed = initial?.doctorConfirmed ?: false,
                        notes = notes.trim(), imagePath = initial?.imagePath.orEmpty(), attachmentPath = initial?.attachmentPath.orEmpty(), updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveSupply(value) else { dao.updateSupply(value); value.id }
                    savePendingAttachments(dao, "supply", id, pendingFiles)
                    editorOpen = false
                }
            },
        ) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Field(name, { name = it }, "名称") }
                item { Field(stages, { stages = it }, "使用阶段") }
                item { Field(purpose, { purpose = it }, "作用") }
                item { Field(necessity, { necessity = it }, "必要性") }
                item { Field(purchaseStatus, { purchaseStatus = it }, "购买状态（待购/已购/暂不购买）") }
                item { Field(quantity, { quantity = it }, "数量") }
                item { Field(price, { price = it }, "价格") }
                item { Field(usage, { usage = it }, "使用方法") }
                item { Field(notes, { notes = it }, "备注") }
                item { ImageAttachmentSection(dao, "supply", initial?.id ?: 0L, pendingFiles) { pendingFiles = it } }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

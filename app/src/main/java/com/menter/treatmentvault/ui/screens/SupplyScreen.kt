package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.SupplyEntity
import kotlinx.coroutines.launch

@Composable
fun SupplyScreen(back: () -> Unit, initialEditId: Long = 0L) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = (context.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.supplies().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SupplyEntity?>(null) }
    var openedInitial by remember(initialEditId) { mutableStateOf(false) }

    LaunchedEffect(initialEditId, list) {
        if (!openedInitial && initialEditId > 0L) {
            list.firstOrNull { it.id.toLong() == initialEditId }?.let {
                editing = it
                editorOpen = true
                openedInitial = true
            }
        }
    }

    Page("药品与用品", back) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("待购", "已购", "暂不购买").forEach { status -> AssistChip(onClick = {}, label = { Text("$status ${list.count { it.purchaseStatus == status }}") }) }
        }
        Text("名称下面直接记文字和图片；购买状态、使用阶段等放到更多信息里。", style = MaterialTheme.typography.bodySmall)
        FastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(value.name, style = MaterialTheme.typography.titleMedium)
                        Text("${value.purchaseStatus} · ${value.necessity}", style = MaterialTheme.typography.bodySmall)
                        val body = richBodyPlainText(value.notes).ifBlank { value.purpose }
                        if (body.isNotBlank()) Text(body, maxLines = 3)
                    }
                }
            }
        }
        Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("新增药品/用品")
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
        var removedFiles by remember(initial?.id, editorOpen) { mutableStateOf<Set<String>>(emptySet()) }
        var detailsOpen by remember(initial?.id, editorOpen) { mutableStateOf(false) }

        FullScreenEditorDialog(
            title = if (initial == null) "新增药品/用品" else "编辑药品/用品",
            onDismiss = { editorOpen = false },
            canSave = name.isNotBlank() || richBodyHasContent(notes) || pendingFiles.isNotEmpty(),
            hasPersistedRecord = initial != null,
            onSave = {
                if (initial == null && name.isBlank() && !richBodyHasContent(notes) && pendingFiles.isEmpty()) {
                    editorOpen = false
                    return@FullScreenEditorDialog
                }
                scope.launch {
                    val value = SupplyEntity(
                        id = initial?.id ?: 0,
                        name = name.trim().ifBlank { "未命名用品" },
                        stages = stages.trim(),
                        purpose = purpose.trim(),
                        necessity = necessity.trim().ifBlank { "按需购买" },
                        purchaseStatus = purchaseStatus.trim().ifBlank { "待购" },
                        quantity = quantity.toIntOrNull(),
                        price = price.toDoubleOrNull(),
                        purchaseUrl = initial?.purchaseUrl.orEmpty(),
                        usage = usage.trim(),
                        doctorConfirmed = initial?.doctorConfirmed ?: false,
                        notes = notes,
                        imagePath = initial?.imagePath.orEmpty(),
                        attachmentPath = initial?.attachmentPath.orEmpty(),
                        updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveSupply(value) else { dao.updateSupply(value); value.id }
                    reconcileRichAttachments(context, dao, "supply", id, notes, removedFiles)
                    pendingFiles = emptyList()
                    removedFiles = emptySet()
                    editorOpen = false
                }
            },
        ) {
            FastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Field(name, { name = it }, "标题/名称") }
                item {
                    RichNoteBodyEditor(
                        dao = dao,
                        ownerType = "supply",
                        ownerId = initial?.id ?: 0L,
                        value = notes,
                        onValueChange = { notes = it },
                        pendingFiles = pendingFiles,
                        onPendingFilesChange = { pendingFiles = it },
                        removedFiles = removedFiles,
                        onRemovedFilesChange = { removedFiles = it },
                        sessionKey = "supply-${initial?.id ?: "new"}",
                    )
                }
                item {
                    TextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (detailsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (detailsOpen) "收起用品信息" else "更多用品信息（全部选填）")
                    }
                }
                if (detailsOpen) {
                    item { Field(stages, { stages = it }, "使用阶段") }
                    item { Field(purpose, { purpose = it }, "作用") }
                    item { Field(necessity, { necessity = it }, "必要性") }
                    item { Field(purchaseStatus, { purchaseStatus = it }, "购买状态（待购/已购/暂不购买）") }
                    item { Field(quantity, { quantity = it }, "数量") }
                    item { Field(price, { price = it }, "价格") }
                    item { Field(usage, { usage = it }, "使用方法") }
                }
            }
        }
    }
}

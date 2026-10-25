package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.ExpenseEntity
import kotlinx.coroutines.launch

@Composable
fun ExpenseScreen(back: () -> Unit, initialEditId: Long = 0L) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = (context.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.expenses().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseEntity?>(null) }
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

    Page("费用记录", back) {
        Text("合计 ¥${"%.2f".format(list.sumOf { it.amount })}", style = MaterialTheme.typography.headlineSmall)
        Text("票据、说明可以直接按笔记方式放文字和图片；报销细项需要时再展开。", style = MaterialTheme.typography.bodySmall)
        FastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(value.type)
                            Text("${value.date} ${value.merchant}")
                            val plain = richBodyPlainText(value.notes)
                            if (plain.isNotBlank()) Text(plain, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        Text("¥${"%.2f".format(value.amount)}")
                    }
                }
            }
        }
        Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("新增费用") }
    }

    if (editorOpen) {
        val initial = editing
        var date by remember(initial?.id, editorOpen) { mutableStateOf(initial?.date.orEmpty()) }
        var type by remember(initial?.id, editorOpen) { mutableStateOf(initial?.type ?: "其他") }
        var amount by remember(initial?.id, editorOpen) { mutableStateOf(initial?.amount?.toString().orEmpty()) }
        var merchant by remember(initial?.id, editorOpen) { mutableStateOf(initial?.merchant.orEmpty()) }
        var insurance by remember(initial?.id, editorOpen) { mutableStateOf(initial?.insuranceAmount?.toString().orEmpty()) }
        var selfPay by remember(initial?.id, editorOpen) { mutableStateOf(initial?.selfPayAmount?.toString().orEmpty()) }
        var reimbursed by remember(initial?.id, editorOpen) { mutableStateOf(initial?.reimbursedAmount?.toString().orEmpty()) }
        var notes by remember(initial?.id, editorOpen) { mutableStateOf(initial?.notes.orEmpty()) }
        var pendingFiles by remember(initial?.id, editorOpen) { mutableStateOf<List<String>>(emptyList()) }
        var removedFiles by remember(initial?.id, editorOpen) { mutableStateOf<Set<String>>(emptySet()) }
        var detailsOpen by remember(initial?.id, editorOpen) { mutableStateOf(false) }

        FullScreenEditorDialog(
            title = if (initial == null) "新增费用" else "编辑费用",
            onDismiss = { closeEditorOrReturnToSource(initialEditId > 0L, { editorOpen = false }, back) },
            canSave = amount.toDoubleOrNull() != null,
            hasPersistedRecord = initial != null,
            onSave = {
                val number = amount.toDoubleOrNull()
                if (number == null) {
                    if (initial == null && !richBodyHasContent(notes) && pendingFiles.isEmpty()) {
                        closeEditorOrReturnToSource(initialEditId > 0L, { editorOpen = false }, back)
                    }
                    return@FullScreenEditorDialog
                }
                scope.launch {
                    val value = ExpenseEntity(
                        id = initial?.id ?: 0,
                        date = date.trim(),
                        type = type.trim().ifBlank { "其他" },
                        amount = number,
                        merchant = merchant.trim(),
                        insuranceAmount = insurance.toDoubleOrNull() ?: 0.0,
                        selfPayAmount = selfPay.toDoubleOrNull() ?: if (initial == null) number else initial.selfPayAmount,
                        reimbursedAmount = reimbursed.toDoubleOrNull() ?: 0.0,
                        notes = notes,
                        receiptPath = initial?.receiptPath.orEmpty(),
                        updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveExpense(value) else { dao.updateExpense(value); value.id }
                    reconcileRichAttachments(context, dao, "expense", id, notes, removedFiles)
                    pendingFiles = emptyList()
                    removedFiles = emptySet()
                    closeEditorOrReturnToSource(initialEditId > 0L, { editorOpen = false }, back)
                }
            },
        ) {
            FastScrollLazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Field(amount, { amount = it }, "金额") }
                item {
                    RichNoteBodyEditor(
                        dao = dao,
                        ownerType = "expense",
                        ownerId = initial?.id ?: 0L,
                        value = notes,
                        onValueChange = { notes = it },
                        pendingFiles = pendingFiles,
                        onPendingFilesChange = { pendingFiles = it },
                        removedFiles = removedFiles,
                        onRemovedFilesChange = { removedFiles = it },
                        label = "说明与票据",
                        sessionKey = "expense-${initial?.id ?: "new"}",
                    )
                }
                item {
                    TextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (detailsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (detailsOpen) "收起费用信息" else "更多费用信息（选填）")
                    }
                }
                if (detailsOpen) {
                    item { Field(date, { date = it }, "日期") }
                    item { Field(type, { type = it }, "类型") }
                    item { Field(merchant, { merchant = it }, "商家/医院") }
                    item { Field(insurance, { insurance = it }, "医保金额") }
                    item { Field(selfPay, { selfPay = it }, "自费金额") }
                    item { Field(reimbursed, { reimbursed = it }, "已报销金额") }
                }
            }
        }
    }
}

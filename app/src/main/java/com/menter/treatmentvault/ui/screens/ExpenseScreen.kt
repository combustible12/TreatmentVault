package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.ExpenseEntity
import kotlinx.coroutines.launch

@Composable
fun ExpenseScreen(back: () -> Unit) {
    val dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication).database.dao()
    val list by dao.expenses().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseEntity?>(null) }

    Page("费用记录", back) {
        Text("合计 ¥${"%.2f".format(list.sumOf { it.amount })}", style = MaterialTheme.typography.headlineSmall)
        Text("点任意记录即可继续编辑和添加票据图片。", style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth().clickable { editing = value; editorOpen = true }) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(value.type)
                            Text("${value.date} ${value.merchant}")
                            if (value.notes.isNotBlank()) Text(value.notes, style = MaterialTheme.typography.bodySmall)
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

        FullScreenEditorDialog(
            title = if (initial == null) "新增费用" else "编辑费用",
            onDismiss = { editorOpen = false },
            canSave = amount.toDoubleOrNull() != null,
            onSave = {
                val number = amount.toDoubleOrNull() ?: return@FullScreenEditorDialog
                scope.launch {
                    val value = ExpenseEntity(
                        id = initial?.id ?: 0, date = date.trim(), type = type.trim().ifBlank { "其他" }, amount = number, merchant = merchant.trim(),
                        insuranceAmount = insurance.toDoubleOrNull() ?: 0.0,
                        selfPayAmount = selfPay.toDoubleOrNull() ?: if (initial == null) number else initial.selfPayAmount,
                        reimbursedAmount = reimbursed.toDoubleOrNull() ?: 0.0,
                        notes = notes.trim(), receiptPath = initial?.receiptPath.orEmpty(), updatedAt = System.currentTimeMillis(),
                    )
                    val id = if (initial == null) dao.saveExpense(value) else { dao.updateExpense(value); value.id }
                    savePendingAttachments(dao, "expense", id, pendingFiles)
                    editorOpen = false
                }
            },
        ) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Field(date, { date = it }, "日期") }
                item { Field(type, { type = it }, "类型") }
                item { Field(amount, { amount = it }, "金额") }
                item { Field(merchant, { merchant = it }, "商家/医院") }
                item { Field(insurance, { insurance = it }, "医保金额") }
                item { Field(selfPay, { selfPay = it }, "自费金额") }
                item { Field(reimbursed, { reimbursed = it }, "已报销金额") }
                item { Field(notes, { notes = it }, "备注") }
                item { ImageAttachmentSection(dao, "expense", initial?.id ?: 0L, pendingFiles) { pendingFiles = it } }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

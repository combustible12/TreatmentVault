package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    var add by remember { mutableStateOf(false) }

    Page("费用记录", back) {
        Text("合计 ¥${"%.2f".format(list.sumOf { it.amount })}", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(list) { value ->
                Card {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(value.type)
                            Text("${value.date} ${value.merchant}")
                        }
                        Text("¥${"%.2f".format(value.amount)}")
                    }
                }
            }
        }
        Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) {
            Text("新增费用")
        }
    }

    if (add) {
        var date by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("其他") }
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            confirmButton = {
                TextButton(onClick = {
                    amount.toDoubleOrNull()?.let { number ->
                        scope.launch {
                            dao.saveExpense(
                                ExpenseEntity(date = date, type = type, amount = number, selfPayAmount = number)
                            )
                            add = false
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } },
            title = { Text("新增费用") },
            text = {
                Column {
                    Field(date, { date = it }, "日期")
                    Field(type, { type = it }, "类型")
                    Field(amount, { amount = it }, "金额")
                }
            },
        )
    }
}

package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Opens an existing record without navigating away from the list that owns it.
 *
 * Keeping the list destination composed is the source of truth for retaining its
 * query, filters and LazyListState. The platform dialog also handles the Android
 * back gesture as a close action instead of popping the list destination.
 */
@Composable
internal fun RecordDetailDialog(
    route: String,
    onClose: () -> Unit,
    go: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            when {
                route.startsWith("note/") -> {
                    val id = route.substringAfter("note/").substringBefore("/").toLongOrNull()
                    if (id != null) NoteEditorScreen(noteId = id, initialCategoryId = null, back = onClose)
                    else LaunchedEffect(route) { onClose() }
                }
                route.startsWith("supplies/") -> SupplyScreen(back = onClose, initialEditId = route.substringAfterLast("/").toLongOrNull() ?: 0L)
                route.startsWith("exams/") -> ExamScreen(back = onClose, initialEditId = route.substringAfterLast("/").toLongOrNull() ?: 0L)
                route.startsWith("treatment/plan/") -> TreatmentScreen(back = onClose, initialPlanId = route.substringAfterLast("/").toLongOrNull() ?: 0L)
                route.startsWith("treatment/record/") -> TreatmentScreen(back = onClose, initialRecordId = route.substringAfterLast("/").toLongOrNull() ?: 0L)
                route.startsWith("symptoms/") -> SymptomScreen(back = onClose, initialEditId = route.substringAfterLast("/").toLongOrNull() ?: 0L)
                route.startsWith("temperature/") -> TemperatureScreen(back = onClose, initialEditId = route.substringAfterLast("/").toLongOrNull() ?: 0L)
                route.startsWith("questions/") -> QuestionScreen(back = onClose, initialEditId = route.substringAfterLast("/").toLongOrNull() ?: 0L)
                route.startsWith("expenses/") -> ExpenseScreen(back = onClose, initialEditId = route.substringAfterLast("/").toLongOrNull() ?: 0L)
                route.startsWith("timeline/") -> TimelineScreen(back = onClose, go = go, initialEditId = route.substringAfterLast("/").toLongOrNull() ?: 0L)
                else -> LaunchedEffect(route) {
                    onClose()
                    go(route)
                }
            }
        }
    }
}

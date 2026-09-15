package com.menter.treatmentvault.ui.screens

import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.menter.treatmentvault.data.RecordStateEntity

internal fun toggleTodoDisplay(state: RecordStateEntity): RecordStateEntity =
    if (isTodo(state)) state.copy(displayType = "normal", completed = false)
    else state.copy(displayType = "todo", completed = false)

internal fun toggleTodoCompleted(state: RecordStateEntity): RecordStateEntity =
    state.copy(displayType = "todo", completed = !state.completed)

internal fun recordDisplayTitle(item: UnifiedRecordItem, state: RecordStateEntity): String =
    state.customTitle?.trim()?.takeIf { it.isNotEmpty() } ?: item.title

@Composable
internal fun pinnedRecordContainerColor(): Color =
    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)

@Composable
internal fun pinnedRecordContentColor(): Color = MaterialTheme.colorScheme.onSecondaryContainer

@Composable
internal fun pinnedRecordAccentColor(): Color = MaterialTheme.colorScheme.primary

@Composable
internal fun recordBackgroundColor(state: RecordStateEntity, normal: Color = Color.Transparent): Color = when {
    isTodo(state) && state.completed -> MaterialTheme.colorScheme.surfaceVariant
    isTodo(state) -> MaterialTheme.colorScheme.tertiaryContainer
    state.pinned -> pinnedRecordContainerColor()
    else -> normal
}

@Composable
internal fun recordContentColor(state: RecordStateEntity): Color = when {
    isTodo(state) && state.completed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    isTodo(state) -> MaterialTheme.colorScheme.onTertiaryContainer
    state.pinned -> pinnedRecordContentColor()
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
internal fun TodoCheckbox(
    state: RecordStateEntity,
    onStateChange: (RecordStateEntity) -> Unit,
) {
    if (isTodo(state)) {
        Checkbox(
            checked = state.completed,
            onCheckedChange = { onStateChange(state.copy(completed = it)) },
        )
    }
}

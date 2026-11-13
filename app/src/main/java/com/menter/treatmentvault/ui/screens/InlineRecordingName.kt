package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction

/** The displayed name is the input itself: one tap focuses it and opens the IME. */
@Composable
internal fun InlineRecordingName(
    value: String,
    onValueChange: (String) -> Unit,
    onEditStarted: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
) {
    var draft by rememberSaveable { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value) {
        if (value == draft.trim()) dirty = false
        if (!focused && !dirty) draft = value
    }
    BasicTextField(
        value = draft,
        onValueChange = { next ->
            draft = next
            dirty = next.isBlank() || next.trim() != value
            // An empty intermediate value must not erase the saved name.
            if (next.isNotBlank()) onValueChange(next.trim())
        },
        modifier = modifier.onFocusChanged { state ->
            if (state.isFocused && !focused) onEditStarted()
            if (!state.isFocused && focused) {
                if (draft.isBlank()) {
                    draft = value
                    dirty = false
                } else draft = draft.trim()
            }
            focused = state.isFocused
        },
        textStyle = textStyle,
        cursorBrush = SolidColor(textStyle.color),
        singleLine = false,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

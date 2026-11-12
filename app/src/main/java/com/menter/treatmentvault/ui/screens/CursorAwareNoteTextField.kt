package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue

/** Keeps the active insertion line visible when the editor window/IME returns. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CursorAwareNoteTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
) {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    // A paste changes text before the new layout is ready. Never use the previous
    // layout's cursor rectangle with the new selection offset.
    val cursor = layout?.takeIf { it.layoutInput.text.text == value.text }
        ?.getCursorRect(value.selection.end.coerceIn(0, value.text.length))

    LaunchedEffect(focused, windowFocused, imeBottom, value.text, value.selection, cursor) {
        if (focused && windowFocused && cursor != null) {
            // Wait for the resized viewport to be placed, then reveal only the
            // insertion line, not the entire (potentially very tall) text block.
            withFrameNanos { }
            requester.bringIntoView(cursor)
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused },
        textStyle = textStyle,
        onTextLayout = { layout = it },
    )
}

package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private val inlineTodoRegex = Regex("^- \\[([ xX])\\](?:\\s)?(.*)$")
private val inlineTodoPrefixRegex = Regex("(?m)^- \\[[ xX]\\]\\s?")

internal data class InlineTodoLine(
    val text: String,
    val isTodo: Boolean = false,
    val isDone: Boolean = false,
)

internal fun parseInlineTodoLines(value: String): List<InlineTodoLine> {
    if (value.isEmpty()) return listOf(InlineTodoLine(""))
    return value.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { raw ->
        val match = inlineTodoRegex.matchEntire(raw)
        if (match == null) {
            InlineTodoLine(text = raw)
        } else {
            InlineTodoLine(
                text = match.groupValues[2],
                isTodo = true,
                isDone = match.groupValues[1].equals("x", ignoreCase = true),
            )
        }
    }.ifEmpty { listOf(InlineTodoLine("")) }
}

internal fun serializeInlineTodoLines(lines: List<InlineTodoLine>): String =
    lines.ifEmpty { listOf(InlineTodoLine("")) }.joinToString("\n") { line ->
        if (!line.isTodo) line.text
        else "- [${if (line.isDone) "x" else " "}] ${line.text}"
    }

internal fun stripInlineTodoMarkers(value: String): String = inlineTodoPrefixRegex.replace(value, "")

@Composable
private fun InlineTodoCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.size(29.dp),
        shape = CircleShape,
        color = if (checked) accent else Color.Transparent,
        contentColor = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.5.dp, if (checked) accent else outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已完成",
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
internal fun InlineTodoTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val lines = remember(value) { parseInlineTodoLines(value) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }
    val focusRequesters = remember(lines.size) { List(lines.size) { FocusRequester() } }

    fun emit(next: List<InlineTodoLine>) {
        onValueChange(serializeInlineTodoLines(next))
    }

    fun replaceLine(index: Int, next: InlineTodoLine) {
        if (index !in lines.indices) return
        val mutable = lines.toMutableList()
        mutable[index] = next
        emit(mutable)
    }

    fun insertAfter(index: Int, todo: Boolean) {
        val mutable = lines.toMutableList()
        val at = (index + 1).coerceIn(0, mutable.size)
        mutable.add(at, InlineTodoLine(text = "", isTodo = todo, isDone = false))
        focusedIndex = at
        pendingFocusIndex = at
        emit(mutable)
    }

    fun deleteLine(index: Int) {
        if (index !in lines.indices) return
        if (lines.size == 1) {
            emit(listOf(InlineTodoLine("")))
            return
        }
        val mutable = lines.toMutableList()
        mutable.removeAt(index)
        val nextFocus = (index - 1).coerceAtLeast(0).coerceAtMost(mutable.lastIndex)
        focusedIndex = nextFocus
        pendingFocusIndex = nextFocus
        emit(mutable)
    }

    LaunchedEffect(lines.size, pendingFocusIndex) {
        val target = pendingFocusIndex ?: return@LaunchedEffect
        if (target in focusRequesters.indices) {
            runCatching { focusRequesters[target].requestFocus() }
            pendingFocusIndex = null
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            lines.forEachIndexed { index, line ->
                val completedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
                val normalColor = MaterialTheme.colorScheme.onSurface
                val style: TextStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (line.isTodo && line.isDone) completedColor else normalColor,
                    textDecoration = if (line.isTodo && line.isDone) TextDecoration.LineThrough else TextDecoration.None,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (line.isTodo) {
                        Box(modifier = Modifier.width(42.dp), contentAlignment = Alignment.Center) {
                            InlineTodoCheck(
                                checked = line.isDone,
                                onCheckedChange = { checked -> replaceLine(index, line.copy(isDone = checked)) },
                            )
                        }
                    }

                    BasicTextField(
                        value = line.text,
                        onValueChange = { next ->
                            val normalized = next.replace("\r\n", "\n").replace('\r', '\n')
                            if ('\n' !in normalized) {
                                replaceLine(index, line.copy(text = normalized))
                            } else {
                                val parts = normalized.split('\n')
                                val mutable = lines.toMutableList()
                                mutable[index] = line.copy(text = parts.first())
                                var at = index + 1
                                parts.drop(1).forEach { part ->
                                    mutable.add(
                                        at++,
                                        InlineTodoLine(
                                            text = part,
                                            isTodo = line.isTodo,
                                            isDone = false,
                                        )
                                    )
                                }
                                val target = (at - 1).coerceAtMost(mutable.lastIndex)
                                focusedIndex = target
                                pendingFocusIndex = target
                                emit(mutable)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .focusRequester(focusRequesters[index])
                            .onFocusChanged { if (it.isFocused) focusedIndex = index }
                            .padding(horizontal = if (line.isTodo) 4.dp else 8.dp, vertical = 10.dp),
                        textStyle = style,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (line.text.isBlank() && index == 0) {
                                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                inner()
                            }
                        },
                    )

                    if (focusedIndex == index && lines.size > 1) {
                        IconButton(onClick = { deleteLine(index) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除这一行", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val current = lines.getOrNull(focusedIndex) ?: lines.first()
                TextButton(
                    onClick = {
                        val index = focusedIndex.coerceIn(0, lines.lastIndex)
                        val live = lines[index]
                        replaceLine(
                            index,
                            if (live.isTodo) live.copy(isTodo = false, isDone = false)
                            else live.copy(isTodo = true, isDone = false),
                        )
                    },
                ) {
                    Icon(
                        if (current.isTodo) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (current.isTodo) "转普通" else "当前行待办")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { insertAfter(focusedIndex.coerceIn(0, lines.lastIndex), todo = false) }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("文字")
                }
                TextButton(onClick = { insertAfter(focusedIndex.coerceIn(0, lines.lastIndex), todo = true) }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("待办")
                }
            }
        }
    }
}

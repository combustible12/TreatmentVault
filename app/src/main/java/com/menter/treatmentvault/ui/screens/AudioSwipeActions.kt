package com.menter.treatmentvault.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class AudioSwipeAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

// Match MR: a short left swipe reveals icon-only circles; right swipe closes them.
internal fun audioActionsOpenAfterDrag(wasOpen: Boolean, distancePx: Float): Boolean =
    if (distancePx == 0f) wasOpen else distancePx < 0f

@Composable
internal fun AudioSwipeActions(
    identity: Any,
    actions: List<AudioSwipeAction>,
    background: Color,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    val actionWidth = (actions.size * 48 + 8).dp
    val revealPx = with(LocalDensity.current) { actionWidth.toPx() }
    val offset = remember(identity) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var opened by remember(identity) { mutableStateOf(false) }
    var dragDistance by remember(identity) { mutableFloatStateOf(0f) }
    fun settle() { scope.launch { offset.animateTo(if (opened) -revealPx else 0f, tween(180)) } }

    Box(
        Modifier.fillMaxWidth().clip(shape).pointerInput(identity, revealPx) {
            detectHorizontalDragGestures(
                onDragStart = {
                    dragDistance = 0f
                    scope.launch { offset.stop() }
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    dragDistance += amount
                    scope.launch { offset.snapTo((offset.value + amount).coerceIn(-revealPx, 0f)) }
                },
                onDragEnd = {
                    opened = audioActionsOpenAfterDrag(opened, dragDistance)
                    settle()
                },
                onDragCancel = { settle() },
            )
        },
    ) {
        if (offset.value < 0f) {
            Row(
                Modifier.align(Alignment.CenterEnd).width(actionWidth).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    IconButton(
                        onClick = { opened = false; settle(); action.onClick() },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).background(Color(0xFF8A8A8A), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(action.icon, action.label, tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                    }
                }
            }
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }.fillMaxWidth().background(background)) {
            content()
        }
    }
}

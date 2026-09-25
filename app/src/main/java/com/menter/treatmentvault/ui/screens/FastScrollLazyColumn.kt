package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App-wide LazyColumn with a transient, draggable fast-scroll thumb.
 * The caller owns list content/state; this component owns only scrollbar UI/gesture state.
 */
@Composable
internal fun FastScrollLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var lingerVisible by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var dragJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val layoutInfo = state.layoutInfo
    val totalCount = layoutInfo.totalItemsCount
    val visibleCount = layoutInfo.visibleItemsInfo.size
    val scrollable = totalCount > visibleCount && (state.canScrollBackward || state.canScrollForward)

    LaunchedEffect(state.isScrollInProgress, dragging, scrollable) {
        if (!scrollable) {
            lingerVisible = false
        } else if (state.isScrollInProgress || dragging) {
            lingerVisible = true
        } else {
            delay(700)
            lingerVisible = false
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )

        if (scrollable && (state.isScrollInProgress || dragging || lingerVisible)) {
            val denominator = (totalCount - visibleCount).coerceAtLeast(1)
            val progress = (state.firstVisibleItemIndex.toFloat() / denominator).coerceIn(0f, 1f)
            val thumbFraction = (visibleCount.toFloat() / totalCount.toFloat()).coerceIn(0.10f, 0.34f)

            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .fillMaxHeight(0.78f)
                    .width(24.dp)
                    .onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                    .pointerInput(totalCount, trackHeightPx) {
                        fun jump(y: Float) {
                            val ratio = (y / trackHeightPx).coerceIn(0f, 1f)
                            val target = (ratio * (totalCount - 1)).toInt()
                            dragJob?.cancel()
                            dragJob = scope.launch { state.scrollToItem(target) }
                        }
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                jump(offset.y)
                            },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                jump(change.position.y)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        )
                    },
                contentAlignment = Alignment.TopEnd,
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            MaterialTheme.shapes.small,
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer {
                            val thumbHeight = trackHeightPx * thumbFraction
                            translationY = progress * (trackHeightPx - thumbHeight).coerceAtLeast(0f)
                        }
                        .width(8.dp)
                        .fillMaxHeight(thumbFraction)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                            MaterialTheme.shapes.large,
                        ),
                )
            }
        }
    }
}

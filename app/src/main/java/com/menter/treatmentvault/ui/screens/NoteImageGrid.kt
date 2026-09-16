package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.abs

internal data class NoteGridImage(
    val index: Int,
    val block: RichBlock.ImageBlock,
    val file: File,
)

@Composable
internal fun AutomaticNoteImageGrid(
    images: List<NoteGridImage>,
    onOpen: (File) -> Unit,
    onDelete: (Int, RichBlock.ImageBlock) -> Unit,
    onMove: (RichBlock.ImageBlock, Int) -> Unit,
) {
    if (images.isEmpty()) return
    if (images.size == 1) {
        val image = images.first()
        if (image.file.isFile) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 520.dp)
                    .clickable { onOpen(image.file) },
            ) {
                Box {
                    AsyncImage(
                        model = image.file,
                        contentDescription = "图片，点击查看高清原图",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 520.dp),
                        contentScale = ContentScale.Fit,
                    )
                    FilledIconButton(
                        onClick = { onDelete(image.index, image.block) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除图片")
                    }
                }
            }
        } else {
            MissingGridImage(onDelete = { onDelete(image.index, image.block) })
        }
        return
    }

    val dragThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        images.chunked(2).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowImages.forEach { image ->
                    key(image.block.key) {
                        var dragOffset by remember(image.block.key) { mutableStateOf(Offset.Zero) }
                        var dragLocalIndex by remember(image.block.key) { mutableIntStateOf(images.indexOf(image)) }
                        var dragging by remember(image.block.key) { mutableStateOf(false) }
                        LaunchedEffect(images, dragging) {
                            if (!dragging) dragLocalIndex = images.indexOfFirst { it.block.key == image.block.key }.coerceAtLeast(0)
                        }

                        if (image.file.isFile) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .pointerInput(image.block.key, images.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragging = true
                                                dragOffset = Offset.Zero
                                                dragLocalIndex = images.indexOfFirst { it.block.key == image.block.key }.coerceAtLeast(0)
                                            },
                                            onDragCancel = {
                                                dragging = false
                                                dragOffset = Offset.Zero
                                            },
                                            onDragEnd = {
                                                dragging = false
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { _, amount ->
                                                dragOffset += amount
                                                var delta = 0
                                                if (abs(dragOffset.y) >= dragThresholdPx) {
                                                    delta = if (dragOffset.y > 0f) 2 else -2
                                                    dragOffset = Offset(dragOffset.x, dragOffset.y % dragThresholdPx)
                                                } else if (abs(dragOffset.x) >= dragThresholdPx) {
                                                    delta = if (dragOffset.x > 0f) 1 else -1
                                                    dragOffset = Offset(dragOffset.x % dragThresholdPx, dragOffset.y)
                                                }
                                                if (delta != 0) {
                                                    val target = (dragLocalIndex + delta).coerceIn(0, images.lastIndex)
                                                    val actualDelta = target - dragLocalIndex
                                                    if (actualDelta != 0) {
                                                        onMove(image.block, actualDelta)
                                                        dragLocalIndex = target
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    .clickable { onOpen(image.file) },
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = image.file,
                                        contentDescription = "图片，点击查看高清原图；长按拖动可调整顺序",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                    FilledIconButton(
                                        onClick = { onDelete(image.index, image.block) },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(34.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除图片", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.weight(1f)) {
                                MissingGridImage(onDelete = { onDelete(image.index, image.block) })
                            }
                        }
                    }
                }
                if (rowImages.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            "${images.size} 张图片 · 长按图片直接拖动排序 · 点图片查看/编辑，并可继续滑看其他照片",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MissingGridImage(onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().aspectRatio(1f)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.BrokenImage, contentDescription = null)
                Spacer(Modifier.height(4.dp))
                Text("图片暂不可用", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除图片")
            }
        }
    }
}

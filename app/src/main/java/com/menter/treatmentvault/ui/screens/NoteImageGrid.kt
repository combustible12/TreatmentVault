package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

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
    onMove: (Int, Int) -> Unit,
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        images.chunked(2).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowImages.forEach { image ->
                    if (image.file.isFile) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable { onOpen(image.file) },
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = image.file,
                                    contentDescription = "图片，点击查看高清原图",
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
                                Row(modifier = Modifier.align(Alignment.BottomStart).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val localIndex = images.indexOf(image)
                                    FilledIconButton(onClick = { onMove(image.index, -1) }, enabled = localIndex > 0, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ArrowBack, "图片前移", modifier = Modifier.size(17.dp)) }
                                    FilledIconButton(onClick = { onMove(image.index, 1) }, enabled = localIndex in 0 until images.lastIndex, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ArrowForward, "图片后移", modifier = Modifier.size(17.dp)) }
                                }
                            }
                        }
                    } else {
                        Box(Modifier.weight(1f)) {
                            MissingGridImage(onDelete = { onDelete(image.index, image.block) })
                        }
                    }
                }
                if (rowImages.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            "${images.size} 张图片 · 可调整顺序 · 点图片查看/编辑，并可继续滑看其他照片",
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

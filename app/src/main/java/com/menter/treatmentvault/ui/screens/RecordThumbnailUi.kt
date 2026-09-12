package com.menter.treatmentvault.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun RecordThumbnail(
    fileName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    if (fileName.isNullOrBlank()) return
    val context = LocalContext.current
    val file = remember(context, fileName) { attachmentFile(context, fileName) }
    if (!file.isFile) return

    AsyncImage(
        model = file,
        contentDescription = "图片缩略图",
        modifier = modifier.size(size).clip(androidx.compose.material3.MaterialTheme.shapes.medium),
        contentScale = ContentScale.Crop,
    )
}

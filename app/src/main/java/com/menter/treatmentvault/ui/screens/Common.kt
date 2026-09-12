package com.menter.treatmentvault.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.menter.treatmentvault.data.AppDao
import com.menter.treatmentvault.data.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun Field(value: String, on: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = on, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
fun NoteBodyField(value: String, on: (String) -> Unit, label: String = "正文") {
    OutlinedTextField(
        value = value,
        onValueChange = on,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 7,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenEditorDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") } },
                        actions = { TextButton(onClick = onSave, enabled = canSave) { Text("保存") } },
                    )
                },
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}

private fun attachmentDir(context: Context): File = File(context.filesDir, "attachments").apply { mkdirs() }
fun attachmentFile(context: Context, fileName: String): File = File(attachmentDir(context), fileName)

suspend fun importImageToApp(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val mime = context.contentResolver.getType(uri).orEmpty()
    val ext = when {
        mime.contains("png") -> ".png"
        mime.contains("webp") -> ".webp"
        mime.contains("gif") -> ".gif"
        else -> ".jpg"
    }
    val fileName = "${UUID.randomUUID()}$ext"
    val target = attachmentFile(context, fileName)
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "无法读取图片" }
        target.outputStream().use { output -> input.copyTo(output) }
    }
    fileName
}

@Composable
fun ImageAttachmentSection(
    dao: AppDao,
    ownerType: String,
    ownerId: Long,
    pendingFiles: List<String>,
    onPendingFilesChange: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(ownerType, ownerId) { if (ownerId > 0) dao.attachments(ownerType, ownerId) else flowOf(emptyList()) }
    val existing by flow.collectAsState(initial = emptyList())
    var previewFile by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = uris.mapNotNull { uri -> runCatching { importImageToApp(context, uri) }.getOrNull() }
            if (imported.isNotEmpty()) onPendingFilesChange(pendingFiles + imported)
        }
    }

    Text("图片", style = MaterialTheme.typography.titleSmall)
    Text("图片会按笔记方式显示，点图片可全屏查看并双指缩放。", style = MaterialTheme.typography.bodySmall)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        existing.forEach { attachment ->
            val file = attachmentFile(context, attachment.fileName)
            NoteImagePreview(
                file = file,
                onOpen = { previewFile = file },
                onRemove = {
                    scope.launch {
                        dao.deleteAttachment(attachment)
                        withContext(Dispatchers.IO) { file.delete() }
                    }
                },
            )
        }
        pendingFiles.forEach { fileName ->
            val file = attachmentFile(context, fileName)
            NoteImagePreview(
                file = file,
                onOpen = { previewFile = file },
                onRemove = {
                    onPendingFilesChange(pendingFiles - fileName)
                    scope.launch(Dispatchers.IO) { file.delete() }
                },
            )
        }
    }

    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("添加图片")
    }

    previewFile?.let { file ->
        FullScreenImageViewer(file = file, onDismiss = { previewFile = null })
    }
}

@Composable
private fun NoteImagePreview(file: File, onOpen: () -> Unit, onRemove: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp).clickable(onClick = onOpen)) {
            AsyncImage(
                model = file,
                contentDescription = "附件图片，点击放大",
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp),
                contentScale = ContentScale.Fit,
            )
        }
        FilledIconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp)) {
            Icon(Icons.Default.Close, contentDescription = "移除图片")
        }
    }
}

@Composable
private fun FullScreenImageViewer(file: File, onDismiss: () -> Unit) {
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offsetX by remember(file) { mutableFloatStateOf(0f) }
    var offsetY by remember(file) { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = nextScale
        if (nextScale <= 1.01f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = file,
                contentDescription = "大图预览",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    )
                    .transformable(transformState),
                contentScale = ContentScale.Fit,
            )
            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xAA2A2A2A), contentColor = Color.White),
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭大图")
            }
        }
    }
}

suspend fun savePendingAttachments(dao: AppDao, ownerType: String, ownerId: Long, fileNames: List<String>) {
    fileNames.forEach { fileName -> dao.saveAttachment(AttachmentEntity(ownerType = ownerType, ownerId = ownerId, fileName = fileName)) }
}

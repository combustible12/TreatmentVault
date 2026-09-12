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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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

private val imageMarkerRegex = Regex("\\[\\[image:([^\\]]+)]]")

private sealed interface RichBlock {
    val key: String
    data class TextBlock(override val key: String = UUID.randomUUID().toString(), val text: String = "") : RichBlock
    data class ImageBlock(override val key: String = UUID.randomUUID().toString(), val fileName: String) : RichBlock
}

fun richBodyPlainText(value: String): String = imageMarkerRegex.replace(value, "")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

private fun parseRichBody(value: String): List<RichBlock> {
    val result = mutableListOf<RichBlock>()
    var cursor = 0
    imageMarkerRegex.findAll(value).forEach { match ->
        val before = value.substring(cursor, match.range.first).trim('\n')
        if (before.isNotBlank()) result += RichBlock.TextBlock(text = before)
        result += RichBlock.ImageBlock(fileName = match.groupValues[1])
        cursor = match.range.last + 1
    }
    val tail = value.substring(cursor).trim('\n')
    if (tail.isNotBlank()) result += RichBlock.TextBlock(text = tail)
    if (result.none { it is RichBlock.TextBlock }) result.add(0, RichBlock.TextBlock())
    return result
}

private fun serializeRichBody(blocks: List<RichBlock>): String = blocks.joinToString("\n\n") { block ->
    when (block) {
        is RichBlock.TextBlock -> block.text.trimEnd()
        is RichBlock.ImageBlock -> "[[image:${block.fileName}]]"
    }
}.trim()

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
fun RichNoteBodyEditor(
    dao: AppDao,
    ownerType: String,
    ownerId: Long,
    value: String,
    onValueChange: (String) -> Unit,
    pendingFiles: List<String>,
    onPendingFilesChange: (List<String>) -> Unit,
    label: String = "正文",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(ownerType, ownerId) { if (ownerId > 0) dao.attachments(ownerType, ownerId) else flowOf(emptyList()) }
    val existing by flow.collectAsState(initial = emptyList())
    val blocks = remember(ownerType, ownerId) { mutableStateListOf<RichBlock>().apply { addAll(parseRichBody(value)) } }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var insertIndex by remember { mutableIntStateOf(blocks.size) }

    fun sync() = onValueChange(serializeRichBody(blocks))

    LaunchedEffect(existing) {
        val names = blocks.filterIsInstance<RichBlock.ImageBlock>().map { it.fileName }.toSet()
        val missing = existing.filterNot { it.fileName in names }
        if (missing.isNotEmpty()) {
            missing.forEach { blocks += RichBlock.ImageBlock(fileName = it.fileName) }
            sync()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = uris.mapNotNull { uri -> runCatching { importImageToApp(context, uri) }.getOrNull() }
            if (imported.isNotEmpty()) {
                var at = insertIndex.coerceIn(0, blocks.size)
                imported.forEach { fileName ->
                    blocks.add(at, RichBlock.ImageBlock(fileName = fileName))
                    at++
                }
                onPendingFilesChange(pendingFiles + imported)
                sync()
            }
        }
    }

    fun move(from: Int, to: Int) {
        if (from !in blocks.indices || to !in blocks.indices || from == to) return
        val item = blocks.removeAt(from)
        blocks.add(to, item)
        sync()
    }

    fun remove(index: Int, block: RichBlock) {
        when (block) {
            is RichBlock.TextBlock -> {
                if (blocks.size == 1) blocks[index] = block.copy(text = "") else blocks.removeAt(index)
                if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
                sync()
            }
            is RichBlock.ImageBlock -> {
                val old = existing.firstOrNull { it.fileName == block.fileName }
                if (old != null) {
                    scope.launch {
                        dao.deleteAttachment(old)
                        withContext(Dispatchers.IO) { attachmentFile(context, old.fileName).delete() }
                    }
                } else if (block.fileName in pendingFiles) {
                    onPendingFilesChange(pendingFiles - block.fileName)
                    scope.launch(Dispatchers.IO) { attachmentFile(context, block.fileName).delete() }
                }
                blocks.removeAt(index)
                if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
                sync()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text("文字和图片可以任意穿插。点图片看大图；每个区块都能继续往下插文字或图片。", style = MaterialTheme.typography.bodySmall)

        blocks.forEachIndexed { index, block ->
            key(block.key) {
                when (block) {
                    is RichBlock.TextBlock -> {
                        OutlinedTextField(
                            value = block.text,
                            onValueChange = { next ->
                                blocks[index] = block.copy(text = next)
                                sync()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(if (index == 0) "直接写内容…" else "继续写…") },
                            minLines = 3,
                        )
                    }
                    is RichBlock.ImageBlock -> {
                        val file = attachmentFile(context, block.fileName)
                        NoteImagePreview(file = file, onOpen = { previewFile = file })
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "上移")
                    }
                    IconButton(onClick = { move(index, index + 1) }, enabled = index < blocks.lastIndex) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "下移")
                    }
                    TextButton(onClick = {
                        blocks.add(index + 1, RichBlock.TextBlock())
                        sync()
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(3.dp))
                        Text("文字")
                    }
                    TextButton(onClick = {
                        insertIndex = index + 1
                        picker.launch(arrayOf("image/*"))
                    }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(3.dp))
                        Text("图片")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { remove(index, block) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除区块")
                    }
                }
            }
        }
    }

    previewFile?.let { file -> FullScreenImageViewer(file = file, onDismiss = { previewFile = null }) }
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        existing.forEach { attachment ->
            val file = attachmentFile(context, attachment.fileName)
            Box {
                NoteImagePreview(file = file, onOpen = { previewFile = file })
                FilledIconButton(onClick = {
                    scope.launch {
                        dao.deleteAttachment(attachment)
                        withContext(Dispatchers.IO) { file.delete() }
                    }
                }, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "移除图片")
                }
            }
        }
        pendingFiles.forEach { fileName ->
            val file = attachmentFile(context, fileName)
            Box {
                NoteImagePreview(file = file, onOpen = { previewFile = file })
                FilledIconButton(onClick = {
                    onPendingFilesChange(pendingFiles - fileName)
                    scope.launch(Dispatchers.IO) { file.delete() }
                }, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "移除图片")
                }
            }
        }
    }
    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("添加图片")
    }
    previewFile?.let { file -> FullScreenImageViewer(file = file, onDismiss = { previewFile = null }) }
}

@Composable
private fun NoteImagePreview(file: File, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 520.dp).clickable(onClick = onOpen)) {
        AsyncImage(
            model = file,
            contentDescription = "图片，点击放大",
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 520.dp),
            contentScale = ContentScale.Fit,
        )
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
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
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
    fileNames.distinct().forEach { fileName -> dao.saveAttachment(AttachmentEntity(ownerType = ownerType, ownerId = ownerId, fileName = fileName)) }
}

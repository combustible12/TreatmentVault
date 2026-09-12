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
    val categoryKey = when (title) {
        "智能录入" -> "smart_input"
        "总时间轴" -> "timeline"
        "检查报告" -> "exams"
        "治疗方案与记录" -> "treatment"
        "药品用品", "药品与用品" -> "supplies"
        "症状记录" -> "symptoms"
        "体温记录" -> "temperature"
        "医生问诊" -> "questions"
        "费用记录" -> "expenses"
        "导入更新包" -> "import"
        "备份与恢复" -> "settings"
        else -> null
    }
    val displayTitle = categoryKey?.let { categoryDisplayName(it, title) } ?: title

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayTitle) },
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
    OutlinedTextField(value = value, onValueChange = on, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = 7)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenEditorDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean = true,
    hasPersistedRecord: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val requestClose = { if (canSave || hasPersistedRecord) onSave() else onDismiss() }
    Dialog(onDismissRequest = requestClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = { IconButton(onClick = requestClose) { Icon(Icons.Default.Close, contentDescription = "保存并关闭") } },
                        actions = { TextButton(onClick = onSave, enabled = canSave || hasPersistedRecord) { Text("保存") } },
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
    sessionKey: Any = ownerId,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(ownerType, ownerId) { if (ownerId > 0) dao.attachments(ownerType, ownerId) else flowOf(emptyList()) }
    val existing by flow.collectAsState(initial = emptyList())
    val blocks = remember(ownerType, sessionKey) { mutableStateListOf<RichBlock>() }
    var lastSerializedValue by remember(ownerType, sessionKey) { mutableStateOf<String?>(null) }
    var hydrated by remember(ownerType, sessionKey) { mutableStateOf(false) }
    var legacyRecovered by remember(ownerType, sessionKey) { mutableStateOf(false) }
    var previewFile by remember(ownerType, sessionKey) { mutableStateOf<File?>(null) }
    var insertIndex by remember(ownerType, sessionKey) { mutableIntStateOf(0) }

    fun sync() {
        val serialized = serializeRichBody(blocks)
        lastSerializedValue = serialized
        onValueChange(serialized)
    }

    LaunchedEffect(ownerType, sessionKey, value) {
        if (lastSerializedValue == null || value != lastSerializedValue) {
            blocks.clear()
            blocks.addAll(parseRichBody(value))
            lastSerializedValue = value
            insertIndex = blocks.size
            hydrated = true
        }
    }

    LaunchedEffect(existing, hydrated) {
        if (!hydrated || legacyRecovered) return@LaunchedEffect
        legacyRecovered = true
        val current = serializeRichBody(blocks)
        val recovered = recoverLegacyImageMarkers(current, existing.map { it.fileName })
        if (recovered != current) {
            blocks.clear()
            blocks.addAll(parseRichBody(recovered))
            lastSerializedValue = recovered
            insertIndex = blocks.size
            onValueChange(recovered)
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
                insertIndex = at
                onPendingFilesChange((pendingFiles + imported).distinct())
                sync()
            }
        }
    }

    fun move(from: Int, to: Int) {
        if (from !in blocks.indices || to !in blocks.indices || from == to) return
        val item = blocks.removeAt(from)
        blocks.add(to, item)
        insertIndex = (to + 1).coerceAtMost(blocks.size)
        sync()
    }

    fun remove(index: Int, block: RichBlock) {
        when (block) {
            is RichBlock.TextBlock -> {
                if (blocks.size == 1) blocks[index] = block.copy(text = "") else blocks.removeAt(index)
                if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
            }
            is RichBlock.ImageBlock -> {
                if (block.fileName in pendingFiles && existing.none { it.fileName == block.fileName }) {
                    onPendingFilesChange(pendingFiles - block.fileName)
                    scope.launch(Dispatchers.IO) { attachmentFile(context, block.fileName).delete() }
                }
                blocks.removeAt(index)
                if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
            }
        }
        insertIndex = insertIndex.coerceAtMost(blocks.size)
        sync()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text("文字和图片的当前顺序就是保存顺序；退出再打开也应保持一致。", style = MaterialTheme.typography.bodySmall)

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
                        if (file.isFile) {
                            NoteImagePreview(file = file, onOpen = { previewFile = file })
                        } else {
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.BrokenImage, contentDescription = null)
                                    Text("图片文件暂时不可用，但位置已保留。", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, contentDescription = "上移") }
                    IconButton(onClick = { move(index, index + 1) }, enabled = index < blocks.lastIndex) { Icon(Icons.Default.ArrowDownward, contentDescription = "下移") }
                    TextButton(onClick = { blocks.add(index + 1, RichBlock.TextBlock()); insertIndex = index + 2; sync() }) {
                        Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(3.dp)); Text("文字")
                    }
                    TextButton(onClick = { insertIndex = index + 1; picker.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null); Spacer(Modifier.width(3.dp)); Text("图片")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { remove(index, block) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "删除区块") }
                }
            }
        }
    }

    previewFile?.let { file -> FullScreenImageViewer(file = file, onDismiss = { previewFile = null }) }
}

suspend fun reconcileRichAttachments(
    context: Context,
    dao: AppDao,
    ownerType: String,
    ownerId: Long,
    body: String,
) {
    if (ownerId <= 0L) return
    val desired = richBodyImageNames(body).distinct()
    val desiredSet = desired.toSet()
    val existing = dao.attachmentsForOwner(ownerType, ownerId)
    val existingNames = existing.map { it.fileName }.toSet()

    desired.filterNot { it in existingNames }.forEach { fileName ->
        if (attachmentFile(context, fileName).isFile) {
            dao.saveAttachment(AttachmentEntity(ownerType = ownerType, ownerId = ownerId, fileName = fileName))
        }
    }

    existing.filterNot { it.fileName in desiredSet }.forEach { attachment ->
        dao.deleteAttachment(attachment)
        withContext(Dispatchers.IO) { attachmentFile(context, attachment.fileName).delete() }
    }
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
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY).transformable(transformState),
                contentScale = ContentScale.Fit,
            )
            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xAA2A2A2A), contentColor = Color.White),
            ) { Icon(Icons.Default.Close, contentDescription = "关闭大图") }
        }
    }
}

package com.menter.treatmentvault.ui.screens

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.menter.treatmentvault.data.AppDao
import com.menter.treatmentvault.data.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val webUrlRegex = Regex("(?i)\\bhttps?://[^\\s<>\\\"']+")

private fun detectedWebUrls(text: String): List<String> = webUrlRegex.findAll(text)
    .map { it.value.trimEnd('.', ',', '，', '。', ';', '；', ':', '：', ')', '）', ']', '】') }
    .filter { it.isNotBlank() }
    .distinct()
    .toList()

@Composable
private fun DetectedLinkActions(text: String) {
    val links = remember(text) { detectedWebUrls(text) }
    if (links.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        links.forEach { url ->
            TextButton(
                onClick = { runCatching { uriHandler.openUri(url) } },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

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
    Column {
        OutlinedTextField(value = value, onValueChange = on, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = 7)
        DetectedLinkActions(value)
    }
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
    removedFiles: Set<String> = emptySet(),
    onRemovedFilesChange: (Set<String>) -> Unit = {},
    label: String = "正文",
    sessionKey: Any = ownerId,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existingState by produceState<List<AttachmentEntity>?>(initialValue = if (ownerId > 0) null else emptyList(), ownerType, ownerId) {
        if (ownerId > 0) {
            dao.attachments(ownerType, ownerId).collect { attachments -> this.value = attachments }
        } else {
            this.value = emptyList()
        }
    }
    val existing = existingState.orEmpty()
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

    LaunchedEffect(existingState, hydrated, ownerId) {
        if (!hydrated || legacyRecovered) return@LaunchedEffect
        val current = serializeRichBody(blocks)
        if (ownerId <= 0L || richBodyImageNames(current).isNotEmpty()) {
            legacyRecovered = true
            return@LaunchedEffect
        }
        val loaded = existingState ?: return@LaunchedEffect
        legacyRecovered = true
        val recovered = recoverLegacyImageMarkers(current, loaded.map { it.fileName }.filterNot { it in removedFiles })
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
                imported.forEach { fileName -> blocks.add(at++, RichBlock.ImageBlock(fileName = fileName)) }
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
                } else if (existing.any { it.fileName == block.fileName }) {
                    onRemovedFilesChange(removedFiles + block.fileName)
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
        Text("文字、图片和链接按当前顺序保存；图片点开看原图，正文中的网页链接可直接打开。", style = MaterialTheme.typography.bodySmall)
        blocks.forEachIndexed { index, block ->
            key(block.key) {
                when (block) {
                    is RichBlock.TextBlock -> {
                        Column {
                            OutlinedTextField(
                                value = block.text,
                                onValueChange = { next -> blocks[index] = block.copy(text = next); sync() },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(if (index == 0) "直接写内容或粘贴链接…" else "继续写…") },
                                minLines = 3,
                            )
                            DetectedLinkActions(block.text)
                        }
                    }
                    is RichBlock.ImageBlock -> {
                        val file = attachmentFile(context, block.fileName)
                        if (file.isFile) NoteImagePreview(file = file, onOpen = { previewFile = file })
                        else Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.BrokenImage, contentDescription = null)
                                Text("图片文件暂时不可用，但位置已保留。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
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
    removedFiles: Set<String> = emptySet(),
) {
    if (ownerId <= 0L) return
    val desired = richBodyImageNames(body).distinct()
    val desiredSet = desired.toSet()
    val existing = dao.attachmentsForOwner(ownerType, ownerId)
    val existingNames = existing.map { it.fileName }.toSet()

    desired.filterNot { it in existingNames }.forEach { fileName ->
        if (attachmentFile(context, fileName).isFile) dao.saveAttachment(AttachmentEntity(ownerType = ownerType, ownerId = ownerId, fileName = fileName))
    }

    val actualRemovals = removedFiles - desiredSet
    existing.filter { it.fileName in actualRemovals }.forEach { attachment ->
        dao.deleteAttachment(attachment)
        withContext(Dispatchers.IO) { attachmentFile(context, attachment.fileName).delete() }
    }
}

@Composable
private fun NoteImagePreview(file: File, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 520.dp).clickable(onClick = onOpen)) {
        AsyncImage(model = file, contentDescription = "图片，点击查看高清原图", modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 520.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun FullScreenImageViewer(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val webView = remember(file.absolutePath) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = false
            settings.allowFileAccess = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            loadUrl(Uri.fromFile(file).toString())
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xAA2A2A2A), contentColor = Color.White),
            ) { Icon(Icons.Default.Close, contentDescription = "关闭高清原图") }
        }
    }
}

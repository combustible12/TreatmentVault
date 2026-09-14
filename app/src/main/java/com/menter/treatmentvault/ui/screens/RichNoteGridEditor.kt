package com.menter.treatmentvault.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.menter.treatmentvault.data.AppDao
import com.menter.treatmentvault.data.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

private val gridEditorWebUrlRegex = Regex("(?i)\\bhttps?://[^\\s<>\\\"']+")

private fun gridEditorUrls(text: String): List<String> = gridEditorWebUrlRegex.findAll(text)
    .map { it.value.trimEnd('.', ',', '，', '。', ';', '；', ':', '：', ')', '）', ']', '】') }
    .filter { it.isNotBlank() }
    .distinct()
    .toList()

@Composable
private fun GridEditorLinkActions(text: String) {
    val links = remember(text) { gridEditorUrls(text) }
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

@Composable
fun RichNoteBodyGridEditor(
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
    val uriHandler = LocalUriHandler.current
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
    var recorderSession by remember(ownerType, sessionKey) { mutableStateOf<AudioRecorderSession?>(null) }
    var audioMessage by remember(ownerType, sessionKey) { mutableStateOf<String?>(null) }
    var expandedLinkKeys by remember(ownerType, sessionKey) { mutableStateOf<Set<String>>(emptySet()) }

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

    fun insertAudioBlock(fileName: String) {
        val at = insertIndex.coerceIn(0, blocks.size)
        blocks.add(at, RichBlock.AudioBlock(fileName = fileName))
        insertIndex = at + 1
        onPendingFilesChange((pendingFiles + fileName).distinct())
        sync()
    }

    fun startRecordingNow() {
        if (recorderSession != null) return
        audioMessage = null
        runCatching { createAudioRecorderSession(context) }
            .onSuccess { recorderSession = it }
            .onFailure { audioMessage = "无法开始录音：${it.message ?: "请检查麦克风"}" }
    }

    fun stopRecordingAndSave() {
        val session = recorderSession ?: return
        recorderSession = null
        if (finishAudioRecording(context, session)) {
            insertAudioBlock(session.fileName)
            audioMessage = null
        } else {
            audioMessage = "录音没有保存成功，请重新录制。"
        }
    }

    fun cancelRecording() {
        val session = recorderSession ?: return
        recorderSession = null
        cancelAudioRecording(context, session)
        audioMessage = null
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecordingNow()
        else audioMessage = "需要麦克风权限才能在应用内录音。"
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

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = uris.mapNotNull { uri -> runCatching { importAudioToApp(context, uri) }.getOrNull() }
            if (imported.isEmpty()) {
                audioMessage = "没有成功导入录音文件。"
            } else {
                var at = insertIndex.coerceIn(0, blocks.size)
                imported.forEach { fileName -> blocks.add(at++, RichBlock.AudioBlock(fileName = fileName)) }
                insertIndex = at
                onPendingFilesChange((pendingFiles + imported).distinct())
                audioMessage = null
                sync()
            }
        }
    }

    val latestRecorderSession by rememberUpdatedState(recorderSession)
    DisposableEffect(ownerType, sessionKey) {
        onDispose { latestRecorderSession?.let { cancelAudioRecording(context, it) } }
    }

    fun currentIndexFor(block: RichBlock): Int = blocks.indexOfFirst { it.key == block.key }

    fun moveBlock(block: RichBlock, delta: Int) {
        val from = currentIndexFor(block)
        val to = from + delta
        if (from !in blocks.indices || to !in blocks.indices) return
        val item = blocks.removeAt(from)
        blocks.add(to, item)
        insertIndex = (to + 1).coerceAtMost(blocks.size)
        sync()
    }

    fun remove(index: Int, block: RichBlock) {
        val actualIndex = currentIndexFor(block).takeIf { it >= 0 } ?: index
        if (actualIndex !in blocks.indices) return
        when (block) {
            is RichBlock.TextBlock -> {
                if (blocks.size == 1) blocks[actualIndex] = block.copy(text = "") else blocks.removeAt(actualIndex)
                if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
            }
            is RichBlock.ImageBlock -> {
                if (block.fileName in pendingFiles && existing.none { it.fileName == block.fileName }) {
                    onPendingFilesChange(pendingFiles - block.fileName)
                    scope.launch(Dispatchers.IO) { attachmentFile(context, block.fileName).delete() }
                } else if (existing.any { it.fileName == block.fileName }) {
                    onRemovedFilesChange(removedFiles + block.fileName)
                }
                blocks.removeAt(actualIndex)
                if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
            }
            is RichBlock.AudioBlock -> {
                if (block.fileName in pendingFiles && existing.none { it.fileName == block.fileName }) {
                    onPendingFilesChange(pendingFiles - block.fileName)
                    scope.launch(Dispatchers.IO) { attachmentFile(context, block.fileName).delete() }
                } else if (existing.any { it.fileName == block.fileName }) {
                    onRemovedFilesChange(removedFiles + block.fileName)
                }
                blocks.removeAt(actualIndex)
                if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
            }
        }
        insertIndex = insertIndex.coerceAtMost(blocks.size)
        sync()
    }

    @Composable
    fun StandardControls(blockIndex: Int, block: RichBlock) {
        val liveIndex = currentIndexFor(block).takeIf { it >= 0 } ?: blockIndex
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { moveBlock(block, -1) }, enabled = liveIndex > 0) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "上移")
            }
            IconButton(onClick = { moveBlock(block, 1) }, enabled = liveIndex in 0 until blocks.lastIndex) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "下移")
            }
            if (block is RichBlock.TextBlock && standaloneWebUrl(block.text) != null) {
                val expanded = block.key in expandedLinkKeys
                IconButton(
                    onClick = {
                        expandedLinkKeys = if (expanded) expandedLinkKeys - block.key else expandedLinkKeys + block.key
                    },
                ) {
                    Icon(
                        if (expanded) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (expanded) "收起链接" else "编辑链接",
                    )
                }
            }
            TextButton(onClick = {
                val at = (currentIndexFor(block) + 1).coerceIn(0, blocks.size)
                blocks.add(at, RichBlock.TextBlock())
                insertIndex = at + 1
                sync()
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(3.dp))
                Text("文字")
            }
            TextButton(onClick = {
                insertIndex = (currentIndexFor(block) + 1).coerceIn(0, blocks.size)
                picker.launch(arrayOf("image/*"))
            }) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(3.dp))
                Text("图片")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { remove(liveIndex, block) }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除区块")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            "文字、图片、录音和链接按当前顺序保存；连续放入 2 张及以上图片会自动变成宫格，点图片仍可查看高清原图。",
            style = MaterialTheme.typography.bodySmall,
        )
        if (recorderSession == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startRecordingNow()
                        } else {
                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("开始录音")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("导入录音")
                }
            }
        } else {
            val session = recorderSession!!
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Column(Modifier.weight(1f)) {
                        Text("正在录音", style = MaterialTheme.typography.titleSmall)
                        RecordingDuration(session.startedAtMs)
                    }
                    TextButton(onClick = { stopRecordingAndSave() }) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("停止并保存")
                    }
                    IconButton(onClick = { cancelRecording() }) {
                        Icon(Icons.Default.Close, contentDescription = "取消录音")
                    }
                }
            }
        }

        audioMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            if (block is RichBlock.ImageBlock) {
                var end = index + 1
                while (end < blocks.size && blocks[end] is RichBlock.ImageBlock) end++
                val imageItems = (index until end).mapNotNull { imageIndex ->
                    val imageBlock = blocks[imageIndex] as? RichBlock.ImageBlock ?: return@mapNotNull null
                    NoteGridImage(
                        index = imageIndex,
                        block = imageBlock,
                        file = attachmentFile(context, imageBlock.fileName),
                    )
                }
                val firstBlock = imageItems.firstOrNull()?.block
                val lastBlock = imageItems.lastOrNull()?.block
                key(imageItems.joinToString("|") { it.block.key }) {
                    AutomaticNoteImageGrid(
                        images = imageItems,
                        onOpen = { previewFile = it },
                        onDelete = { imageIndex, imageBlock -> remove(imageIndex, imageBlock) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                val lastLive = lastBlock?.let(::currentIndexFor)?.takeIf { it >= 0 } ?: blocks.lastIndex
                                val at = (lastLive + 1).coerceIn(0, blocks.size)
                                blocks.add(at, RichBlock.TextBlock())
                                insertIndex = at + 1
                                sync()
                            },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(3.dp))
                            Text("宫格后写文字")
                        }
                        TextButton(
                            onClick = {
                                val lastLive = lastBlock?.let(::currentIndexFor)?.takeIf { it >= 0 } ?: blocks.lastIndex
                                insertIndex = (lastLive + 1).coerceIn(0, blocks.size)
                                picker.launch(arrayOf("image/*"))
                            },
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(3.dp))
                            Text("继续加图")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { firstBlock?.let { moveBlock(it, -1) } },
                            enabled = firstBlock?.let(::currentIndexFor)?.let { it > 0 } == true,
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "第一张图片上移")
                        }
                    }
                }
                index = end
            } else {
                val blockIndex = index
                key(block.key) {
                    when (block) {
                        is RichBlock.TextBlock -> {
                            val compactUrl = standaloneWebUrl(block.text)
                            if (compactUrl != null && block.key !in expandedLinkKeys) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FilledTonalIconButton(onClick = { runCatching { uriHandler.openUri(compactUrl) } }) {
                                        Icon(Icons.Default.Link, contentDescription = "打开链接")
                                    }
                                }
                            } else {
                                Column {
                                    OutlinedTextField(
                                        value = block.text,
                                        onValueChange = { next ->
                                            val liveIndex = currentIndexFor(block)
                                            if (liveIndex >= 0) {
                                                blocks[liveIndex] = block.copy(text = next)
                                                sync()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(if (blockIndex == 0) "直接写内容或粘贴链接…" else "继续写…") },
                                        minLines = 3,
                                    )
                                    GridEditorLinkActions(block.text)
                                }
                            }
                        }
                        is RichBlock.AudioBlock -> {
                            val file = attachmentFile(context, block.fileName)
                            if (file.isFile) {
                                AudioBlockPlayer(file)
                            } else {
                                Card(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Default.LibraryMusic, contentDescription = null)
                                        Text("录音文件暂时不可用，但位置已保留。", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        is RichBlock.ImageBlock -> Unit
                    }
                    StandardControls(blockIndex, block)
                }
                index++
            }
        }
    }

    previewFile?.let { file -> GridFullScreenImageViewer(file = file, onDismiss = { previewFile = null }) }
}

@Composable
private fun GridFullScreenImageViewer(file: File, onDismiss: () -> Unit) {
    val sourceSize = remember(file.absolutePath) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val width = options.outWidth.takeIf { it > 0 } ?: 1
        val height = options.outHeight.takeIf { it > 0 } ?: 1
        width to height
    }
    var quarterTurns by remember(file.absolutePath) { mutableIntStateOf(0) }
    var zoom by remember(file.absolutePath) { mutableFloatStateOf(1f) }
    var pan by remember(file.absolutePath) { mutableStateOf(Offset.Zero) }

    fun resetView() {
        zoom = 1f
        pan = Offset.Zero
    }

    fun rotateBy(turns: Int) {
        quarterTurns = (quarterTurns + turns).mod(4)
        resetView()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(file.absolutePath, quarterTurns) {
                    detectTransformGestures { _, drag, gestureZoom, _ ->
                        val nextZoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                        zoom = nextZoom
                        pan = if (nextZoom <= 1.001f) Offset.Zero else pan + drag
                    }
                }
                .pointerInput(file.absolutePath, quarterTurns) {
                    detectTapGestures(onDoubleTap = { resetView() })
                },
            contentAlignment = Alignment.Center,
        ) {
            val sourceWidth = sourceSize.first.toFloat()
            val sourceHeight = sourceSize.second.toFloat()
            val rotated = quarterTurns % 2 != 0
            val fittedWidthSource = if (rotated) sourceHeight else sourceWidth
            val fittedHeightSource = if (rotated) sourceWidth else sourceHeight
            val fit = minOf(
                maxWidth.value / fittedWidthSource,
                maxHeight.value / fittedHeightSource,
            )
            val imageWidth = (sourceWidth * fit).dp
            val imageHeight = (sourceHeight * fit).dp

            AsyncImage(
                model = file,
                contentDescription = "高清原图",
                modifier = Modifier
                    .size(imageWidth, imageHeight)
                    .graphicsLayer {
                        rotationZ = quarterTurns * 90f
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
                contentScale = ContentScale.FillBounds,
            )

            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xAA2A2A2A),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭高清原图")
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(start = 12.dp, end = 12.dp, bottom = 48.dp),
                color = Color(0xCC222222),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = { rotateBy(-1) },
                        ) {
                            Icon(Icons.Default.RotateLeft, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("左转", color = Color.White)
                        }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = { rotateBy(1) },
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("右转", color = Color.White)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = { resetView() },
                        ) {
                            Text("适应", color = Color.White)
                        }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                quarterTurns = 0
                                resetView()
                            },
                        ) {
                            Text("重置", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

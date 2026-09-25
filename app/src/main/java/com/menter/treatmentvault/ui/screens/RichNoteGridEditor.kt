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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp)); Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun RichNoteBodyGridEditor(
    dao: AppDao, ownerType: String, ownerId: Long, value: String, onValueChange: (String) -> Unit,
    pendingFiles: List<String>, onPendingFilesChange: (List<String>) -> Unit,
    removedFiles: Set<String> = emptySet(), onRemovedFilesChange: (Set<String>) -> Unit = {},
    label: String = "正文", sessionKey: Any = ownerId,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val existingState by produceState<List<AttachmentEntity>?>(initialValue = if (ownerId > 0) null else emptyList(), ownerType, ownerId) {
        if (ownerId > 0) dao.attachments(ownerType, ownerId).collect { attachments -> this.value = attachments } else this.value = emptyList()
    }
    val existing = existingState.orEmpty()
    val blocks = remember(ownerType, sessionKey) { mutableStateListOf<RichBlock>() }
    var lastSerializedValue by remember(ownerType, sessionKey) { mutableStateOf<String?>(null) }
    var hydrated by remember(ownerType, sessionKey) { mutableStateOf(false) }
    var legacyRecovered by remember(ownerType, sessionKey) { mutableStateOf(false) }
    var previewImageName by remember(ownerType, sessionKey) { mutableStateOf<String?>(null) }
    var insertIndex by remember(ownerType, sessionKey) { mutableIntStateOf(0) }
    var recorderSession by remember(ownerType, sessionKey) { mutableStateOf<AudioRecorderSession?>(null) }
    var audioMessage by remember(ownerType, sessionKey) { mutableStateOf<String?>(null) }
    var expandedLinkKeys by remember(ownerType, sessionKey) { mutableStateOf<Set<String>>(emptySet()) }
    var imageMenuExpanded by remember(ownerType, sessionKey) { mutableStateOf(false) }
    var audioMenuExpanded by remember(ownerType, sessionKey) { mutableStateOf(false) }
    val textSelections = remember(ownerType, sessionKey) { mutableStateMapOf<String, TextRange>() }
    val textFocusRequesters = remember(ownerType, sessionKey) { mutableMapOf<String, FocusRequester>() }

    fun sync() { val serialized = serializeRichBody(blocks); lastSerializedValue = serialized; onValueChange(serialized) }

    LaunchedEffect(ownerType, sessionKey, value) {
        if (lastSerializedValue == null || value != lastSerializedValue) {
            blocks.clear(); blocks.addAll(parseRichBody(value)); lastSerializedValue = value; insertIndex = blocks.size; hydrated = true
        }
    }
    LaunchedEffect(existingState, hydrated, ownerId) {
        if (!hydrated || legacyRecovered) return@LaunchedEffect
        val current = serializeRichBody(blocks)
        if (ownerId <= 0L || richBodyImageNames(current).isNotEmpty()) { legacyRecovered = true; return@LaunchedEffect }
        val loaded = existingState ?: return@LaunchedEffect
        legacyRecovered = true
        val recovered = recoverLegacyImageMarkers(current, loaded.map { it.fileName }.filterNot { it in removedFiles })
        if (recovered != current) { blocks.clear(); blocks.addAll(parseRichBody(recovered)); lastSerializedValue = recovered; insertIndex = blocks.size; onValueChange(recovered) }
    }

    fun insertAudioBlock(fileName: String) {
        val at = insertIndex.coerceIn(0, blocks.size); blocks.add(at, RichBlock.AudioBlock(fileName = fileName)); insertIndex = at + 1
        onPendingFilesChange((pendingFiles + fileName).distinct()); sync()
    }
    fun startRecordingNow() {
        if (recorderSession != null) return
        audioMessage = null
        runCatching { createAudioRecorderSession(context) }.onSuccess { recorderSession = it }.onFailure { audioMessage = "无法开始录音：${it.message ?: "请检查麦克风"}" }
    }
    fun stopRecordingAndSave() {
        val session = recorderSession ?: return; recorderSession = null
        if (finishAudioRecording(context, session)) { insertAudioBlock(session.fileName); audioMessage = null } else audioMessage = "录音没有保存成功，请重新录制。"
    }
    fun cancelRecording() { val session = recorderSession ?: return; recorderSession = null; cancelAudioRecording(context, session); audioMessage = null }

    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecordingNow() else audioMessage = "需要麦克风权限才能在应用内录音。"
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = uris.mapNotNull { uri -> runCatching { importImageToApp(context, uri) }.getOrNull() }
            if (imported.isNotEmpty()) {
                var at = insertIndex.coerceIn(0, blocks.size); imported.forEach { fileName -> blocks.add(at++, RichBlock.ImageBlock(fileName = fileName)) }
                insertIndex = at; onPendingFilesChange((pendingFiles + imported).distinct()); sync()
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = uris.mapNotNull { uri -> runCatching { importAudioToApp(context, uri) }.getOrNull() }
            if (imported.isEmpty()) audioMessage = "没有成功导入录音文件。" else {
                var at = insertIndex.coerceIn(0, blocks.size); imported.forEach { fileName -> blocks.add(at++, RichBlock.AudioBlock(fileName = fileName)) }
                insertIndex = at; onPendingFilesChange((pendingFiles + imported).distinct()); audioMessage = null; sync()
            }
        }
    }
    val latestRecorderSession by rememberUpdatedState(recorderSession)
    DisposableEffect(ownerType, sessionKey) { onDispose { latestRecorderSession?.let { cancelAudioRecording(context, it) } } }

    fun currentIndexFor(block: RichBlock): Int = blocks.indexOfFirst { it.key == block.key }
    fun focusRequesterFor(block: RichBlock.TextBlock): FocusRequester =
        textFocusRequesters.getOrPut(block.key) { FocusRequester() }
    fun insertBlankLines(block: RichBlock.TextBlock, blankLineCount: Int) {
        val index = currentIndexFor(block)
        if (index < 0) return
        val selection = (textSelections[block.key] ?: TextRange(block.text.length)).let {
            TextRange(it.start.coerceIn(0, block.text.length), it.end.coerceIn(0, block.text.length))
        }
        val inserted = "\n".repeat(blankLineCount + 1)
        val nextText = block.text.replaceRange(selection.min, selection.max, inserted)
        val nextCursor = selection.min + inserted.length
        blocks[index] = block.copy(text = nextText)
        textSelections[block.key] = TextRange(nextCursor)
        sync()
        focusRequesterFor(block).requestFocus()
    }
    fun moveBlock(block: RichBlock, delta: Int) {
        val from = currentIndexFor(block); val to = from + delta
        if (from !in blocks.indices || to !in blocks.indices) return
        val item = blocks.removeAt(from); blocks.add(to, item); insertIndex = (to + 1).coerceAtMost(blocks.size); sync()
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
                if (block.fileName in pendingFiles && existing.none { it.fileName == block.fileName }) { onPendingFilesChange(pendingFiles - block.fileName); scope.launch(Dispatchers.IO) { attachmentFile(context, block.fileName).delete() } }
                else if (existing.any { it.fileName == block.fileName }) onRemovedFilesChange(removedFiles + block.fileName)
                blocks.removeAt(actualIndex); if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
            }
            is RichBlock.AudioBlock -> {
                if (block.fileName in pendingFiles && existing.none { it.fileName == block.fileName }) { onPendingFilesChange(pendingFiles - block.fileName); scope.launch(Dispatchers.IO) { attachmentFile(context, block.fileName).delete() } }
                else if (existing.any { it.fileName == block.fileName }) onRemovedFilesChange(removedFiles + block.fileName)
                blocks.removeAt(actualIndex); if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
            }
        }
        insertIndex = insertIndex.coerceAtMost(blocks.size); sync()
    }

    @Composable fun StandardControls(blockIndex: Int, block: RichBlock) {
        val liveIndex = currentIndexFor(block).takeIf { it >= 0 } ?: blockIndex
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { moveBlock(block, -1) }, enabled = liveIndex > 0) { Icon(Icons.Default.ArrowUpward, "上移") }
            IconButton(onClick = { moveBlock(block, 1) }, enabled = liveIndex in 0 until blocks.lastIndex) { Icon(Icons.Default.ArrowDownward, "下移") }
            if (block is RichBlock.TextBlock) {
                TextButton(onClick = { insertBlankLines(block, 1) }) { Text("空一行") }
                TextButton(onClick = { insertBlankLines(block, 2) }) { Text("空两行") }
                if (standaloneWebUrl(block.text) != null) {
                    val expanded = block.key in expandedLinkKeys
                    IconButton(onClick = { expandedLinkKeys = if (expanded) expandedLinkKeys - block.key else expandedLinkKeys + block.key }) { Icon(if (expanded) Icons.Default.Check else Icons.Default.Edit, if (expanded) "收起链接" else "编辑链接") }
                }
            }
            Spacer(Modifier.weight(1f)); IconButton(onClick = { remove(liveIndex, block) }) { Icon(Icons.Default.DeleteOutline, "删除区块") }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { imageMenuExpanded = true }) { Icon(Icons.Default.AddPhotoAlternate, "添加图片") }
                DropdownMenu(expanded = imageMenuExpanded, onDismissRequest = { imageMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("图库") }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) }, onClick = { imageMenuExpanded = false; insertIndex = blocks.size; picker.launch(arrayOf("image/*")) })
                }
            }
            Box {
                IconButton(onClick = { audioMenuExpanded = true }) { Icon(Icons.Default.Mic, "录音") }
                DropdownMenu(expanded = audioMenuExpanded, onDismissRequest = { audioMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("开始录音") }, leadingIcon = { Icon(Icons.Default.Mic, null) }, onClick = {
                        audioMenuExpanded = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecordingNow() else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    })
                    DropdownMenuItem(text = { Text("导入录音") }, leadingIcon = { Icon(Icons.Default.LibraryMusic, null) }, onClick = { audioMenuExpanded = false; insertIndex = blocks.size; audioPicker.launch(arrayOf("audio/*")) })
                }
            }
            IconButton(onClick = { val at = blocks.size; blocks.add(RichBlock.TextBlock()); insertIndex = at + 1; sync() }) { Icon(Icons.Default.Add, "新增文字") }
        }

        if (recorderSession != null) {
            val session = recorderSession!!
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); RecordingDuration(session.startedAtMs); Spacer(Modifier.weight(1f))
                    TextButton(onClick = { stopRecordingAndSave() }) { Text("停止并保存") }; IconButton(onClick = { cancelRecording() }) { Icon(Icons.Default.Close, "取消录音") }
                }
            }
        }
        audioMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            if (block is RichBlock.ImageBlock) {
                var end = index + 1; while (end < blocks.size && blocks[end] is RichBlock.ImageBlock) end++
                val imageItems = (index until end).mapNotNull { imageIndex -> val imageBlock = blocks[imageIndex] as? RichBlock.ImageBlock ?: return@mapNotNull null; NoteGridImage(imageIndex, imageBlock, attachmentFile(context, imageBlock.fileName)) }
                val firstBlock = imageItems.firstOrNull()?.block; val lastBlock = imageItems.lastOrNull()?.block
                key(imageItems.joinToString("|") { it.block.key }) {
                    AutomaticNoteImageGrid(images = imageItems, onOpen = { previewImageName = it.name }, onDelete = { imageIndex, imageBlock -> remove(imageIndex, imageBlock) }, onMove = { imageIndex, delta -> (blocks.getOrNull(imageIndex) as? RichBlock.ImageBlock)?.let { moveBlock(it, delta) } })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { val lastLive = lastBlock?.let(::currentIndexFor)?.takeIf { it >= 0 } ?: blocks.lastIndex; val at = (lastLive + 1).coerceIn(0, blocks.size); blocks.add(at, RichBlock.TextBlock()); insertIndex = at + 1; sync() }) { Icon(Icons.Default.Add, "图片后写文字") }
                        IconButton(onClick = { val lastLive = lastBlock?.let(::currentIndexFor)?.takeIf { it >= 0 } ?: blocks.lastIndex; insertIndex = (lastLive + 1).coerceIn(0, blocks.size); picker.launch(arrayOf("image/*")) }) { Icon(Icons.Default.AddPhotoAlternate, "继续加图") }
                        Spacer(Modifier.weight(1f)); IconButton(onClick = { firstBlock?.let { moveBlock(it, -1) } }, enabled = firstBlock?.let(::currentIndexFor)?.let { it > 0 } == true) { Icon(Icons.Default.ArrowUpward, "图片组上移") }
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
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { FilledTonalIconButton(onClick = { runCatching { uriHandler.openUri(compactUrl) } }) { Icon(Icons.Default.Link, "打开链接") } }
                            } else {
                                Column {
                                    BasicTextField(
                                        value = TextFieldValue(
                                            text = block.text,
                                            selection = (textSelections[block.key] ?: TextRange(block.text.length)).let {
                                                TextRange(it.start.coerceIn(0, block.text.length), it.end.coerceIn(0, block.text.length))
                                            },
                                        ),
                                        onValueChange = { next ->
                                            textSelections[block.key] = next.selection
                                            val liveIndex = currentIndexFor(block)
                                            if (liveIndex >= 0) { blocks[liveIndex] = block.copy(text = next.text); sync() }
                                        },
                                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 112.dp).focusRequester(focusRequesterFor(block)).padding(horizontal = 2.dp, vertical = 10.dp),
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    )
                                    GridEditorLinkActions(block.text)
                                }
                            }
                        }
                        is RichBlock.AudioBlock -> {
                            val file = attachmentFile(context, block.fileName)
                            if (file.isFile) AudioBlockPlayer(file) else Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.LibraryMusic, null); Text("录音文件暂时不可用", style = MaterialTheme.typography.bodySmall) }
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

    val galleryFiles = blocks.filterIsInstance<RichBlock.ImageBlock>().map { attachmentFile(context, it.fileName) }.filter { it.isFile }
    previewImageName?.let { selectedName ->
        val initialIndex = galleryFiles.indexOfFirst { it.name == selectedName }.coerceAtLeast(0)
        if (galleryFiles.isNotEmpty()) GridFullScreenImageViewer(galleryFiles, initialIndex) { previewImageName = null }
    }
}

@Composable
private fun GridFullScreenImageViewer(files: List<File>, initialIndex: Int, onDismiss: () -> Unit) {
    var currentIndex by remember(files, initialIndex) { mutableIntStateOf(initialIndex.coerceIn(0, files.lastIndex)) }
    val file = files[currentIndex]
    val sourceSize = remember(file.absolutePath, file.lastModified()) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        (o.outWidth.takeIf { it > 0 } ?: 1) to (o.outHeight.takeIf { it > 0 } ?: 1)
    }
    var quarterTurns by remember(file.absolutePath) { mutableIntStateOf(0) }
    var zoom by remember(file.absolutePath) { mutableFloatStateOf(1f) }
    var pan by remember(file.absolutePath) { mutableStateOf(Offset.Zero) }
    var swipeAccum by remember(file.absolutePath) { mutableStateOf(Offset.Zero) }
    var editing by remember(file.absolutePath) { mutableStateOf(false) }
    var editRevision by remember(file.absolutePath) { mutableIntStateOf(0) }
    var controlsVisible by remember(file.absolutePath) { mutableStateOf(false) }

    fun resetView() {
        zoom = 1f
        pan = Offset.Zero
        swipeAccum = Offset.Zero
    }

    fun rotateBy(turns: Int) {
        quarterTurns = (quarterTurns + turns).mod(4)
        resetView()
    }

    fun moveGallery(delta: Int) {
        if (files.size > 1) {
            currentIndex = (currentIndex + delta + files.size) % files.size
            quarterTurns = 0
            editing = false
            resetView()
        }
    }

    Dialog(
        onDismissRequest = {
            if (editing) editing = false else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (editing) {
            NoteImageEditorContent(
                file = file,
                onDismiss = { editing = false },
                onSaved = {
                    editRevision += 1
                    resetView()
                    editing = false
                },
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Black,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    if (controlsVisible) {
                        Surface(Modifier.fillMaxWidth(), color = Color(0xFF222222), contentColor = Color.White) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton({ rotateBy(-1) }, Modifier.weight(1f)) { Text("左转", color = Color.White) }
                                TextButton({ rotateBy(1) }, Modifier.weight(1f)) { Text("右转", color = Color.White) }
                                TextButton({ resetView() }, Modifier.weight(1f)) { Text("适应", color = Color.White) }
                                TextButton({ quarterTurns = 0; resetView() }, Modifier.weight(1f)) { Text("重置", color = Color.White) }
                                TextButton({ editing = true }, Modifier.weight(1f)) { Text("编辑", color = Color.White) }
                            }
                        }
                    }
                },
            ) { scaffoldPadding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(scaffoldPadding)
                        .pointerInput(file.absolutePath, currentIndex) {
                            detectTransformGestures { _, drag, gestureZoom, _ ->
                                val nextZoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                                zoom = nextZoom
                                if (nextZoom <= 1.001f) {
                                    pan = Offset.Zero
                                    swipeAccum += drag
                                    val x = swipeAccum.x; val y = swipeAccum.y
                                    if (kotlin.math.abs(x) > 120f || kotlin.math.abs(y) > 120f) {
                                        if (kotlin.math.abs(x) >= kotlin.math.abs(y)) moveGallery(if (x < 0) 1 else -1)
                                        else moveGallery(if (y < 0) 1 else -1)
                                        swipeAccum = Offset.Zero
                                    }
                                } else pan += drag
                            }
                        }
                        .combinedClickable(
                            onClick = { controlsVisible = !controlsVisible },
                            onDoubleClick = { resetView() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    key(file.absolutePath, editRevision) {
                        AsyncImage(
                            model = file, contentDescription = "高清原图",
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                rotationZ = quarterTurns * 90f; scaleX = zoom; scaleY = zoom
                                translationX = pan.x; translationY = pan.y
                            },
                            contentScale = ContentScale.Fit,
                        )
                    }
                    FilledIconButton(
                        onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xAA2A2A2A), contentColor = Color.White),
                    ) { Icon(Icons.Default.Close, "关闭高清原图") }
                    if (files.size > 1) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                            color = Color(0x99222222), contentColor = Color.White, shape = MaterialTheme.shapes.large,
                        ) { Text("${currentIndex + 1} / ${files.size} · 左右或上下滑动", Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                    }
                }
            }
        }
    }
}

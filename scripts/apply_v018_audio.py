from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected snippet not found in {path}: {old[:160]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/build.gradle.kts",
    'versionCode = 29\n        versionName = "0.17.0"',
    'versionCode = 30\n        versionName = "0.18.0"',
)

replace_once(
    "app/src/main/AndroidManifest.xml",
    '    <uses-permission android:name="android.permission.INTERNET" />\n',
    '    <uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n',
)

Path("app/src/main/java/com/menter/treatmentvault/ui/screens/RichContent.kt").write_text(r'''package com.menter.treatmentvault.ui.screens

import java.util.UUID

internal val imageMarkerRegex = Regex("\\[\\[image:([^\\]]+)]]")
internal val audioMarkerRegex = Regex("\\[\\[audio:([^\\]]+)]]")
private val mediaMarkerRegex = Regex("\\[\\[(image|audio):([^\\]]+)]]")

internal sealed interface RichBlock {
    val key: String

    data class TextBlock(
        override val key: String = UUID.randomUUID().toString(),
        val text: String = "",
    ) : RichBlock

    data class ImageBlock(
        override val key: String = UUID.randomUUID().toString(),
        val fileName: String,
    ) : RichBlock

    data class AudioBlock(
        override val key: String = UUID.randomUUID().toString(),
        val fileName: String,
    ) : RichBlock
}

fun richBodyPlainText(value: String): String = mediaMarkerRegex.replace(value, "")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

fun richBodyImageNames(value: String): List<String> =
    imageMarkerRegex.findAll(value).map { it.groupValues[1] }.toList()

fun richBodyAudioNames(value: String): List<String> =
    audioMarkerRegex.findAll(value).map { it.groupValues[1] }.toList()

fun richBodyAttachmentNames(value: String): List<String> =
    mediaMarkerRegex.findAll(value).map { it.groupValues[2] }.toList()

fun richBodyHasContent(value: String): Boolean =
    richBodyPlainText(value).isNotBlank() || richBodyAttachmentNames(value).isNotEmpty()

internal fun parseRichBody(value: String): List<RichBlock> {
    val result = mutableListOf<RichBlock>()
    var cursor = 0
    mediaMarkerRegex.findAll(value).forEach { match ->
        val before = value.substring(cursor, match.range.first).trim('\n')
        if (before.isNotEmpty()) result += RichBlock.TextBlock(text = before)
        val fileName = match.groupValues[2]
        result += when (match.groupValues[1]) {
            "audio" -> RichBlock.AudioBlock(fileName = fileName)
            else -> RichBlock.ImageBlock(fileName = fileName)
        }
        cursor = match.range.last + 1
    }
    val tail = value.substring(cursor).trim('\n')
    if (tail.isNotEmpty()) result += RichBlock.TextBlock(text = tail)
    if (result.none { it is RichBlock.TextBlock }) result.add(0, RichBlock.TextBlock())
    return result
}

internal fun serializeRichBody(blocks: List<RichBlock>): String = blocks.joinToString("\n\n") { block ->
    when (block) {
        is RichBlock.TextBlock -> block.text.trimEnd()
        is RichBlock.ImageBlock -> "[[image:${block.fileName}]]"
        is RichBlock.AudioBlock -> "[[audio:${block.fileName}]]"
    }
}.trim()

private fun looksLikeImageAttachment(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

internal fun recoverLegacyImageMarkers(body: String, attachmentNames: List<String>): String {
    if (richBodyImageNames(body).isNotEmpty()) return body
    val imageNames = attachmentNames.filter(::looksLikeImageAttachment).distinct()
    if (imageNames.isEmpty()) return body
    val markers = imageNames.joinToString("\n\n") { "[[image:$it]]" }
    return if (body.isBlank()) markers else body.trimEnd() + "\n\n" + markers
}
''', encoding="utf-8")

Path("app/src/main/java/com/menter/treatmentvault/ui/screens/AudioSupport.kt").write_text(r'''package com.menter.treatmentvault.ui.screens

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class AudioRecorderSession(
    val fileName: String,
    val recorder: MediaRecorder,
    val startedAtMs: Long,
)

@Suppress("DEPRECATION")
internal fun createAudioRecorderSession(context: Context): AudioRecorderSession {
    val fileName = "${UUID.randomUUID()}.m4a"
    val file = attachmentFile(context, fileName)
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
    try {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioChannels(1)
        recorder.setAudioSamplingRate(44_100)
        recorder.setAudioEncodingBitRate(128_000)
        recorder.setOutputFile(file.absolutePath)
        recorder.prepare()
        recorder.start()
    } catch (t: Throwable) {
        runCatching { recorder.release() }
        file.delete()
        throw t
    }
    return AudioRecorderSession(fileName, recorder, SystemClock.elapsedRealtime())
}

internal fun finishAudioRecording(context: Context, session: AudioRecorderSession): Boolean {
    val file = attachmentFile(context, session.fileName)
    val stopped = runCatching { session.recorder.stop() }.isSuccess
    runCatching { session.recorder.release() }
    if (!stopped || !file.isFile || file.length() <= 0L) {
        file.delete()
        return false
    }
    return true
}

internal fun cancelAudioRecording(context: Context, session: AudioRecorderSession) {
    runCatching { session.recorder.stop() }
    runCatching { session.recorder.release() }
    attachmentFile(context, session.fileName).delete()
}

suspend fun importAudioToApp(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
    val ext = when {
        "mpeg" in mime || "mp3" in mime -> ".mp3"
        "wav" in mime -> ".wav"
        "ogg" in mime -> ".ogg"
        "flac" in mime -> ".flac"
        "aac" in mime -> ".aac"
        "amr" in mime -> ".amr"
        else -> ".m4a"
    }
    val fileName = "${UUID.randomUUID()}$ext"
    val target = attachmentFile(context, fileName)
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "无法读取录音文件" }
        target.outputStream().use { output -> input.copyTo(output) }
    }
    fileName
}

private fun formatAudioDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
internal fun RecordingDuration(startedAtMs: Long) {
    var elapsed by remember(startedAtMs) { mutableLongStateOf(0L) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            elapsed = SystemClock.elapsedRealtime() - startedAtMs
            delay(250)
        }
    }
    Text(formatAudioDuration(elapsed), style = MaterialTheme.typography.labelLarge)
}

@Composable
internal fun AudioBlockPlayer(file: java.io.File) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isPlaying by remember(file.absolutePath) { mutableStateOf(false) }
    val player = remember(file.absolutePath) {
        runCatching { MediaPlayer.create(context, Uri.fromFile(file)) }.getOrNull()
    }
    val duration = remember(player) { runCatching { player?.duration?.toLong() ?: 0L }.getOrDefault(0L) }

    DisposableEffect(player) {
        player?.setOnCompletionListener { isPlaying = false }
        onDispose { runCatching { player?.release() } }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.VolumeUp, contentDescription = null)
            IconButton(
                enabled = player != null,
                onClick = {
                    player ?: return@IconButton
                    if (isPlaying) {
                        runCatching { player.pause() }
                        isPlaying = false
                    } else {
                        runCatching { player.start() }.onSuccess { isPlaying = true }
                    }
                },
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "暂停" else "播放")
            }
            Column(Modifier.weight(1f)) {
                Text("录音", style = MaterialTheme.typography.titleSmall)
                Text(if (player == null) "无法播放这个音频" else formatAudioDuration(duration), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
''', encoding="utf-8")

common = Path("app/src/main/java/com/menter/treatmentvault/ui/screens/Common.kt")
text = common.read_text(encoding="utf-8")


def replace_common(old: str, new: str) -> None:
    global text
    if old not in text:
        raise SystemExit(f"Common.kt snippet not found: {old[:160]!r}")
    text = text.replace(old, new, 1)


replace_common(
    "import android.content.Context\nimport android.net.Uri\nimport android.webkit.WebView",
    "import android.Manifest\nimport android.content.Context\nimport android.content.pm.PackageManager\nimport android.net.Uri\nimport android.webkit.WebView",
)
replace_common(
    "import androidx.compose.ui.window.DialogProperties\nimport coil.compose.AsyncImage",
    "import androidx.compose.ui.window.DialogProperties\nimport androidx.core.content.ContextCompat\nimport coil.compose.AsyncImage",
)
replace_common(
    '''    var previewFile by remember(ownerType, sessionKey) { mutableStateOf<File?>(null) }
    var insertIndex by remember(ownerType, sessionKey) { mutableIntStateOf(0) }''',
    '''    var previewFile by remember(ownerType, sessionKey) { mutableStateOf<File?>(null) }
    var insertIndex by remember(ownerType, sessionKey) { mutableIntStateOf(0) }
    var recorderSession by remember(ownerType, sessionKey) { mutableStateOf<AudioRecorderSession?>(null) }
    var audioMessage by remember(ownerType, sessionKey) { mutableStateOf<String?>(null) }''',
)

picker_marker = "    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->"
if picker_marker not in text:
    raise SystemExit("picker marker missing")
audio_logic = r'''    fun insertAudioBlock(fileName: String) {
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

'''
text = text.replace(picker_marker, audio_logic + picker_marker, 1)

image_picker = '''    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
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
'''
if image_picker not in text:
    raise SystemExit("image picker block missing")
audio_picker = image_picker + r'''
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
'''
text = text.replace(image_picker, audio_picker, 1)

replace_common(
    '''            is RichBlock.ImageBlock -> {
                if (block.fileName in pendingFiles && existing.none { it.fileName == block.fileName }) {
                    onPendingFilesChange(pendingFiles - block.fileName)
                    scope.launch(Dispatchers.IO) { attachmentFile(context, block.fileName).delete() }
                } else if (existing.any { it.fileName == block.fileName }) {
                    onRemovedFilesChange(removedFiles + block.fileName)
                }
                blocks.removeAt(index)
                if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
            }
''',
    '''            is RichBlock.ImageBlock -> {
                if (block.fileName in pendingFiles && existing.none { it.fileName == block.fileName }) {
                    onPendingFilesChange(pendingFiles - block.fileName)
                    scope.launch(Dispatchers.IO) { attachmentFile(context, block.fileName).delete() }
                } else if (existing.any { it.fileName == block.fileName }) {
                    onRemovedFilesChange(removedFiles + block.fileName)
                }
                blocks.removeAt(index)
                if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
            }
            is RichBlock.AudioBlock -> {
                if (block.fileName in pendingFiles && existing.none { it.fileName == block.fileName }) {
                    onPendingFilesChange(pendingFiles - block.fileName)
                    scope.launch(Dispatchers.IO) { attachmentFile(context, block.fileName).delete() }
                } else if (existing.any { it.fileName == block.fileName }) {
                    onRemovedFilesChange(removedFiles + block.fileName)
                }
                blocks.removeAt(index)
                if (blocks.none { it is RichBlock.TextBlock }) blocks.add(0, RichBlock.TextBlock())
            }
''',
)

replace_common(
    '        Text("文字、图片和链接按当前顺序保存；图片点开看原图，正文中的网页链接可直接打开。", style = MaterialTheme.typography.bodySmall)\n        blocks.forEachIndexed { index, block ->',
    '''        Text("文字、图片、录音和链接按当前顺序保存；录音可在正文里直接播放。", style = MaterialTheme.typography.bodySmall)
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
                    IconButton(onClick = { cancelRecording() }) { Icon(Icons.Default.Close, contentDescription = "取消录音") }
                }
            }
        }
        audioMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        blocks.forEachIndexed { index, block ->''',
)

replace_common(
    '''                    is RichBlock.ImageBlock -> {
                        val file = attachmentFile(context, block.fileName)
                        if (file.isFile) NoteImagePreview(file = file, onOpen = { previewFile = file })
                        else Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.BrokenImage, contentDescription = null)
                                Text("图片文件暂时不可用，但位置已保留。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
''',
    '''                    is RichBlock.ImageBlock -> {
                        val file = attachmentFile(context, block.fileName)
                        if (file.isFile) NoteImagePreview(file = file, onOpen = { previewFile = file })
                        else Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.BrokenImage, contentDescription = null)
                                Text("图片文件暂时不可用，但位置已保留。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    is RichBlock.AudioBlock -> {
                        val file = attachmentFile(context, block.fileName)
                        if (file.isFile) AudioBlockPlayer(file)
                        else Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.LibraryMusic, contentDescription = null)
                                Text("录音文件暂时不可用，但位置已保留。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
''',
)
replace_common(
    "    val desired = richBodyImageNames(body).distinct()",
    "    val desired = richBodyAttachmentNames(body).distinct()",
)

common.write_text(text, encoding="utf-8")

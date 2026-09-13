package com.menter.treatmentvault.ui.screens

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

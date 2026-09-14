package com.menter.treatmentvault.ui.screens

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

internal data class AudioTimeMarker(
    val positionMs: Long,
    val label: String,
)

private val audioTimeMarkerRegex = Regex("\\[\\[audiomark:(\\d+):([^]]*)]]")

internal fun audioMarkersFromContent(content: String): List<AudioTimeMarker> =
    audioTimeMarkerRegex.findAll(content).mapNotNull { match ->
        val position = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        val rawLabel = match.groupValues.getOrNull(2).orEmpty()
        AudioTimeMarker(position, Uri.decode(rawLabel).ifBlank { "标记" })
    }.sortedBy { it.positionMs }.toList()

internal fun contentWithAudioMarkers(content: String, markers: List<AudioTimeMarker>): String {
    val clean = audioTimeMarkerRegex.replace(content, "")
        .lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trim()
    val encodedMarkers = markers
        .sortedBy { it.positionMs }
        .joinToString("\n") { marker ->
            "[[audiomark:${marker.positionMs}:${Uri.encode(marker.label.trim().ifBlank { "标记" })}]]"
        }
    return listOf(clean, encodedMarkers).filter { it.isNotBlank() }.joinToString("\n")
}

private fun formatAudioTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private class SilenceSkipController(
    private val player: MediaPlayer,
    private val durationMs: Long,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val quietSince = AtomicLong(0L)
    private var visualizer: Visualizer? = null

    fun enable(): Boolean {
        if (visualizer != null) {
            return runCatching {
                visualizer?.enabled = true
                true
            }.getOrDefault(false)
        }
        return runCatching {
            val captureRange = Visualizer.getCaptureSizeRange()
            val captureSize = captureRange.firstOrNull()?.coerceAtLeast(128) ?: 128
            visualizer = Visualizer(player.audioSessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                            val data = waveform ?: return
                            if (data.isEmpty()) return
                            var sum = 0.0
                            data.forEach { sample ->
                                val centered = (sample.toInt() and 0xff) - 128
                                sum += centered * centered.toDouble()
                            }
                            val rms = sqrt(sum / data.size)
                            val now = SystemClock.elapsedRealtime()
                            if (rms <= 4.8) {
                                if (quietSince.get() == 0L) quietSince.compareAndSet(0L, now)
                                val quietFor = now - quietSince.get()
                                if (quietFor >= 900L) {
                                    handler.post {
                                        if (!runCatching { player.isPlaying }.getOrDefault(false)) return@post
                                        val current = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
                                        val target = (current + 750L).coerceAtMost((durationMs - 80L).coerceAtLeast(0L))
                                        if (target > current + 100L) {
                                            runCatching { player.seekTo(target.toInt()) }
                                        }
                                    }
                                    quietSince.set(now)
                                }
                            } else {
                                quietSince.set(0L)
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) = Unit
                    },
                    (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1),
                    true,
                    false,
                )
                enabled = true
            }
            true
        }.getOrElse {
            release()
            false
        }
    }

    fun disable() {
        quietSince.set(0L)
        runCatching { visualizer?.enabled = false }
    }

    fun release() {
        quietSince.set(0L)
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
    }
}

@Composable
internal fun AdvancedAudioPlayer(
    file: File,
    markers: List<AudioTimeMarker>,
    onMarkersChange: (List<AudioTimeMarker>) -> Unit,
) {
    val context = LocalContext.current
    val player = remember(file.absolutePath) {
        runCatching { MediaPlayer.create(context, Uri.fromFile(file)) }.getOrNull()
    }
    val durationMs = remember(player) { runCatching { player?.duration?.toLong() ?: 0L }.getOrDefault(0L) }
    val silenceController = remember(player, durationMs) {
        player?.let { SilenceSkipController(it, durationMs) }
    }

    var isPlaying by remember(file.absolutePath) { mutableStateOf(false) }
    var positionMs by remember(file.absolutePath) { mutableLongStateOf(0L) }
    var dragging by remember(file.absolutePath) { mutableStateOf(false) }
    var dragPositionMs by remember(file.absolutePath) { mutableFloatStateOf(0f) }
    var playbackSpeed by remember(file.absolutePath) { mutableFloatStateOf(1f) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var skipSilence by remember(file.absolutePath) { mutableStateOf(false) }
    var silenceUnavailable by remember(file.absolutePath) { mutableStateOf(false) }
    var markerDialogOpen by remember { mutableStateOf(false) }
    var pendingMarkerMs by remember { mutableLongStateOf(0L) }
    var markerLabel by remember { mutableStateOf("") }
    var editingMarker by remember { mutableStateOf<AudioTimeMarker?>(null) }
    var editingLabel by remember { mutableStateOf("") }

    DisposableEffect(player) {
        player?.setOnCompletionListener {
            isPlaying = false
            positionMs = durationMs
        }
        onDispose {
            silenceController?.release()
            runCatching { player?.release() }
        }
    }

    LaunchedEffect(isPlaying, dragging, player) {
        while (isPlaying && player != null) {
            if (!dragging) {
                positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(positionMs)
            }
            delay(180)
        }
    }

    fun seekTo(targetMs: Long) {
        val safe = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        runCatching { player?.seekTo(safe.toInt()) }
        positionMs = safe
        dragPositionMs = safe.toFloat()
    }

    fun setSpeed(speed: Float) {
        player ?: return
        runCatching {
            val params = player.playbackParams
            params.speed = speed
            player.playbackParams = params
            playbackSpeed = speed
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    enabled = player != null,
                    onClick = {
                        player ?: return@IconButton
                        if (isPlaying) {
                            runCatching { player.pause() }
                            isPlaying = false
                        } else {
                            if (positionMs >= durationMs - 100L) seekTo(0L)
                            runCatching {
                                val params = player.playbackParams
                                params.speed = playbackSpeed
                                player.playbackParams = params
                                player.start()
                            }.onSuccess { isPlaying = true }
                        }
                    },
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                    )
                }

                Column(Modifier.weight(1f)) {
                    Slider(
                        value = if (dragging) dragPositionMs else positionMs.toFloat(),
                        onValueChange = {
                            dragging = true
                            dragPositionMs = it
                        },
                        onValueChangeFinished = {
                            seekTo(dragPositionMs.toLong())
                            dragging = false
                        },
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        enabled = player != null && durationMs > 0L,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatAudioTime(if (dragging) dragPositionMs.toLong() else positionMs), style = MaterialTheme.typography.labelSmall)
                        Text(formatAudioTime(durationMs), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    OutlinedButton(onClick = { speedMenuOpen = true }, enabled = player != null) {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("${playbackSpeed}×")
                    }
                    DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}×") },
                                trailingIcon = {
                                    if (speed == playbackSpeed) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                onClick = {
                                    setSpeed(speed)
                                    speedMenuOpen = false
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text("跳过静音", style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = skipSilence,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val ok = silenceController?.enable() == true
                                skipSilence = ok
                                silenceUnavailable = !ok
                            } else {
                                silenceController?.disable()
                                skipSilence = false
                                silenceUnavailable = false
                            }
                        },
                        enabled = player != null,
                    )
                }
            }

            if (silenceUnavailable) {
                Text("这台设备暂时不支持自动跳过静音，普通播放不受影响。", style = MaterialTheme.typography.bodySmall)
            }

            FilledTonalButton(
                onClick = {
                    pendingMarkerMs = runCatching { player?.currentPosition?.toLong() ?: positionMs }.getOrDefault(positionMs)
                    markerLabel = ""
                    markerDialogOpen = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = player != null,
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("在 ${formatAudioTime(positionMs)} 添加标记")
            }

            Text("标记列表 · ${markers.size}", style = MaterialTheme.typography.titleSmall)
            if (markers.isEmpty()) {
                Text("还没有标记。播放到需要记住的位置时，点“添加标记”。录音时也可以长按悬浮方块快速打标。", style = MaterialTheme.typography.bodySmall)
            } else {
                markers.sortedBy { it.positionMs }.forEach { marker ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { seekTo(marker.positionMs) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssistChip(
                            onClick = { seekTo(marker.positionMs) },
                            label = { Text(formatAudioTime(marker.positionMs)) },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        Text(marker.label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(
                            onClick = {
                                editingMarker = marker
                                editingLabel = marker.label
                            },
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑标记")
                        }
                        IconButton(
                            onClick = { onMarkersChange(markers.filterNot { it === marker }) },
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除标记")
                        }
                    }
                }
            }
        }
    }

    if (markerDialogOpen) {
        AlertDialog(
            onDismissRequest = { markerDialogOpen = false },
            title = { Text("添加时间标记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("时间点：${formatAudioTime(pendingMarkerMs)}")
                    OutlinedTextField(
                        value = markerLabel,
                        onValueChange = { markerLabel = it },
                        label = { Text("标签内容") },
                        placeholder = { Text("例如：医生说到用药剂量") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val label = markerLabel.trim().ifBlank { "标记 ${markers.size + 1}" }
                    onMarkersChange((markers + AudioTimeMarker(pendingMarkerMs, label)).sortedBy { it.positionMs })
                    markerDialogOpen = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { markerDialogOpen = false }) { Text("取消") } },
        )
    }

    editingMarker?.let { target ->
        AlertDialog(
            onDismissRequest = { editingMarker = null },
            title = { Text("编辑标记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("时间点：${formatAudioTime(target.positionMs)}")
                    OutlinedTextField(
                        value = editingLabel,
                        onValueChange = { editingLabel = it },
                        label = { Text("标签内容") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newLabel = editingLabel.trim().ifBlank { target.label }
                    var replaced = false
                    val updated = markers.map { marker ->
                        if (!replaced && marker === target) {
                            replaced = true
                            marker.copy(label = newLabel)
                        } else marker
                    }
                    onMarkersChange(updated)
                    editingMarker = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingMarker = null }) { Text("取消") } },
        )
    }
}

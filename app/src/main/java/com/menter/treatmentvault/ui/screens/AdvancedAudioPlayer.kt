package com.menter.treatmentvault.ui.screens

import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

internal data class AudioTimeMarker(
    val positionMs: Long,
    val label: String,
)

private fun decodeAudioMarkerLabel(value: String): String =
    runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

private fun encodeAudioMarkerLabel(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private val audioTimeMarkerRegex = Regex("\\[\\[audiomark:(\\d+):([^]]*)]]")

internal fun audioMarkersFromContent(content: String): List<AudioTimeMarker> =
    audioTimeMarkerRegex.findAll(content).mapNotNull { match ->
        val position = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        val rawLabel = match.groupValues.getOrNull(2).orEmpty()
        AudioTimeMarker(position, decodeAudioMarkerLabel(rawLabel).ifBlank { "标记" })
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
            "[[audiomark:${marker.positionMs}:${encodeAudioMarkerLabel(marker.label.trim().ifBlank { "标记" })}]]"
        }
    return listOf(clean, encodedMarkers).filter { it.isNotBlank() }.joinToString("\n")
}

internal fun formatAudioTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

internal fun audioFileDurationMs(file: File): Long {
    if (!file.isFile) return 0L
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdvancedAudioPlayer(
    file: File,
    durationHintMs: Long,
    active: Boolean,
    expanded: Boolean,
    onCollapse: () -> Unit,
    markers: List<AudioTimeMarker>,
    onMarkersChange: (List<AudioTimeMarker>) -> Unit,
    playRequest: Int = 0,
    onPlayingChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    // Inactive rows show a progress bar without opening a decoder for every recording.
    val player = remember(file.absolutePath, active) {
        if (active) runCatching { MediaPlayer.create(context, Uri.fromFile(file)) }.getOrNull() else null
    }
    val durationMs = remember(player, durationHintMs) {
        runCatching { player?.duration?.toLong() ?: durationHintMs }.getOrDefault(durationHintMs)
    }
    val silenceController = remember(player, durationMs) {
        player?.let { SilenceSkipController(it, durationMs) }
    }

    var isPlaying by remember(file.absolutePath) { mutableStateOf(false) }
    var positionMs by rememberSaveable(file.absolutePath) { mutableLongStateOf(0L) }
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
    var handledPlayRequest by rememberSaveable(file.absolutePath) { mutableIntStateOf(0) }

    DisposableEffect(player) {
        runCatching { player?.seekTo(positionMs.toInt()) }
        player?.setOnCompletionListener {
            isPlaying = false
            positionMs = durationMs
            onPlayingChange(false)
        }
        onDispose {
            isPlaying = false
            skipSilence = false
            onPlayingChange(false)
            silenceController?.release()
            runCatching { player?.release() }
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

    fun togglePlayback() {
        player ?: return
        if (isPlaying) {
            runCatching { player.pause() }
            isPlaying = false
            onPlayingChange(false)
        } else {
            if (positionMs >= durationMs - 100L) seekTo(0L)
            runCatching {
                val params = player.playbackParams
                params.speed = playbackSpeed
                player.playbackParams = params
                player.start()
            }.onSuccess {
                isPlaying = true
                onPlayingChange(true)
            }
        }
    }

    LaunchedEffect(playRequest, active) {
        if (active && playRequest > handledPlayRequest) {
            handledPlayRequest = playRequest
            togglePlayback()
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Slider(
                modifier = Modifier.fillMaxWidth().height(20.dp),
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
                enabled = durationMs > 0L,
                // Keep Slider's seeking, touch handling and accessibility; replace
                // the default thick track, vertical handle and terminal stop dot.
                thumb = {
                    // The Slider's thumb slot has a minimum height. Center the
                    // small circle within it instead of leaving it at the top.
                    Box(Modifier.width(10.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(10.dp).background(Color(0xFF8A8A8A), CircleShape))
                    }
                },
                track = { state ->
                    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        val range = state.valueRange.endInclusive - state.valueRange.start
                        val fraction = if (range > 0f) {
                            ((state.value - state.valueRange.start) / range).coerceIn(0f, 1f)
                        } else 0f
                        val startX = if (layoutDirection == LayoutDirection.Rtl) size.width else 0f
                        val endX = if (layoutDirection == LayoutDirection.Rtl) 0f else size.width
                        val start = Offset(startX, center.y)
                        drawLine(Color(0xFFD4D4D4), start, Offset(endX, center.y), size.height, StrokeCap.Round)
                        if (fraction > 0f) {
                            drawLine(
                                Color(0xFF8A8A8A), start,
                                Offset(startX + (endX - startX) * fraction, center.y),
                                size.height, StrokeCap.Round,
                            )
                        }
                    }
                },
            )
            if (expanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatAudioTime(if (dragging) dragPositionMs.toLong() else positionMs), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal, color = Color(0xFF858585))
                    Text(formatAudioTime(durationMs), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal, color = Color(0xFF858585))
                }
            }
        }

        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    Box(Modifier.height(32.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            "${playbackSpeed}×",
                            modifier = Modifier.clickable(enabled = player != null, onClickLabel = "播放速度") { speedMenuOpen = true },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF858585),
                        )
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
                    Text("跳过静音", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal)
                    Switch(
                        modifier = Modifier.size(width = 44.dp, height = 32.dp).scale(0.75f),
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
                        colors = SwitchDefaults.colors(
                            uncheckedTrackColor = Color(0xFFD9D9D9),
                            uncheckedThumbColor = Color.White,
                            uncheckedBorderColor = Color.Transparent,
                            checkedTrackColor = Color(0xFF8A8A8A),
                            checkedThumbColor = Color.White,
                            checkedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }

            if (silenceUnavailable) {
                Text("这台设备暂时不支持自动跳过静音，普通播放不受影响。", style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlatButton(
                    onClick = {
                        pendingMarkerMs = runCatching { player?.currentPosition?.toLong() ?: positionMs }.getOrDefault(positionMs)
                        markerLabel = ""
                        markerDialogOpen = true
                    },
                    modifier = Modifier.weight(5f).height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = player != null,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) { Text("添加标记", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, color = Color(0xFF858585)) }
                FlatButton(
                    onClick = onCollapse,
                    modifier = Modifier.weight(1f).height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        Icons.Rounded.ArrowDropUp,
                        contentDescription = "收起详情和标记",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("标记列表 · ${markers.size}", style = MaterialTheme.typography.titleSmall, fontSize = 13.sp, color = Color(0xFF686868))
            if (markers.isEmpty()) {
                Text("还没有标记。播放到需要记住的位置时，点“添加标记”。录音时也可以长按悬浮方块快速打标。", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    markers.sortedBy { it.positionMs }.forEachIndexed { index, marker ->
                        key(marker.positionMs, marker.label, index) {
                            AudioSwipeActions(
                                identity = marker,
                                actions = listOf(AudioSwipeAction("删除标记", Icons.Rounded.Delete) {
                                    onMarkersChange(markers.filterNot { it === marker })
                                }),
                                background = MaterialTheme.colorScheme.surfaceContainerHighest,
                                actionBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
                                actionCellSizeDp = 32,
                                actionCircleSizeDp = 28,
                                actionIconSizeDp = 20,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        formatAudioTime(marker.positionMs),
                                        modifier = Modifier.clickable(onClickLabel = "跳到此时间") { seekTo(marker.positionMs) }
                                            .padding(vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF858585),
                                    )
                                    Text(
                                        marker.label,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF686868),
                                        modifier = Modifier.weight(1f).clickable(onClickLabel = "修改标记名称") {
                                            editingMarker = marker
                                            editingLabel = marker.label
                                        }.padding(vertical = 6.dp),
                                    )
                                }
                            }
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
                    FlatTextField(
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
                    FlatTextField(
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

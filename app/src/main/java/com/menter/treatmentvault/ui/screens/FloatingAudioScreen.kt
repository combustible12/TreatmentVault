package com.menter.treatmentvault.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CustomEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREF_AUDIO_GRID_MODE = "audio_grid_mode"

@Composable
fun FloatingAudioScreen(back: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val entries by dao.allCustomEntriesFlow().collectAsState(emptyList())
    val recordings = remember(entries) {
        entries.filter { it.categoryId == "floating_audio" }.sortedByDescending { it.updatedAt }
    }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(FloatingRecorderService.PREFS_NAME, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(FloatingRecorderService.PREF_ENABLED, false)) }
    var message by remember { mutableStateOf("") }
    var expandedRecordingId by remember { mutableStateOf<Long?>(null) }
    var gridMode by remember { mutableStateOf(prefs.getBoolean(PREF_AUDIO_GRID_MODE, false)) }
    var pendingDelete by remember { mutableStateOf<CustomEntryEntity?>(null) }

    fun setGridMode(value: Boolean) {
        gridMode = value
        prefs.edit().putBoolean(PREF_AUDIO_GRID_MODE, value).apply()
    }

    fun startFloatingRecorder() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, FloatingRecorderService::class.java).setAction(FloatingRecorderService.ACTION_ENABLE),
        )
        enabled = true
        message = "悬浮球已开启。退出应用后也可以直接点球开始录音。"
    }

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) {
            startFloatingRecorder()
        } else {
            enabled = false
            message = "需要允许“显示在其他应用上层”，悬浮球才能常驻。"
        }
    }

    fun requestOverlayOrStart() {
        if (Settings.canDrawOverlays(context)) {
            startFloatingRecorder()
        } else {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestOverlayOrStart()
        else {
            enabled = false
            message = "需要麦克风权限才能录音。"
        }
    }

    fun enableRecorder() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            requestOverlayOrStart()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun disableRecorder() {
        context.stopService(Intent(context, FloatingRecorderService::class.java))
        enabled = false
        message = "悬浮球已关闭。"
    }

    fun updateMarkers(entry: CustomEntryEntity, nextMarkers: List<AudioTimeMarker>) {
        scope.launch {
            dao.updateCustomEntry(
                entry.copy(
                    content = contentWithAudioMarkers(entry.content, nextMarkers),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun deleteRecording(entry: CustomEntryEntity) {
        scope.launch {
            val attachmentRows = dao.attachmentsForOwner("note", entry.id)
            val fileNames = (attachmentRows.map { it.fileName } + richBodyAttachmentNames(entry.content)).distinct()
            dao.clearAttachmentsForOwner("note", entry.id)
            dao.deleteRecordState("note", entry.id)
            dao.deleteRecordFolder("note", entry.id)
            dao.deleteCustomEntryById(entry.id)
            withContext(Dispatchers.IO) {
                fileNames.forEach { fileName -> runCatching { attachmentFile(context, fileName).delete() } }
            }
            if (expandedRecordingId == entry.id) expandedRecordingId = null
            pendingDelete = null
            message = "录音已删除。"
        }
    }

    Page("悬浮录音", back) {
        if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FloatingRecorderSettingsCard(
                        enabled = enabled,
                        message = message,
                        onEnabledChange = { checked -> if (checked) enableRecorder() else disableRecorder() },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RecordingLayoutHeader(
                        count = recordings.size,
                        gridMode = true,
                        onGridModeChange = ::setGridMode,
                    )
                }
                if (recordings.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyRecordingCard() }
                } else {
                    gridItems(
                        items = recordings,
                        key = { it.id },
                        span = { entry ->
                            if (expandedRecordingId == entry.id) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                        },
                    ) { entry ->
                        RecordingCard(
                            context = context,
                            entry = entry,
                            compact = expandedRecordingId != entry.id,
                            expanded = expandedRecordingId == entry.id,
                            onToggleExpanded = {
                                expandedRecordingId = if (expandedRecordingId == entry.id) null else entry.id
                            },
                            onDelete = { pendingDelete = entry },
                            onMarkersChange = { updateMarkers(entry, it) },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    FloatingRecorderSettingsCard(
                        enabled = enabled,
                        message = message,
                        onEnabledChange = { checked -> if (checked) enableRecorder() else disableRecorder() },
                    )
                }
                item {
                    RecordingLayoutHeader(
                        count = recordings.size,
                        gridMode = false,
                        onGridModeChange = ::setGridMode,
                    )
                }
                if (recordings.isEmpty()) {
                    item { EmptyRecordingCard() }
                } else {
                    listItems(recordings, key = { it.id }) { entry ->
                        RecordingCard(
                            context = context,
                            entry = entry,
                            compact = false,
                            expanded = expandedRecordingId == entry.id,
                            onToggleExpanded = {
                                expandedRecordingId = if (expandedRecordingId == entry.id) null else entry.id
                            },
                            onDelete = { pendingDelete = entry },
                            onMarkersChange = { updateMarkers(entry, it) },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条录音？") },
            text = {
                Text("“${entry.title.ifBlank { "录音" }}”会连同音频文件和时间标记一起永久删除，无法恢复。")
            },
            confirmButton = {
                TextButton(onClick = { deleteRecording(entry) }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FloatingRecorderSettingsCard(
    enabled: Boolean,
    message: String,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("常驻悬浮球", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "开启一次后，你在桌面或其他 App 里都可以直接点球录音，不会跳出录音页面。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Text(
                "待机是半透明圆点；开始录音后会直接变成圆角方块。可拖动位置。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "录音保存后可拖动进度、倍速播放、添加时间标记、从标记列表跳转，也可以删除录音；“跳过静音”只影响播放。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "手机重启后需要回到这里重新开启一次。Android 会保留系统自己的麦克风/前台服务提示。",
                style = MaterialTheme.typography.bodySmall,
            )
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RecordingLayoutHeader(
    count: Int,
    gridMode: Boolean,
    onGridModeChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("悬浮录音记录 · $count", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = { onGridModeChange(false) }) {
                Icon(
                    Icons.Default.ViewList,
                    contentDescription = "列表显示",
                    tint = if (!gridMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onGridModeChange(true) }) {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = "宫格显示",
                    tint = if (gridMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyRecordingCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("还没有悬浮录音", style = MaterialTheme.typography.titleSmall)
            Text("开启悬浮球后，点一下开始录音，再点一下停止；录音会自动出现在这里。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RecordingCard(
    context: Context,
    entry: CustomEntryEntity,
    compact: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDelete: () -> Unit,
    onMarkersChange: (List<AudioTimeMarker>) -> Unit,
) {
    val fileName = remember(entry.content) { richBodyAudioNames(entry.content).firstOrNull() }
    val markers = remember(entry.content) { audioMarkersFromContent(entry.content) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if (compact) 12.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                Text(entry.title.ifBlank { "录音" }, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                if (entry.date.isNotBlank()) Text(entry.date, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                if (markers.isNotEmpty()) Text("${markers.size} 个标记", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(2.dp))
                OutlinedButton(onClick = onToggleExpanded, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ExpandMore, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("播放 / 标记")
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.title.ifBlank { "录音" }, style = MaterialTheme.typography.titleSmall)
                        if (entry.date.isNotBlank()) Text(entry.date, style = MaterialTheme.typography.bodySmall)
                        if (markers.isNotEmpty()) Text("${markers.size} 个时间标记", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除录音")
                    }
                }
                OutlinedButton(onClick = onToggleExpanded, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (expanded) "收起" else "播放 / 标记")
                }
            }

            if (expanded && fileName != null) {
                val file = attachmentFile(context, fileName)
                if (file.isFile) {
                    AdvancedAudioPlayer(
                        file = file,
                        markers = markers,
                        onMarkersChange = onMarkersChange,
                    )
                } else {
                    Text("录音文件不存在", style = MaterialTheme.typography.bodySmall)
                }
            } else if (fileName == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null)
                    Text("没有找到可播放的录音文件", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

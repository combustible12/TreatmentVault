package com.menter.treatmentvault.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CustomEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREF_AUDIO_GRID_MODE = "audio_grid_mode"

internal enum class RecordingMarkerFilter(val label: String) {
    ALL("全部"), MARKED("标记"), UNMARKED("未标记")
}

internal fun filterAudioRecordings(
    entries: List<CustomEntryEntity>,
    query: String,
    markerFilter: RecordingMarkerFilter,
): List<CustomEntryEntity> {
    val cleanQuery = query.trim()
    return entries
        .asSequence()
        .filter { it.categoryId == "floating_audio" }
        .filter { entry ->
            val markers = audioMarkersFromContent(entry.content)
            when (markerFilter) {
                RecordingMarkerFilter.ALL -> true
                RecordingMarkerFilter.MARKED -> markers.isNotEmpty()
                RecordingMarkerFilter.UNMARKED -> markers.isEmpty()
            }
        }
        .filter { entry ->
            if (cleanQuery.isBlank()) true
            else {
                val markerLabels = audioMarkersFromContent(entry.content).joinToString(" ") { it.label }
                listOf(entry.title, entry.date, markerLabels).any { it.contains(cleanQuery, ignoreCase = true) }
            }
        }
        // Renaming and editing markers must never move a recording to the top.
        .sortedWith(
            compareByDescending<CustomEntryEntity> { it.createdAt.takeIf { value -> value > 0L } ?: it.id }
                .thenByDescending { it.id }
        )
        .toList()
}

@Composable
fun FloatingAudioScreen(back: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val entries by dao.allCustomEntriesFlow().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val prefs = remember { context.getSharedPreferences(FloatingRecorderService.PREFS_NAME, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(FloatingRecorderService.PREF_ENABLED, false)) }
    var message by remember { mutableStateOf("") }
    var expandedRecordingId by remember { mutableStateOf<Long?>(null) }
    var playbackRequestRecordingId by remember { mutableStateOf<Long?>(null) }
    var playbackRequestSequence by rememberSaveable { mutableIntStateOf(0) }
    var playingRecordingId by remember { mutableStateOf<Long?>(null) }
    var gridMode by remember { mutableStateOf(prefs.getBoolean(PREF_AUDIO_GRID_MODE, false)) }
    var pendingRenameId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<CustomEntryEntity?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var markerFilterName by rememberSaveable { mutableStateOf(RecordingMarkerFilter.ALL.name) }
    var showFloatingHelp by remember { mutableStateOf(false) }
    val markerFilter = runCatching { RecordingMarkerFilter.valueOf(markerFilterName) }.getOrDefault(RecordingMarkerFilter.ALL)
    val allRecordings = remember(entries) { filterAudioRecordings(entries, "", RecordingMarkerFilter.ALL) }
    val recordings = remember(entries, query, markerFilter) { filterAudioRecordings(entries, query, markerFilter) }

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
            if (playingRecordingId == entry.id) playingRecordingId = null
            pendingDelete = null
            message = "录音已删除。"
        }
    }

    Page("悬浮录音", back) {
        if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FloatingRecorderSettingsCard(
                        enabled = enabled,
                        message = message,
                        onShowHelp = { showFloatingHelp = true },
                        onResetPosition = {
                            ContextCompat.startForegroundService(context, Intent(context, FloatingRecorderService::class.java).setAction(FloatingRecorderService.ACTION_RESET_POSITION))
                            message = "悬浮球已移回屏幕内。"
                        },
                        onEnabledChange = { checked -> if (checked) enableRecorder() else disableRecorder() },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RecordingSearchAndFilters(
                        query = query,
                        onQueryChange = { query = it },
                        selected = markerFilter,
                        onSelected = { markerFilterName = it.name },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RecordingLayoutHeader(
                        count = recordings.size,
                        totalCount = allRecordings.size,
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
                            active = playbackRequestRecordingId == entry.id,
                            playing = playingRecordingId == entry.id,
                            playRequest = if (playbackRequestRecordingId == entry.id) playbackRequestSequence else 0,
                            onPlay = {
                                if (playingRecordingId != entry.id) expandedRecordingId = entry.id
                                playbackRequestRecordingId = entry.id
                                playbackRequestSequence++
                            },
                            onCollapse = { expandedRecordingId = null },
                            onRename = {
                                renameText = entry.title
                                renameError = ""
                                pendingRenameId = entry.id
                            },
                            onDelete = { pendingDelete = entry },
                            onMarkersChange = { updateMarkers(entry, it) },
                            onPlayingChange = { playing -> playingRecordingId = if (playing) entry.id else playingRecordingId.takeUnless { it == entry.id } },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    FloatingRecorderSettingsCard(
                        enabled = enabled,
                        message = message,
                        onShowHelp = { showFloatingHelp = true },
                        onResetPosition = {
                            ContextCompat.startForegroundService(context, Intent(context, FloatingRecorderService::class.java).setAction(FloatingRecorderService.ACTION_RESET_POSITION))
                            message = "悬浮球已移回屏幕内。"
                        },
                        onEnabledChange = { checked -> if (checked) enableRecorder() else disableRecorder() },
                    )
                }
                item {
                    RecordingSearchAndFilters(
                        query = query,
                        onQueryChange = { query = it },
                        selected = markerFilter,
                        onSelected = { markerFilterName = it.name },
                    )
                }
                item {
                    RecordingLayoutHeader(
                        count = recordings.size,
                        totalCount = allRecordings.size,
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
                            active = playbackRequestRecordingId == entry.id,
                            playing = playingRecordingId == entry.id,
                            playRequest = if (playbackRequestRecordingId == entry.id) playbackRequestSequence else 0,
                            onPlay = {
                                if (playingRecordingId != entry.id) expandedRecordingId = entry.id
                                playbackRequestRecordingId = entry.id
                                playbackRequestSequence++
                            },
                            onCollapse = { expandedRecordingId = null },
                            onRename = {
                                renameText = entry.title
                                renameError = ""
                                pendingRenameId = entry.id
                            },
                            onDelete = { pendingDelete = entry },
                            onMarkersChange = { updateMarkers(entry, it) },
                            onPlayingChange = { playing -> playingRecordingId = if (playing) entry.id else playingRecordingId.takeUnless { it == entry.id } },
                        )
                    }
                }
            }
        }
    }

    if (showFloatingHelp) {
        AlertDialog(
            onDismissRequest = { showFloatingHelp = false },
            title = { Text("常驻悬浮球说明") },
            text = {
                Text("开启后可在桌面或其他 App 直接点球录音。待机时是半透明圆点，录音时变成圆角方块并可拖动；长按可添加时间标记。手机重启后需重新开启一次，Android 的麦克风和前台服务提示会保留。")
            },
            confirmButton = { TextButton(onClick = { showFloatingHelp = false }) { Text("知道了") } },
        )
    }

    pendingRenameId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!renaming) pendingRenameId = null },
            title = { Text("重命名录音") },
            text = {
                Column {
                    FlatTextField(
                        value = renameText,
                        onValueChange = { renameText = it; renameError = "" },
                        label = { Text("录音名称") },
                        singleLine = true,
                        enabled = !renaming,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (renameError.isNotBlank()) Text(renameError, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank() && !renaming, onClick = {
                    val title = renameText.trim()
                    renaming = true
                    scope.launch {
                        try {
                            dao.renameCustomEntry(id, title)
                            pendingRenameId = null
                        } catch (_: Exception) {
                            renameError = "保存失败，请重试"
                        } finally {
                            renaming = false
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(enabled = !renaming, onClick = { pendingRenameId = null }) { Text("取消") }
            },
        )
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
    onShowHelp: () -> Unit,
    onResetPosition: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text("常驻悬浮球", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onShowHelp, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "查看常驻悬浮球说明", modifier = Modifier.size(19.dp))
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF8A8A8A),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFDADADA),
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
            FlatButton(onClick = onResetPosition, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("重置悬浮球位置") }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RecordingSearchAndFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    selected: RecordingMarkerFilter,
    onSelected: (RecordingMarkerFilter) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordingMarkerFilter.entries.forEach { option ->
                    FlatFilterChip(
                        selected = selected == option,
                        onClick = { onSelected(option) },
                        label = { Text(option.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            FlatTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索录音名称或标记") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "清空搜索")
                    }
                },
                containerColor = Color.White,
            )
        }
    }
}

@Composable
private fun RecordingLayoutHeader(
    count: Int,
    totalCount: Int,
    gridMode: Boolean,
    onGridModeChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (count == totalCount) "悬浮录音记录 · $count" else "悬浮录音记录 · $count/$totalCount",
            style = MaterialTheme.typography.titleMedium,
        )
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
    active: Boolean,
    playing: Boolean,
    playRequest: Int,
    onPlay: () -> Unit,
    onCollapse: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMarkersChange: (List<AudioTimeMarker>) -> Unit,
    onPlayingChange: (Boolean) -> Unit,
) {
    val fileName = remember(entry.content) { richBodyAudioNames(entry.content).firstOrNull() }
    val markers = remember(entry.content) { audioMarkersFromContent(entry.content) }
    val file = remember(fileName) { fileName?.let { attachmentFile(context, it) } }
    val durationMs by produceState<Long?>(initialValue = null, file?.absolutePath, file?.lastModified()) {
        value = withContext(Dispatchers.IO) { file?.let(::audioFileDurationMs)?.takeIf { it > 0L } }
    }
    val detailLine = buildList {
        if (entry.date.isNotBlank()) {
            // Compact display only; preserve the stored recording timestamp.
            add(if (entry.date.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"))) entry.date.dropLast(3) else entry.date)
        }
        add(durationMs?.let(::formatAudioTime) ?: "--:--")
    }.joinToString("  ·  ")
    AudioSwipeActions(
        identity = entry.id,
        actions = listOf(
            AudioSwipeAction("删除录音", Icons.Rounded.Delete, onDelete),
        ),
        background = MaterialTheme.colorScheme.surface,
        shape = CardDefaults.shape,
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clickable(onClickLabel = "修改录音标题", onClick = onRename),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (markers.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.size(6.dp),
                                shape = CircleShape,
                                color = Color(0xFF35A857),
                            ) {}
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            entry.title.ifBlank { "录音" },
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = onPlay, modifier = Modifier.size(28.dp), enabled = file?.isFile == true) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "暂停" else "播放")
                    }
                }
                Text(detailLine, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp, color = Color(0xFF858585))

                if (file != null) {
                    if (file.isFile) {
                        AdvancedAudioPlayer(
                            file = file,
                            durationHintMs = durationMs ?: 0L,
                            active = active,
                            expanded = expanded,
                            onCollapse = onCollapse,
                            markers = markers,
                            onMarkersChange = onMarkersChange,
                            playRequest = playRequest,
                            onPlayingChange = onPlayingChange,
                        )
                    } else Text("录音文件不存在", style = MaterialTheme.typography.bodySmall)
                } else if (fileName == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text("没有找到可播放的录音文件", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

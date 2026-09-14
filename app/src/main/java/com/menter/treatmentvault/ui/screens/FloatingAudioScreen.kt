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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.menter.treatmentvault.TreatmentVaultApplication

@Composable
fun FloatingAudioScreen(back: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val entries by dao.allCustomEntriesFlow().collectAsState(emptyList())
    val recordings = remember(entries) {
        entries.filter { it.categoryId == "floating_audio" }.sortedByDescending { it.updatedAt }
    }
    val prefs = remember { context.getSharedPreferences(FloatingRecorderService.PREFS_NAME, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(FloatingRecorderService.PREF_ENABLED, false)) }
    var message by remember { mutableStateOf("") }

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

    Page("悬浮录音", back) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
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
                            Switch(
                                checked = enabled,
                                onCheckedChange = { checked -> if (checked) enableRecorder() else disableRecorder() },
                            )
                        }
                        Text(
                            "悬浮球是纯色圆点：待机约 30% 透明度，录音中只略微加深，不显示麦克风图标；可拖动位置。",
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

            item {
                Text("悬浮录音记录 · ${recordings.size}", style = MaterialTheme.typography.titleMedium)
            }

            if (recordings.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("还没有悬浮录音", style = MaterialTheme.typography.titleSmall)
                            Text("开启悬浮球后，点一下开始录音，再点一下停止；录音会自动出现在这里。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(recordings, key = { it.id }) { entry ->
                    val fileName = remember(entry.content) { richBodyAudioNames(entry.content).firstOrNull() }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.title.ifBlank { "录音" }, style = MaterialTheme.typography.titleSmall)
                            if (entry.date.isNotBlank()) Text(entry.date, style = MaterialTheme.typography.bodySmall)
                            if (fileName != null) {
                                val file = attachmentFile(context, fileName)
                                if (file.isFile) AudioBlockPlayer(file)
                                else Text("录音文件不存在", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

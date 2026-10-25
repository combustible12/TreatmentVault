package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.menter.treatmentvault.update.UpdateManager
import com.menter.treatmentvault.update.UpdateOutcome
import java.io.File

private sealed interface DirectUpdateUiState {
    data object Idle : DirectUpdateUiState
    data object Working : DirectUpdateUiState
    data object WaitingForPermission : DirectUpdateUiState
    data class Message(val title: String, val text: String, val retryable: Boolean = false) : DirectUpdateUiState
}

/** Runs the complete update flow from one user action, without an intermediate screen. */
@Composable
internal fun DirectUpdateFlow(requestToken: Int) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var retryToken by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<DirectUpdateUiState>(DirectUpdateUiState.Idle) }
    var pendingApk by remember { mutableStateOf<File?>(null) }

    fun openInstaller(apk: File) {
        if (UpdateManager.canInstallPackages(context)) {
            pendingApk = null
            state = DirectUpdateUiState.Idle
            UpdateManager.install(context, apk)
        } else {
            pendingApk = apk
            state = DirectUpdateUiState.WaitingForPermission
            UpdateManager.requestInstallPermission(context)
        }
    }

    LaunchedEffect(requestToken, retryToken) {
        if (requestToken <= 0) return@LaunchedEffect
        state = DirectUpdateUiState.Working
        when (val result = UpdateManager.checkAndDownload(context)) {
            UpdateOutcome.Latest -> state = DirectUpdateUiState.Message("无需更新", "当前已经是最新版本。")
            is UpdateOutcome.Error -> state = DirectUpdateUiState.Message("更新失败", result.message, retryable = true)
            is UpdateOutcome.Ready -> openInstaller(result.apk)
        }
    }

    DisposableEffect(lifecycleOwner, pendingApk) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                pendingApk?.let { apk ->
                    if (UpdateManager.canInstallPackages(context)) {
                        openInstaller(apk)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val current = state) {
        DirectUpdateUiState.Idle -> Unit
        DirectUpdateUiState.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在更新") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(14.dp))
                    Text("正在检查、下载并校验安装包…")
                }
            },
            confirmButton = {},
        )
        DirectUpdateUiState.WaitingForPermission -> AlertDialog(
            onDismissRequest = {},
            title = { Text("需要安装权限") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请允许此应用安装未知来源应用。授权后返回这里，会自动继续打开安装界面。")
                    Spacer(Modifier.height(2.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { UpdateManager.requestInstallPermission(context) }) { Text("打开设置") }
            },
            dismissButton = {
                TextButton(onClick = { pendingApk = null; state = DirectUpdateUiState.Idle }) { Text("取消") }
            },
        )
        is DirectUpdateUiState.Message -> AlertDialog(
            onDismissRequest = { state = DirectUpdateUiState.Idle },
            title = { Text(current.title) },
            text = { Text(current.text) },
            confirmButton = {
                if (current.retryable) {
                    TextButton(onClick = { retryToken++ }) { Text("重试") }
                } else {
                    TextButton(onClick = { state = DirectUpdateUiState.Idle }) { Text("知道了") }
                }
            },
            dismissButton = if (current.retryable) {
                { TextButton(onClick = { state = DirectUpdateUiState.Idle }) { Text("关闭") } }
            } else null,
        )
    }
}

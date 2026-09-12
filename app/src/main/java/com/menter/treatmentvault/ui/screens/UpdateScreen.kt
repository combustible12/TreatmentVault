package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.menter.treatmentvault.BuildConfig
import com.menter.treatmentvault.update.UpdateManager
import com.menter.treatmentvault.update.UpdateOutcome
import kotlinx.coroutines.launch

@Composable
fun UpdateScreen(back: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("当前版本 ${BuildConfig.VERSION_NAME}") }
    var working by remember { mutableStateOf(false) }

    Page("应用更新", back) {
        Text("以后有新功能时，在这里点一次即可检查、下载并进入系统安装界面。安卓仍会要求你最后确认一次安装。", style = MaterialTheme.typography.bodyMedium)
        Text(status)
        Button(
            onClick = {
                working = true
                status = "正在检查并下载更新…"
                scope.launch {
                    when (val result = UpdateManager.checkAndDownload(context)) {
                        UpdateOutcome.Latest -> status = "已经是最新版本"
                        is UpdateOutcome.Error -> status = "${result.message}。如果更新仓库还没建立，这里会暂时不可用。"
                        is UpdateOutcome.Ready -> {
                            status = "已下载 ${result.info.versionName}，正在打开安装界面…"
                            val started = UpdateManager.install(context, result.apk)
                            if (!started) status = "请先允许此应用安装未知来源应用，返回后再点一次升级。"
                        }
                    }
                    working = false
                }
            },
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
            Text(if (working) "处理中…" else "一键检查并升级")
        }
    }
}

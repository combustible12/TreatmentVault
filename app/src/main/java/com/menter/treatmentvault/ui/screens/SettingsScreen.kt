package com.menter.treatmentvault.ui.screens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.backup.BackupManager
import kotlinx.coroutines.launch
@Composable fun SettingsScreen(back:()->Unit){val ctx=LocalContext.current;val app=ctx.applicationContext as TreatmentVaultApplication;val bm=remember{BackupManager(ctx,app.database)};val scope=rememberCoroutineScope();var msg by remember{mutableStateOf("")};val exp=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){u->if(u!=null)scope.launch{runCatching{bm.export(u)}.onSuccess{msg="备份完成"}.onFailure{msg=it.message?:"失败"}}};val res=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)scope.launch{runCatching{bm.restore(u)}.onSuccess{msg="恢复完成"}.onFailure{msg=it.message?:"失败"}}};Page("备份与恢复",back){Button({exp.launch("treatment_backup.zip")},Modifier.fillMaxWidth()){Text("导出完整数据备份")};OutlinedButton({res.launch(arrayOf("application/zip","application/octet-stream"))},Modifier.fillMaxWidth()){Text("从备份恢复")};Text("结构化数据和应用内图片附件会一起打包备份。",style=MaterialTheme.typography.bodySmall);if(msg.isNotBlank())Text(msg)} }

package com.menter.treatmentvault.ui.screens
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.importer.*
import kotlinx.coroutines.launch

@Composable fun ImportScreen(back:()->Unit){val ctx=LocalContext.current;val app=ctx.applicationContext as TreatmentVaultApplication;val importer=remember{UpdateImporter(ctx,app.database)};val scope=rememberCoroutineScope();var preview by remember{mutableStateOf<ImportPreview?>(null)};var msg by remember{mutableStateOf("")}
 fun nameOf(u:android.net.Uri):String{var n="update";ctx.contentResolver.query(u,null,null,null,null)?.use{c->val i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(c.moveToFirst()&&i>=0)n=c.getString(i)};return n}
 val updateLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)runCatching{importer.preview(u,nameOf(u))}.onSuccess{preview=it}.onFailure{msg=it.message?:"读取失败"}}
 val csvLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)scope.launch{runCatching{val text=ctx.contentResolver.openInputStream(u)!!.bufferedReader().use{it.readText()};MementoCsv.import(text,app.database.dao())}.onSuccess{msg="已导入 $it 条 Memento 用品"}.onFailure{msg=it.message?:"导入失败"}}}
 Page("导入更新包",back){Text("你把资料发给 ChatGPT → 下载 JSON/ZIP 更新包 → 这里一键导入。",style=MaterialTheme.typography.bodyLarge);Button({updateLauncher.launch(arrayOf("application/json","application/zip","application/octet-stream"))},Modifier.fillMaxWidth()){Text("选择 JSON / ZIP 更新包")};OutlinedButton({csvLauncher.launch(arrayOf("text/csv","text/*"))},Modifier.fillMaxWidth()){Text("导入 Memento CSV")};if(msg.isNotBlank())Text(msg);preview?.let{p->Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("导入预览",style=MaterialTheme.typography.titleLarge);Text(p.fileName);p.counts.forEach{(k,v)->Text("$k：$v 条")};Button({scope.launch{val r=importer.import(p);msg="完成：新增 ${r.added}，更新 ${r.updated}，失败 ${r.failed}";preview=null}},Modifier.fillMaxWidth()){Text("确认导入")};TextButton({preview=null}){Text("取消")}}}}} }

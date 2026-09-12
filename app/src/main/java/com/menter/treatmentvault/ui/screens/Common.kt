package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Page(title:String,onBack:()->Unit,content:@Composable ColumnScope.()->Unit){ Scaffold(topBar={TopAppBar(title={Text(title)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}})}){p->Column(Modifier.padding(p).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)} }
@Composable fun Field(value:String,on:(String)->Unit,label:String){OutlinedTextField(value,on,{Text(label)},modifier=Modifier.fillMaxWidth())}

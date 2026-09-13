from pathlib import Path


def replace_once(path: str, old: str, new: str):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected snippet not found in {path}: {old[:180]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# Home: double-tap title to return to top.
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/HomeScreen.kt",
    "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items",
    "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.rememberLazyListState",
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/HomeScreen.kt",
    "    val scope = rememberCoroutineScope()\n    val drawerState = rememberDrawerState(DrawerValue.Closed)",
    "    val scope = rememberCoroutineScope()\n    val listState = rememberLazyListState()\n    val drawerState = rememberDrawerState(DrawerValue.Closed)",
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/HomeScreen.kt",
    '                    title = { Text("治疗笔记") },',
    '''                    title = {\n                        Text(\n                            "治疗笔记",\n                            modifier = Modifier.combinedClickable(\n                                onClick = {},\n                                onDoubleClick = { scope.launch { listState.animateScrollToItem(0) } },\n                            ),\n                        )\n                    },''',
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/HomeScreen.kt",
    '''            LazyColumn(\n                modifier = Modifier.fillMaxSize().padding(padding),''',
    '''            LazyColumn(\n                state = listState,\n                modifier = Modifier.fillMaxSize().padding(padding),''',
)

# All records.
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/AllRecordsScreen.kt",
    "import androidx.compose.foundation.lazy.LazyRow\nimport androidx.compose.foundation.lazy.items",
    "import androidx.compose.foundation.lazy.LazyRow\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.rememberLazyListState",
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/AllRecordsScreen.kt",
    "    val scope = rememberCoroutineScope()\n\n    var query",
    "    val scope = rememberCoroutineScope()\n    val listState = rememberLazyListState()\n\n    var query",
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/AllRecordsScreen.kt",
    '                    title = { Text("全部记录") },',
    '''                    title = {\n                        Text(\n                            "全部记录",\n                            modifier = Modifier.combinedClickable(\n                                onClick = {},\n                                onDoubleClick = { scope.launch { listState.animateScrollToItem(0) } },\n                            ),\n                        )\n                    },''',
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/AllRecordsScreen.kt",
    '''        LazyColumn(\n            modifier = Modifier.fillMaxSize().padding(padding),''',
    '''        LazyColumn(\n            state = listState,\n            modifier = Modifier.fillMaxSize().padding(padding),''',
)

# Folder/category list: keep long-press rename and add double-tap to top.
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/FolderScreen.kt",
    "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items",
    "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.rememberLazyListState",
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/FolderScreen.kt",
    "    val scope = rememberCoroutineScope()\n\n    val folder",
    "    val scope = rememberCoroutineScope()\n    val listState = rememberLazyListState()\n\n    val folder",
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/FolderScreen.kt",
    '''                            modifier = Modifier.combinedClickable(\n                                onClick = {},\n                                onLongClick = { renameFolder = folder },\n                            ),''',
    '''                            modifier = Modifier.combinedClickable(\n                                onClick = {},\n                                onDoubleClick = { scope.launch { listState.animateScrollToItem(0) } },\n                                onLongClick = { renameFolder = folder },\n                            ),''',
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/FolderScreen.kt",
    '''        LazyColumn(\n            modifier = Modifier.fillMaxSize().padding(padding),''',
    '''        LazyColumn(\n            state = listState,\n            modifier = Modifier.fillMaxSize().padding(padding),''',
)

# Todo center is generated by the previous 0.19 patch script, then enhanced here.
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/TodoCenterScreen.kt",
    "import androidx.compose.foundation.lazy.LazyRow\nimport androidx.compose.foundation.lazy.items",
    "import androidx.compose.foundation.lazy.LazyRow\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.rememberLazyListState",
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/TodoCenterScreen.kt",
    "    val scope = rememberCoroutineScope()\n\n    var query",
    "    val scope = rememberCoroutineScope()\n    val listState = rememberLazyListState()\n\n    var query",
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/TodoCenterScreen.kt",
    '                title = { Text("全部待办") },',
    '''                title = {\n                    Text(\n                        "全部待办",\n                        modifier = Modifier.combinedClickable(\n                            onClick = {},\n                            onDoubleClick = { scope.launch { listState.animateScrollToItem(0) } },\n                        ),\n                    )\n                },''',
)
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/TodoCenterScreen.kt",
    '''        LazyColumn(\n            modifier = Modifier.fillMaxSize().padding(padding),''',
    '''        LazyColumn(\n            state = listState,\n            modifier = Modifier.fillMaxSize().padding(padding),''',
)

print("Double-tap title scroll-to-top support applied")

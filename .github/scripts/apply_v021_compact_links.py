from pathlib import Path


def replace_once(path: str, old: str, new: str):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected snippet not found in {path}: {old[:180]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# Version bump.
replace_once(
    "app/build.gradle.kts",
    'versionCode = 32\n        versionName = "0.20.0"',
    'versionCode = 33\n        versionName = "0.21.0"',
)

# Standalone URL detection: display-only compaction. The original URL stays in the note body.
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/Common.kt",
    '''private fun detectedWebUrls(text: String): List<String> = webUrlRegex.findAll(text)\n    .map { it.value.trimEnd('.', ',', '，', '。', ';', '；', ':', '：', ')', '）', ']', '】') }\n    .filter { it.isNotBlank() }\n    .distinct()\n    .toList()\n\n@Composable\nprivate fun DetectedLinkActions''',
    '''private fun detectedWebUrls(text: String): List<String> = webUrlRegex.findAll(text)\n    .map { it.value.trimEnd('.', ',', '，', '。', ';', '；', ':', '：', ')', '）', ']', '】') }\n    .filter { it.isNotBlank() }\n    .distinct()\n    .toList()\n\ninternal fun standaloneWebUrl(text: String): String? {\n    val trimmed = text.trim()\n    val links = detectedWebUrls(trimmed)\n    return links.singleOrNull()?.takeIf { it == trimmed }\n}\n\n@Composable\nprivate fun DetectedLinkActions''',
)

# Rich editor keeps direct-open behavior while allowing standalone links to collapse into one icon.
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/Common.kt",
    '''    val context = LocalContext.current\n    val scope = rememberCoroutineScope()\n''',
    '''    val context = LocalContext.current\n    val uriHandler = LocalUriHandler.current\n    val scope = rememberCoroutineScope()\n''',
)

replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/Common.kt",
    '''    var recorderSession by remember(ownerType, sessionKey) { mutableStateOf<AudioRecorderSession?>(null) }\n    var audioMessage by remember(ownerType, sessionKey) { mutableStateOf<String?>(null) }\n''',
    '''    var recorderSession by remember(ownerType, sessionKey) { mutableStateOf<AudioRecorderSession?>(null) }\n    var audioMessage by remember(ownerType, sessionKey) { mutableStateOf<String?>(null) }\n    var expandedLinkKeys by remember(ownerType, sessionKey) { mutableStateOf<Set<String>>(emptySet()) }\n''',
)

replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/Common.kt",
    '''        Text("文字、图片、录音和链接按当前顺序保存；录音可在正文里直接播放。", style = MaterialTheme.typography.bodySmall)\n''',
    '''        Text("文字、图片、录音和链接按当前顺序保存；单独粘贴的链接会自动收成小图标，点击仍可直接打开。", style = MaterialTheme.typography.bodySmall)\n''',
)

replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/Common.kt",
    '''                    is RichBlock.TextBlock -> {\n                        Column {\n                            OutlinedTextField(\n                                value = block.text,\n                                onValueChange = { next -> blocks[index] = block.copy(text = next); sync() },\n                                modifier = Modifier.fillMaxWidth(),\n                                placeholder = { Text(if (index == 0) "直接写内容或粘贴链接…" else "继续写…") },\n                                minLines = 3,\n                            )\n                            DetectedLinkActions(block.text)\n                        }\n                    }\n''',
    '''                    is RichBlock.TextBlock -> {\n                        val compactUrl = standaloneWebUrl(block.text)\n                        if (compactUrl != null && block.key !in expandedLinkKeys) {\n                            Row(\n                                modifier = Modifier.fillMaxWidth(),\n                                verticalAlignment = Alignment.CenterVertically,\n                            ) {\n                                FilledTonalIconButton(\n                                    onClick = { runCatching { uriHandler.openUri(compactUrl) } },\n                                ) {\n                                    Icon(Icons.Default.Link, contentDescription = "打开链接")\n                                }\n                            }\n                        } else {\n                            Column {\n                                OutlinedTextField(\n                                    value = block.text,\n                                    onValueChange = { next -> blocks[index] = block.copy(text = next); sync() },\n                                    modifier = Modifier.fillMaxWidth(),\n                                    placeholder = { Text(if (index == 0) "直接写内容或粘贴链接…" else "继续写…") },\n                                    minLines = 3,\n                                )\n                                DetectedLinkActions(block.text)\n                            }\n                        }\n                    }\n''',
)

replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/Common.kt",
    '''                    IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, contentDescription = "上移") }\n                    IconButton(onClick = { move(index, index + 1) }, enabled = index < blocks.lastIndex) { Icon(Icons.Default.ArrowDownward, contentDescription = "下移") }\n                    TextButton(onClick = { blocks.add(index + 1, RichBlock.TextBlock()); insertIndex = index + 2; sync() }) {\n''',
    '''                    IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, contentDescription = "上移") }\n                    IconButton(onClick = { move(index, index + 1) }, enabled = index < blocks.lastIndex) { Icon(Icons.Default.ArrowDownward, contentDescription = "下移") }\n                    if (block is RichBlock.TextBlock && standaloneWebUrl(block.text) != null) {\n                        val expanded = block.key in expandedLinkKeys\n                        IconButton(\n                            onClick = {\n                                expandedLinkKeys = if (expanded) expandedLinkKeys - block.key else expandedLinkKeys + block.key\n                            },\n                        ) {\n                            Icon(\n                                if (expanded) Icons.Default.Check else Icons.Default.Edit,\n                                contentDescription = if (expanded) "收起链接" else "编辑链接",\n                            )\n                        }\n                    }\n                    TextButton(onClick = { blocks.add(index + 1, RichBlock.TextBlock()); insertIndex = index + 2; sync() }) {\n''',
)

# Regression coverage: standalone URL collapses; mixed text keeps the old text + clickable-link behavior.
test_path = Path("app/src/test/java/com/menter/treatmentvault/ui/screens/RichContentTest.kt")
test_text = test_path.read_text(encoding="utf-8")
insert = '''\n    @Test\n    fun standaloneUrlCanCollapseWithoutChangingStoredText() {\n        val url = "https://example.com/a?x=1"\n        assertEquals(url, standaloneWebUrl(url))\n        assertEquals(url, standaloneWebUrl("  $url  "))\n    }\n\n    @Test\n    fun mixedTextLinkDoesNotCollapseAwayTheSurroundingText() {\n        assertEquals(null, standaloneWebUrl("查看 https://example.com 这个页面"))\n    }\n'''
if 'standaloneUrlCanCollapseWithoutChangingStoredText' not in test_text:
    test_text = test_text.replace('\n}\n', insert + '\n}\n')
    test_path.write_text(test_text, encoding="utf-8")

# Publish notes for 0.21.0.
replace_once(
    ".github/workflows/android.yml",
    '"notes": "修复归属文件夹编辑：笔记不再锁定最初的“待整理/主文件夹”，现在可以自由取消旧归属并切换到新文件夹；保存时会真正更新笔记主归属，不会暗中保留旧文件夹。其他记录也允许先取消最后一个归属再选择新的文件夹，保存前会要求至少保留一个。新增回归测试，防止这类界面状态与实际保存结果不一致的问题再次引入。"',
    '"notes": "链接显示优化：单独粘贴 http/https 链接时自动收成小链接图标，点击图标仍可直接打开；原始 URL 继续完整保存在正文中，因此搜索、备份和恢复不受影响。文字中夹带链接时继续保留原来的可点击打开方式，并提供编辑/收起入口。"',
)

print("TreatmentVault 0.21.0 compact links applied")

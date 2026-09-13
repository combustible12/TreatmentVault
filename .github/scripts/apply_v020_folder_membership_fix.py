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
    'versionCode = 31\n        versionName = "0.19.0"',
    'versionCode = 32\n        versionName = "0.20.0"',
)

# Add a direct suspend lookup so folder changes can atomically update a note's real primary category.
replace_once(
    "app/src/main/java/com/menter/treatmentvault/data/AppDao.kt",
    '@Query("SELECT * FROM CustomEntryEntity WHERE id=:id LIMIT 1") fun customEntry(id:Long):Flow<CustomEntryEntity?>\n',
    '@Query("SELECT * FROM CustomEntryEntity WHERE id=:id LIMIT 1") fun customEntry(id:Long):Flow<CustomEntryEntity?>\n    @Query("SELECT * FROM CustomEntryEntity WHERE id=:id LIMIT 1") suspend fun customEntryById(id:Long):CustomEntryEntity?\n',
)

# Remove the locked-primary UI behavior. Every folder checkbox is editable; only saving an empty set is blocked.
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/RecordFolderUi.kt",
    '    val requiredPrimary = if (item.ownerType == "note") item.categoryKey else null\n    var managing by remember(item.key) { mutableStateOf(false) }\n',
    '    val currentPrimary = if (item.ownerType == "note") item.categoryKey else null\n    var managing by remember(item.key) { mutableStateOf(false) }\n',
)

replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/RecordFolderUi.kt",
    '''                    Text(\n                        if (requiredPrimary != null) "当前笔记所在的主文件夹会保留；可以再勾选其他文件夹。"\n                        else "同一条记录可以同时出现在多个文件夹；至少保留一个归属。",\n                        style = MaterialTheme.typography.bodySmall,\n                    )\n''',
    '''                    Text(\n                        "可以自由调整归属文件夹；编辑时可以先取消旧归属，但保存前至少选择一个。取消当前文件夹并勾选新文件夹后，会真正移动，不会暗中保留旧归属。",\n                        style = MaterialTheme.typography.bodySmall,\n                    )\n''',
)

replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/RecordFolderUi.kt",
    '''                        items(availableFolders, key = { it.key }) { folder ->\n                            val checked = folder.key in selected || folder.key == requiredPrimary\n                            val locked = folder.key == requiredPrimary\n                            ListItem(\n                                modifier = Modifier.fillMaxWidth().clickable(enabled = !locked) {\n                                    selected = when {\n                                        folder.key !in selected -> selected + folder.key\n                                        item.ownerType != "note" && selected.size <= 1 -> selected\n                                        else -> selected - folder.key\n                                    }\n                                },\n                                headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },\n                                supportingContent = {\n                                    val path = folderPathText(folder.key, categories)\n                                    if (path != folder.name) Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)\n                                },\n                                leadingContent = {\n                                    Checkbox(\n                                        checked = checked,\n                                        enabled = !locked,\n                                        onCheckedChange = { next ->\n                                            selected = when {\n                                                next -> selected + folder.key\n                                                item.ownerType != "note" && selected.size <= 1 -> selected\n                                                else -> selected - folder.key\n                                            }\n                                        },\n                                    )\n                                },\n                                trailingContent = { if (locked) Text("主文件夹", style = MaterialTheme.typography.labelSmall) },\n                            )\n                        }\n''',
    '''                        items(availableFolders, key = { it.key }) { folder ->\n                            val checked = folder.key in selected\n                            ListItem(\n                                modifier = Modifier.fillMaxWidth().clickable {\n                                    selected = toggleFolderSelection(selected, folder.key)\n                                },\n                                headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },\n                                supportingContent = {\n                                    val path = folderPathText(folder.key, categories)\n                                    if (path != folder.name) Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)\n                                },\n                                leadingContent = {\n                                    Checkbox(\n                                        checked = checked,\n                                        onCheckedChange = { next ->\n                                            selected = if (next) selected + folder.key else selected - folder.key\n                                        },\n                                    )\n                                },\n                                trailingContent = {\n                                    if (folder.key == currentPrimary) Text("当前归属", style = MaterialTheme.typography.labelSmall)\n                                },\n                            )\n                        }\n''',
)

replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/RecordFolderUi.kt",
    '''            } else if (managing && onFoldersChange != null) {\n                TextButton(\n                    enabled = item.ownerType == "note" || selected.isNotEmpty(),\n                    onClick = {\n                        val next = if (requiredPrimary != null) selected + requiredPrimary else selected\n                        onFoldersChange(next)\n                        onDismiss()\n                    },\n                ) { Text("保存归属") }\n''',
    '''            } else if (managing && onFoldersChange != null) {\n                TextButton(\n                    enabled = selected.isNotEmpty(),\n                    onClick = {\n                        onFoldersChange(selected)\n                        onDismiss()\n                    },\n                ) { Text("保存归属") }\n''',
)

# Pure helper so this interaction has a regression test and can never silently refuse a user's uncheck again.
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/RecordFolderUi.kt",
    'internal fun folderPathText(categoryKey: String, categories: List<CategoryEntity>): String =\n    folderPathFor(categoryKey, categories).joinToString("  ›  ") { it.name }\n\n',
    'internal fun folderPathText(categoryKey: String, categories: List<CategoryEntity>): String =\n    folderPathFor(categoryKey, categories).joinToString("  ›  ") { it.name }\n\ninternal fun toggleFolderSelection(selected: Set<String>, folderKey: String): Set<String> =\n    if (folderKey in selected) selected - folderKey else selected + folderKey\n\n',
)

# Persist a note's new primary folder instead of secretly keeping its old categoryId.
replace_once(
    "app/src/main/java/com/menter/treatmentvault/ui/screens/UnifiedRecord.kt",
    '''    if (item.ownerType == "note") {\n        val requiredPrimary = item.categoryKey\n        requestedFolderKeys.filter { it != requiredPrimary }.forEachIndexed { index, folderKey ->\n            dao.saveRecordFolder(RecordFolderEntity(item.ownerType, item.ownerId, folderKey, now + index))\n        }\n        return\n    }\n''',
    '''    if (item.ownerType == "note") {\n        val selected = requestedFolderKeys.filter { it.isNotBlank() }.toCollection(linkedSetOf())\n            .ifEmpty { linkedSetOf(item.categoryKey) }\n        val primary = if (item.categoryKey in selected) item.categoryKey else selected.first()\n        val note = dao.customEntryById(item.ownerId)\n        if (note != null && note.categoryId != primary) {\n            dao.updateCustomEntry(note.copy(categoryId = primary, updatedAt = now))\n        }\n        selected.filter { it != primary }.forEachIndexed { index, folderKey ->\n            dao.saveRecordFolder(RecordFolderEntity(item.ownerType, item.ownerId, folderKey, now + index + 1))\n        }\n        return\n    }\n''',
)

# Regression tests: current primary can be unchecked, and selection may be temporarily empty while editing.
test_path = Path("app/src/test/java/com/menter/treatmentvault/ui/screens/RecordFolderUiTest.kt")
test_text = test_path.read_text(encoding="utf-8")
insert = '''\n    @Test\n    fun `current folder can be unchecked while editing memberships`() {\n        assertEquals(emptySet<String>(), toggleFolderSelection(setOf("inbox"), "inbox"))\n    }\n\n    @Test\n    fun `a different folder can replace the old one without old folder being re-added`() {\n        var selected: Set<String> = setOf("inbox")\n        selected = toggleFolderSelection(selected, "patient")\n        selected = toggleFolderSelection(selected, "inbox")\n        assertEquals(setOf("patient"), selected)\n    }\n'''
if 'current folder can be unchecked while editing memberships' not in test_text:
    test_text = test_text.replace('\n}\n', insert + '\n}\n')
    test_path.write_text(test_text, encoding="utf-8")

print("TreatmentVault 0.20.0 folder membership fix applied")

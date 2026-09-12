package com.menter.treatmentvault.ui.screens

internal fun noteDraftHasContent(
    title: String,
    content: String,
    pendingFiles: List<String>,
): Boolean =
    title.isNotBlank() ||
        richBodyPlainText(content).isNotBlank() ||
        content.contains("[[image:") ||
        pendingFiles.isNotEmpty()

internal fun shouldPersistNote(
    persistedId: Long,
    title: String,
    content: String,
    pendingFiles: List<String>,
): Boolean = persistedId > 0L || noteDraftHasContent(title, content, pendingFiles)

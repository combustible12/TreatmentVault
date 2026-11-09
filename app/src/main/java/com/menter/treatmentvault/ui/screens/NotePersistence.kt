package com.menter.treatmentvault.ui.screens

internal fun noteDraftHasContent(
    title: String,
    content: String,
    pendingFiles: List<String>,
): Boolean =
    title.isNotBlank() || richBodyHasContent(content) || pendingFiles.isNotEmpty()

internal fun shouldPersistNote(
    persistedId: Long,
    title: String,
    content: String,
    pendingFiles: List<String>,
    tags: String = "",
): Boolean = persistedId > 0L || tags.isNotBlank() || noteDraftHasContent(title, content, pendingFiles)

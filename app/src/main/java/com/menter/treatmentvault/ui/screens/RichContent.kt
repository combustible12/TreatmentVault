package com.menter.treatmentvault.ui.screens

import java.util.UUID

internal val imageMarkerRegex = Regex("\\[\\[image:([^\\]]+)]]")
internal val audioMarkerRegex = Regex("\\[\\[audio:([^\\]]+)]]")
private val mediaMarkerRegex = Regex("\\[\\[(image|audio):([^\\]]+)]]")

internal sealed interface RichBlock {
    val key: String

    data class TextBlock(
        override val key: String = UUID.randomUUID().toString(),
        val text: String = "",
    ) : RichBlock

    data class ImageBlock(
        override val key: String = UUID.randomUUID().toString(),
        val fileName: String,
    ) : RichBlock

    data class AudioBlock(
        override val key: String = UUID.randomUUID().toString(),
        val fileName: String,
    ) : RichBlock
}

fun richBodyPlainText(value: String): String = stripInlineTodoMarkers(mediaMarkerRegex.replace(value, ""))
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

fun richBodyImageNames(value: String): List<String> =
    imageMarkerRegex.findAll(value).map { it.groupValues[1] }.toList()

fun richBodyAudioNames(value: String): List<String> =
    audioMarkerRegex.findAll(value).map { it.groupValues[1] }.toList()

fun richBodyAttachmentNames(value: String): List<String> =
    mediaMarkerRegex.findAll(value).map { it.groupValues[2] }.toList()

fun richBodyHasContent(value: String): Boolean =
    richBodyPlainText(value).isNotBlank() || richBodyAttachmentNames(value).isNotEmpty()

internal fun parseRichBody(value: String): List<RichBlock> {
    val result = mutableListOf<RichBlock>()
    var cursor = 0
    mediaMarkerRegex.findAll(value).forEach { match ->
        val before = value.substring(cursor, match.range.first).trim('\n')
        if (before.isNotEmpty()) result += RichBlock.TextBlock(text = before)
        val fileName = match.groupValues[2]
        result += when (match.groupValues[1]) {
            "audio" -> RichBlock.AudioBlock(fileName = fileName)
            else -> RichBlock.ImageBlock(fileName = fileName)
        }
        cursor = match.range.last + 1
    }
    val tail = value.substring(cursor).trim('\n')
    if (tail.isNotEmpty()) result += RichBlock.TextBlock(text = tail)
    if (result.none { it is RichBlock.TextBlock }) result.add(0, RichBlock.TextBlock())
    return result
}

internal fun serializeRichBody(blocks: List<RichBlock>): String = blocks.joinToString("\n\n") { block ->
    when (block) {
        is RichBlock.TextBlock -> block.text.trimEnd()
        is RichBlock.ImageBlock -> "[[image:${block.fileName}]]"
        is RichBlock.AudioBlock -> "[[audio:${block.fileName}]]"
    }
}.trim()

private fun looksLikeImageAttachment(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

internal fun recoverLegacyImageMarkers(body: String, attachmentNames: List<String>): String {
    if (richBodyImageNames(body).isNotEmpty()) return body
    val imageNames = attachmentNames.filter(::looksLikeImageAttachment).distinct()
    if (imageNames.isEmpty()) return body
    val markers = imageNames.joinToString("\n\n") { "[[image:$it]]" }
    return if (body.isBlank()) markers else body.trimEnd() + "\n\n" + markers
}

package com.menter.treatmentvault.ui.screens

import java.util.UUID

internal val imageMarkerRegex = Regex("\\[\\[image:([^\\]]+)]]")

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
}

fun richBodyPlainText(value: String): String = imageMarkerRegex.replace(value, "")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

fun richBodyImageNames(value: String): List<String> =
    imageMarkerRegex.findAll(value).map { it.groupValues[1] }.toList()

fun richBodyHasContent(value: String): Boolean =
    richBodyPlainText(value).isNotBlank() || richBodyImageNames(value).isNotEmpty()

internal fun parseRichBody(value: String): List<RichBlock> {
    val result = mutableListOf<RichBlock>()
    var cursor = 0
    imageMarkerRegex.findAll(value).forEach { match ->
        val before = value.substring(cursor, match.range.first).trim('\n')
        if (before.isNotEmpty()) result += RichBlock.TextBlock(text = before)
        result += RichBlock.ImageBlock(fileName = match.groupValues[1])
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
    }
}.trim()

internal fun recoverLegacyImageMarkers(body: String, attachmentNames: List<String>): String {
    if (attachmentNames.isEmpty() || richBodyImageNames(body).isNotEmpty()) return body
    val markers = attachmentNames.distinct().joinToString("\n\n") { "[[image:$it]]" }
    return if (body.isBlank()) markers else body.trimEnd() + "\n\n" + markers
}

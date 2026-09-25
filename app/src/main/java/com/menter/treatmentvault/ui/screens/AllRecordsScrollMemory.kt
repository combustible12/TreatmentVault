package com.menter.treatmentvault.ui.screens

/**
 * Navigation-stable viewport bookmark for the unified records list.
 *
 * The previous implementation stored only the LazyColumn index. That is fragile because
 * opening/editing a record can refresh and reorder the list before the destination returns.
 * We now bookmark the first visible record by its stable UnifiedRecordItem.key and keep the
 * absolute index only as a fallback when that record was deleted.
 */
data class AllRecordsScrollMemory(
    val anchorKey: String? = null,
    val fallbackIndex: Int = 0,
    val offset: Int = 0,
    val restorePending: Boolean = false,
) {
    fun capture(anchorKey: String?, index: Int, offset: Int): AllRecordsScrollMemory =
        copy(
            anchorKey = anchorKey,
            fallbackIndex = index.coerceAtLeast(0),
            offset = offset.coerceAtLeast(0),
            restorePending = true,
        )
}

data class AllRecordsScrollTarget(val index: Int, val offset: Int)

fun resolveAllRecordsRestoreTarget(
    memory: AllRecordsScrollMemory,
    orderedRecordKeys: List<String>,
    headerCount: Int = 3,
): AllRecordsScrollTarget? {
    if (!memory.restorePending || orderedRecordKeys.isEmpty()) return null

    val anchorRecordIndex = memory.anchorKey?.let(orderedRecordKeys::indexOf)?.takeIf { it >= 0 }
    val targetIndex = if (anchorRecordIndex != null) {
        headerCount + anchorRecordIndex
    } else {
        memory.fallbackIndex.coerceIn(0, headerCount + orderedRecordKeys.lastIndex)
    }

    return AllRecordsScrollTarget(targetIndex, memory.offset.coerceAtLeast(0))
}

fun visibleRecordAnchorKey(
    firstVisibleItemIndex: Int,
    orderedRecordKeys: List<String>,
    headerCount: Int = 3,
): String? {
    val recordIndex = firstVisibleItemIndex - headerCount
    return orderedRecordKeys.getOrNull(recordIndex)
}

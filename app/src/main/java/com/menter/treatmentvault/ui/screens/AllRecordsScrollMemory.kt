package com.menter.treatmentvault.ui.screens

/**
 * Navigation-stable viewport bookmark for the unified records list.
 *
 * Stores the LazyColumn index and scroll offset directly, plus an anchor record key.
 * The LazyColumn index already includes any header items, so we never need to add or
 * subtract a hard-coded header count. If the list changed while the user was away,
 * we re-anchor using the record key.
 */
data class AllRecordsScrollMemory(
    val anchorKey: String? = null,
    val lazyColumnIndex: Int = 0,
    val offset: Int = 0,
    val restorePending: Boolean = false,
) {
    fun capture(anchorKey: String?, lazyColumnIndex: Int, offset: Int): AllRecordsScrollMemory =
        copy(
            anchorKey = anchorKey,
            lazyColumnIndex = lazyColumnIndex.coerceAtLeast(0),
            offset = offset.coerceAtLeast(0),
            restorePending = true,
        )
}

data class AllRecordsScrollTarget(val index: Int, val offset: Int)

/**
 * Resolve the restore target for the records list.
 *
 * @param memory the saved bookmark
 * @param orderedRecordKeys the current record keys, in list order
 * @param headerCount how many header items currently precede the records list
 */
fun resolveAllRecordsRestoreTarget(
    memory: AllRecordsScrollMemory,
    orderedRecordKeys: List<String>,
    headerCount: Int,
): AllRecordsScrollTarget? {
    if (!memory.restorePending) return null

    val anchorRecordIndex = memory.anchorKey?.let(orderedRecordKeys::indexOf)?.takeIf { it >= 0 }
    if (anchorRecordIndex != null) {
        return AllRecordsScrollTarget(headerCount + anchorRecordIndex, memory.offset.coerceAtLeast(0))
    }

    if (orderedRecordKeys.isEmpty()) return null

    val maxIndex = headerCount + orderedRecordKeys.size - 1
    return AllRecordsScrollTarget(
        memory.lazyColumnIndex.coerceIn(0, maxIndex.coerceAtLeast(0)),
        memory.offset.coerceAtLeast(0),
    )
}

/**
 * Find the record key at the current first visible LazyColumn index.
 *
 * @param firstVisibleItemIndex LazyColumn index, including header items
 * @param orderedRecordKeys current record keys, in list order
 * @param headerCount how many header items currently precede the records list
 */
fun visibleRecordAnchorKey(
    firstVisibleItemIndex: Int,
    orderedRecordKeys: List<String>,
    headerCount: Int,
): String? {
    val recordIndex = firstVisibleItemIndex - headerCount
    return orderedRecordKeys.getOrNull(recordIndex)
}

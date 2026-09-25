package com.menter.treatmentvault.ui.screens

data class AllRecordsScrollMemory(
    val index: Int = 0,
    val offset: Int = 0,
    val restorePending: Boolean = false,
) {
    fun capture(index: Int, offset: Int): AllRecordsScrollMemory =
        copy(index = index.coerceAtLeast(0), offset = offset.coerceAtLeast(0), restorePending = true)
}

data class AllRecordsScrollTarget(val index: Int, val offset: Int)

fun resolveAllRecordsRestoreTarget(
    memory: AllRecordsScrollMemory,
    recordCount: Int,
    headerCount: Int = 3,
): AllRecordsScrollTarget? {
    if (!memory.restorePending || recordCount <= 0) return null
    val maxIndex = (recordCount + headerCount - 1).coerceAtLeast(0)
    return AllRecordsScrollTarget(
        index = memory.index.coerceIn(0, maxIndex),
        offset = memory.offset.coerceAtLeast(0),
    )
}

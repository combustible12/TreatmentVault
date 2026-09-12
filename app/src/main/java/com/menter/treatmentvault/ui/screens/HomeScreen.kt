package com.menter.treatmentvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CategoryEntity
import kotlinx.coroutines.launch
import kotlin.math.abs

private suspend fun persistVisibleHomeOrder(
    allCategories: List<CategoryEntity>,
    orderedVisible: List<CategoryEntity>,
    save: suspend (CategoryEntity) -> Unit,
) {
    val slots = allCategories.filterNot { it.hidden }.map { it.sortOrder }.sorted()
    val now = System.currentTimeMillis()
    orderedVisible.forEachIndexed { index, category ->
        save(
            category.copy(
                sortOrder = slots.getOrElse(index) { index * 10 },
                updatedAt = now + index,
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(go: (String) -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreatmentVaultApplication
    val dao = app.database.dao()
    val categories by dao.categories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var draggingKey by remember { mutableStateOf<String?>(null) }
    var localVisible by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }

    LaunchedEffect(categories, draggingKey) {
        if (draggingKey == null) {
            localVisible = categories.filterNot { it.hidden }
        }
    }
    val latestVisible by rememberUpdatedState(localVisible)
    val latestCategories by rememberUpdatedState(categories)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("治疗资料库") },
                actions = {
                    IconButton(onClick = { go("categories") }) {
                        Icon(Icons.Default.Settings, contentDescription = "类目管理")
                    }
                    IconButton(onClick = { go("update") }) {
                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = "检查更新")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "长按卡片即可拖动排序",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 14.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(localVisible, key = { it.key }) { category ->
                    val route = if (category.isSystem) category.route else "custom/${category.key}"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .pointerInput(category.key) {
                                var dx = 0f
                                var dy = 0f
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dx = 0f
                                        dy = 0f
                                        draggingKey = category.key
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        localVisible = latestCategories.filterNot { it.hidden }
                                    },
                                    onDragEnd = {
                                        val visibleSnapshot = latestVisible
                                        val allSnapshot = latestCategories
                                        draggingKey = null
                                        scope.launch {
                                            persistVisibleHomeOrder(allSnapshot, visibleSnapshot) {
                                                dao.updateCategory(it)
                                            }
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dx += dragAmount.x
                                        dy += dragAmount.y
                                        val threshold = 54.dp.toPx()
                                        if (abs(dx) >= threshold || abs(dy) >= threshold) {
                                            val current = latestVisible.toMutableList()
                                            val from = current.indexOfFirst { it.key == category.key }
                                            val step = if (abs(dx) > abs(dy)) {
                                                if (dx > 0f) 1 else -1
                                            } else {
                                                if (dy > 0f) 2 else -2
                                            }
                                            val to = from + step
                                            if (from >= 0 && to in current.indices) {
                                                val moved = current.removeAt(from)
                                                current.add(to, moved)
                                                localVisible = current
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            dx = 0f
                                            dy = 0f
                                        }
                                    },
                                )
                            }
                            .clickable(enabled = draggingKey == null) {
                                if (route.isNotBlank()) go(route)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(categoryIcon(category.iconKey), contentDescription = null)
                            Text(category.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

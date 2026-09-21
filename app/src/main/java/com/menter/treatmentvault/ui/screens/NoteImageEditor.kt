package com.menter.treatmentvault.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

enum class NoteImageEditMode { CROP, DRAW, MOSAIC, TEXT }

private sealed interface NoteImageEditOperation {
    data class DrawStroke(val points: List<PointF>) : NoteImageEditOperation
    data class MosaicStroke(val points: List<PointF>) : NoteImageEditOperation
    data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) : NoteImageEditOperation
    data class AddText(val text: String, val x: Float, val y: Float) : NoteImageEditOperation
}

private class NoteImageEditView(context: Context) : View(context) {
    private var original: Bitmap? = null
    private var working: Bitmap? = null
    var mode: NoteImageEditMode = NoteImageEditMode.DRAW

    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
        set(value) {
            field = value
            notifyHistoryChanged()
        }

    private val operations = mutableListOf<NoteImageEditOperation>()
    private val redoOperations = mutableListOf<NoteImageEditOperation>()

    private var lastBitmapPoint: PointF? = null
    private var currentDrawStroke: MutableList<PointF>? = null
    private var currentMosaicStroke: MutableList<PointF>? = null
    private var cropStart: PointF? = null
    private var cropEnd: PointF? = null
    private var textPoint: PointF? = null

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.RED
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val cropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    fun load(file: File) {
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return
        original = decoded.copy(Bitmap.Config.ARGB_8888, false)
        operations.clear()
        redoOperations.clear()
        working = decoded.copy(Bitmap.Config.ARGB_8888, true)
        clearTransientState()
        notifyHistoryChanged()
        invalidate()
    }

    fun reset() {
        val src = original ?: return
        operations.clear()
        redoOperations.clear()
        working = src.copy(Bitmap.Config.ARGB_8888, true)
        clearTransientState()
        notifyHistoryChanged()
        invalidate()
    }

    fun undo(): Boolean {
        if (operations.isEmpty()) return false
        redoOperations += operations.removeAt(operations.lastIndex)
        rebuildFromHistory()
        notifyHistoryChanged()
        return true
    }

    fun redo(): Boolean {
        if (redoOperations.isEmpty()) return false
        operations += redoOperations.removeAt(redoOperations.lastIndex)
        rebuildFromHistory()
        notifyHistoryChanged()
        return true
    }

    private fun notifyHistoryChanged() {
        onHistoryChanged?.invoke(operations.isNotEmpty(), redoOperations.isNotEmpty())
    }

    private fun clearTransientState() {
        lastBitmapPoint = null
        currentDrawStroke = null
        currentMosaicStroke = null
        cropStart = null
        cropEnd = null
        textPoint = null
    }

    private fun copyPoint(point: PointF): PointF = PointF(point.x, point.y)

    private fun commitOperation(operation: NoteImageEditOperation) {
        operations += operation
        redoOperations.clear()
        notifyHistoryChanged()
    }

    private fun displayRect(bitmap: Bitmap): RectF {
        if (width <= 0 || height <= 0) return RectF()
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        return RectF((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f)
    }

    private fun toBitmapPoint(x: Float, y: Float): PointF? {
        val bitmap = working ?: return null
        val rect = displayRect(bitmap)
        if (!rect.contains(x, y) || rect.width() <= 0f || rect.height() <= 0f) return null
        val bx = ((x - rect.left) / rect.width() * bitmap.width).coerceIn(0f, bitmap.width - 1f)
        val by = ((y - rect.top) / rect.height() * bitmap.height).coerceIn(0f, bitmap.height - 1f)
        return PointF(bx, by)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(AndroidColor.BLACK)
        val bitmap = working ?: return
        val rect = displayRect(bitmap)
        canvas.drawBitmap(bitmap, null, rect, null)
        if (mode == NoteImageEditMode.CROP) {
            val a = cropStart
            val b = cropEnd
            if (a != null && b != null) {
                val left = rect.left + min(a.x, b.x) / bitmap.width * rect.width()
                val right = rect.left + max(a.x, b.x) / bitmap.width * rect.width()
                val top = rect.top + min(a.y, b.y) / bitmap.height * rect.height()
                val bottom = rect.top + max(a.y, b.y) / bitmap.height * rect.height()
                canvas.drawRect(left, top, right, bottom, cropPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bitmap = working ?: return false

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            when (mode) {
                NoteImageEditMode.DRAW -> finishDrawStroke(bitmap)
                NoteImageEditMode.MOSAIC -> finishMosaicStroke()
                else -> Unit
            }
            lastBitmapPoint = null
            invalidate()
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (mode == NoteImageEditMode.DRAW || mode == NoteImageEditMode.MOSAIC) {
                rebuildFromHistory()
            }
            currentDrawStroke = null
            currentMosaicStroke = null
            lastBitmapPoint = null
            invalidate()
            return true
        }

        val point = toBitmapPoint(event.x, event.y) ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (mode) {
                    NoteImageEditMode.CROP -> {
                        cropStart = point
                        cropEnd = point
                    }
                    NoteImageEditMode.DRAW -> {
                        lastBitmapPoint = point
                        currentDrawStroke = mutableListOf(copyPoint(point))
                    }
                    NoteImageEditMode.MOSAIC -> {
                        currentMosaicStroke = mutableListOf(copyPoint(point))
                        applyMosaic(bitmap, point)
                    }
                    NoteImageEditMode.TEXT -> textPoint = point
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                when (mode) {
                    NoteImageEditMode.CROP -> cropEnd = point
                    NoteImageEditMode.DRAW -> {
                        val last = lastBitmapPoint ?: point
                        Canvas(bitmap).drawLine(last.x, last.y, point.x, point.y, drawPaint)
                        lastBitmapPoint = point
                        currentDrawStroke?.add(copyPoint(point))
                    }
                    NoteImageEditMode.MOSAIC -> {
                        applyMosaic(bitmap, point)
                        currentMosaicStroke?.add(copyPoint(point))
                    }
                    NoteImageEditMode.TEXT -> textPoint = point
                }
                invalidate()
            }
        }
        return true
    }

    private fun finishDrawStroke(bitmap: Bitmap) {
        val points = currentDrawStroke?.map(::copyPoint).orEmpty()
        currentDrawStroke = null
        if (points.isEmpty()) return
        if (points.size == 1) {
            val point = points.first()
            Canvas(bitmap).drawCircle(point.x, point.y, drawPaint.strokeWidth / 2f, Paint(drawPaint).apply { style = Paint.Style.FILL })
        }
        commitOperation(NoteImageEditOperation.DrawStroke(points))
    }

    private fun finishMosaicStroke() {
        val points = currentMosaicStroke?.map(::copyPoint).orEmpty()
        currentMosaicStroke = null
        if (points.isNotEmpty()) commitOperation(NoteImageEditOperation.MosaicStroke(points))
    }

    private fun drawStroke(bitmap: Bitmap, points: List<PointF>) {
        if (points.isEmpty()) return
        val canvas = Canvas(bitmap)
        if (points.size == 1) {
            val point = points.first()
            canvas.drawCircle(point.x, point.y, drawPaint.strokeWidth / 2f, Paint(drawPaint).apply { style = Paint.Style.FILL })
            return
        }
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            canvas.drawLine(a.x, a.y, b.x, b.y, drawPaint)
        }
    }

    private fun applyMosaic(bitmap: Bitmap, point: PointF) {
        val radius = max(18, min(bitmap.width, bitmap.height) / 28)
        val left = (point.x.toInt() - radius).coerceAtLeast(0)
        val top = (point.y.toInt() - radius).coerceAtLeast(0)
        val right = (point.x.toInt() + radius).coerceAtMost(bitmap.width)
        val bottom = (point.y.toInt() + radius).coerceAtMost(bitmap.height)
        val w = right - left
        val h = bottom - top
        if (w < 4 || h < 4) return
        val area = Bitmap.createBitmap(bitmap, left, top, w, h)
        val tiny = Bitmap.createScaledBitmap(area, max(1, w / 10), max(1, h / 10), false)
        val pixelated = Bitmap.createScaledBitmap(tiny, w, h, false)
        Canvas(bitmap).drawBitmap(pixelated, left.toFloat(), top.toFloat(), null)
    }

    private fun applyText(bitmap: Bitmap, operation: NoteImageEditOperation.AddText) {
        val size = max(32f, min(bitmap.width, bitmap.height) / 14f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = size
            style = Paint.Style.FILL
            setShadowLayer(5f, 1f, 1f, AndroidColor.BLACK)
        }
        Canvas(bitmap).drawText(operation.text, operation.x, operation.y, paint)
    }

    private fun cropBitmap(bitmap: Bitmap, operation: NoteImageEditOperation.CropRect): Bitmap {
        val left = operation.left.coerceIn(0, bitmap.width - 1)
        val top = operation.top.coerceIn(0, bitmap.height - 1)
        val right = operation.right.coerceIn(left + 1, bitmap.width)
        val bottom = operation.bottom.coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            .copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun rebuildFromHistory() {
        val src = original ?: return
        var bitmap = src.copy(Bitmap.Config.ARGB_8888, true)
        operations.forEach { operation ->
            when (operation) {
                is NoteImageEditOperation.DrawStroke -> drawStroke(bitmap, operation.points)
                is NoteImageEditOperation.MosaicStroke -> operation.points.forEach { applyMosaic(bitmap, it) }
                is NoteImageEditOperation.AddText -> applyText(bitmap, operation)
                is NoteImageEditOperation.CropRect -> bitmap = cropBitmap(bitmap, operation)
            }
        }
        working = bitmap
        clearTransientState()
        invalidate()
    }

    fun commitCrop(): Boolean {
        val bitmap = working ?: return false
        val a = cropStart ?: return false
        val b = cropEnd ?: return false
        val left = min(a.x, b.x).toInt().coerceIn(0, bitmap.width - 1)
        val top = min(a.y, b.y).toInt().coerceIn(0, bitmap.height - 1)
        val right = max(a.x, b.x).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = max(a.y, b.y).toInt().coerceIn(top + 1, bitmap.height)
        if (right - left < 20 || bottom - top < 20) return false
        val operation = NoteImageEditOperation.CropRect(left, top, right, bottom)
        working = cropBitmap(bitmap, operation)
        cropStart = null
        cropEnd = null
        commitOperation(operation)
        invalidate()
        return true
    }

    fun addText(text: String): Boolean {
        val bitmap = working ?: return false
        val value = text.trim()
        if (value.isEmpty()) return false
        val point = textPoint ?: PointF(bitmap.width * 0.15f, bitmap.height * 0.2f)
        val operation = NoteImageEditOperation.AddText(value, point.x, point.y)
        applyText(bitmap, operation)
        commitOperation(operation)
        invalidate()
        return true
    }

    fun save(file: File): Boolean {
        val bitmap = working ?: return false
        return runCatching {
            val ext = file.extension.lowercase()
            val format = when (ext) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.JPEG
            }
            FileOutputStream(file, false).use { out -> bitmap.compress(format, 94, out) }
            true
        }.getOrDefault(false)
    }
}

@Composable
internal fun NoteImageEditorContent(
    file: File,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var mode by remember(file.absolutePath) { mutableStateOf(NoteImageEditMode.DRAW) }
    var textValue by remember(file.absolutePath) { mutableStateOf("") }
    var viewRef by remember(file.absolutePath) { mutableStateOf<NoteImageEditView?>(null) }
    var message by remember(file.absolutePath) { mutableStateOf<String?>(null) }
    var canUndo by remember(file.absolutePath) { mutableStateOf(false) }
    var canRedo by remember(file.absolutePath) { mutableStateOf(false) }

    val historyCallback = remember(file.absolutePath) {
        { undo: Boolean, redo: Boolean ->
            canUndo = undo
            canRedo = redo
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                        ) { Text("取消") }

                        Spacer(Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                if (viewRef?.undo() == true) message = null
                            },
                            enabled = canUndo,
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                        ) { Text("撤销") }

                        TextButton(
                            onClick = {
                                if (viewRef?.redo() == true) message = null
                            },
                            enabled = canRedo,
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                        ) { Text("重做") }

                        TextButton(
                            onClick = {
                                viewRef?.reset()
                                message = "已恢复到打开编辑器时的原图"
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                        ) { Text("重置") }

                        Button(
                            onClick = {
                                if (viewRef?.save(file) == true) {
                                    onSaved()
                                } else {
                                    message = "保存失败，请重试"
                                }
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) { Text("保存") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = mode == NoteImageEditMode.CROP,
                            onClick = { mode = NoteImageEditMode.CROP; message = null },
                            label = { Text("裁剪", maxLines = 1, overflow = TextOverflow.Clip) },
                            leadingIcon = { Icon(Icons.Default.Crop, null, modifier = Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = mode == NoteImageEditMode.DRAW,
                            onClick = { mode = NoteImageEditMode.DRAW; message = null },
                            label = { Text("画笔", maxLines = 1, overflow = TextOverflow.Clip) },
                            leadingIcon = { Icon(Icons.Default.Brush, null, modifier = Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = mode == NoteImageEditMode.MOSAIC,
                            onClick = { mode = NoteImageEditMode.MOSAIC; message = null },
                            label = { Text("马赛克", maxLines = 1, overflow = TextOverflow.Clip) },
                            leadingIcon = { Icon(Icons.Default.GridOn, null, modifier = Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = mode == NoteImageEditMode.TEXT,
                            onClick = { mode = NoteImageEditMode.TEXT; message = null },
                            label = { Text("文字", maxLines = 1, overflow = TextOverflow.Clip) },
                            leadingIcon = { Icon(Icons.Default.TextFields, null, modifier = Modifier.size(18.dp)) },
                        )
                    }

                    if (mode == NoteImageEditMode.CROP) {
                        Button(
                            onClick = {
                                if (viewRef?.commitCrop() == true) {
                                    message = null
                                } else {
                                    message = "请先在图片上拖出裁剪范围"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("应用当前裁剪范围") }
                    }

                    if (mode == NoteImageEditMode.TEXT) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = textValue,
                                onValueChange = { textValue = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("输入文字后点图片选择位置") },
                                singleLine = true,
                            )
                            Button(
                                onClick = {
                                    if (viewRef?.addText(textValue) == true) {
                                        textValue = ""
                                        message = null
                                    }
                                },
                            ) { Text("写入") }
                        }
                    }

                    message?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    NoteImageEditView(ctx).also {
                        it.onHistoryChanged = historyCallback
                        it.load(file)
                        it.mode = mode
                        viewRef = it
                    }
                },
                update = {
                    it.mode = mode
                    it.onHistoryChanged = historyCallback
                },
            )
        }
    }
}

@Composable
internal fun NoteImageEditorDialog(
    file: File,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        NoteImageEditorContent(
            file = file,
            onDismiss = onDismiss,
            onSaved = {
                onSaved()
                onDismiss()
            },
        )
    }
}

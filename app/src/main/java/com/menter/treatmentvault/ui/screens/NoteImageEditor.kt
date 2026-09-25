package com.menter.treatmentvault.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class NoteImageEditMode { CROP, DRAW, MOSAIC, TEXT }
enum class NoteImageTextStyle { NORMAL, BOLD, ITALIC, OUTLINE }

private sealed interface NoteImageEditOperation {
    data class DrawStroke(val points: List<PointF>) : NoteImageEditOperation
    data class MosaicStroke(val points: List<PointF>) : NoteImageEditOperation
    data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) : NoteImageEditOperation
}

private data class NoteImageTextItem(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val sizePx: Float,
    val color: Int,
    val style: NoteImageTextStyle,
)

private data class NoteImageEditorSnapshot(
    val operations: List<NoteImageEditOperation>,
    val texts: List<NoteImageTextItem>,
)

private class NoteImageEditView(context: Context) : View(context) {
    private var original: Bitmap? = null
    private var working: Bitmap? = null
    var mode: NoteImageEditMode = NoteImageEditMode.DRAW

    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
        set(value) {
            field = value
            notifyHistoryChanged()
        }

    var onTextSelectionChanged: ((NoteImageTextItem?) -> Unit)? = null
        set(value) {
            field = value
            notifyTextSelectionChanged()
        }

    private val operations = mutableListOf<NoteImageEditOperation>()
    private val textItems = mutableListOf<NoteImageTextItem>()
    private val undoHistory = mutableListOf<NoteImageEditorSnapshot>()
    private val redoHistory = mutableListOf<NoteImageEditorSnapshot>()

    private var nextTextId = 1L
    private var selectedTextId: Long? = null
    private var lastBitmapPoint: PointF? = null
    private var currentDrawStroke: MutableList<PointF>? = null
    private var currentMosaicStroke: MutableList<PointF>? = null
    private var cropStart: PointF? = null
    private var cropEnd: PointF? = null
    private var textPoint: PointF? = null

    private var textDragStartPoint: PointF? = null
    private var textDragStartItem: NoteImageTextItem? = null
    private var textDragHistoryPushed = false
    private var scaleHistoryPushed = false

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
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (mode != NoteImageEditMode.TEXT || selectedText() == null) return false
                if (!scaleHistoryPushed) {
                    pushHistory()
                    scaleHistoryPushed = true
                }
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val item = selectedText() ?: return false
                val minSize = 18f
                val maxSize = ((working?.let { max(it.width, it.height) } ?: 2048) * 0.45f).coerceAtLeast(220f)
                replaceText(item.copy(sizePx = (item.sizePx * detector.scaleFactor).coerceIn(minSize, maxSize)))
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scaleHistoryPushed = false
            }
        },
    )

    fun load(file: File) {
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return
        original = decoded.copy(Bitmap.Config.ARGB_8888, false)
        operations.clear()
        textItems.clear()
        undoHistory.clear()
        redoHistory.clear()
        working = decoded.copy(Bitmap.Config.ARGB_8888, true)
        selectedTextId = null
        nextTextId = 1L
        clearTransientState()
        notifyHistoryChanged()
        notifyTextSelectionChanged()
        invalidate()
    }

    fun reset() {
        val src = original ?: return
        if (operations.isNotEmpty() || textItems.isNotEmpty()) pushHistory()
        operations.clear()
        textItems.clear()
        working = src.copy(Bitmap.Config.ARGB_8888, true)
        selectedTextId = null
        clearTransientState()
        notifyHistoryChanged()
        notifyTextSelectionChanged()
        invalidate()
    }

    fun undo(): Boolean {
        if (undoHistory.isEmpty()) return false
        redoHistory += snapshot()
        restoreSnapshot(undoHistory.removeAt(undoHistory.lastIndex))
        notifyHistoryChanged()
        return true
    }

    fun redo(): Boolean {
        if (redoHistory.isEmpty()) return false
        undoHistory += snapshot()
        restoreSnapshot(redoHistory.removeAt(redoHistory.lastIndex))
        notifyHistoryChanged()
        return true
    }

    private fun deepCopyOperation(operation: NoteImageEditOperation): NoteImageEditOperation = when (operation) {
        is NoteImageEditOperation.DrawStroke -> operation.copy(points = operation.points.map(::copyPoint))
        is NoteImageEditOperation.MosaicStroke -> operation.copy(points = operation.points.map(::copyPoint))
        is NoteImageEditOperation.CropRect -> operation.copy()
    }

    private fun snapshot(): NoteImageEditorSnapshot = NoteImageEditorSnapshot(
        operations = operations.map(::deepCopyOperation),
        texts = textItems.map { it.copy() },
    )

    private fun pushHistory() {
        undoHistory += snapshot()
        if (undoHistory.size > 40) undoHistory.removeAt(0)
        redoHistory.clear()
        notifyHistoryChanged()
    }

    private fun restoreSnapshot(snapshot: NoteImageEditorSnapshot) {
        operations.clear()
        operations += snapshot.operations.map(::deepCopyOperation)
        textItems.clear()
        textItems += snapshot.texts.map { it.copy() }
        if (selectedTextId != null && textItems.none { it.id == selectedTextId }) selectedTextId = null
        rebuildFromHistory()
        notifyTextSelectionChanged()
    }

    private fun notifyHistoryChanged() {
        onHistoryChanged?.invoke(undoHistory.isNotEmpty(), redoHistory.isNotEmpty())
    }

    private fun selectedText(): NoteImageTextItem? =
        selectedTextId?.let { id -> textItems.firstOrNull { it.id == id } }

    private fun notifyTextSelectionChanged() {
        onTextSelectionChanged?.invoke(selectedText())
    }

    fun clearTextSelection() {
        selectedTextId = null
        textPoint = null
        notifyTextSelectionChanged()
        invalidate()
    }

    private fun selectText(id: Long?) {
        selectedTextId = id
        notifyTextSelectionChanged()
        invalidate()
    }

    private fun replaceText(item: NoteImageTextItem) {
        val index = textItems.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            textItems[index] = item
            notifyTextSelectionChanged()
            invalidate()
        }
    }

    fun updateSelectedTextContent(text: String): Boolean {
        val item = selectedText() ?: return false
        val value = text.trim()
        if (value.isEmpty() || value == item.text) return false
        pushHistory()
        replaceText(item.copy(text = value))
        return true
    }

    fun updateSelectedTextColor(color: Int): Boolean {
        val item = selectedText() ?: return false
        if (item.color == color) return false
        pushHistory()
        replaceText(item.copy(color = color))
        return true
    }

    fun updateSelectedTextStyle(style: NoteImageTextStyle): Boolean {
        val item = selectedText() ?: return false
        if (item.style == style) return false
        pushHistory()
        replaceText(item.copy(style = style))
        return true
    }

    fun scaleSelectedTextBy(factor: Float): Boolean {
        val item = selectedText() ?: return false
        val minSize = 18f
        val maxSize = ((working?.let { max(it.width, it.height) } ?: 2048) * 0.45f).coerceAtLeast(220f)
        val next = (item.sizePx * factor).coerceIn(minSize, maxSize)
        if (abs(next - item.sizePx) < 0.1f) return false
        pushHistory()
        replaceText(item.copy(sizePx = next))
        return true
    }

    private fun clearTransientState() {
        lastBitmapPoint = null
        currentDrawStroke = null
        currentMosaicStroke = null
        cropStart = null
        cropEnd = null
        textPoint = null
        textDragStartPoint = null
        textDragStartItem = null
        textDragHistoryPushed = false
        scaleHistoryPushed = false
    }

    private fun copyPoint(point: PointF): PointF = PointF(point.x, point.y)

    private fun commitOperation(operation: NoteImageEditOperation) {
        pushHistory()
        operations += operation
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

    private fun textPaint(item: NoteImageTextItem, stroke: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (stroke) AndroidColor.BLACK else item.color
            textSize = item.sizePx
            style = if (stroke) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = if (stroke) max(3f, item.sizePx * 0.08f) else 0f
            strokeJoin = Paint.Join.ROUND
            typeface = when (item.style) {
                NoteImageTextStyle.BOLD -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                NoteImageTextStyle.ITALIC -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }
            if (item.style != NoteImageTextStyle.OUTLINE && !stroke) {
                setShadowLayer(max(2f, item.sizePx * 0.035f), 1f, 1f, AndroidColor.BLACK)
            }
        }

    private fun textBounds(item: NoteImageTextItem): RectF {
        val paint = textPaint(item)
        val fm = paint.fontMetrics
        val width = paint.measureText(item.text)
        val pad = max(28f, item.sizePx * 0.32f)
        return RectF(
            item.x - pad,
            item.y + fm.ascent - pad,
            item.x + width + pad,
            item.y + fm.descent + pad,
        )
    }

    private fun drawTextItem(canvas: Canvas, item: NoteImageTextItem) {
        if (item.style == NoteImageTextStyle.OUTLINE) {
            canvas.drawText(item.text, item.x, item.y, textPaint(item, stroke = true))
        }
        canvas.drawText(item.text, item.x, item.y, textPaint(item))
    }

    private fun hitTestText(point: PointF): NoteImageTextItem? =
        textItems.asReversed().firstOrNull { textBounds(it).contains(point.x, point.y) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(AndroidColor.BLACK)
        val bitmap = working ?: return
        val rect = displayRect(bitmap)
        canvas.drawBitmap(bitmap, null, rect, null)

        val sx = rect.width() / bitmap.width
        val sy = rect.height() / bitmap.height
        canvas.save()
        canvas.translate(rect.left, rect.top)
        canvas.scale(sx, sy)
        textItems.forEach { drawTextItem(canvas, it) }

        if (mode == NoteImageEditMode.TEXT) {
            selectedText()?.let { item ->
                selectionPaint.strokeWidth = 3f / max(0.001f, min(sx, sy))
                canvas.drawRect(textBounds(item), selectionPaint)
            }
        }

        if (mode == NoteImageEditMode.CROP) {
            val a = cropStart
            val b = cropEnd
            if (a != null && b != null) {
                cropPaint.strokeWidth = 4f / max(0.001f, min(sx, sy))
                canvas.drawRect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y), cropPaint)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bitmap = working ?: return false
        if (mode == NoteImageEditMode.TEXT) {
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) return true
        }

        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (mode == NoteImageEditMode.DRAW || mode == NoteImageEditMode.MOSAIC) rebuildFromHistory()
            currentDrawStroke = null
            currentMosaicStroke = null
            lastBitmapPoint = null
            textDragStartPoint = null
            textDragStartItem = null
            textDragHistoryPushed = false
            invalidate()
            return true
        }

        val point = toBitmapPoint(event.x, event.y)

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            when (mode) {
                NoteImageEditMode.DRAW -> finishDrawStroke(bitmap)
                NoteImageEditMode.MOSAIC -> finishMosaicStroke()
                NoteImageEditMode.TEXT -> {
                    textDragStartPoint = null
                    textDragStartItem = null
                    textDragHistoryPushed = false
                }
                else -> Unit
            }
            lastBitmapPoint = null
            invalidate()
            return true
        }

        if (point == null) return true

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
                    NoteImageEditMode.TEXT -> {
                        val hit = hitTestText(point)
                        if (hit != null) {
                            selectText(hit.id)
                            textDragStartPoint = copyPoint(point)
                            textDragStartItem = hit
                            textDragHistoryPushed = false
                        } else {
                            selectText(null)
                            textPoint = copyPoint(point)
                        }
                    }
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
                    NoteImageEditMode.TEXT -> {
                        val start = textDragStartPoint
                        val startItem = textDragStartItem
                        if (start != null && startItem != null) {
                            val dx = point.x - start.x
                            val dy = point.y - start.y
                            if (!textDragHistoryPushed && (abs(dx) > 1f || abs(dy) > 1f)) {
                                pushHistory()
                                textDragHistoryPushed = true
                            }
                            replaceText(
                                startItem.copy(
                                    x = (startItem.x + dx).coerceIn(0f, bitmap.width.toFloat()),
                                    y = (startItem.y + dy).coerceIn(0f, bitmap.height.toFloat()),
                                ),
                            )
                        }
                    }
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
            Canvas(bitmap).drawCircle(
                point.x,
                point.y,
                drawPaint.strokeWidth / 2f,
                Paint(drawPaint).apply { style = Paint.Style.FILL },
            )
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
            canvas.drawCircle(
                point.x,
                point.y,
                drawPaint.strokeWidth / 2f,
                Paint(drawPaint).apply { style = Paint.Style.FILL },
            )
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
                is NoteImageEditOperation.CropRect -> bitmap = cropBitmap(bitmap, operation)
            }
        }
        working = bitmap
        cropStart = null
        cropEnd = null
        lastBitmapPoint = null
        currentDrawStroke = null
        currentMosaicStroke = null
        textDragStartPoint = null
        textDragStartItem = null
        textDragHistoryPushed = false
        scaleHistoryPushed = false
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

        pushHistory()
        val operation = NoteImageEditOperation.CropRect(left, top, right, bottom)
        working = cropBitmap(bitmap, operation)
        operations += operation

        for (i in textItems.indices) {
            val item = textItems[i]
            textItems[i] = item.copy(
                x = item.x - left,
                y = item.y - top,
            )
        }
        cropStart = null
        cropEnd = null
        notifyHistoryChanged()
        notifyTextSelectionChanged()
        invalidate()
        return true
    }

    fun addText(text: String): Boolean {
        val bitmap = working ?: return false
        val value = text.trim()
        if (value.isEmpty()) return false

        val baseSize = max(36f, min(bitmap.width, bitmap.height) / 12f)
        val point = textPoint ?: PointF(bitmap.width * 0.18f, bitmap.height * 0.25f)
        pushHistory()
        val item = NoteImageTextItem(
            id = nextTextId++,
            text = value,
            x = point.x,
            y = point.y,
            sizePx = baseSize,
            color = AndroidColor.WHITE,
            style = NoteImageTextStyle.OUTLINE,
        )
        textItems += item
        textPoint = null
        selectText(item.id)
        notifyHistoryChanged()
        invalidate()
        return true
    }

    private fun drawAllTexts(canvas: Canvas) {
        textItems.forEach { drawTextItem(canvas, it) }
    }

    fun save(file: File): Boolean {
        val bitmap = working ?: return false
        return runCatching {
            val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            drawAllTexts(Canvas(output))
            val ext = file.extension.lowercase()
            val format = when (ext) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.JPEG
            }
            FileOutputStream(file, false).use { out -> output.compress(format, 94, out) }
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
    var selectedText by remember(file.absolutePath) { mutableStateOf<NoteImageTextItem?>(null) }

    val historyCallback = remember(file.absolutePath) {
        { undo: Boolean, redo: Boolean ->
            canUndo = undo
            canRedo = redo
        }
    }
    val selectionCallback = remember(file.absolutePath) {
        { item: NoteImageTextItem? ->
            selectedText = item
            if (item != null) textValue = item.text
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
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
                            onClick = { if (viewRef?.undo() == true) message = null },
                            enabled = canUndo,
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                        ) { Text("撤销") }

                        TextButton(
                            onClick = { if (viewRef?.redo() == true) message = null },
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
                                if (viewRef?.save(file) == true) onSaved()
                                else message = "保存失败，请重试"
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
                                if (viewRef?.commitCrop() == true) message = null
                                else message = "请先在图片上拖出裁剪范围"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("应用当前裁剪范围") }
                    }

                    if (mode == NoteImageEditMode.TEXT) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = textValue, onValueChange = { textValue = it }, modifier = Modifier.weight(1f), placeholder = { Text("文字") }, singleLine = true)
                            Button(onClick = {
                                val ok = if (selectedText != null) viewRef?.updateSelectedTextContent(textValue) == true else viewRef?.addText(textValue) == true
                                if (!ok && textValue.isBlank()) message = "请输入文字"
                            }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Text(if (selectedText != null) "更新" else "添加") }
                            if (selectedText != null) TextButton(onClick = { viewRef?.clearTextSelection(); selectedText = null; textValue = "" }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text("新增") }
                        }
                        selectedText?.let { item ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { viewRef?.scaleSelectedTextBy(0.86f) }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 1.dp)) { Text("－") }
                                Text("字号 ${item.sizePx.toInt()}", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { viewRef?.scaleSelectedTextBy(1.16f) }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 1.dp)) { Text("＋") }
                                listOf("白" to AndroidColor.WHITE, "黑" to AndroidColor.BLACK, "红" to AndroidColor.RED, "黄" to AndroidColor.YELLOW, "蓝" to AndroidColor.BLUE, "绿" to AndroidColor.rgb(0, 160, 80)).forEach { (label, value) ->
                                    TextButton(onClick = { viewRef?.updateSelectedTextColor(value) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 1.dp, vertical = 1.dp)) { Text(if (item.color == value) "✓$label" else label, color = Color(value), maxLines = 1) }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf(NoteImageTextStyle.NORMAL to "常规", NoteImageTextStyle.BOLD to "粗体", NoteImageTextStyle.ITALIC to "斜体", NoteImageTextStyle.OUTLINE to "描边").forEach { (style, label) ->
                                    TextButton(onClick = { viewRef?.updateSelectedTextStyle(style) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 1.dp)) { Text(if (item.style == style) "✓$label" else label, maxLines = 1) }
                                }
                            }
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
        },
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            factory = { ctx ->
                NoteImageEditView(ctx).also {
                    it.onHistoryChanged = historyCallback
                    it.onTextSelectionChanged = selectionCallback
                    it.load(file)
                    it.mode = mode
                    viewRef = it
                }
            },
            update = {
                it.mode = mode
                it.onHistoryChanged = historyCallback
                it.onTextSelectionChanged = selectionCallback
            },
        )
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

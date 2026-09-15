package com.menter.treatmentvault.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
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

private class NoteImageEditView(context: Context) : View(context) {
    private var original: Bitmap? = null
    private var working: Bitmap? = null
    var mode: NoteImageEditMode = NoteImageEditMode.DRAW
    private var lastBitmapPoint: android.graphics.PointF? = null
    private var cropStart: android.graphics.PointF? = null
    private var cropEnd: android.graphics.PointF? = null
    private var textPoint: android.graphics.PointF? = null

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
        working = decoded.copy(Bitmap.Config.ARGB_8888, true)
        cropStart = null
        cropEnd = null
        textPoint = null
        invalidate()
    }

    fun reset() {
        val src = original ?: return
        working = src.copy(Bitmap.Config.ARGB_8888, true)
        cropStart = null
        cropEnd = null
        textPoint = null
        invalidate()
    }

    private fun displayRect(bitmap: Bitmap): RectF {
        if (width <= 0 || height <= 0) return RectF()
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        return RectF((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f)
    }

    private fun toBitmapPoint(x: Float, y: Float): android.graphics.PointF? {
        val bitmap = working ?: return null
        val rect = displayRect(bitmap)
        if (!rect.contains(x, y) || rect.width() <= 0f || rect.height() <= 0f) return null
        val bx = ((x - rect.left) / rect.width() * bitmap.width).coerceIn(0f, bitmap.width - 1f)
        val by = ((y - rect.top) / rect.height() * bitmap.height).coerceIn(0f, bitmap.height - 1f)
        return android.graphics.PointF(bx, by)
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
        val point = toBitmapPoint(event.x, event.y) ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (mode) {
                    NoteImageEditMode.CROP -> { cropStart = point; cropEnd = point }
                    NoteImageEditMode.DRAW -> lastBitmapPoint = point
                    NoteImageEditMode.MOSAIC -> applyMosaic(point)
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
                    }
                    NoteImageEditMode.MOSAIC -> applyMosaic(point)
                    NoteImageEditMode.TEXT -> textPoint = point
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lastBitmapPoint = null
        }
        return true
    }

    private fun applyMosaic(point: android.graphics.PointF) {
        val bitmap = working ?: return
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

    fun commitCrop(): Boolean {
        val bitmap = working ?: return false
        val a = cropStart ?: return false
        val b = cropEnd ?: return false
        val left = min(a.x, b.x).toInt().coerceIn(0, bitmap.width - 1)
        val top = min(a.y, b.y).toInt().coerceIn(0, bitmap.height - 1)
        val right = max(a.x, b.x).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = max(a.y, b.y).toInt().coerceIn(top + 1, bitmap.height)
        if (right - left < 20 || bottom - top < 20) return false
        working = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top).copy(Bitmap.Config.ARGB_8888, true)
        cropStart = null
        cropEnd = null
        invalidate()
        return true
    }

    fun addText(text: String): Boolean {
        val bitmap = working ?: return false
        val value = text.trim()
        if (value.isEmpty()) return false
        val point = textPoint ?: android.graphics.PointF(bitmap.width * 0.15f, bitmap.height * 0.2f)
        val size = max(32f, min(bitmap.width, bitmap.height) / 14f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = size
            style = Paint.Style.FILL
            setShadowLayer(5f, 1f, 1f, AndroidColor.BLACK)
        }
        Canvas(bitmap).drawText(value, point.x, point.y, paint)
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
internal fun NoteImageEditorDialog(
    file: File,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var mode by remember { mutableStateOf(NoteImageEditMode.DRAW) }
    var textValue by remember { mutableStateOf("") }
    var viewRef by remember { mutableStateOf<NoteImageEditView?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewRef?.reset(); message = "已恢复到打开编辑器时的原图" }) { Text("重置") }
                    Button(onClick = {
                        if (viewRef?.save(file) == true) {
                            onSaved()
                            onDismiss()
                        } else message = "保存失败，请重试"
                    }) { Text("保存") }
                }

                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx -> NoteImageEditView(ctx).also { it.load(file); it.mode = mode; viewRef = it } },
                    update = { it.mode = mode },
                )

                message?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall) }

                if (mode == NoteImageEditMode.TEXT) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入要添加的文字，再点图片选择位置") },
                            singleLine = true,
                        )
                        Button(onClick = { if (viewRef?.addText(textValue) == true) textValue = "" }) { Text("写入") }
                    }
                }
                if (mode == NoteImageEditMode.CROP) {
                    TextButton(
                        onClick = { if (viewRef?.commitCrop() != true) message = "请先在图片上拖出裁剪范围" },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("应用当前裁剪范围") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FilterChip(selected = mode == NoteImageEditMode.CROP, onClick = { mode = NoteImageEditMode.CROP }, label = { Text("裁剪") }, leadingIcon = { Icon(Icons.Default.Crop, null) })
                    FilterChip(selected = mode == NoteImageEditMode.DRAW, onClick = { mode = NoteImageEditMode.DRAW }, label = { Text("画笔") }, leadingIcon = { Icon(Icons.Default.Brush, null) })
                    FilterChip(selected = mode == NoteImageEditMode.MOSAIC, onClick = { mode = NoteImageEditMode.MOSAIC }, label = { Text("马赛克") }, leadingIcon = { Icon(Icons.Default.GridOn, null) })
                    FilterChip(selected = mode == NoteImageEditMode.TEXT, onClick = { mode = NoteImageEditMode.TEXT }, label = { Text("文字") }, leadingIcon = { Icon(Icons.Default.TextFields, null) })
                }
            }
        }
    }
}

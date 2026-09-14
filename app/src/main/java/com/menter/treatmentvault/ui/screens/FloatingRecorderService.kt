package com.menter.treatmentvault.ui.screens

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.menter.treatmentvault.MainActivity
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.AttachmentEntity
import com.menter.treatmentvault.data.CategoryEntity
import com.menter.treatmentvault.data.CustomEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class FloatingRecorderService : Service() {
    companion object {
        const val ACTION_ENABLE = "com.menter.treatmentvault.action.ENABLE_FLOATING_RECORDER"
        const val PREFS_NAME = "floating_recorder"
        const val PREF_ENABLED = "enabled"
        private const val PREF_X = "bubble_x"
        private const val PREF_Y = "bubble_y"
        private const val CHANNEL_ID = "floating_recorder"
        private const val NOTIFICATION_ID = 4102
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var bubbleDrawable: GradientDrawable? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var recorderSession: AudioRecorderSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat(buildNotification(recording = false))
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, true).apply()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        if (bubbleView != null) return

        val size = dp(38)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(84, 78, 72))
        }
        val view = View(this).apply {
            background = drawable
            alpha = 0.30f
            elevation = dp(4).toFloat()
            contentDescription = "悬浮录音"
        }
        bubbleDrawable = drawable

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(PREF_X, dp(12))
            y = prefs.getInt(PREF_Y, dp(220))
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downRawX).toInt()
                    params.y = startY + (event.rawY - downRawY).toInt()
                    runCatching { windowManager.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - downRawX)
                    val dy = abs(event.rawY - downRawY)
                    if (dx <= touchSlop && dy <= touchSlop) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        toggleRecording()
                    } else {
                        prefs.edit().putInt(PREF_X, params.x).putInt(PREF_Y, params.y).apply()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
        bubbleView = view
        bubbleParams = params
    }

    private fun updateBubbleRecordingState(recording: Boolean) {
        val drawable = bubbleDrawable ?: return
        if (recording) {
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.cornerRadius = dp(10).toFloat()
            bubbleView?.animate()?.alpha(0.40f)?.setDuration(120)?.start()
            bubbleView?.contentDescription = "正在悬浮录音"
        } else {
            drawable.shape = GradientDrawable.OVAL
            drawable.cornerRadius = 0f
            bubbleView?.animate()?.alpha(0.30f)?.setDuration(120)?.start()
            bubbleView?.contentDescription = "悬浮录音"
        }
        bubbleView?.invalidate()
    }

    private fun toggleRecording() {
        if (recorderSession == null) startRecording() else stopRecordingAndPersist()
    }

    private fun startRecording() {
        runCatching { createAudioRecorderSession(this) }
            .onSuccess {
                recorderSession = it
                updateBubbleRecordingState(recording = true)
                startForegroundCompat(buildNotification(recording = true))
            }
            .onFailure {
                updateBubbleRecordingState(recording = false)
                Toast.makeText(this, "无法开始录音，请检查麦克风权限", Toast.LENGTH_SHORT).show()
            }
    }

    private fun stopRecordingAndPersist() {
        val session = recorderSession ?: return
        recorderSession = null
        val saved = finishAudioRecording(this, session)
        updateBubbleRecordingState(recording = false)
        startForegroundCompat(buildNotification(recording = false))

        if (!saved) {
            Toast.makeText(this, "录音没有保存成功", Toast.LENGTH_SHORT).show()
            return
        }

        serviceScope.launch {
            runCatching { persistRecording(session.fileName) }
                .onFailure { attachmentFile(this@FloatingRecorderService, session.fileName).delete() }
        }
    }

    private suspend fun persistRecording(fileName: String) {
        val app = applicationContext as TreatmentVaultApplication
        val dao = app.database.dao()
        if (dao.categoryByKey("floating_audio") == null) {
            dao.saveCategory(
                CategoryEntity(
                    key = "floating_audio",
                    name = "悬浮录音",
                    route = "floating_audio",
                    iconKey = "circle",
                    sortOrder = 7,
                    isSystem = true,
                    smartEnabled = false,
                )
            )
        }

        val now = LocalDateTime.now()
        val fullTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val shortTime = now.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        val id = dao.saveCustomEntry(
            CustomEntryEntity(
                categoryId = "floating_audio",
                date = fullTime,
                title = "录音 $shortTime",
                content = "[[audio:$fileName]]",
            )
        )
        dao.saveAttachment(AttachmentEntity(ownerType = "note", ownerId = id, fileName = fileName))
    }

    private fun buildNotification(recording: Boolean): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (recording) "正在录音" else "悬浮录音已开启")
            .setContentText(if (recording) "悬浮球已变成圆角方块；点击可停止并保存" else "点击圆形悬浮球可直接开始录音")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "悬浮录音", NotificationManager.IMPORTANCE_LOW).apply {
                description = "保持悬浮录音球可用"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        recorderSession?.let { session ->
            recorderSession = null
            if (finishAudioRecording(this, session)) {
                runCatching {
                    runBlocking(Dispatchers.IO) { persistRecording(session.fileName) }
                }.onFailure {
                    attachmentFile(this, session.fileName).delete()
                }
            }
        }

        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        bubbleDrawable = null
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, false).apply()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

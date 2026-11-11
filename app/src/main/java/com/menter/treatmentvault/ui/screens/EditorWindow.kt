package com.menter.treatmentvault.ui.screens

import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.menter.treatmentvault.R
import java.util.UUID

/**
 * A non-floating editor window measured by Android's actual window constraints.
 * Compose 1.7's full-width Dialog measures against screenHeightDp instead, which can
 * leave its scroll viewport below the IME even when the list reports its end.
 * Keep this local to editors; ordinary alerts and image viewers retain their behavior.
 */
@Composable
internal fun EditorWindow(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val hostView = LocalView.current
    val composition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val id = rememberSaveable { UUID.randomUUID().toString() }
    val holder = remember(hostView, composition) {
        val nativeDialog = ComponentDialog(hostView.context, R.style.Theme_TreatmentVault_Editor)
        val window = requireNotNull(nativeDialog.window)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val view = EditorComposeView(nativeDialog.context, window).apply {
            setTag(androidx.compose.ui.R.id.compose_view_saveable_id_tag, "EditorWindow:$id")
            setParentCompositionContext(composition)
            setViewTreeViewModelStoreOwner(hostView.findViewTreeViewModelStoreOwner())
            body = { Box(Modifier.fillMaxSize().semantics { dialog() }) { currentContent() } }
        }
        nativeDialog.setContentView(view)
        nativeDialog.setCanceledOnTouchOutside(false)
        nativeDialog.onBackPressedDispatcher.addCallback { currentDismiss() }
        nativeDialog to view
    }
    DisposableEffect(holder) {
        val (dialog, view) = holder
        dialog.show()
        dialog.window?.let { window ->
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        onDispose {
            dialog.dismiss()
            view.disposeComposition()
        }
    }
}

private class EditorComposeView(context: Context, override val window: Window) :
    AbstractComposeView(context), DialogWindowProvider {
    var body: @Composable () -> Unit by mutableStateOf({})
    @Composable override fun Content() { body() }
}

from pathlib import Path

common = Path('app/src/main/java/com/menter/treatmentvault/ui/screens/Common.kt')
text = common.read_text(encoding='utf-8')

text = text.replace('import android.webkit.WebView\n', 'import android.graphics.BitmapFactory\n')
text = text.replace('import androidx.compose.foundation.clickable\n', 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectTapGestures\nimport androidx.compose.foundation.gestures.detectTransformGestures\n')
text = text.replace('import androidx.compose.ui.graphics.Color\n', 'import androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.ui.input.pointer.pointerInput\n')
text = text.replace('import androidx.compose.ui.viewinterop.AndroidView\n', '')

old = '''@Composable
private fun FullScreenImageViewer(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val webView = remember(file.absolutePath) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = false
            settings.allowFileAccess = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            loadUrl(Uri.fromFile(file).toString())
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xAA2A2A2A), contentColor = Color.White),
            ) { Icon(Icons.Default.Close, contentDescription = "关闭高清原图") }
        }
    }
}'''

new = '''@Composable
private fun FullScreenImageViewer(file: File, onDismiss: () -> Unit) {
    val sourceSize = remember(file.absolutePath) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val width = options.outWidth.takeIf { it > 0 } ?: 1
        val height = options.outHeight.takeIf { it > 0 } ?: 1
        width to height
    }
    var quarterTurns by remember(file.absolutePath) { mutableIntStateOf(0) }
    var zoom by remember(file.absolutePath) { mutableFloatStateOf(1f) }
    var pan by remember(file.absolutePath) { mutableStateOf(Offset.Zero) }

    fun resetView() {
        zoom = 1f
        pan = Offset.Zero
    }

    fun rotateBy(turns: Int) {
        quarterTurns = (quarterTurns + turns).mod(4)
        resetView()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(file.absolutePath, quarterTurns) {
                    detectTransformGestures { _, drag, gestureZoom, _ ->
                        val nextZoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                        zoom = nextZoom
                        pan = if (nextZoom <= 1.001f) Offset.Zero else pan + drag
                    }
                }
                .pointerInput(file.absolutePath, quarterTurns) {
                    detectTapGestures(onDoubleTap = { resetView() })
                },
            contentAlignment = Alignment.Center,
        ) {
            val sourceWidth = sourceSize.first.toFloat()
            val sourceHeight = sourceSize.second.toFloat()
            val rotated = quarterTurns % 2 != 0
            val fittedWidthSource = if (rotated) sourceHeight else sourceWidth
            val fittedHeightSource = if (rotated) sourceWidth else sourceHeight
            val fit = minOf(
                maxWidth.value / fittedWidthSource,
                maxHeight.value / fittedHeightSource,
            )
            val imageWidth = (sourceWidth * fit).dp
            val imageHeight = (sourceHeight * fit).dp

            AsyncImage(
                model = file,
                contentDescription = "高清原图",
                modifier = Modifier
                    .size(imageWidth, imageHeight)
                    .graphicsLayer {
                        rotationZ = quarterTurns * 90f
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
                contentScale = ContentScale.FillBounds,
            )

            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xAA2A2A2A), contentColor = Color.White),
            ) { Icon(Icons.Default.Close, contentDescription = "关闭高清原图") }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                color = Color(0xCC222222),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(onClick = { rotateBy(-1) }) {
                        Icon(Icons.Default.RotateLeft, contentDescription = "逆时针旋转90度")
                    }
                    TextButton(onClick = { resetView() }) { Text("适应", color = Color.White) }
                    IconButton(onClick = { rotateBy(1) }) {
                        Icon(Icons.Default.RotateRight, contentDescription = "顺时针旋转90度")
                    }
                    TextButton(onClick = {
                        quarterTurns = 0
                        resetView()
                    }) { Text("重置", color = Color.White) }
                }
            }
        }
    }
}'''

if old not in text:
    raise SystemExit('Old FullScreenImageViewer block not found; refusing partial patch')
text = text.replace(old, new)
common.write_text(text, encoding='utf-8')

build = Path('app/build.gradle.kts')
build_text = build.read_text(encoding='utf-8')
build_text = build_text.replace('versionCode = 33', 'versionCode = 34')
build_text = build_text.replace('versionName = "0.21.0"', 'versionName = "0.22.0"')
if 'versionCode = 34' not in build_text or 'versionName = "0.22.0"' not in build_text:
    raise SystemExit('Version bump failed')
build.write_text(build_text, encoding='utf-8')

print('TreatmentVault 0.22.0 image viewer changes applied')

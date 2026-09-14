from pathlib import Path

common = Path("app/src/main/java/com/menter/treatmentvault/ui/screens/Common.kt")
text = common.read_text(encoding="utf-8")
old = '''            Surface(
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
'''
new = '''            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 20.dp),
                color = Color(0xCC222222),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = { rotateBy(-1) },
                        ) {
                            Icon(Icons.Default.RotateLeft, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("左转", color = Color.White)
                        }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = { rotateBy(1) },
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("右转", color = Color.White)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = { resetView() },
                        ) { Text("适应", color = Color.White) }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = {
                                quarterTurns = 0
                                resetView()
                            },
                        ) { Text("重置", color = Color.White) }
                    }
                }
            }
'''
if old not in text:
    raise SystemExit("Expected 0.22.0 image toolbar block was not found; refusing to patch an unknown layout.")
common.write_text(text.replace(old, new, 1), encoding="utf-8")

gradle = Path("app/build.gradle.kts")
gradle_text = gradle.read_text(encoding="utf-8")
if 'versionCode = 34' not in gradle_text or 'versionName = "0.22.0"' not in gradle_text:
    raise SystemExit("Unexpected app version; refusing to overwrite a newer build.")
gradle_text = gradle_text.replace('versionCode = 34', 'versionCode = 35', 1)
gradle_text = gradle_text.replace('versionName = "0.22.0"', 'versionName = "0.22.1"', 1)
gradle.write_text(gradle_text, encoding="utf-8")

# Guardrails: make the regression obvious if the layout is later flattened back into an overflowing single row.
patched = common.read_text(encoding="utf-8")
required = [
    '.fillMaxWidth()\n                    .navigationBarsPadding()',
    'Text("左转", color = Color.White)',
    'Text("右转", color = Color.White)',
    'modifier = Modifier.weight(1f)',
    'bottom = 20.dp',
]
for marker in required:
    if marker not in patched:
        raise SystemExit(f"Missing responsive image toolbar marker: {marker}")
print("Applied TreatmentVault 0.22.1 responsive image toolbar patch")

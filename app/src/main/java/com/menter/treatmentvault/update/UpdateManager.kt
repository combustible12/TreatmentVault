package com.menter.treatmentvault.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.menter.treatmentvault.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val UPDATE_JSON = "https://raw.githubusercontent.com/combustible12/TreatmentVault-Updates/main/update.json"

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String = "",
    val notes: String = "",
)

sealed class UpdateOutcome {
    data object Latest : UpdateOutcome()
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateOutcome()
    data class Error(val message: String) : UpdateOutcome()
}

object UpdateManager {
    suspend fun checkAndDownload(context: Context): UpdateOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val info = Gson().fromJson(readText(UPDATE_JSON), UpdateInfo::class.java)
            if (info.versionCode <= BuildConfig.VERSION_CODE) return@runCatching UpdateOutcome.Latest
            require(info.apkUrl.startsWith("https://")) { "更新地址无效" }
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "TreatmentVault-${info.versionName}.apk")
            download(info.apkUrl, file)
            if (info.sha256.isNotBlank()) {
                val actual = sha256(file)
                require(actual.equals(info.sha256.trim(), ignoreCase = true)) { "更新包校验失败" }
            }
            UpdateOutcome.Ready(info, file)
        }.getOrElse { UpdateOutcome.Error(it.message ?: "检查更新失败") }
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(context: Context, apk: File): Boolean {
        if (!canInstallPackages(context)) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private fun readText(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000; c.readTimeout = 12000; c.setRequestProperty("User-Agent", "TreatmentVault/${BuildConfig.VERSION_NAME}")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun download(url: String, file: File) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000; c.setRequestProperty("User-Agent", "TreatmentVault/${BuildConfig.VERSION_NAME}")
        c.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        c.disconnect()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

package io.github.satoru2727.miteru.core.crash

import android.content.Context
import android.os.Build
import io.github.satoru2727.miteru.BuildConfig
import io.github.satoru2727.miteru.core.log.MiteruLog
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/**
 * クラッシュを端末内に保存し、ユーザー操作で共有できるようにする。
 *
 * ・自動送信・解析は行わない。
 * ・保存先はアプリ専用領域（files/crash/last-crash.txt）。
 * ・サーバー URL やホスト名は [MiteruLog.mask] で伏せる。
 */
class CrashReporter(private val context: Context) {

    private val reportFile = File(context.filesDir, "crash/last-crash.txt")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashReport(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
        MiteruLog.i("Crash", "crash reporter installed")
    }

    fun hasPendingReport(): Boolean = reportFile.exists() && reportFile.length() > 0

    fun readPendingReport(): String? =
        runCatching { reportFile.takeIf { it.exists() }?.readText() }.getOrNull()

    fun clearPendingReport() {
        runCatching { reportFile.delete() }
    }

    /** 現在のログと端末情報からバグ報告用のテキストを組み立てる。 */
    fun buildReport(kind: String): String = buildString {
        appendLine("# Miteru $kind report")
        appendLine()
        appendLine("## App")
        appendLine("- version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("- package: ${context.packageName}")
        appendLine()
        appendLine("## Device")
        appendLine("- model: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("- Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("- ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
        appendLine("- locale: ${Locale.getDefault()}")
        appendLine("- timezone: ${TimeZone.getDefault().id}")
        appendLine()
        appendLine("## Recent logs")
        appendLine("```")
        appendLine(MiteruLog.dump().ifBlank { "(no logs)" })
        appendLine("```")
    }

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        val text = buildString {
            appendLine("# Miteru crash report")
            appendLine("- time: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
            appendLine("- thread: ${thread.name}")
            appendLine()
            appendLine("## Stack trace")
            appendLine("```")
            appendLine(throwable.stackTraceToString().lineSequence().take(120).joinToString("\n"))
            appendLine("```")
            appendLine()
            append(buildReport("crash"))
        }
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(MiteruLog.mask(text))
    }
}

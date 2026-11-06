package io.github.satoru2727.miteru.core.log

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 直近のログをメモリとファイルに保持する。クラッシュレポートやバグ報告に添付する。
 *
 * ・自動送信は一切行わない。ユーザーが「共有」したときだけ端末外に出る。
 * ・サーバー URL などは書き込む前にマスクする（[mask] を参照）。
 */
object MiteruLog {

    private const val MaxLines = 500
    private const val MaxFileBytes = 256 * 1024L

    private val buffer = ArrayDeque<String>()
    private val lock = Any()
    private var logFile: File? = null
    private var formatter: DateTimeFormatter? = null

    fun install(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, "logs")
            dir.mkdirs()
            logFile = File(dir, "miteru.log")
            formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
        }
    }

    fun d(tag: String, message: String) = append("D", tag, message, null)

    fun i(tag: String, message: String) = append("I", tag, message, null)

    fun w(tag: String, message: String) = append("W", tag, message, null)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        append("E", tag, message, throwable)

    fun dump(): String = synchronized(lock) { buffer.joinToString("\n") }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            logFile?.delete()
        }
    }

    /** URL・ホスト名などをレポートから伏せる。 */
    fun mask(value: String): String =
        value.replace(Regex("""https?://[^\s/"']+"""), "https://<server>")

    private fun append(level: String, tag: String, message: String, throwable: Throwable?) {
        val time = formatter?.format(java.time.LocalDateTime.now(ZoneId.systemDefault()))
            ?: Instant.now().toString()
        val line = buildString {
            append(time)
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(": ")
            append(mask(message))
            if (throwable != null) {
                append(" | ")
                append(throwable.javaClass.simpleName)
                append(": ")
                append(mask(throwable.message ?: ""))
                throwable.stackTrace.take(8).forEach { frame ->
                    append("\n    at ")
                    append(frame.toString())
                }
            }
        }
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > MaxLines) buffer.removeFirst()
            val file = logFile
            if (file != null) {
                runCatching {
                    if (file.length() > MaxFileBytes) {
                        File(file.parentFile, "miteru.1.log").let { old ->
                            if (old.exists()) old.delete()
                            file.renameTo(old)
                        }
                    }
                    file.appendText(line + "\n")
                }
            }
        }
    }
}

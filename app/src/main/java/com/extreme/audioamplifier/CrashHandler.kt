package com.extreme.audioamplifier

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val logFile = File(context.filesDir, "last_crash.txt")

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val report = buildString {
                appendLine("=== CRASH REPORT ===")
                appendLine("Time: $time")
                appendLine("Thread: ${thread.name}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine()
                appendLine(sw.toString())
            }
            logFile.writeText(report)
        } catch (_: Exception) {}

        // Show error activity
        try {
            val intent = Intent(context, ErrorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("crash_text", logFile.readText())
            }
            context.startActivity(intent)
        } catch (_: Exception) {}

        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        fun install(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext))
        }

        fun getLastCrash(context: Context): String? {
            val f = File(context.filesDir, "last_crash.txt")
            return if (f.exists()) f.readText() else null
        }
    }
}

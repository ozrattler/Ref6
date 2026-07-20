package com.refsix.wear

import android.app.Application
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Ref6Application : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
    }

    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val logFile = File(filesDir, "crash.log")

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entry = buildString {
                    append("\n--- CRASH $timestamp thread=${thread.name} ---\n")
                    append(throwable.stackTraceToString())
                    append('\n')
                }
                logFile.appendText(entry)

                // Keep last ~50 KB
                val maxBytes = 50 * 1024L
                if (logFile.length() > maxBytes) {
                    val bytes = logFile.readBytes()
                    logFile.writeBytes(bytes.copyOfRange((bytes.size - maxBytes.toInt()), bytes.size))
                }
            } catch (_: Exception) {
                // never crash inside the crash handler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

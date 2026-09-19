package com.lfcaze.tv

import android.app.Application
import android.content.Context
import android.util.Log
import com.lfcaze.tv.ui.CrashActivity
import java.io.File
import kotlin.system.exitProcess

class LfcApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("LFCAZE_CRASH", "FATAL CRASH DETECTED ON ${thread.name}", throwable)

                // Save crash report to file
                try {
                    val crashFile = File(filesDir, "last_crash.txt")
                    val report = buildString {
                        appendLine("Timestamp: ${System.currentTimeMillis()}")
                        appendLine("Thread: ${thread.name}")
                        appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                        appendLine(throwable.stackTraceToString())
                    }
                    crashFile.writeText(report)
                } catch (e: Exception) {
                    Log.e("LFCAZE_CRASH", "Failed to write crash file", e)
                }

                // Launch CrashActivity
                CrashActivity.start(this, throwable)

                // Kill the dying process cleanly
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            } catch (e: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}

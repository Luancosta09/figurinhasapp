package com.figurinhas.app

import android.app.Application
import android.util.Log
import java.io.File

/** Grava o erro em um arquivo quando o app fecha, para a tela inicial poder mostrá-lo depois. */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(filesDir, CRASH_FILE).writeText(Log.getStackTraceString(error))
            } catch (e: Throwable) {
                // ignora: não dá para fazer mais nada aqui
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        const val CRASH_FILE = "crash.txt"
    }
}

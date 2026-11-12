package com.jubahir.arrowroute

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // remember the stack trace of any crash so the next launch can show it
        val crashPrefs = Prefs(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                crashPrefs.saveCrash("CRASH in thread " + t.name + "\n" + Log.getStackTraceString(e))
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }

        val ads = AdManager(this)
        ads.loadAll()
        setContent {
            var crash by remember { mutableStateOf(crashPrefs.lastCrash) }
            if (crash.isNotEmpty()) {
                CrashScreen(crash) {
                    crashPrefs.lastCrash = ""
                    crash = ""
                }
            } else {
                AppRoot(ads)
            }
        }
    }
}

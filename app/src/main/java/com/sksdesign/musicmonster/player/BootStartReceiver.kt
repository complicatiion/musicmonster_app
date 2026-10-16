package com.sksdesign.musicmonster.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.sksdesign.musicmonster.MainActivity
import com.sksdesign.musicmonster.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class BootStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context.applicationContext).settings.first()
                when {
                    settings.autostartOpenApp -> {
                        val activityIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(activityIntent)
                    }
                    settings.autostartBackground -> {
                        val serviceIntent = Intent(context, PlaybackNotificationService::class.java)
                        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

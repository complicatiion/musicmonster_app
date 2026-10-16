package com.sksdesign.musicmonster.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sksdesign.musicmonster.MainActivity
import com.sksdesign.musicmonster.R
import com.sksdesign.musicmonster.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BluetoothAutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context.applicationContext).settings.first()
                if (!settings.androidAutoEnabled || !settings.autostartAndroidAuto || settings.androidAutoBluetoothAddress.isBlank()) return@launch
                if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@launch
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (device?.address != settings.androidAutoBluetoothAddress) return@launch
                val openPlayer = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_ANDROID_AUTO_MODE, true)
                val started = runCatching { context.startActivity(openPlayer); true }.getOrDefault(false)
                if (!started) showLaunchNotification(context, openPlayer, settings.androidAutoBluetoothName.ifBlank { device.name ?: "Bluetooth device" })
            } finally {
                pending.finish()
            }
        }
    }

    private fun showLaunchNotification(context: Context, intent: Intent, deviceName: String) {
        val channelId = "musicmonster_android_auto_start"
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Android Auto startup", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val pendingIntent = PendingIntent.getActivity(context, 71, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.logo_mark)
            .setContentTitle("Music Monster Android Auto")
            .setContentText("Tap to open Android Auto mode for $deviceName")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(7101, notification)
    }
}

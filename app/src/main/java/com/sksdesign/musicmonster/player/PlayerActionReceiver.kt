package com.sksdesign.musicmonster.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlayerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlayerNotificationController.ACTION_PREVIOUS -> MusicMonsterRuntime.controller?.previous()
            PlayerNotificationController.ACTION_TOGGLE -> MusicMonsterRuntime.controller?.toggle()
            PlayerNotificationController.ACTION_NEXT -> MusicMonsterRuntime.controller?.next()
        }
    }
}

package com.sksdesign.musicmonster.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlayerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = MusicMonsterRuntime.controller ?: return
        when (intent.action) {
            PlayerNotificationController.ACTION_PREVIOUS -> controller.previous()
            PlayerNotificationController.ACTION_TOGGLE -> controller.toggle()
            PlayerNotificationController.ACTION_NEXT -> controller.next()
        }
        PlaybackNotificationService.sync(context, controller.currentTrack.value != null)
    }
}

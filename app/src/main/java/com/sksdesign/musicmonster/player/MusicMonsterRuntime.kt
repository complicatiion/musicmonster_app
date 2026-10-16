package com.sksdesign.musicmonster.player

import android.content.Context

object MusicMonsterRuntime {
    @Volatile
    private var sharedController: PlayerController? = null

    val controller: PlayerController?
        get() = sharedController

    fun getOrCreateController(context: Context): PlayerController {
        return sharedController ?: synchronized(this) {
            sharedController ?: PlayerController(context.applicationContext).also { sharedController = it }
        }
    }
}

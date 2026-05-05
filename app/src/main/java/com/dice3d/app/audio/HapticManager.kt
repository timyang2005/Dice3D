package com.dice3d.app.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun vibrateOnCollision(intensity: Float) {
        val amplitude = (intensity.coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(30L, amplitude)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30L)
        }
    }

    fun vibrateOnRoll() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(50L, 180)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50L)
        }
    }
}

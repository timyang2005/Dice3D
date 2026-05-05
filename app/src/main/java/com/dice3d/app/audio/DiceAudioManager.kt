package com.dice3d.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import com.dice3d.app.R

class DiceAudioManager(context: Context) {

    private val soundPool: SoundPool
    private var rollSoundId = 0
    private var hitSoundId = 0
    private var landSoundId = 0
    private var loaded = false

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
        }

        rollSoundId = soundPool.load(context, R.raw.dice_roll, 1)
        hitSoundId = soundPool.load(context, R.raw.dice_hit, 1)
        landSoundId = soundPool.load(context, R.raw.dice_land, 1)
    }

    fun playRoll() {
        if (loaded) {
            soundPool.play(rollSoundId, 0.6f, 0.6f, 1, 0, 1.0f)
        }
    }

    fun playHit(intensity: Float) {
        if (loaded) {
            val vol = intensity.coerceIn(0.1f, 1.0f)
            soundPool.play(hitSoundId, vol, vol, 1, 0, 0.8f + intensity * 0.4f)
        }
    }

    fun playLand() {
        if (loaded) {
            soundPool.play(landSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
        }
    }

    fun release() {
        soundPool.release()
    }
}

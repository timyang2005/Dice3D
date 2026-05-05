package com.dice3d.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class GyroThrowDetector(
    context: Context,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastTime = 0L

    private val shakeThreshold = 18f
    private val cooldownMs = 800L
    private var lastShakeTime = 0L

    private var isEnabled = false

    fun enable() {
        if (accelerometer != null && !isEnabled) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            isEnabled = true
        }
    }

    fun disable() {
        if (isEnabled) {
            sensorManager.unregisterListener(this)
            isEnabled = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val deltaX = x - lastX
        val deltaY = y - lastY
        val deltaZ = z - lastZ

        val acceleration = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

        val now = System.currentTimeMillis()
        if (acceleration > shakeThreshold && (now - lastShakeTime) > cooldownMs) {
            lastShakeTime = now
            onShakeDetected()
        }

        lastX = x; lastY = y; lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

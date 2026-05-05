package com.dice3d.app.engine

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin

class CameraController {

    private val defaultDistance = 8f
    private val defaultPitch = -35f
    private val defaultYaw = 30f

    var distance = defaultDistance
        private set
    var pitch = defaultPitch
        private set
    var yaw = defaultYaw
        private set

    var targetX = 0f
        private set
    var targetY = 1.5f
        private set
    var targetZ = 0f
        private set

    private val minDistance = 3f
    private val maxDistance = 25f
    private val minPitch = -85f
    private val maxPitch = 85f
    private val maxPan = 8f

    private var isAnimating = false
    private var animStartDistance = 0f
    private var animStartPitch = 0f
    private var animStartYaw = 0f
    private var animStartTargetX = 0f
    private var animStartTargetY = 0f
    private var animStartTargetZ = 0f
    private var animProgress = 0f
    private val animDuration = 0.3f

    fun getViewMatrix(viewMatrix: FloatArray) {
        if (isAnimating) {
            animProgress += 0.016f / animDuration
            if (animProgress >= 1f) {
                animProgress = 1f
                isAnimating = false
            }
            val t = smoothstep(animProgress)
            distance = lerp(animStartDistance, defaultDistance, t)
            pitch = lerp(animStartPitch, defaultPitch, t)
            yaw = lerp(animStartYaw, defaultYaw, t)
            targetX = lerp(animStartTargetX, 0f, t)
            targetY = lerp(animStartTargetY, 1.5f, t)
            targetZ = lerp(animStartTargetZ, 0f, t)
        }

        val eyeX = targetX + distance * cos(Math.toRadians(pitch.toDouble())) * sin(Math.toRadians(yaw.toDouble()))
        val eyeY = targetY + distance * sin(Math.toRadians(-pitch.toDouble()))
        val eyeZ = targetZ + distance * cos(Math.toRadians(pitch.toDouble())) * cos(Math.toRadians(yaw.toDouble()))

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX.toFloat(), eyeY.toFloat(), eyeZ.toFloat(),
            targetX, targetY, targetZ,
            0f, 1f, 0f
        )
    }

    fun rotate(deltaYaw: Float, deltaPitch: Float) {
        yaw += deltaYaw
        pitch = (pitch + deltaPitch).coerceIn(minPitch, maxPitch)
    }

    fun zoom(delta: Float) {
        distance = (distance * (1f + delta * 0.001f)).coerceIn(minDistance, maxDistance)
    }

    fun pan(dx: Float, dy: Float, dz: Float) {
        val yawRad = Math.toRadians(yaw.toDouble())
        val sinY = sin(yawRad).toFloat()
        val cosY = cos(yawRad).toFloat()

        targetX += (-sinY * dz + cosY * dx) * distance * 0.002f
        targetY += dy * distance * 0.002f
        targetZ += (cosY * dz + sinY * dx) * distance * 0.002f

        targetX = targetX.coerceIn(-maxPan, maxPan)
        targetY = targetY.coerceIn(0f, maxPan)
        targetZ = targetZ.coerceIn(-maxPan, maxPan)
    }

    fun resetView() {
        animStartDistance = distance
        animStartPitch = pitch
        animStartYaw = yaw
        animStartTargetX = targetX
        animStartTargetY = targetY
        animStartTargetZ = targetZ
        animProgress = 0f
        isAnimating = true
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}

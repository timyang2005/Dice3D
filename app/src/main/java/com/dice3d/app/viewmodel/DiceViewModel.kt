package com.dice3d.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dice3d.app.audio.DiceAudioManager
import com.dice3d.app.audio.HapticManager
import com.dice3d.app.data.AppSettings
import com.dice3d.app.data.DiceType
import com.dice3d.app.data.HistoryRepository
import com.dice3d.app.data.HistoryDatabase
import com.dice3d.app.data.RollResult
import com.dice3d.app.data.SettingsRepository
import com.dice3d.app.engine.CameraController
import com.dice3d.app.engine.DiceMeshGenerator
import com.dice3d.app.engine.GLRenderer
import com.dice3d.app.physics.DicePhysicsBody
import com.dice3d.app.physics.LibbulletjmeAdapter
import com.dice3d.app.physics.PhysicsAdapter
import com.dice3d.app.sensor.GyroThrowDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class DiceViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val historyRepo = HistoryRepository(HistoryDatabase.getDatabase(application).historyDao())
    private val audioManager = DiceAudioManager(application)
    private val hapticManager = HapticManager(application)
    private val gyroDetector = GyroThrowDetector(application) { rollDice() }

    val cameraController = CameraController()
    val physicsAdapter: PhysicsAdapter = LibbulletjmeAdapter()
    val glRenderer = GLRenderer(cameraController)

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _diceResults = MutableStateFlow<List<Int>>(emptyList())
    val diceResults: StateFlow<List<Int>> = _diceResults.asStateFlow()

    private val _isRolling = MutableStateFlow(false)
    val isRolling: StateFlow<Boolean> = _isRolling.asStateFlow()

    private val _totalResult = MutableStateFlow(0)
    val totalResult: StateFlow<Int> = _totalResult.asStateFlow()

    private var currentDiceType = DiceType.D6
    private var currentDiceCount = 1
    private var physicsLoopRunning = false
    private var lastPhysicsTime = 0L
    private var diceBodyMap = mutableMapOf<Int, DicePhysicsBody>()

    init {
        physicsAdapter.initialize()
        physicsAdapter.setOnCollisionListener { intensity ->
            val s = settings.value
            if (s.soundEnabled) audioManager.playHit(intensity / 10f)
            if (s.hapticEnabled) hapticManager.vibrateOnCollision(intensity / 10f)
        }

        viewModelScope.launch {
            settings.collect { s ->
                currentDiceType = s.diceType
                currentDiceCount = s.diceCount
                glRenderer.setDiceColor(s.diceColor)
                glRenderer.setDarkScene(
                    when (s.darkMode) {
                        com.dice3d.app.data.DarkModePreference.DARK -> true
                        com.dice3d.app.data.DarkModePreference.LIGHT -> false
                        com.dice3d.app.data.DarkModePreference.FOLLOW_SYSTEM -> {
                            val config = application.resources.configuration
                            (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                                android.content.res.Configuration.UI_MODE_NIGHT_YES
                        }
                    }
                )
                if (s.gyroEnabled) gyroDetector.enable() else gyroDetector.disable()
                rebuildDice()
            }
        }
    }

    fun rollDice() {
        if (currentDiceCount <= 0) return

        val s = settings.value
        if (s.soundEnabled) audioManager.playRoll()
        if (s.hapticEnabled) hapticManager.vibrateOnRoll()

        if (_isRolling.value) {
            applyBounce()
            return
        }

        _isRolling.value = true
        _diceResults.value = emptyList()
        _totalResult.value = 0

        throwDice()
        startPhysicsLoop()
    }

    private fun throwDice() {
        diceBodyMap.values.forEach { body ->
            val spread = currentDiceCount * 0.4f
            body.position = floatArrayOf(
                (Random.nextFloat() - 0.5f) * spread,
                3.5f + Random.nextFloat() * 2f,
                (Random.nextFloat() - 0.5f) * spread
            )

            body.linearVelocity = floatArrayOf(
                (Random.nextFloat() - 0.5f) * 6f,
                -2f + Random.nextFloat() * 3f,
                (Random.nextFloat() - 0.5f) * 6f
            )

            body.angularVelocity = floatArrayOf(
                (Random.nextFloat() - 0.5f) * 15f,
                (Random.nextFloat() - 0.5f) * 15f,
                (Random.nextFloat() - 0.5f) * 15f
            )

            val angle = Random.nextFloat() * kotlin.math.PI.toFloat() * 2f
            val axis = floatArrayOf(
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat()
            )
            val len = sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2])
            val sinHalf = sin(angle * 0.5f)
            body.orientation = floatArrayOf(
                (axis[0] / len) * sinHalf,
                (axis[1] / len) * sinHalf,
                (axis[2] / len) * sinHalf,
                cos(angle * 0.5f)
            )

            body.wakeUp()
        }
    }

    private fun applyBounce() {
        diceBodyMap.values.forEach { body ->
            if (!body.isSleeping) {
                val vel = body.linearVelocity
                vel[1] += 5f + Random.nextFloat() * 3f
                vel[0] += (Random.nextFloat() - 0.5f) * 3f
                vel[2] += (Random.nextFloat() - 0.5f) * 3f
                body.linearVelocity = vel

                val angVel = body.angularVelocity
                angVel[0] += (Random.nextFloat() - 0.5f) * 8f
                angVel[1] += (Random.nextFloat() - 0.5f) * 8f
                angVel[2] += (Random.nextFloat() - 0.5f) * 8f
                body.angularVelocity = angVel

                body.wakeUp()
            }
        }
    }

    private fun rebuildDice() {
        physicsAdapter.clear()
        diceBodyMap.clear()
        glRenderer.clearDiceMeshes()

        for (i in 0 until currentDiceCount) {
            val mesh = DiceMeshGenerator.generateMesh(currentDiceType)
            val boundingRadius = when (currentDiceType) {
                DiceType.D4 -> 0.6f
                DiceType.D6 -> 0.5f
                DiceType.D8 -> 0.55f
                DiceType.D10 -> 0.55f
                DiceType.D12 -> 0.6f
                DiceType.D20 -> 0.6f
                DiceType.D100 -> 0.6f
            }

            val body = physicsAdapter.createDiceBody(i, mesh, boundingRadius)
            body.position = floatArrayOf(
                (i - currentDiceCount / 2f) * 1.2f,
                0.5f,
                0f
            )

            diceBodyMap[i] = body
            glRenderer.addDiceMesh(i, mesh)
        }
    }

    private fun startPhysicsLoop() {
        if (physicsLoopRunning) return
        physicsLoopRunning = true
        lastPhysicsTime = System.nanoTime()

        Thread {
            while (physicsLoopRunning) {
                val now = System.nanoTime()
                var dt = (now - lastPhysicsTime) / 1_000_000_000f
                lastPhysicsTime = now

                val speed = settings.value.simSpeed
                dt = (dt * speed).coerceIn(0.001f, 0.033f)

                val fixedStep = 1f / 90f
                var accumulator = dt

                while (accumulator >= fixedStep) {
                    physicsAdapter.step(fixedStep)
                    accumulator -= fixedStep
                }

                glRenderer.updatePhysicsBodies(physicsAdapter.getAllBodies())

                if (allDiceStopped() && _isRolling.value) {
                    val results = physicsAdapter.getAllBodies().map { body ->
                        body.getUpFace()
                    }
                    _diceResults.value = results
                    _totalResult.value = results.sum()
                    _isRolling.value = false
                    physicsLoopRunning = false

                    val s = settings.value
                    if (s.soundEnabled) audioManager.playLand()

                    viewModelScope.launch {
                        historyRepo.insertResult(
                            RollResult(
                                timestamp = System.currentTimeMillis(),
                                diceType = currentDiceType.name,
                                diceCount = currentDiceCount,
                                individualResults = results.joinToString(","),
                                total = results.sum()
                            )
                        )
                    }
                    return@Thread
                }

                try { Thread.sleep(16) } catch (_: InterruptedException) { break }
            }
        }.start()
    }

    private fun allDiceStopped(): Boolean {
        return physicsAdapter.getAllBodies().all { it.isSleeping }
    }

    fun resetCamera() {
        cameraController.resetView()
    }

    fun getHistoryRepo(): HistoryRepository = historyRepo

    override fun onCleared() {
        super.onCleared()
        physicsLoopRunning = false
        audioManager.release()
        gyroDetector.disable()
    }
}

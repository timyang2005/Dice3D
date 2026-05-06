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
import com.dice3d.app.engine.DiceBody
import com.dice3d.app.engine.DiceMeshGenerator
import com.dice3d.app.engine.GLRenderer
import com.dice3d.app.engine.PhysicsWorld
import com.dice3d.app.sensor.GyroThrowDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiceViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val historyRepo = HistoryRepository(HistoryDatabase.getDatabase(application).historyDao())
    private val audioManager = DiceAudioManager(application)
    private val hapticManager = HapticManager(application)
    private val gyroDetector = GyroThrowDetector(application) { rollDice() }

    val cameraController = CameraController()
    val physicsWorld = PhysicsWorld()
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

    init {
        physicsWorld.onCollision = { intensity ->
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
            physicsWorld.applyBounce()
            return
        }

        _isRolling.value = true
        _diceResults.value = emptyList()
        _totalResult.value = 0

        physicsWorld.throwDice()
        startPhysicsLoop()
    }

    private fun rebuildDice() {
        physicsWorld.clearDice()
        glRenderer.clearDiceMeshes()

        for (i in 0 until currentDiceCount) {
            val mesh = DiceMeshGenerator.generateMesh(currentDiceType)
            val body = DiceBody(i, mesh)
            body.pos.x = (i - currentDiceCount / 2f) * 1.2f
            body.pos.y = 0.5f
            body.pos.z = 0f

            physicsWorld.addDice(body)
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

                val steps = 3
                val subDt = dt / steps
                for (i in 0 until steps) {
                    physicsWorld.step(subDt)
                }

                glRenderer.updatePhysicsBodies(physicsWorld.getDice())

                if (physicsWorld.allDiceStopped() && _isRolling.value) {
                    val results = physicsWorld.getDice().map { body ->
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

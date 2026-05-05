package com.dice3d.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dice3d.app.data.DiceType
import com.dice3d.app.data.HistoryDatabase
import com.dice3d.app.data.HistoryRepository
import com.dice3d.app.data.RollResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = HistoryRepository(HistoryDatabase.getDatabase(application).historyDao())

    private val _history = MutableStateFlow<List<RollResult>>(emptyList())
    val history = _history.asStateFlow()

    private val _stats = MutableStateFlow<Map<DiceType, Map<Int, Int>>>(emptyMap())
    val stats = _stats.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _history.value = repo.getAllResults()
            loadStats()
        }
    }

    private suspend fun loadStats() {
        val statsMap = mutableMapOf<DiceType, Map<Int, Int>>()
        for (type in DiceType.entries) {
            statsMap[type] = repo.getStatsForType(type)
        }
        _stats.value = statsMap
    }

    fun deleteResult(result: RollResult) {
        viewModelScope.launch {
            repo.deleteResult(result)
            loadHistory()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.clearAll()
            loadHistory()
        }
    }
}

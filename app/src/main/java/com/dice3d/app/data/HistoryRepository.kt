package com.dice3d.app.data

class HistoryRepository(private val dao: HistoryDao) {

    suspend fun getAllResults(): List<RollResult> = dao.getAll()

    suspend fun getResultsByType(diceType: DiceType): List<RollResult> =
        dao.getByType(diceType.name)

    suspend fun insertResult(result: RollResult): Long = dao.insert(result)

    suspend fun deleteResult(result: RollResult) = dao.delete(result)

    suspend fun clearAll() = dao.deleteAll()

    suspend fun getStatsForType(diceType: DiceType): Map<Int, Int> {
        val results = dao.getByType(diceType.name)
        val stats = mutableMapOf<Int, Int>()
        results.forEach { roll ->
            roll.individualResults.split(",").mapNotNull { it.trim().toIntOrNull() }
                .forEach { value ->
                    stats[value] = (stats[value] ?: 0) + 1
                }
        }
        return stats
    }
}

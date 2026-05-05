package com.dice3d.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "roll_history")
data class RollResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val diceType: String,
    val diceCount: Int,
    val individualResults: String,
    val total: Int
)

class DiceTypeConverter {
    @TypeConverter
    fun fromDiceType(type: DiceType): String = type.name

    @TypeConverter
    fun toDiceType(name: String): DiceType = DiceType.valueOf(name)
}

class IntListConverter {
    @TypeConverter
    fun fromIntList(list: List<Int>): String = list.joinToString(",")

    @TypeConverter
    fun toIntList(str: String): List<Int> =
        if (str.isBlank()) emptyList() else str.split(",").map { it.trim().toInt() }
}

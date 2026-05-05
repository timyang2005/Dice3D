package com.dice3d.app.data

enum class DiceType(val faces: Int, val displayName: String) {
    D4(4, "D4"),
    D6(6, "D6"),
    D8(8, "D8"),
    D10(10, "D10"),
    D12(12, "D12"),
    D20(20, "D20"),
    D100(100, "D100");

    fun maxValue(): Int = if (this == D100) 100 else faces

    fun isPercentile(): Boolean = this == D100

    companion object {
        val presets: List<DiceType> = entries
    }
}

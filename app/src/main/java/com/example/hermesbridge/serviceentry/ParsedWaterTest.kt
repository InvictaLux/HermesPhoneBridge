package com.example.hermesbridge.serviceentry

data class ParsedWaterTest(
    val freeChlorine: Float? = null,
    val ph: Float? = null,
    val totalAlkalinity: Float? = null,
    val cya: Float? = null,
    val calciumHardness: Float? = null,
    val salt: Float? = null
) {
    fun isReadyForSubmission(): Boolean {
        return freeChlorine != null && ph != null && totalAlkalinity != null
    }

    fun toNormalizedMap(): Map<String, Float?> {
        return mapOf(
            ReadingField.FreeChlorine.jsonKey to freeChlorine,
            ReadingField.Ph.jsonKey to ph,
            ReadingField.TotalAlkalinity.jsonKey to totalAlkalinity,
            ReadingField.Cya.jsonKey to cya,
            ReadingField.CalciumHardness.jsonKey to calciumHardness,
            ReadingField.Salt.jsonKey to salt
        )
    }
}

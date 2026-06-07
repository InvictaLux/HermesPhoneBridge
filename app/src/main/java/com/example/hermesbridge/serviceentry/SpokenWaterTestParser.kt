package com.example.hermesbridge.serviceentry

import android.util.Log

class SpokenWaterTestParser {

    private val chlorineAliases = listOf("chlorine", "free chlorine", "sanitizer")
    private val phAliases = listOf("ph", "acidity")
    private val alkalinityAliases = listOf("alkalinity", "total alkalinity")
    private val cyaAliases = listOf("cya", "stabilizer", "cyanuric acid")
    private val hardnessAliases = listOf("hardness", "calcium", "calcium hardness", "total hardness", "th")
    private val saltAliases = listOf("salt", "salinity")

    fun parse(input: String, current: ParsedWaterTest = ParsedWaterTest()): ParsedWaterTest {
        val normalized = input.lowercase().replace(",", " ")
        var result = current

        // Handle corrections: "change pH to 7.6"
        if (normalized.contains("change") || normalized.contains("was")) {
            val parts = normalized.split(" to ", " was ")
            if (parts.size == 2) {
                val fieldPart = parts[0]
                val valuePart = parts[1]
                val value = parseNumber(valuePart)
                if (value != null) {
                    when {
                        containsAlias(fieldPart, chlorineAliases) -> result = result.copy(freeChlorine = value)
                        containsAlias(fieldPart, phAliases) -> result = result.copy(ph = value)
                        containsAlias(fieldPart, alkalinityAliases) -> result = result.copy(totalAlkalinity = value)
                        containsAlias(fieldPart, cyaAliases) -> result = result.copy(cya = value)
                        containsAlias(fieldPart, hardnessAliases) -> result = result.copy(calciumHardness = value)
                        containsAlias(fieldPart, saltAliases) -> result = result.copy(salt = value)
                    }
                    return result
                }
            }
        }

        // Standard parsing for "Label Value Label Value..."
        val words = normalized.split(" ").filter { it.isNotEmpty() }
        var i = 0
        while (i < words.size) {
            val word = words[i]
            
            // Check if word is an alias
            val fieldMatch = when {
                containsAlias(word, chlorineAliases) -> ReadingField.FreeChlorine
                containsAlias(word, phAliases) -> ReadingField.Ph
                containsAlias(word, alkalinityAliases) -> ReadingField.TotalAlkalinity
                containsAlias(word, cyaAliases) -> ReadingField.Cya
                containsAlias(word, hardnessAliases) -> ReadingField.CalciumHardness
                containsAlias(word, saltAliases) -> ReadingField.Salt
                else -> null
            }

            if (fieldMatch != null) {
                // Look for number in next words
                var foundValue: Float? = null
                var j = i + 1
                
                // Peak ahead up to 3 words to find a number (e.g. "seven point four")
                if (j < words.size) {
                    // Try single word
                    foundValue = parseNumber(words[j])
                    if (foundValue != null) {
                        i = j
                    } else if (j + 2 < words.size && words[j+1] == "point") {
                        // Try "seven point four"
                        foundValue = parseNumber("${words[j]} point ${words[j+2]}")
                        if (foundValue != null) i = j + 2
                    }
                }

                if (foundValue != null) {
                    result = when (fieldMatch) {
                        ReadingField.FreeChlorine -> result.copy(freeChlorine = foundValue)
                        ReadingField.Ph -> result.copy(ph = foundValue)
                        ReadingField.TotalAlkalinity, ReadingField.Alkalinity -> result.copy(totalAlkalinity = foundValue)
                        ReadingField.Cya -> result.copy(cya = foundValue)
                        ReadingField.CalciumHardness -> result.copy(calciumHardness = foundValue)
                        ReadingField.Salt -> result.copy(salt = foundValue)
                    }
                }
            }
            i++
        }

        return result
    }

    private fun containsAlias(input: String, aliases: List<String>): Boolean {
        // Exact match or partial if it's a multi-word alias
        return aliases.any { alias -> 
            if (alias.contains(" ")) input.contains(alias) else input == alias
        }
    }

    private fun parseNumber(input: String): Float? {
        if (input.contains("point")) {
            val parts = input.split("point")
            if (parts.size == 2) {
                val whole = parseWordToNumber(parts[0])
                val fraction = parseWordToNumber(parts[1])
                if (whole != null && fraction != null) {
                    return "$whole.$fraction".toFloatOrNull()
                }
            }
        }
        input.toFloatOrNull()?.let { return it }
        return parseWordToNumber(input)?.toFloat()
    }

    private fun parseWordToNumber(word: String): Int? {
        val mapping = mapOf(
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
            "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
        )
        return mapping[word.trim()]
    }
}

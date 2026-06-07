package com.example.hermesbridge.serviceentry

enum class ReadingField(val jsonKey: String, val unit: String) {
    FreeChlorine("free_chlorine_ppm", "ppm"),
    Ph("ph", ""),
    TotalAlkalinity("total_alkalinity_ppm", "ppm"), // Frontend sends this
    Alkalinity("alkalinity_ppm", "ppm"), // Backend returns this
    Cya("cya_ppm", "ppm"),
    CalciumHardness("calcium_hardness_ppm", "ppm"),
    Salt("salt_ppm", "ppm")
}

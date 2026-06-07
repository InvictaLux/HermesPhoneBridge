package com.example.hermesbridge.serviceentry

data class KnownPool(
    val id: String,
    val customerName: String,
    val latitude: Double,
    val longitude: Double,
    val volumeGallons: Int
)

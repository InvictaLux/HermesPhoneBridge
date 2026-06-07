package com.example.hermesbridge.serviceentry

class PoolRegistry {
    private val pools = listOf(
        KnownPool(
            id = "pool_mccullough",
            customerName = "McCullough",
            latitude = 34.0522, // Example coordinates
            longitude = -118.2437,
            volumeGallons = 15000
        ),
        KnownPool(
            id = "pool_smith",
            customerName = "Smith",
            latitude = 34.0525,
            longitude = -118.2435,
            volumeGallons = 20000
        )
    )

    fun findNearbyPools(lat: Double, lon: Double, radiusMeters: Double): List<KnownPool> {
        return pools.filter { pool ->
            calculateDistance(lat, lon, pool.latitude, pool.longitude) <= radiusMeters
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}

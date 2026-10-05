package dev.tupolev.ata.monitor

import kotlin.math.*

enum class MonitorStatus { SAFE, SUSPICIOUS, ALARM, STALE, UNKNOWN }

data class LocationSample(
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
)

object AlarmEngine {

    fun evaluate(
        config: TrackerConfig,
        sample: LocationSample,
        now: Long = System.currentTimeMillis(),
    ): TrackerConfig {
        if (!config.armed || config.anchorLat == null || config.anchorLon == null) {
            return config.copy(
                lastTimestamp = maxOf(config.lastTimestamp, sample.timestamp),
                lastLat = sample.lat,
                lastLon = sample.lon,
            )
        }

        if (sample.timestamp <= config.lastTimestamp) return config

        val distance = haversine(
            config.anchorLat,
            config.anchorLon,
            sample.lat,
            sample.lon,
        )

        if (now - sample.timestamp > config.staleMinutes * 60_000L) {
            return config.copy(
                status = MonitorStatus.STALE,
                lastTimestamp = sample.timestamp,
                lastLat = sample.lat,
                lastLon = sample.lon,
                lastDistanceM = distance,
            )
        }

        if (distance >= config.immediateAlarmM) {
            return config.copy(
                status = MonitorStatus.ALARM,
                suspiciousCount = config.confirmations,
                lastTimestamp = sample.timestamp,
                lastLat = sample.lat,
                lastLon = sample.lon,
                lastDistanceM = distance,
            )
        }

        if (distance > config.safeRadiusM) {
            val count = config.suspiciousCount + 1
            return config.copy(
                status = if (count >= config.confirmations) MonitorStatus.ALARM else MonitorStatus.SUSPICIOUS,
                suspiciousCount = count,
                lastTimestamp = sample.timestamp,
                lastLat = sample.lat,
                lastLon = sample.lon,
                lastDistanceM = distance,
            )
        }

        return config.copy(
            status = MonitorStatus.SAFE,
            suspiciousCount = 0,
            lastTimestamp = sample.timestamp,
            lastLat = sample.lat,
            lastLon = sample.lon,
            lastDistanceM = distance,
        )
    }

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)

        val a = sin(dPhi / 2).pow(2) +
            cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)

        return 2 * earthRadius * atan2(sqrt(a), sqrt(1 - a))
    }
}

package com.ableLabs.zero100.gps

/**
 * NMEA 문장 파싱 — RMC, VTG, GGA에서 속도/위치/위성정보 추출
 */
data class GpsData(
    val speedKmh: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val satellites: Int = 0,
    val fixQuality: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val hdop: Double = 99.9,
    val isValid: Boolean = false
)

object NmeaParser {

    private var lastSatellites: Int = 0
    private var lastFixQuality: Int = 0
    private var lastHdop: Double = 99.9

    /**
     * 한 줄의 NMEA 문장을 파싱하여 GpsData 반환.
     * 지원: $GNRMC, $GPRMC, $GNVTG, $GPVTG, $GNGGA, $GPGGA
     */
    fun parse(sentence: String): GpsData? {
        if (!validateChecksum(sentence)) return null

        val parts = sentence.substringBefore('*').split(',')
        if (parts.size < 3) return null

        val type = parts[0]
        return when {
            type.endsWith("RMC") -> parseRmc(parts)
            type.endsWith("VTG") -> parseVtg(parts)
            type.endsWith("GGA") -> parseGga(parts)
            else -> null
        }
    }

    /**
     * $xxRMC — 추천 최소 특정 위치/속도
     * 필드: time, status, lat, N/S, lon, E/W, speedKnots, course, date, ...
     */
    private fun parseRmc(parts: List<String>): GpsData? {
        if (parts.size < 10) return null
        val status = parts[2]
        val isValid = status == "A"

        val lat = parseLatitude(parts[3], parts[4])
        val lon = parseLongitude(parts[5], parts[6])
        val speedKnots = parts[7].toDoubleOrNull() ?: 0.0
        val speedKmh = speedKnots * 1.852

        return GpsData(
            speedKmh = speedKmh,
            latitude = lat,
            longitude = lon,
            satellites = lastSatellites,
            fixQuality = lastFixQuality,
            hdop = lastHdop,
            isValid = isValid
        )
    }

    /**
     * $xxVTG — 대지 속도 (Ground Speed)
     * 필드: courseTrue, T, courseMag, M, speedKnots, N, speedKmh, K, mode
     */
    private fun parseVtg(parts: List<String>): GpsData? {
        if (parts.size < 8) return null
        val speedKmh = parts[7].toDoubleOrNull() ?: return null
        val mode = if (parts.size > 9) parts[9] else ""
        val isValid = mode != "N" // N = No fix

        return GpsData(
            speedKmh = speedKmh,
            satellites = lastSatellites,
            fixQuality = lastFixQuality,
            hdop = lastHdop,
            isValid = isValid
        )
    }

    /**
     * $xxGGA — 위성/Fix 품질 정보 업데이트용
     */
    private fun parseGga(parts: List<String>): GpsData? {
        if (parts.size < 10) return null
        lastFixQuality = parts[6].toIntOrNull() ?: 0
        lastSatellites = parts[7].toIntOrNull() ?: 0
        lastHdop = parts[8].toDoubleOrNull() ?: 99.9

        val lat = parseLatitude(parts[2], parts[3])
        val lon = parseLongitude(parts[4], parts[5])

        return GpsData(
            latitude = lat,
            longitude = lon,
            satellites = lastSatellites,
            fixQuality = lastFixQuality,
            hdop = lastHdop,
            isValid = lastFixQuality > 0
        )
    }

    // NMEA 위도: ddmm.mmmm → decimal degrees
    private fun parseLatitude(raw: String, dir: String): Double {
        if (raw.length < 4) return 0.0
        val deg = raw.substring(0, 2).toDoubleOrNull() ?: return 0.0
        val min = raw.substring(2).toDoubleOrNull() ?: return 0.0
        val result = deg + min / 60.0
        return if (dir == "S") -result else result
    }

    // NMEA 경도: dddmm.mmmm → decimal degrees
    private fun parseLongitude(raw: String, dir: String): Double {
        if (raw.length < 5) return 0.0
        val deg = raw.substring(0, 3).toDoubleOrNull() ?: return 0.0
        val min = raw.substring(3).toDoubleOrNull() ?: return 0.0
        val result = deg + min / 60.0
        return if (dir == "W") -result else result
    }

    // NMEA checksum 검증: XOR of all chars between $ and *
    private fun validateChecksum(sentence: String): Boolean {
        val starIdx = sentence.indexOf('*')
        if (starIdx < 0 || starIdx + 3 > sentence.length) return false

        val data = sentence.substring(1, starIdx) // $ 다음부터 * 전까지
        val expected = sentence.substring(starIdx + 1, starIdx + 3)

        var checksum = 0
        for (c in data) {
            checksum = checksum xor c.code
        }

        return try {
            checksum == expected.toInt(16)
        } catch (e: NumberFormatException) {
            false
        }
    }
}

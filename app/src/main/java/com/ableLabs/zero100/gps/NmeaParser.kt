package com.ableLabs.zero100.gps

/**
 * NMEA 문장 파싱 — RMC, VTG에서 속도, GGA에서 위성정보 추출
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
    private var lastLatitude: Double = 0.0
    private var lastLongitude: Double = 0.0
    private var lastSpeedKmh: Double = 0.0

    /**
     * 한 줄의 NMEA 문장을 파싱하여 GpsData 반환.
     * RMC/VTG: 속도 데이터 → GpsData 반환 (엔진에 전달됨)
     * GGA: 위성/HDOP 업데이트만 → null 반환 (속도 0으로 덮어쓰기 방지)
     */
    fun parse(sentence: String): GpsData? {
        if (!validateChecksum(sentence)) return null

        val parts = sentence.substringBefore('*').split(',')
        if (parts.size < 3) return null

        val type = parts[0]
        return when {
            type.endsWith("RMC") -> parseRmc(parts)
            type.endsWith("VTG") -> parseVtg(parts)
            type.endsWith("GGA") -> { parseGga(parts); null } // 위성 정보만 업데이트, emit 안 함
            type.endsWith("GSV") -> { parseGsv(parts); null }  // 위성 상세, emit 안 함
            else -> null
        }
    }

    /**
     * $xxRMC — 속도 + 위치 (주요 속도 소스)
     */
    private fun parseRmc(parts: List<String>): GpsData? {
        if (parts.size < 10) return null
        val status = parts[2]
        val isValid = status == "A"

        val lat = parseLatitude(parts[3], parts[4])
        val lon = parseLongitude(parts[5], parts[6])
        val speedKnots = parts[7].toDoubleOrNull() ?: 0.0
        val speedKmh = speedKnots * 1.852

        if (isValid) {
            lastSpeedKmh = speedKmh
            if (lat != 0.0) lastLatitude = lat
            if (lon != 0.0) lastLongitude = lon
        }

        return GpsData(
            speedKmh = speedKmh,
            latitude = if (lat != 0.0) lat else lastLatitude,
            longitude = if (lon != 0.0) lon else lastLongitude,
            satellites = lastSatellites,
            fixQuality = lastFixQuality,
            hdop = lastHdop,
            isValid = isValid
        )
    }

    /**
     * $xxVTG — 대지 속도 (보조 속도 소스, RMC보다 정확할 수 있음)
     */
    private fun parseVtg(parts: List<String>): GpsData? {
        if (parts.size < 8) return null
        val speedKmh = parts[7].toDoubleOrNull() ?: return null
        val mode = if (parts.size > 9) parts[9] else ""
        val isValid = mode != "N"

        if (isValid) {
            lastSpeedKmh = speedKmh
        }

        return GpsData(
            speedKmh = speedKmh,
            latitude = lastLatitude,
            longitude = lastLongitude,
            satellites = lastSatellites,
            fixQuality = lastFixQuality,
            hdop = lastHdop,
            isValid = isValid
        )
    }

    /**
     * $xxGGA — 위성/Fix/HDOP 정보만 업데이트 (속도 emit 안 함)
     */
    private fun parseGga(parts: List<String>) {
        if (parts.size < 10) return
        lastFixQuality = parts[6].toIntOrNull() ?: 0
        lastSatellites = parts[7].toIntOrNull() ?: 0
        lastHdop = parts[8].toDoubleOrNull() ?: 99.9

        val lat = parseLatitude(parts[2], parts[3])
        val lon = parseLongitude(parts[4], parts[5])
        if (lat != 0.0) lastLatitude = lat
        if (lon != 0.0) lastLongitude = lon
    }

    /**
     * $xxGSV — 위성 상세 정보 (파싱만, emit 안 함)
     */
    private fun parseGsv(parts: List<String>) {
        // GSV에서 총 위성 수 업데이트 가능하지만 GGA가 더 정확
    }

    private fun parseLatitude(raw: String, dir: String): Double {
        if (raw.length < 4) return 0.0
        val deg = raw.substring(0, 2).toDoubleOrNull() ?: return 0.0
        val min = raw.substring(2).toDoubleOrNull() ?: return 0.0
        val result = deg + min / 60.0
        return if (dir == "S") -result else result
    }

    private fun parseLongitude(raw: String, dir: String): Double {
        if (raw.length < 5) return 0.0
        val deg = raw.substring(0, 3).toDoubleOrNull() ?: return 0.0
        val min = raw.substring(3).toDoubleOrNull() ?: return 0.0
        val result = deg + min / 60.0
        return if (dir == "W") -result else result
    }

    private fun validateChecksum(sentence: String): Boolean {
        val starIdx = sentence.indexOf('*')
        if (starIdx < 0 || starIdx + 3 > sentence.length) return false

        val data = sentence.substring(1, starIdx)
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

package com.ableLabs.zero100.gps

/**
 * u-blox M9 UBX 프로토콜 설정 명령 생성
 * - 25Hz 측정 레이트 설정
 * - 필요한 NMEA 메시지만 활성화
 * - 자동차 모드(Dynamic Model) 설정
 */
object UbxConfig {

    // UBX 헤더
    private const val SYNC1: Byte = 0xB5.toByte()
    private const val SYNC2: Byte = 0x62

    /**
     * 25Hz 설정 — UBX-CFG-RATE
     * measRate = 40ms, navRate = 1, timeRef = UTC
     */
    fun setRate25Hz(): ByteArray = buildUbxMessage(
        classId = 0x06, msgId = 0x08,
        payload = byteArrayOf(
            0x28, 0x00,  // measRate = 40ms (little-endian)
            0x01, 0x00,  // navRate = 1
            0x01, 0x00   // timeRef = UTC
        )
    )

    /**
     * 10Hz 설정 — 안정성 우선 시
     */
    fun setRate10Hz(): ByteArray = buildUbxMessage(
        classId = 0x06, msgId = 0x08,
        payload = byteArrayOf(
            0x64, 0x00,  // measRate = 100ms
            0x01, 0x00,
            0x01, 0x00
        )
    )

    /**
     * Automotive Dynamic Model 설정 — UBX-CFG-NAV5
     * 자동차 모드로 설정하면 속도 측정 정확도 향상
     */
    fun setAutomotiveMode(): ByteArray {
        val payload = ByteArray(36)
        payload[0] = 0x01  // mask: apply dynModel
        payload[1] = 0x00
        payload[2] = 0x04  // dynModel = 4 (Automotive)
        return buildUbxMessage(classId = 0x06, msgId = 0x24, payload = payload)
    }

    /**
     * NMEA RMC 메시지 활성화 — UBX-CFG-MSG
     * msgClass=0xF0 (NMEA), msgId=0x04 (RMC), rate=1
     */
    fun enableRmc(): ByteArray = setNmeaMessageRate(0x04, 1)

    /**
     * NMEA VTG 메시지 활성화
     */
    fun enableVtg(): ByteArray = setNmeaMessageRate(0x05, 1)

    /**
     * NMEA GGA 메시지 활성화
     */
    fun enableGga(): ByteArray = setNmeaMessageRate(0x00, 1)

    /**
     * 불필요한 NMEA 메시지 비활성화 (GSA, GSV, GLL)
     * 25Hz에서 대역폭 확보를 위해 필수
     */
    fun disableUnnecessaryMessages(): List<ByteArray> = listOf(
        setNmeaMessageRate(0x02, 0), // GSA off
        setNmeaMessageRate(0x03, 0), // GSV off
        setNmeaMessageRate(0x01, 0), // GLL off
    )

    /**
     * UART 보레이트 설정 — UBX-CFG-PRT
     * 25Hz NMEA 출력을 위해 최소 115200bps 권장
     */
    fun setBaudRate115200(): ByteArray {
        val payload = ByteArray(20)
        payload[0] = 0x01  // portID = UART1
        // reserved
        payload[4] = 0xC0.toByte()  // txReady off
        payload[5] = 0x08.toByte()  // mode: 8N1
        payload[6] = 0x00
        payload[7] = 0x00
        // baudRate = 115200 (little-endian)
        payload[8] = 0x00
        payload[9] = 0xC2.toByte()
        payload[10] = 0x01
        payload[11] = 0x00
        // inProtoMask = UBX + NMEA
        payload[12] = 0x03
        payload[13] = 0x00
        // outProtoMask = UBX + NMEA
        payload[14] = 0x03
        payload[15] = 0x00
        return buildUbxMessage(classId = 0x06, msgId = 0x00, payload = payload)
    }

    /**
     * 전체 초기화 시퀀스 — 앱 시작 시 순서대로 전송
     */
    fun getInitSequence(): List<ByteArray> {
        val commands = mutableListOf<ByteArray>()
        commands.add(setAutomotiveMode())
        commands.add(enableRmc())
        commands.add(enableVtg())
        commands.add(enableGga())
        commands.addAll(disableUnnecessaryMessages())
        commands.add(setRate25Hz())
        return commands
    }

    // --- Internal ---

    private fun setNmeaMessageRate(nmeaMsgId: Int, rate: Int): ByteArray = buildUbxMessage(
        classId = 0x06, msgId = 0x01,
        payload = byteArrayOf(
            0xF0.toByte(),      // NMEA class
            nmeaMsgId.toByte(), // NMEA message ID
            rate.toByte()       // rate on current port
        )
    )

    private fun buildUbxMessage(classId: Int, msgId: Int, payload: ByteArray): ByteArray {
        val length = payload.size
        val msg = ByteArray(8 + length)
        msg[0] = SYNC1
        msg[1] = SYNC2
        msg[2] = classId.toByte()
        msg[3] = msgId.toByte()
        msg[4] = (length and 0xFF).toByte()
        msg[5] = (length shr 8 and 0xFF).toByte()
        payload.copyInto(msg, 6)

        // UBX checksum (Fletcher-8)
        var ckA = 0
        var ckB = 0
        for (i in 2 until 6 + length) {
            ckA = (ckA + msg[i]) and 0xFF
            ckB = (ckB + ckA) and 0xFF
        }
        msg[6 + length] = ckA.toByte()
        msg[7 + length] = ckB.toByte()

        return msg
    }
}

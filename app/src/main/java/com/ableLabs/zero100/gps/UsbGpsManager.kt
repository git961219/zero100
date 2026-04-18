package com.ableLabs.zero100.gps

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class UsbGpsManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var port: UsbSerialPort? = null
    private var readJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _gpsData = MutableSharedFlow<GpsData>(extraBufferCapacity = 50)
    val gpsData: SharedFlow<GpsData> = _gpsData

    private val _rawRate = MutableStateFlow(0)
    val rawRate: StateFlow<Int> = _rawRate

    // 디버그: 최근 수신된 NMEA 원본 (UI에서 확인용)
    private val _lastRawLines = MutableStateFlow<List<String>>(emptyList())
    val lastRawLines: StateFlow<List<String>> = _lastRawLines

    private var nmeaBuffer = StringBuilder()

    /**
     * USB GPS 모듈 연결 — 자동 보레이트 감지
     * 9600, 38400, 115200 순서로 시도하여 유효한 NMEA 데이터가 오는 보레이트를 찾음
     */
    suspend fun connectAutoDetect(): Boolean {
        _connectionState.value = ConnectionState.CONNECTING

        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            _connectionState.value = ConnectionState.ERROR
            return false
        }

        val driver = drivers[0]
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            _connectionState.value = ConnectionState.ERROR
            return false
        }

        // 보레이트 자동 감지: 각 속도로 시도하여 유효 NMEA 확인
        val baudRates = listOf(9600, 38400, 115200)

        for (baud in baudRates) {
            try {
                val testPort = driver.ports[0]
                testPort.open(connection)
                testPort.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                // 1초간 데이터 읽어서 유효한 NMEA가 있는지 확인
                val testBuffer = ByteArray(4096)
                val startTime = System.currentTimeMillis()
                val received = StringBuilder()

                while (System.currentTimeMillis() - startTime < 1500) {
                    val len = testPort.read(testBuffer, 200)
                    if (len > 0) {
                        received.append(String(testBuffer, 0, len, Charsets.US_ASCII))
                    }
                    // $G로 시작하는 NMEA 문장이 있으면 성공
                    if (received.contains("\$G")) {
                        port = testPort
                        _connectionState.value = ConnectionState.CONNECTED
                        startReading()
                        return true
                    }
                }

                // 이 보레이트에서 NMEA 안 나옴 → 닫고 다음 시도
                testPort.close()
            } catch (e: IOException) {
                // 다음 보레이트 시도
            }
        }

        _connectionState.value = ConnectionState.ERROR
        return false
    }

    /**
     * u-blox M9 초기화: 25Hz + Automotive 모드 설정
     * 기본 NMEA가 잘 나오는 것을 확인한 후에만 호출
     */
    suspend fun initializeUblox() {
        val currentPort = port ?: return

        // 1단계: 보레이트를 먼저 115200으로 변경 (25Hz NMEA에 38400은 부족)
        try {
            currentPort.write(UbxConfig.setBaudRate115200(), 100)
            delay(200)
            currentPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            delay(100)
        } catch (e: IOException) { }

        // 2단계: Automotive 모드 + 25Hz + 메시지 설정
        for (cmd in UbxConfig.getInitSequence()) {
            try {
                currentPort.write(cmd, 100)
                delay(50)
            } catch (e: IOException) { }
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        try { port?.close() } catch (_: IOException) {}
        port = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun getAvailableDevices(): List<UsbDevice> {
        return UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .map { it.device }
    }

    // --- 내부 ---

    private fun startReading() {
        readJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(4096)
            var messageCount = 0
            var lastCountTime = System.currentTimeMillis()
            val recentLines = mutableListOf<String>()
            var consecutiveErrors = 0

            while (isActive) {
                try {
                    val len = port?.read(buffer, 200) ?: 0
                    if (len > 0) {
                        consecutiveErrors = 0 // 성공하면 에러 카운트 리셋
                        val data = String(buffer, 0, len, Charsets.US_ASCII)
                        nmeaBuffer.append(data)

                        while (true) {
                            val newlineIdx = nmeaBuffer.indexOf('\n')
                            if (newlineIdx < 0) break

                            val line = nmeaBuffer.substring(0, newlineIdx).trim()
                            nmeaBuffer.delete(0, newlineIdx + 1)

                            if (line.startsWith("$")) {
                                recentLines.add(line)
                                if (recentLines.size > 5) recentLines.removeAt(0)
                                _lastRawLines.value = recentLines.toList()

                                val gpsData = NmeaParser.parse(line)
                                if (gpsData != null) {
                                    _gpsData.emit(gpsData)
                                    messageCount++
                                }
                            }
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastCountTime >= 1000) {
                            _rawRate.value = messageCount
                            messageCount = 0
                            lastCountTime = now
                        }

                        if (nmeaBuffer.length > 8192) {
                            nmeaBuffer.clear()
                        }
                    }
                } catch (e: IOException) {
                    consecutiveErrors++
                    if (consecutiveErrors >= 10) {
                        // 10회 연속 오류 시에만 연결 해제 (진짜 물리적 분리)
                        _connectionState.value = ConnectionState.ERROR
                        break
                    }
                    // 일시적 오류는 무시하고 계속 시도
                    try { delay(100) } catch (_: Exception) {}
                }
            }
        }
    }
}

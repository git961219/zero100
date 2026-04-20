package com.ableLabs.zero100.gps

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
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

private const val ACTION_USB_PERMISSION = "com.ableLabs.zero100.USB_PERMISSION"

class UsbGpsManager(
    private val context: Context,
    private val scope: CoroutineScope  // 외부에서 주입 (ViewModel scope)
) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var port: UsbSerialPort? = null
    private var readJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _gpsData = MutableSharedFlow<GpsData>(extraBufferCapacity = 50)
    val gpsData: SharedFlow<GpsData> = _gpsData

    private val _rawRate = MutableStateFlow(0)
    val rawRate: StateFlow<Int> = _rawRate

    private val _lastRawLines = MutableStateFlow<List<String>>(emptyList())
    val lastRawLines: StateFlow<List<String>> = _lastRawLines

    private var nmeaBuffer = StringBuilder()

    // USB 연결/분리 이벤트
    private val _usbAttached = MutableSharedFlow<Boolean>(extraBufferCapacity = 5)
    val usbAttached: SharedFlow<Boolean> = _usbAttached

    // USB 권한 콜백
    private val _permissionGranted = MutableStateFlow<Boolean?>(null)

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                _permissionGranted.value = granted
            }
        }
    }

    // USB 핫플러그 감지 리시버
    private val usbHotplugReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    _usbAttached.tryEmit(true)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    _usbAttached.tryEmit(false)
                    // USB 빠지면 즉시 disconnect
                    disconnect()
                }
            }
        }
    }

    init {
        val permFilter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbPermissionReceiver, permFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbPermissionReceiver, permFilter)
        }

        // USB 연결/분리 이벤트 등록
        val hotplugFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbHotplugReceiver, hotplugFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbHotplugReceiver, hotplugFilter)
        }
    }

    /**
     * USB GPS 모듈 연결 — USB 권한 요청 + 자동 보레이트 감지
     */
    suspend fun connectAutoDetect(): Boolean {
        _connectionState.value = ConnectionState.CONNECTING

        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return false
        }

        val driver = drivers[0]
        val device = driver.device

        // USB 권한 확인 및 요청
        if (!usbManager.hasPermission(device)) {
            _permissionGranted.value = null
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, permIntent)

            // 권한 응답 대기 (최대 30초)
            val startWait = System.currentTimeMillis()
            while (_permissionGranted.value == null && System.currentTimeMillis() - startWait < 30000) {
                delay(200)
            }

            if (_permissionGranted.value != true) {
                _connectionState.value = ConnectionState.ERROR
                return false
            }
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            _connectionState.value = ConnectionState.ERROR
            return false
        }

        // 보레이트 자동 감지
        val baudRates = listOf(9600, 38400, 115200)

        for (baud in baudRates) {
            try {
                val testPort = driver.ports[0]
                testPort.open(connection)
                testPort.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                val testBuffer = ByteArray(4096)
                val startTime = System.currentTimeMillis()
                val received = StringBuilder()

                while (System.currentTimeMillis() - startTime < 1500) {
                    val len = testPort.read(testBuffer, 200)
                    if (len > 0) {
                        received.append(String(testBuffer, 0, len, Charsets.US_ASCII))
                    }
                    if (received.contains("\$G")) {
                        port = testPort
                        _connectionState.value = ConnectionState.CONNECTED
                        startReading()
                        return true
                    }
                }

                // 이 보레이트에서 실패 → 안전하게 닫기
                try { testPort.close() } catch (_: IOException) {}
            } catch (e: IOException) {
                // 다음 보레이트 시도
            }
        }

        _connectionState.value = ConnectionState.DISCONNECTED
        return false
    }

    /**
     * u-blox M9 초기화: 25Hz + Automotive 모드
     */
    suspend fun initializeUblox() {
        val currentPort = port ?: return

        // 1단계: 먼저 현재 보레이트에서 설정 명령 전송
        // (보레이트 변경 전에 Automotive 모드 등 기본 설정)
        try {
            currentPort.write(UbxConfig.setAutomotiveMode(), 100)
            delay(100)
            currentPort.write(UbxConfig.enableRmc(), 100)
            delay(50)
            currentPort.write(UbxConfig.enableVtg(), 100)
            delay(50)
            currentPort.write(UbxConfig.enableGga(), 100)
            delay(50)
            for (cmd in UbxConfig.optimizeMessages()) {
                currentPort.write(cmd, 100)
                delay(50)
            }
        } catch (_: IOException) { }

        // 2단계: 보레이트 변경 (38400 → 115200)
        try {
            currentPort.write(UbxConfig.setBaudRate115200(), 100)
            delay(500)  // 모듈이 보레이트 전환할 시간 확보
            currentPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            delay(200)
        } catch (_: IOException) { }

        // 3단계: 새 보레이트에서 25Hz 설정
        try {
            currentPort.write(UbxConfig.setRate25Hz(), 100)
            delay(100)
        } catch (_: IOException) { }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        try { port?.close() } catch (_: IOException) {}
        port = null
        nmeaBuffer.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
        _rawRate.value = 0
    }

    fun destroy() {
        disconnect()
        try { context.unregisterReceiver(usbPermissionReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(usbHotplugReceiver) } catch (_: Exception) {}
    }

    // --- 내부 ---

    private fun startReading() {
        readJob = scope.launch(Dispatchers.IO) {  // ViewModel scope 사용 → 누수 방지
            val buffer = ByteArray(4096)
            var messageCount = 0
            var lastCountTime = System.currentTimeMillis()
            val recentLines = mutableListOf<String>()
            var consecutiveErrors = 0

            while (isActive) {
                try {
                    val len = port?.read(buffer, 200) ?: 0
                    if (len > 0) {
                        consecutiveErrors = 0
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
                        _connectionState.value = ConnectionState.ERROR
                        break
                    }
                    try { delay(100) } catch (_: Exception) {}
                }
            }
        }
    }
}

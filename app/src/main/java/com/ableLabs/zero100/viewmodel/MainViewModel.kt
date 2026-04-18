package com.ableLabs.zero100.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.ableLabs.zero100.data.AppDatabase
import com.ableLabs.zero100.data.MIGRATION_1_2
import com.ableLabs.zero100.data.MeasurementRecord
import com.ableLabs.zero100.gps.ConnectionState
import com.ableLabs.zero100.gps.GpsData
import com.ableLabs.zero100.gps.UsbGpsManager
import com.ableLabs.zero100.measurement.MeasureState
import com.ableLabs.zero100.measurement.Zero100Engine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * GPS 안정화 단계 — 메인 화면에서 단계별 표시용
 */
enum class GpsStage {
    DISCONNECTED,   // GPS 미연결
    CONNECTED,      // USB 연결됨
    SATELLITES,     // 위성 수신 중 (5개 미만)
    UPGRADING,      // 25Hz 전환 중
    STABILIZING,    // 안정화 중 (조건 미충족)
    READY           // 측정 가능 (rawRate>=20 && satellites>=8 && hdop<3.0)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val gpsManager = UsbGpsManager(app)
    val engine = Zero100Engine()

    private val db = Room.databaseBuilder(
        app, AppDatabase::class.java, "zero100-db"
    ).addMigrations(MIGRATION_1_2).build()

    // --- SharedPreferences 설정 ---
    private val prefs = app.getSharedPreferences("zero100_settings", Context.MODE_PRIVATE)

    private val _targetSpeedSetting = MutableStateFlow(prefs.getInt("target_speed", 200))
    val targetSpeedSetting: StateFlow<Int> = _targetSpeedSetting

    private val _oneFootRollout = MutableStateFlow(prefs.getBoolean("one_foot_rollout", false))
    val oneFootRollout: StateFlow<Boolean> = _oneFootRollout

    // --- GPS 상태 ---
    val connectionState: StateFlow<ConnectionState> = gpsManager.connectionState
    val rawRate: StateFlow<Int> = gpsManager.rawRate

    // --- GPS 안정화 단계 ---
    private val _gpsStage = MutableStateFlow(GpsStage.DISCONNECTED)
    val gpsStage: StateFlow<GpsStage> = _gpsStage

    // --- 측정 상태 ---
    val measureState: StateFlow<MeasureState> = engine.state
    val currentSpeed: StateFlow<Double> = engine.currentSpeed
    val result = engine.result

    // --- 최신 GPS 데이터 ---
    private val _latestGps = MutableStateFlow(GpsData())
    val latestGps: StateFlow<GpsData> = _latestGps

    // --- 기록 ---
    private val _records = MutableStateFlow<List<MeasurementRecord>>(emptyList())
    val records: StateFlow<List<MeasurementRecord>> = _records

    private val _bestRecord = MutableStateFlow<MeasurementRecord?>(null)
    val bestRecord: StateFlow<MeasurementRecord?> = _bestRecord

    // --- 실시간 경과 시간 ---
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs

    // --- 자동 25Hz 전환 ---
    private var auto25HzAttempted = false
    private var auto25HzRetryCount = 0
    private val MAX_25HZ_RETRIES = 3

    init {
        // GPS 데이터 -> 엔진으로 전달 + UI 업데이트
        viewModelScope.launch {
            gpsManager.gpsData.collect { data ->
                _latestGps.value = data
                engine.onGpsData(data)
                _elapsedMs.value = engine.getElapsedMs()
            }
        }

        // GPS 단계 계산 (connectionState, rawRate, latestGps를 조합)
        viewModelScope.launch {
            combine(
                connectionState,
                rawRate,
                latestGps
            ) { conn, rate, gps ->
                when {
                    conn != ConnectionState.CONNECTED -> GpsStage.DISCONNECTED
                    gps.satellites < 5 -> GpsStage.CONNECTED
                    rate < 20 && auto25HzAttempted && auto25HzRetryCount < MAX_25HZ_RETRIES -> GpsStage.UPGRADING
                    rate >= 20 && gps.satellites >= 8 && gps.hdop < 3.0 -> GpsStage.READY
                    rate >= 20 -> GpsStage.STABILIZING
                    gps.satellites >= 5 -> GpsStage.SATELLITES
                    else -> GpsStage.CONNECTED
                }
            }.collect { stage ->
                _gpsStage.value = stage
            }
        }

        // 자동 25Hz 전환: 위성 5개 이상 잡히면 자동 시도
        viewModelScope.launch {
            latestGps.collect { gps ->
                if (connectionState.value == ConnectionState.CONNECTED
                    && gps.satellites >= 5
                    && !auto25HzAttempted
                ) {
                    auto25HzAttempted = true
                    attemptAuto25Hz()
                }
            }
        }

        // 측정 완료 시 자동 저장
        viewModelScope.launch {
            engine.result.filterNotNull().collect { measureResult ->
                // 1-foot rollout 보정 적용
                val adjustedMs = if (_oneFootRollout.value) {
                    (measureResult.elapsedMs - 300).coerceAtLeast(0)
                } else {
                    measureResult.elapsedMs
                }
                // 구간 랩타임을 JSON으로 직렬화
                val splitsJson = measureResult.splits.joinToString(",", "[", "]") { split ->
                    """{"speed":${split.speedKmh.toInt()},"ms":${split.elapsedMs}}"""
                }
                val record = MeasurementRecord(
                    targetSpeed = measureResult.targetSpeed,
                    elapsedMs = adjustedMs,
                    peakSpeed = measureResult.peakSpeed,
                    splitsJson = splitsJson
                )
                db.measurementDao().insert(record)
                loadRecords()
            }
        }

        loadRecords()
    }

    // --- 자동 25Hz 전환 (재시도 로직 포함) ---
    private fun attemptAuto25Hz() {
        viewModelScope.launch {
            for (attempt in 1..MAX_25HZ_RETRIES) {
                auto25HzRetryCount = attempt
                gpsManager.initializeUblox()
                // 전환 후 2초 대기하여 rawRate 확인
                delay(2000)
                if (rawRate.value >= 20) {
                    // 성공
                    return@launch
                }
                if (attempt < MAX_25HZ_RETRIES) {
                    delay(3000) // 실패 시 3초 후 재시도
                }
            }
            // 3회 모두 실패 — 수동 버튼은 여전히 사용 가능
        }
    }

    // --- Actions ---

    fun connectGps() {
        auto25HzAttempted = false
        auto25HzRetryCount = 0
        viewModelScope.launch {
            gpsManager.connectAutoDetect()
        }
    }

    /**
     * 25Hz 모드 수동 활성화 (자동 전환 실패 시 사용)
     */
    fun enable25Hz() {
        auto25HzAttempted = false
        auto25HzRetryCount = 0
        viewModelScope.launch {
            auto25HzAttempted = true
            attemptAuto25Hz()
        }
    }

    fun disconnectGps() {
        auto25HzAttempted = false
        auto25HzRetryCount = 0
        gpsManager.disconnect()
    }

    fun startMeasurement(targetSpeed: Double? = null) {
        val speed = targetSpeed ?: _targetSpeedSetting.value.toDouble()
        engine.targetSpeed = speed
        engine.arm()
    }

    fun finishMeasurement() {
        engine.finish()
    }

    fun deleteRecord(record: MeasurementRecord) {
        viewModelScope.launch {
            db.measurementDao().delete(record)
            loadRecords()
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            db.measurementDao().deleteAll()
            loadRecords()
        }
    }

    // --- 설정 변경 ---

    fun setTargetSpeed(speed: Int) {
        _targetSpeedSetting.value = speed
        prefs.edit().putInt("target_speed", speed).apply()
    }

    fun setOneFootRollout(enabled: Boolean) {
        _oneFootRollout.value = enabled
        prefs.edit().putBoolean("one_foot_rollout", enabled).apply()
    }

    private fun loadRecords() {
        viewModelScope.launch {
            _records.value = db.measurementDao().getAll()
            _bestRecord.value = db.measurementDao().getBest()
        }
    }

    override fun onCleared() {
        super.onCleared()
        gpsManager.disconnect()
    }
}

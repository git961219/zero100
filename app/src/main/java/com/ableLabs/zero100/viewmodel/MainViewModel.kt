package com.ableLabs.zero100.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.ableLabs.zero100.data.AppDatabase
import com.ableLabs.zero100.data.MIGRATION_1_2
import com.ableLabs.zero100.data.MIGRATION_2_3
import com.ableLabs.zero100.data.MIGRATION_3_4
import com.ableLabs.zero100.data.MIGRATION_4_5
import com.ableLabs.zero100.data.MIGRATION_5_6
import com.ableLabs.zero100.data.MIGRATION_6_7
import com.ableLabs.zero100.data.MIGRATION_7_8
import com.ableLabs.zero100.data.MIGRATION_8_9
import com.ableLabs.zero100.data.MeasurementRecord
import com.ableLabs.zero100.data.Vehicle
import com.ableLabs.zero100.gps.GforceManager
import com.ableLabs.zero100.gps.ConnectionState
import com.ableLabs.zero100.gps.GpsData
import com.ableLabs.zero100.gps.InternalGpsManager
import com.ableLabs.zero100.gps.UsbGpsManager
import com.ableLabs.zero100.measurement.MeasureMode
import com.ableLabs.zero100.measurement.MeasureState
import com.ableLabs.zero100.measurement.Zero100Engine
import com.ableLabs.zero100.ui.theme.ThemeMode
import com.ableLabs.zero100.update.UpdateChecker
import com.ableLabs.zero100.update.UpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * GPS 안정화 단계 -- 메인 화면에서 단계별 표시용
 */
enum class GpsStage {
    DISCONNECTED,       // GPS 미연결
    CONNECTED,          // USB 연결됨
    SATELLITES,         // 위성 수신 중 (5개 미만)
    UPGRADING,          // 25Hz 전환 중
    STABILIZING,        // 안정화 중 (조건 미충족)
    READY,              // 측정 가능 (외부: rawRate>=20 && satellites>=8 && hdop<3.0)
    INTERNAL_READY      // 내장 GPS로 측정 가능
}

/**
 * GPS 소스 구분
 */
enum class GpsSource {
    INTERNAL,   // 핸드폰 내장 GPS
    EXTERNAL    // USB 외부 GPS 모듈
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val gpsManager = UsbGpsManager(app, viewModelScope)
    val internalGps = InternalGpsManager(app)
    val engine = Zero100Engine()
    val updateChecker = UpdateChecker(app)
    val gforceManager = GforceManager(app)

    // GPS 소스 관리
    private val _gpsSource = MutableStateFlow(GpsSource.INTERNAL)
    val gpsSource: StateFlow<GpsSource> = _gpsSource

    // 위치 권한 상태
    private val _locationPermissionGranted = MutableStateFlow(false)
    val locationPermissionGranted: StateFlow<Boolean> = _locationPermissionGranted

    private val db = Room.databaseBuilder(
        app, AppDatabase::class.java, "zero100-db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build()

    // --- SharedPreferences 설정 ---
    private val prefs = app.getSharedPreferences("zero100_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", 0)) { ThemeMode.DARK }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _targetSpeedSetting = MutableStateFlow(prefs.getInt("target_speed", 200))
    val targetSpeedSetting: StateFlow<Int> = _targetSpeedSetting

    private val _oneFootRollout = MutableStateFlow(prefs.getBoolean("one_foot_rollout", false))
    val oneFootRollout: StateFlow<Boolean> = _oneFootRollout

    private val _measureMode = MutableStateFlow(MeasureMode.ACCELERATION)
    val measureMode: StateFlow<MeasureMode> = _measureMode

    // --- 차량 프로필 ---
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles

    private val _selectedVehicleId = MutableStateFlow(prefs.getLong("selected_vehicle_id", 0L))
    val selectedVehicleId: StateFlow<Long> = _selectedVehicleId

    private val _decelStartSpeed = MutableStateFlow(prefs.getInt("decel_start_speed", 100))
    val decelStartSpeed: StateFlow<Int> = _decelStartSpeed

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

    // --- OTA 업데이트 ---
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _updateProgress = MutableStateFlow(-1) // -1=미시작, 0~100=진행중
    val updateProgress: StateFlow<Int> = _updateProgress

    private val _updateChecking = MutableStateFlow(false)
    val updateChecking: StateFlow<Boolean> = _updateChecking

    // --- 자동 25Hz 전환 ---
    private var auto25HzAttempted = false
    private var auto25HzRetryCount = 0
    private val MAX_25HZ_RETRIES = 3

    init {
        // 외부 GPS 데이터 수집 (기존)
        viewModelScope.launch {
            gpsManager.gpsData.collect { data ->
                if (gpsManager.connectionState.value == ConnectionState.CONNECTED) {
                    _gpsSource.value = GpsSource.EXTERNAL
                    _latestGps.value = data
                    engine.onGpsData(data)
                    _elapsedMs.value = engine.getElapsedMs()
                }
            }
        }

        // 내장 GPS 데이터 수집
        viewModelScope.launch {
            internalGps.gpsData.collect { data ->
                if (_gpsSource.value == GpsSource.INTERNAL) {
                    _latestGps.value = data
                    engine.onGpsData(data)
                    _elapsedMs.value = engine.getElapsedMs()
                }
            }
        }

        // 외부 GPS 연결 상태 감시 -> 끊기면 내장으로 복귀
        viewModelScope.launch {
            gpsManager.connectionState.collect { state ->
                if (state != ConnectionState.CONNECTED) {
                    _gpsSource.value = GpsSource.INTERNAL
                }
            }
        }

        // USB 핫플러그 이벤트 처리
        viewModelScope.launch {
            gpsManager.usbAttached.collect { attached ->
                if (attached) {
                    // USB 꽂으면 자동 연결 시도
                    delay(500) // USB 안정화 대기
                    auto25HzAttempted = false
                    auto25HzRetryCount = 0
                    gpsManager.connectAutoDetect()
                }
                // detached는 UsbGpsManager 내부에서 이미 disconnect() 처리
            }
        }

        // GPS 단계 계산 (connectionState, rawRate, latestGps, gpsSource를 조합)
        viewModelScope.launch {
            combine(
                connectionState,
                rawRate,
                latestGps,
                _gpsSource
            ) { conn, rate, gps, source ->
                when {
                    // 외부 GPS 연결됨
                    conn == ConnectionState.CONNECTED -> when {
                        gps.satellites < 5 -> GpsStage.CONNECTED
                        rate < 20 && auto25HzAttempted && auto25HzRetryCount < MAX_25HZ_RETRIES -> GpsStage.UPGRADING
                        rate >= 20 && gps.satellites >= 8 && gps.hdop < 3.0 -> GpsStage.READY
                        rate >= 20 -> GpsStage.STABILIZING
                        gps.satellites >= 5 -> GpsStage.SATELLITES
                        else -> GpsStage.CONNECTED
                    }
                    // 내장 GPS 사용 중
                    source == GpsSource.INTERNAL && gps.isValid -> GpsStage.INTERNAL_READY
                    // 아무것도 안 됨
                    else -> GpsStage.DISCONNECTED
                }
            }.collect { stage ->
                _gpsStage.value = stage
            }
        }

        // 자동 25Hz 전환: 위성 8개 이상 + 안정적 수신 상태에서 시도
        viewModelScope.launch {
            latestGps.collect { gps ->
                if (connectionState.value == ConnectionState.CONNECTED
                    && gps.satellites >= 8
                    && gps.hdop < 5.0
                    && !auto25HzAttempted
                ) {
                    auto25HzAttempted = true
                    delay(2000) // 수신 안정화 대기
                    attemptAuto25Hz()
                }
            }
        }

        // 측정 완료 시 자동 저장
        viewModelScope.launch {
            engine.result.filterNotNull().collect { measureResult ->
                gforceManager.stop()
                // 1-foot rollout 보정 적용
                val adjustedMs = if (_oneFootRollout.value) {
                    (measureResult.elapsedMs - 300).coerceAtLeast(0)
                } else {
                    measureResult.elapsedMs
                }
                // 구간 랩타임을 JSON으로 직렬화 (거리 + major 포함)
                val splitsJson = measureResult.splits.joinToString(",", "[", "]") { split ->
                    """{"speed":${split.speedKmh.toInt()},"ms":${split.elapsedMs},"dist":${"%.1f".format(split.distanceM)},"major":${split.isMajor}}"""
                }
                // GPS 궤적을 JSON으로 직렬화
                val trackPointsJson = measureResult.trackPoints.joinToString(",", "[", "]") { pt ->
                    """{"t":${pt.timeMs},"lat":${pt.lat},"lon":${pt.lon},"spd":${"%.1f".format(pt.speedKmh)}}"""
                }
                // 거리 체크포인트를 JSON으로 직렬화
                val distCheckJson = measureResult.distanceCheckpoints.joinToString(",", "[", "]") { cp ->
                    """{"dist":${"%.1f".format(cp.distanceM)},"label":"${cp.label}","ms":${cp.elapsedMs},"spd":${"%.1f".format(cp.speedKmh)}}"""
                }
                // 측정 중 평균 GPS 품질 계산
                val avgHdop = measureResult.trackPoints
                    .filter { it.lat != 0.0 }
                    .let { pts -> if (pts.isEmpty()) 0.0 else _latestGps.value.hdop }
                val avgSats = _latestGps.value.satellites
                val hz = rawRate.value

                val record = MeasurementRecord(
                    targetSpeed = measureResult.targetSpeed,
                    elapsedMs = adjustedMs,
                    peakSpeed = measureResult.peakSpeed,
                    splitsJson = splitsJson,
                    trackPointsJson = trackPointsJson,
                    isRolloutApplied = _oneFootRollout.value,
                    distanceBySpeed = measureResult.distanceBySpeed,
                    distanceByGps = measureResult.distanceByGps,
                    gpsSource = _gpsSource.value.name,
                    avgHdop = avgHdop,
                    avgSatellites = avgSats,
                    updateRateHz = hz,
                    distanceCheckpointsJson = distCheckJson,
                    measureMode = measureResult.measureMode.name,
                    peakG = measureResult.peakG,
                    vehicleId = _selectedVehicleId.value,
                    accelMs = measureResult.accelMs,
                    decelMs = measureResult.decelMs,
                    accelDistance = measureResult.accelDistance,
                    decelDistance = measureResult.decelDistance
                )
                db.measurementDao().insert(record)
                loadRecords()
            }
        }

        loadRecords()
        loadVehicles()

        // G-force -> engine에 peakG 전달
        viewModelScope.launch {
            gforceManager.gforceData.collect { gData ->
                val totalPeak = maxOf(
                    kotlin.math.abs(gData.peakLongG),
                    kotlin.math.abs(gData.peakLatG)
                )
                engine.peakG = totalPeak
            }
        }

        // GPS 자동 연결 시도 (외부 USB)
        viewModelScope.launch {
            delay(500) // UI 초기화 대기
            gpsManager.connectAutoDetect()
        }

        // 앱 시작 시 업데이트 확인 (5초 후)
        viewModelScope.launch {
            delay(5000)
            val info = updateChecker.checkForUpdate()
            if (info?.isNewer == true) {
                _updateInfo.value = info
            }
        }
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

    // --- OTA 업데이트 액션 ---

    fun downloadUpdate() {
        val url = _updateInfo.value?.downloadUrl ?: return
        viewModelScope.launch {
            _updateProgress.value = 0
            val success = updateChecker.downloadAndInstall(url) { progress ->
                _updateProgress.value = progress
            }
            if (!success) _updateProgress.value = -1
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun checkForUpdateManual() {
        viewModelScope.launch {
            _updateChecking.value = true
            val info = updateChecker.checkForUpdate()
            _updateChecking.value = false
            if (info?.isNewer == true) {
                _updateInfo.value = info
            } else {
                // 최신 버전이면 null 유지 (UI에서 "최신 버전입니다" 표시)
                _updateInfo.value = null
            }
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
        engine.measureMode = _measureMode.value
        if (_measureMode.value == MeasureMode.DECELERATION) {
            engine.decelStartSpeed = _decelStartSpeed.value.toDouble()
        }
        // COMBINED: targetSpeed를 가속 목표로 사용
        gforceManager.resetPeak()
        gforceManager.start()
        engine.arm()
    }

    fun finishMeasurement() {
        engine.finish()
        gforceManager.stop()
    }

    fun setMeasureMode(mode: MeasureMode) {
        _measureMode.value = mode
    }

    fun setDecelStartSpeed(speed: Int) {
        _decelStartSpeed.value = speed
        prefs.edit().putInt("decel_start_speed", speed).apply()
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

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putInt("theme_mode", mode.ordinal).apply()
    }

    /**
     * 위치 권한 획득 후 내장 GPS 시작
     */
    fun onLocationPermissionResult(granted: Boolean) {
        _locationPermissionGranted.value = granted
        if (granted) {
            internalGps.start()
        }
    }

    suspend fun getRecordById(id: Long): MeasurementRecord? {
        return db.measurementDao().getById(id)
    }

    private fun loadRecords() {
        viewModelScope.launch {
            _records.value = db.measurementDao().getAll()
            _bestRecord.value = db.measurementDao().getBest()
        }
    }

    // --- 차량 프로필 관리 ---

    private fun loadVehicles() {
        viewModelScope.launch {
            _vehicles.value = db.vehicleDao().getAll()
        }
    }

    fun addVehicle(name: String, make: String, model: String, year: Int, notes: String) {
        viewModelScope.launch {
            val vehicle = Vehicle(name = name, make = make, model = model, year = year, notes = notes)
            db.vehicleDao().insert(vehicle)
            loadVehicles()
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            db.vehicleDao().update(vehicle)
            loadVehicles()
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            db.vehicleDao().delete(vehicle)
            if (_selectedVehicleId.value == vehicle.id) {
                _selectedVehicleId.value = 0L
                prefs.edit().putLong("selected_vehicle_id", 0L).apply()
            }
            loadVehicles()
        }
    }

    fun setDefaultVehicle(vehicleId: Long) {
        viewModelScope.launch {
            db.vehicleDao().clearAllDefaults()
            if (vehicleId > 0) {
                db.vehicleDao().setDefault(vehicleId)
            }
            _selectedVehicleId.value = vehicleId
            prefs.edit().putLong("selected_vehicle_id", vehicleId).apply()
            loadVehicles()
        }
    }

    suspend fun getVehicleById(id: Long): Vehicle? {
        return db.vehicleDao().getById(id)
    }

    override fun onCleared() {
        super.onCleared()
        gpsManager.destroy()
        internalGps.stop()
        gforceManager.stop()
    }
}

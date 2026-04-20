package com.ableLabs.zero100.gps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

data class GforceData(
    val longitudinal: Float = 0f,  // 전후 G (가속 +, 감속 -)
    val lateral: Float = 0f,       // 좌우 G
    val total: Float = 0f,         // 총 G
    val peakLongG: Float = 0f,     // 피크 전후 G
    val peakLatG: Float = 0f       // 피크 좌우 G
)

/**
 * Android 가속도 센서를 사용하여 G-force를 실시간 측정.
 * TYPE_LINEAR_ACCELERATION 사용 (중력 제거된 가속도).
 * 사용 불가 시 TYPE_ACCELEROMETER에서 중력 수동 제거.
 */
class GforceManager(context: Context) : SensorEventListener {

    companion object {
        private const val G = 9.81f
        private const val ALPHA = 0.15f // 로우패스 필터 계수
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _gforceData = MutableStateFlow(GforceData())
    val gforceData: StateFlow<GforceData> = _gforceData

    private var peakLongG = 0f
    private var peakLatG = 0f

    // 중력 제거용 (TYPE_ACCELEROMETER 사용 시)
    private val gravity = FloatArray(3)
    private var gravityInitialized = false
    private var useLinear = false

    // 로우패스 필터 적용된 값
    private var filteredX = 0f
    private var filteredY = 0f

    fun start() {
        peakLongG = 0f
        peakLatG = 0f
        gravityInitialized = false
        filteredX = 0f
        filteredY = 0f

        // LINEAR_ACCELERATION이 있으면 우선 사용
        if (linearSensor != null) {
            useLinear = true
            sensorManager.registerListener(this, linearSensor, SensorManager.SENSOR_DELAY_GAME)
        } else if (accelSensor != null) {
            useLinear = false
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun resetPeak() {
        peakLongG = 0f
        peakLatG = 0f
        _gforceData.value = _gforceData.value.copy(peakLongG = 0f, peakLatG = 0f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val ax: Float
        val ay: Float

        if (useLinear) {
            // TYPE_LINEAR_ACCELERATION: 이미 중력 제거됨
            ax = event.values[0]  // lateral (좌우)
            ay = event.values[1]  // longitudinal (전후)
        } else {
            // TYPE_ACCELEROMETER: 수동 중력 제거 (로우패스)
            if (!gravityInitialized) {
                gravity[0] = event.values[0]
                gravity[1] = event.values[1]
                gravity[2] = event.values[2]
                gravityInitialized = true
                return
            }
            gravity[0] = ALPHA * event.values[0] + (1 - ALPHA) * gravity[0]
            gravity[1] = ALPHA * event.values[1] + (1 - ALPHA) * gravity[1]
            gravity[2] = ALPHA * event.values[2] + (1 - ALPHA) * gravity[2]

            ax = event.values[0] - gravity[0]
            ay = event.values[1] - gravity[1]
        }

        // 로우패스 필터로 노이즈 감소
        filteredX = ALPHA * ax + (1 - ALPHA) * filteredX
        filteredY = ALPHA * ay + (1 - ALPHA) * filteredY

        val longG = filteredY / G   // 전후 G (폰 세로 기준: Y축)
        val latG = filteredX / G    // 좌우 G (X축)
        val totalG = sqrt(filteredX * filteredX + filteredY * filteredY) / G

        if (abs(longG) > abs(peakLongG)) peakLongG = longG
        if (abs(latG) > abs(peakLatG)) peakLatG = latG

        _gforceData.value = GforceData(
            longitudinal = longG,
            lateral = latG,
            total = totalG,
            peakLongG = peakLongG,
            peakLatG = peakLatG
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

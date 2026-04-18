package com.ableLabs.zero100.measurement

import com.ableLabs.zero100.gps.GpsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class MeasureState {
    IDLE,       // 대기 중
    READY,      // 정차 감지 → 출발 대기
    MEASURING,  // 측정 중 (가속 중)
    FINISHED    // 측정 완료 (수동 종료 또는 최대 속도 도달)
}

data class SplitTime(
    val speedKmh: Double,   // 구간 속도 (60, 100, 150, 200)
    val elapsedMs: Long     // 해당 속도 도달 시간
) {
    val elapsedSeconds: Double get() = elapsedMs / 1000.0
}

data class MeasureResult(
    val targetSpeed: Double,        // 최종 목표 속도 (km/h)
    val elapsedMs: Long,            // 최종 소요 시간 (ms)
    val peakSpeed: Double,          // 최고 속도
    val speedLog: List<SpeedPoint>, // 속도 로그 (그래프용)
    val splits: List<SplitTime>,    // 구간별 랩타임 (60, 100, 150, 200)
    val timestamp: Long = System.currentTimeMillis()
) {
    val elapsedSeconds: Double get() = elapsedMs / 1000.0
}

data class SpeedPoint(
    val timeMs: Long,    // 측정 시작 이후 ms
    val speedKmh: Double
)

class Zero100Engine {

    companion object {
        const val STANDSTILL_THRESHOLD = 2.0  // km/h 이하면 정차로 판단
        const val MAX_TARGET_SPEED = 200.0    // 최대 목표: 200km/h
        val SPLIT_SPEEDS = listOf(60.0, 100.0, 150.0, 200.0) // 구간 기록 속도
    }

    private val _state = MutableStateFlow(MeasureState.IDLE)
    val state: StateFlow<MeasureState> = _state

    private val _currentSpeed = MutableStateFlow(0.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed

    private val _result = MutableStateFlow<MeasureResult?>(null)
    val result: StateFlow<MeasureResult?> = _result

    // 실시간 구간 기록 (UI에서 측정 중에도 표시 가능)
    private val _liveSplits = MutableStateFlow<List<SplitTime>>(emptyList())
    val liveSplits: StateFlow<List<SplitTime>> = _liveSplits

    var targetSpeed: Double = MAX_TARGET_SPEED

    private var startTime: Long = 0L
    private var peakSpeed: Double = 0.0
    private val speedLog = mutableListOf<SpeedPoint>()
    private val splits = mutableListOf<SplitTime>()
    private val recordedSplits = mutableSetOf<Double>() // 이미 기록된 구간

    /**
     * 측정 모드 시작 — 정차 감지 대기
     */
    fun arm() {
        _state.value = MeasureState.IDLE
        _result.value = null
        _liveSplits.value = emptyList()
        peakSpeed = 0.0
        speedLog.clear()
        splits.clear()
        recordedSplits.clear()
    }

    /**
     * 수동 종료 — 200까지 안 가도 현재까지 결과 저장
     */
    fun finish() {
        if (_state.value != MeasureState.MEASURING) return

        val elapsed = System.currentTimeMillis() - startTime
        _result.value = MeasureResult(
            targetSpeed = peakSpeed, // 실제 도달한 최고 속도를 목표로
            elapsedMs = elapsed,
            peakSpeed = peakSpeed,
            speedLog = speedLog.toList(),
            splits = splits.toList()
        )
        _state.value = MeasureState.FINISHED
    }

    /**
     * GPS 데이터 수신 시마다 호출
     * 상태 머신: IDLE → READY → MEASURING → FINISHED
     */
    fun onGpsData(data: GpsData) {
        if (!data.isValid) return

        _currentSpeed.value = data.speedKmh

        when (_state.value) {
            MeasureState.IDLE -> {
                if (data.speedKmh <= STANDSTILL_THRESHOLD) {
                    _state.value = MeasureState.READY
                }
            }

            MeasureState.READY -> {
                if (data.speedKmh > STANDSTILL_THRESHOLD) {
                    _state.value = MeasureState.MEASURING
                    startTime = System.currentTimeMillis()
                    peakSpeed = data.speedKmh
                    speedLog.clear()
                    splits.clear()
                    recordedSplits.clear()
                    speedLog.add(SpeedPoint(0, data.speedKmh))
                }
            }

            MeasureState.MEASURING -> {
                val elapsed = System.currentTimeMillis() - startTime
                speedLog.add(SpeedPoint(elapsed, data.speedKmh))

                if (data.speedKmh > peakSpeed) {
                    peakSpeed = data.speedKmh
                }

                // 구간별 랩타임 자동 기록
                for (splitSpeed in SPLIT_SPEEDS) {
                    if (splitSpeed !in recordedSplits && data.speedKmh >= splitSpeed) {
                        val exactTime = interpolateTargetTime(splitSpeed)
                        splits.add(SplitTime(splitSpeed, exactTime))
                        recordedSplits.add(splitSpeed)
                        _liveSplits.value = splits.toList()
                    }
                }

                // 최대 목표 속도(200) 도달 → 자동 완료
                if (data.speedKmh >= targetSpeed) {
                    val exactTime = interpolateTargetTime(targetSpeed)
                    _result.value = MeasureResult(
                        targetSpeed = targetSpeed,
                        elapsedMs = exactTime,
                        peakSpeed = peakSpeed,
                        speedLog = speedLog.toList(),
                        splits = splits.toList()
                    )
                    _state.value = MeasureState.FINISHED
                }

                // 속도가 다시 정차 수준으로 떨어지면 리셋
                if (data.speedKmh <= STANDSTILL_THRESHOLD && elapsed > 1000) {
                    _state.value = MeasureState.READY
                    speedLog.clear()
                    splits.clear()
                    recordedSplits.clear()
                    _liveSplits.value = emptyList()
                }
            }

            MeasureState.FINISHED -> {
                // arm() 호출 전까지 유지
            }
        }
    }

    /**
     * 마지막 두 포인트 사이에서 목표 속도 도달 시점을 선형 보간
     */
    private fun interpolateTargetTime(target: Double): Long {
        if (speedLog.size < 2) return speedLog.lastOrNull()?.timeMs ?: 0

        val last = speedLog.last()
        val prev = speedLog[speedLog.size - 2]

        if (last.speedKmh == prev.speedKmh) return last.timeMs

        val ratio = (target - prev.speedKmh) / (last.speedKmh - prev.speedKmh)
        val interpolatedTime = prev.timeMs + (ratio * (last.timeMs - prev.timeMs)).toLong()
        return interpolatedTime
    }

    /**
     * 현재 측정 경과 시간 (ms)
     */
    fun getElapsedMs(): Long {
        return if (_state.value == MeasureState.MEASURING) {
            System.currentTimeMillis() - startTime
        } else {
            0
        }
    }
}

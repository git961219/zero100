package com.ableLabs.zero100.measurement

import com.ableLabs.zero100.gps.GpsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

enum class MeasureState {
    IDLE,       // 대기 중
    READY,      // 정차 감지 -> 출발 대기 (가속) / 목표속도 도달 대기 (감속)
    MEASURING,  // 측정 중 (가속 중 / 감속 중)
    FINISHED    // 측정 완료 (수동 종료 또는 목표 도달)
}

enum class MeasureMode {
    ACCELERATION,  // 가속: 정차 → 목표속도
    DECELERATION   // 감속: 목표속도 → 정차
}

data class SplitTime(
    val speedKmh: Double,   // 구간 속도 (10, 20, ..., 200)
    val elapsedMs: Long,    // 해당 속도 도달 시간
    val distanceM: Double = 0.0,  // 출발부터 이 구간까지 거리 (m)
    val isMajor: Boolean = false  // 60, 100, 150, 200은 true
) {
    val elapsedSeconds: Double get() = elapsedMs / 1000.0
}

/**
 * 거리 기반 체크포인트 기록
 */
data class DistanceCheckpoint(
    val distanceM: Double,    // 체크포인트 거리 (m)
    val label: String,        // "60ft", "1/4 mile" 등
    val elapsedMs: Long,      // 해당 거리 도달 시간
    val speedKmh: Double      // 해당 거리 도달 시 속도
)

data class MeasureResult(
    val targetSpeed: Double,        // 최종 목표 속도 (km/h)
    val elapsedMs: Long,            // 최종 소요 시간 (ms)
    val peakSpeed: Double,          // 최고 속도
    val speedLog: List<SpeedPoint>, // 속도 로그 (그래프용)
    val splits: List<SplitTime>,    // 구간별 랩타임 (10km/h 단위)
    val trackPoints: List<TrackPoint> = emptyList(), // GPS 궤적 (지도용)
    val timestamp: Long = System.currentTimeMillis(),
    val distanceBySpeed: Double = 0.0,  // 속도 적분 거리 (m)
    val distanceByGps: Double = 0.0,    // GPS 좌표 거리 (m)
    val distanceCheckpoints: List<DistanceCheckpoint> = emptyList(),
    val measureMode: MeasureMode = MeasureMode.ACCELERATION  // 가속/감속 모드
) {
    val elapsedSeconds: Double get() = elapsedMs / 1000.0

    /** 두 거리의 일치율 (%) */
    val distanceAccuracy: Double
        get() {
            if (distanceBySpeed == 0.0 || distanceByGps == 0.0) return 0.0
            val smaller = minOf(distanceBySpeed, distanceByGps)
            val larger = maxOf(distanceBySpeed, distanceByGps)
            return (smaller / larger) * 100
        }

    /** 대표 거리 (두 값의 평균, 둘 다 0이면 0) */
    val distanceM: Double
        get() {
            if (distanceBySpeed == 0.0 && distanceByGps == 0.0) return 0.0
            if (distanceBySpeed == 0.0) return distanceByGps
            if (distanceByGps == 0.0) return distanceBySpeed
            return (distanceBySpeed + distanceByGps) / 2.0
        }
}

data class SpeedPoint(
    val timeMs: Long,    // 측정 시작 이후 ms
    val speedKmh: Double
)

/**
 * GPS 궤적 포인트 -- 지도 표시용
 */
data class TrackPoint(
    val timeMs: Long,       // 측정 시작 이후 ms
    val lat: Double,
    val lon: Double,
    val speedKmh: Double
)

class Zero100Engine {

    companion object {
        const val STANDSTILL_THRESHOLD = 2.0  // km/h 이하면 정차로 판단
        const val MAX_TARGET_SPEED = 200.0    // 최대 목표: 200km/h

        // 10km/h 단위 구간 (Dragy 방식)
        val SPLIT_SPEEDS = (10..200 step 10).map { it.toDouble() }

        // 주요 구간 (UI에서 강조 표시용)
        val MAJOR_SPLITS = listOf(60.0, 100.0, 150.0, 200.0)

        // 거리 기반 체크포인트
        val DISTANCE_CHECKPOINTS = listOf(
            18.3 to "60ft",       // 론치 성능
            100.0 to "100m",
            201.0 to "1/8 mile",
            402.0 to "1/4 mile",
            805.0 to "1/2 mile"
        )

        /** Haversine 공식으로 두 좌표 간 거리 (m) */
        fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val R = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return R * c
        }
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

    // 실시간 거리 체크포인트
    private val _liveDistanceCheckpoints = MutableStateFlow<List<DistanceCheckpoint>>(emptyList())
    val liveDistanceCheckpoints: StateFlow<List<DistanceCheckpoint>> = _liveDistanceCheckpoints

    // 실시간 이동 거리
    private val _liveDistance = MutableStateFlow(0.0)
    val liveDistance: StateFlow<Double> = _liveDistance

    var targetSpeed: Double = MAX_TARGET_SPEED
    var measureMode: MeasureMode = MeasureMode.ACCELERATION
    var decelStartSpeed: Double = 100.0  // 감속 시작 속도 (km/h)

    private var startTime: Long = 0L
    private var peakSpeed: Double = 0.0
    private val speedLog = java.util.concurrent.CopyOnWriteArrayList<SpeedPoint>()
    private val splits = java.util.concurrent.CopyOnWriteArrayList<SplitTime>()
    private val trackPoints = java.util.concurrent.CopyOnWriteArrayList<TrackPoint>()
    private val distanceCheckpoints = java.util.concurrent.CopyOnWriteArrayList<DistanceCheckpoint>()
    private val recordedSplits = mutableSetOf<Double>() // 이미 기록된 구간
    private val recordedCheckpoints = mutableSetOf<Double>() // 이미 기록된 거리 체크포인트
    private var currentDistance: Double = 0.0 // 현재까지 이동 거리 (m)

    /**
     * 측정 모드 시작 -- 정차 감지 대기
     */
    fun arm() {
        _state.value = MeasureState.IDLE
        _result.value = null
        _liveSplits.value = emptyList()
        _liveDistanceCheckpoints.value = emptyList()
        _liveDistance.value = 0.0
        peakSpeed = 0.0
        speedLog.clear()
        splits.clear()
        trackPoints.clear()
        distanceCheckpoints.clear()
        recordedSplits.clear()
        recordedCheckpoints.clear()
        currentDistance = 0.0
    }

    /**
     * 수동 종료 -- 200까지 안 가도 현재까지 결과 저장
     */
    fun finish() {
        if (_state.value != MeasureState.MEASURING) return

        val elapsed = System.currentTimeMillis() - startTime
        val logList = speedLog.toList()
        val tpList = trackPoints.toList()
        _result.value = MeasureResult(
            targetSpeed = if (measureMode == MeasureMode.DECELERATION) decelStartSpeed else peakSpeed,
            elapsedMs = elapsed,
            peakSpeed = peakSpeed,
            speedLog = logList,
            splits = splits.toList(),
            trackPoints = tpList,
            distanceBySpeed = calcDistanceBySpeed(logList),
            distanceByGps = calcDistanceByGps(tpList),
            distanceCheckpoints = distanceCheckpoints.toList(),
            measureMode = measureMode
        )
        _state.value = MeasureState.FINISHED
    }

    /**
     * GPS 데이터 수신 시마다 호출
     * 상태 머신: IDLE -> READY -> MEASURING -> FINISHED
     */
    fun onGpsData(data: GpsData) {
        if (!data.isValid) return

        _currentSpeed.value = data.speedKmh

        if (measureMode == MeasureMode.DECELERATION) {
            onGpsDataDeceleration(data)
        } else {
            onGpsDataAcceleration(data)
        }
    }

    /**
     * 가속 모드 상태 머신: IDLE -> READY -> MEASURING -> FINISHED
     */
    private fun onGpsDataAcceleration(data: GpsData) {
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
                    trackPoints.clear()
                    distanceCheckpoints.clear()
                    recordedSplits.clear()
                    recordedCheckpoints.clear()
                    currentDistance = 0.0
                    _liveDistance.value = 0.0
                    speedLog.add(SpeedPoint(0, data.speedKmh))
                    // 좌표가 유효하면 첫 궤적 포인트 기록
                    if (data.latitude != 0.0 || data.longitude != 0.0) {
                        trackPoints.add(TrackPoint(0, data.latitude, data.longitude, data.speedKmh))
                    }
                }
            }

            MeasureState.MEASURING -> {
                val elapsed = System.currentTimeMillis() - startTime
                speedLog.add(SpeedPoint(elapsed, data.speedKmh))

                // GPS 궤적 수집 (좌표가 유효할 때만)
                if (data.latitude != 0.0 || data.longitude != 0.0) {
                    trackPoints.add(TrackPoint(elapsed, data.latitude, data.longitude, data.speedKmh))
                }

                if (data.speedKmh > peakSpeed) {
                    peakSpeed = data.speedKmh
                }

                // 실시간 거리 계산 (속도 적분 -- 마지막 두 포인트)
                if (speedLog.size >= 2) {
                    val prev = speedLog[speedLog.size - 2]
                    val curr = speedLog.last()
                    val dtSec = (curr.timeMs - prev.timeMs) / 1000.0
                    val avgSpeedMs = (prev.speedKmh + curr.speedKmh) / 2.0 / 3.6
                    currentDistance += avgSpeedMs * dtSec
                    _liveDistance.value = currentDistance
                }

                // 구간별 랩타임 자동 기록 (10km/h 단위)
                for (splitSpeed in SPLIT_SPEEDS) {
                    if (splitSpeed !in recordedSplits && data.speedKmh >= splitSpeed) {
                        val exactTime = interpolateTargetTime(splitSpeed)
                        val isMajor = splitSpeed in MAJOR_SPLITS
                        splits.add(SplitTime(splitSpeed, exactTime, currentDistance, isMajor))
                        recordedSplits.add(splitSpeed)
                        _liveSplits.value = splits.toList()
                    }
                }

                // 거리 기반 체크포인트 자동 기록
                for ((checkDist, label) in DISTANCE_CHECKPOINTS) {
                    if (checkDist !in recordedCheckpoints && currentDistance >= checkDist) {
                        // 보간: 정확한 도달 시점 추정
                        val exactTime = interpolateDistanceTime(checkDist)
                        distanceCheckpoints.add(
                            DistanceCheckpoint(checkDist, label, exactTime, data.speedKmh)
                        )
                        recordedCheckpoints.add(checkDist)
                        _liveDistanceCheckpoints.value = distanceCheckpoints.toList()
                    }
                }

                // 최대 목표 속도(200) 도달 -> 자동 완료
                if (data.speedKmh >= targetSpeed) {
                    val exactTime = interpolateTargetTime(targetSpeed)
                    val logList = speedLog.toList()
                    val tpList = trackPoints.toList()
                    _result.value = MeasureResult(
                        targetSpeed = targetSpeed,
                        elapsedMs = exactTime,
                        peakSpeed = peakSpeed,
                        speedLog = logList,
                        splits = splits.toList(),
                        trackPoints = tpList,
                        distanceBySpeed = calcDistanceBySpeed(logList),
                        distanceByGps = calcDistanceByGps(tpList),
                        distanceCheckpoints = distanceCheckpoints.toList(),
                        measureMode = MeasureMode.ACCELERATION
                    )
                    _state.value = MeasureState.FINISHED
                }

                // 속도가 다시 정차 수준으로 떨어지면 리셋
                if (data.speedKmh <= STANDSTILL_THRESHOLD && elapsed > 1000) {
                    _state.value = MeasureState.READY
                    speedLog.clear()
                    splits.clear()
                    trackPoints.clear()
                    distanceCheckpoints.clear()
                    recordedSplits.clear()
                    recordedCheckpoints.clear()
                    currentDistance = 0.0
                    _liveSplits.value = emptyList()
                    _liveDistanceCheckpoints.value = emptyList()
                    _liveDistance.value = 0.0
                }
            }

            MeasureState.FINISHED -> {
                // arm() 호출 전까지 유지
            }
        }
    }

    /**
     * 감속 모드 상태 머신:
     * IDLE -> 속도 >= decelStartSpeed -> READY
     * READY -> 속도 < decelStartSpeed (브레이크 시작) -> MEASURING
     * MEASURING -> 속도 <= STANDSTILL_THRESHOLD -> FINISHED
     */
    private fun onGpsDataDeceleration(data: GpsData) {
        when (_state.value) {
            MeasureState.IDLE -> {
                // 감속: 목표 속도 이상이면 READY
                if (data.speedKmh >= decelStartSpeed) {
                    _state.value = MeasureState.READY
                }
            }

            MeasureState.READY -> {
                // 아직 목표 속도 미만이면 IDLE로 복귀
                if (data.speedKmh < decelStartSpeed - 5.0) {
                    // 5km/h 여유를 두어 경계에서 떨리는 것 방지
                    _state.value = MeasureState.IDLE
                    return
                }
                // 속도가 decelStartSpeed 아래로 내려가면 (브레이크 시작) -> MEASURING
                if (data.speedKmh < decelStartSpeed) {
                    _state.value = MeasureState.MEASURING
                    startTime = System.currentTimeMillis()
                    peakSpeed = data.speedKmh
                    speedLog.clear()
                    splits.clear()
                    trackPoints.clear()
                    distanceCheckpoints.clear()
                    recordedSplits.clear()
                    recordedCheckpoints.clear()
                    currentDistance = 0.0
                    _liveDistance.value = 0.0
                    speedLog.add(SpeedPoint(0, data.speedKmh))
                    if (data.latitude != 0.0 || data.longitude != 0.0) {
                        trackPoints.add(TrackPoint(0, data.latitude, data.longitude, data.speedKmh))
                    }
                }
            }

            MeasureState.MEASURING -> {
                val elapsed = System.currentTimeMillis() - startTime
                speedLog.add(SpeedPoint(elapsed, data.speedKmh))

                if (data.latitude != 0.0 || data.longitude != 0.0) {
                    trackPoints.add(TrackPoint(elapsed, data.latitude, data.longitude, data.speedKmh))
                }

                if (data.speedKmh > peakSpeed) {
                    peakSpeed = data.speedKmh
                }

                // 실시간 거리 계산
                if (speedLog.size >= 2) {
                    val prev = speedLog[speedLog.size - 2]
                    val curr = speedLog.last()
                    val dtSec = (curr.timeMs - prev.timeMs) / 1000.0
                    val avgSpeedMs = (prev.speedKmh + curr.speedKmh) / 2.0 / 3.6
                    currentDistance += avgSpeedMs * dtSec
                    _liveDistance.value = currentDistance
                }

                // 감속 구간 기록: 역방향 (decelStartSpeed에서 내려가며 10km/h 단위)
                val decelSplitSpeeds = SPLIT_SPEEDS.filter { it < decelStartSpeed }.reversed()
                for (splitSpeed in decelSplitSpeeds) {
                    if (splitSpeed !in recordedSplits && data.speedKmh <= splitSpeed) {
                        val exactTime = interpolateDecelTargetTime(splitSpeed)
                        val isMajor = splitSpeed in MAJOR_SPLITS
                        splits.add(SplitTime(splitSpeed, exactTime, currentDistance, isMajor))
                        recordedSplits.add(splitSpeed)
                        _liveSplits.value = splits.toList()
                    }
                }

                // 거리 기반 체크포인트
                for ((checkDist, label) in DISTANCE_CHECKPOINTS) {
                    if (checkDist !in recordedCheckpoints && currentDistance >= checkDist) {
                        val exactTime = interpolateDistanceTime(checkDist)
                        distanceCheckpoints.add(
                            DistanceCheckpoint(checkDist, label, exactTime, data.speedKmh)
                        )
                        recordedCheckpoints.add(checkDist)
                        _liveDistanceCheckpoints.value = distanceCheckpoints.toList()
                    }
                }

                // 완전 정지 -> 완료
                if (data.speedKmh <= STANDSTILL_THRESHOLD) {
                    val logList = speedLog.toList()
                    val tpList = trackPoints.toList()
                    _result.value = MeasureResult(
                        targetSpeed = decelStartSpeed,
                        elapsedMs = elapsed,
                        peakSpeed = peakSpeed,
                        speedLog = logList,
                        splits = splits.toList(),
                        trackPoints = tpList,
                        distanceBySpeed = calcDistanceBySpeed(logList),
                        distanceByGps = calcDistanceByGps(tpList),
                        distanceCheckpoints = distanceCheckpoints.toList(),
                        measureMode = MeasureMode.DECELERATION
                    )
                    _state.value = MeasureState.FINISHED
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
     * 감속 모드: 속도가 target 이하로 떨어지는 시점을 선형 보간
     */
    private fun interpolateDecelTargetTime(target: Double): Long {
        if (speedLog.size < 2) return speedLog.lastOrNull()?.timeMs ?: 0

        val last = speedLog.last()
        val prev = speedLog[speedLog.size - 2]

        if (last.speedKmh == prev.speedKmh) return last.timeMs

        // 감속: prev.speed > target >= last.speed
        val ratio = (prev.speedKmh - target) / (prev.speedKmh - last.speedKmh)
        val interpolatedTime = prev.timeMs + (ratio * (last.timeMs - prev.timeMs)).toLong()
        return interpolatedTime
    }

    /**
     * 거리 기반 체크포인트 도달 시점 보간
     * 마지막 두 포인트의 거리 증분에서 정확한 시점 추정
     */
    private fun interpolateDistanceTime(targetDist: Double): Long {
        if (speedLog.size < 2) return speedLog.lastOrNull()?.timeMs ?: 0

        val last = speedLog.last()
        val prev = speedLog[speedLog.size - 2]

        val dtSec = (last.timeMs - prev.timeMs) / 1000.0
        val segmentDist = (prev.speedKmh + last.speedKmh) / 2.0 / 3.6 * dtSec
        val distBefore = currentDistance - segmentDist

        if (segmentDist <= 0) return last.timeMs

        val ratio = (targetDist - distBefore) / segmentDist
        return prev.timeMs + (ratio * (last.timeMs - prev.timeMs)).toLong()
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

    /**
     * 방법 A: 속도-시간 적분 (사다리꼴) -> 거리(m)
     */
    private fun calcDistanceBySpeed(log: List<SpeedPoint>): Double {
        if (log.size < 2) return 0.0
        var distance = 0.0
        for (i in 0 until log.size - 1) {
            val sp1 = log[i]
            val sp2 = log[i + 1]
            val dtSec = (sp2.timeMs - sp1.timeMs) / 1000.0
            // km/h -> m/s : /3.6
            distance += (sp1.speedKmh + sp2.speedKmh) / 2.0 / 3.6 * dtSec
        }
        return distance
    }

    /**
     * 방법 B: GPS 좌표 거리 (Haversine 합산) -> 거리(m)
     */
    private fun calcDistanceByGps(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var distance = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            distance += haversineDistance(p1.lat, p1.lon, p2.lat, p2.lon)
        }
        return distance
    }
}

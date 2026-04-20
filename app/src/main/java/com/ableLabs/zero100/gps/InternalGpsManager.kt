package com.ableLabs.zero100.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 내장 GPS (FusedLocationProvider) 매니저
 * - FusedLocation으로 위치/속도 수신
 * - GnssStatus 콜백으로 위성 수 수신
 */
class InternalGpsManager(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _gpsData = MutableSharedFlow<GpsData>(extraBufferCapacity = 50)
    val gpsData: SharedFlow<GpsData> = _gpsData

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var locationCallback: LocationCallback? = null
    private var gnssCallback: GnssStatus.Callback? = null
    private var satelliteCount: Int = 0

    @SuppressLint("MissingPermission")
    fun start() {
        if (_isRunning.value) return

        // 위치 요청: 고정밀, 500ms 간격 (가능한 최대 속도)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
            .setMinUpdateIntervalMillis(200L)
            .setMaxUpdateDelayMillis(1000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val speedKmh = if (location.hasSpeed()) {
                    location.speed.toDouble() * 3.6
                } else {
                    0.0
                }

                val data = GpsData(
                    speedKmh = speedKmh,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    satellites = satelliteCount,
                    fixQuality = if (location.hasAccuracy()) 1 else 0,
                    hdop = if (location.hasAccuracy()) {
                        (location.accuracy / 5.0).coerceIn(0.5, 99.9)
                    } else 99.9,
                    isValid = location.hasAccuracy() // 정확도 정보가 있으면 유효
                )
                _gpsData.tryEmit(data)
            }
        }

        // GNSS 위성 수 콜백
        gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var usedCount = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) usedCount++
                }
                satelliteCount = usedCount
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            locationManager.registerGnssStatusCallback(gnssCallback!!, android.os.Handler(Looper.getMainLooper()))
            _isRunning.value = true
        } catch (e: SecurityException) {
            _isRunning.value = false
        }
    }

    fun stop() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        gnssCallback?.let { locationManager.unregisterGnssStatusCallback(it) }
        locationCallback = null
        gnssCallback = null
        _isRunning.value = false
    }
}

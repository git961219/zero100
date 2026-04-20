package com.ableLabs.zero100.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ableLabs.zero100.ui.theme.LocalZero100Colors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ableLabs.zero100.measurement.SplitTime
import com.ableLabs.zero100.measurement.TrackPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * 측정 궤적을 지도 위에 표시하는 Composable.
 * - CartoDB Dark Matter 타일 (다크 테마)
 * - 속도별 색상으로 Polyline 그리기
 * - 구간 도달 지점에 마커 표시
 */
@Composable
fun MapResultView(
    trackPoints: List<TrackPoint>,
    splits: List<SplitTime>,
    modifier: Modifier = Modifier
) {
    val c = LocalZero100Colors.current
    val context = LocalContext.current
    // 테마 감지: 배경 red 채널로 판단 (다크면 작은 값)
    val isDark = c.background.red < 0.5f

    remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
        }
    }

    val mapView = remember(isDark) {
        MapView(context).apply {
            val tileSource = if (isDark) {
                XYTileSource(
                    "CartoDB-DarkMatter",
                    0, 19, 256, ".png",
                    arrayOf(
                        "https://a.basemaps.cartocdn.com/rastertiles/dark_all/",
                        "https://b.basemaps.cartocdn.com/rastertiles/dark_all/",
                        "https://c.basemaps.cartocdn.com/rastertiles/dark_all/"
                    )
                )
            } else {
                XYTileSource(
                    "CartoDB-Voyager",
                    0, 19, 256, ".png",
                    arrayOf(
                        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
                        "https://c.basemaps.cartocdn.com/rastertiles/voyager/"
                    )
                )
            }
            setTileSource(tileSource)
            setMultiTouchControls(true)
            zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .border(
                width = 1.dp,
                color = c.info.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        update = { map ->
            map.overlays.clear()

            if (trackPoints.size < 2) {
                map.controller.setZoom(15.0)
                if (trackPoints.isNotEmpty()) {
                    map.controller.setCenter(
                        GeoPoint(trackPoints[0].lat, trackPoints[0].lon)
                    )
                }
                return@AndroidView
            }

            for (i in 0 until trackPoints.size - 1) {
                val pt1 = trackPoints[i]
                val pt2 = trackPoints[i + 1]

                val segment = Polyline().apply {
                    addPoint(GeoPoint(pt1.lat, pt1.lon))
                    addPoint(GeoPoint(pt2.lat, pt2.lon))
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.color = speedToColor(pt2.speedKmh)
                    setOnClickListener { _, _, _ -> false }
                }
                map.overlays.add(segment)
            }

            for (split in splits) {
                val closest = trackPoints.minByOrNull {
                    kotlin.math.abs(it.timeMs - split.elapsedMs)
                } ?: continue

                if (closest.lat == 0.0 && closest.lon == 0.0) continue

                val marker = Marker(map).apply {
                    position = GeoPoint(closest.lat, closest.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "${split.speedKmh.toInt()}km/h"
                    snippet = "%.2f초".format(split.elapsedSeconds)
                }
                map.overlays.add(marker)
            }

            val lats = trackPoints.map { it.lat }
            val lons = trackPoints.map { it.lon }
            val boundingBox = BoundingBox(
                lats.max(), lons.max(),
                lats.min(), lons.min()
            )
            map.post {
                map.zoomToBoundingBox(boundingBox, true, 80)
            }
        }
    )
}

private fun speedToColor(speedKmh: Double): Int {
    return when {
        speedKmh < 30  -> AndroidColor.parseColor("#2196F3")
        speedKmh < 60  -> AndroidColor.parseColor("#00BCD4")
        speedKmh < 100 -> AndroidColor.parseColor("#4CAF50")
        speedKmh < 150 -> AndroidColor.parseColor("#FFEB3B")
        speedKmh < 200 -> AndroidColor.parseColor("#FF9800")
        else           -> AndroidColor.parseColor("#F44336")
    }
}

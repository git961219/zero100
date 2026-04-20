package com.ableLabs.zero100.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ableLabs.zero100.R
import com.ableLabs.zero100.gps.ConnectionState
import com.ableLabs.zero100.ui.theme.*
import com.ableLabs.zero100.measurement.MeasureMode
import com.ableLabs.zero100.update.UpdateInfo
import com.ableLabs.zero100.viewmodel.GpsSource
import com.ableLabs.zero100.viewmodel.GpsStage
import com.ableLabs.zero100.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToMeasure: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val c = LocalZero100Colors.current
    val connectionState by viewModel.connectionState.collectAsState()
    val latestGps by viewModel.latestGps.collectAsState()
    val currentSpeed by viewModel.currentSpeed.collectAsState()
    val rawRate by viewModel.rawRate.collectAsState()
    val bestRecord by viewModel.bestRecord.collectAsState()
    val gpsStage by viewModel.gpsStage.collectAsState()
    val targetSpeed by viewModel.targetSpeedSetting.collectAsState()
    val gpsSource by viewModel.gpsSource.collectAsState()
    val measureMode by viewModel.measureMode.collectAsState()
    val decelStartSpeed by viewModel.decelStartSpeed.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()

    val isReady = gpsStage == GpsStage.READY || gpsStage == GpsStage.INTERNAL_READY
    val isGpsConnected = connectionState == ConnectionState.CONNECTED
    val hasAnyGps = isGpsConnected || gpsStage == GpsStage.INTERNAL_READY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        GpsSignalBar(connectionState, latestGps.satellites, latestGps.hdop, latestGps.fixQuality)

        Spacer(modifier = Modifier.height(4.dp))

        GpsSourceBadge(gpsSource, rawRate)

        Spacer(modifier = Modifier.height(8.dp))

        // OTA 업데이트 배너
        updateInfo?.let { info ->
            UpdateBanner(
                info = info,
                progress = updateProgress,
                onDownload = { viewModel.downloadUpdate() },
                onDismiss = { viewModel.dismissUpdate() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        GpsReadyMessage(gpsStage)

        Spacer(modifier = Modifier.height(24.dp))

        if (hasAnyGps) {
            SpeedDisplay(currentSpeed)

            Spacer(modifier = Modifier.height(16.dp))

            if (isGpsConnected) {
                GpsQualityCard(latestGps.hdop, latestGps.satellites, latestGps.fixQuality)
            }

            if (gpsSource == GpsSource.INTERNAL && !isGpsConnected) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.warning.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.external_gps_hint),
                        modifier = Modifier.padding(12.dp),
                        color = c.warning,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            bestRecord?.let { record ->
                RecordCard(record.displayTime, targetSpeed)
            }

            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Spacer(modifier = Modifier.height(48.dp))

            Icon(
                Icons.Filled.Usb,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.gps_signal_waiting),
                color = c.textTertiary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        // 가속/감속 모드 토글
        MeasureModeTabs(
            currentMode = measureMode,
            onModeChange = { viewModel.setMeasureMode(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 모드별 안내 텍스트
        val modeLabel = when (measureMode) {
            MeasureMode.ACCELERATION -> "0-${targetSpeed} km/h"
            MeasureMode.DECELERATION -> "${decelStartSpeed}-0 km/h"
            MeasureMode.COMBINED -> "0-${targetSpeed}-0 km/h"
        }
        Text(
            modeLabel,
            color = c.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateToMeasure,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            enabled = isReady,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when (measureMode) {
                    MeasureMode.DECELERATION -> c.danger
                    else -> c.accent
                },
                disabledContainerColor = when (measureMode) {
                    MeasureMode.DECELERATION -> c.danger.copy(alpha = 0.3f)
                    else -> c.accent.copy(alpha = 0.3f)
                }
            )
        ) {
            Icon(
                if (isReady) Icons.Filled.Speed else Icons.Filled.HourglassTop,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                if (isReady) {
                    when (measureMode) {
                        MeasureMode.ACCELERATION -> stringResource(R.string.start_measure)
                        MeasureMode.DECELERATION -> stringResource(R.string.start_braking_test)
                        MeasureMode.COMBINED -> stringResource(R.string.start_combined_test)
                    }
                } else stringResource(R.string.preparing),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onNavigateToHistory) {
                Icon(Icons.Filled.History, contentDescription = null, tint = c.textSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.history), color = c.textSecondary)
            }
            TextButton(onClick = onNavigateToSettings) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = c.textSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.settings), color = c.textSecondary)
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 컴포넌트
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun MeasureModeTabs(
    currentMode: MeasureMode,
    onModeChange: (MeasureMode) -> Unit
) {
    val c = LocalZero100Colors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val isAccel = currentMode == MeasureMode.ACCELERATION
        val isDecel = currentMode == MeasureMode.DECELERATION
        val isCombined = currentMode == MeasureMode.COMBINED
        FilterChip(
            selected = isAccel,
            onClick = { onModeChange(MeasureMode.ACCELERATION) },
            label = {
                Text(
                    stringResource(R.string.mode_accel),
                    fontWeight = if (isAccel) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = c.accent,
                selectedLabelColor = c.textPrimary,
                selectedLeadingIconColor = c.textPrimary,
                containerColor = c.card,
                labelColor = c.textSecondary,
                iconColor = c.textSecondary
            ),
            shape = RoundedCornerShape(12.dp)
        )
        FilterChip(
            selected = isDecel,
            onClick = { onModeChange(MeasureMode.DECELERATION) },
            label = {
                Text(
                    stringResource(R.string.mode_decel),
                    fontWeight = if (isDecel) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.TrendingDown, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = c.danger,
                selectedLabelColor = c.textPrimary,
                selectedLeadingIconColor = c.textPrimary,
                containerColor = c.card,
                labelColor = c.textSecondary,
                iconColor = c.textSecondary
            ),
            shape = RoundedCornerShape(12.dp)
        )
        FilterChip(
            selected = isCombined,
            onClick = { onModeChange(MeasureMode.COMBINED) },
            label = {
                Text(
                    stringResource(R.string.mode_combined),
                    fontWeight = if (isCombined) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = c.accent,
                selectedLabelColor = c.textPrimary,
                selectedLeadingIconColor = c.textPrimary,
                containerColor = c.card,
                labelColor = c.textSecondary,
                iconColor = c.textSecondary
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun GpsSignalBar(state: ConnectionState, satellites: Int, hdop: Double, fix: Int) {
    val c = LocalZero100Colors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            ConnectionState.CONNECTED -> c.success
                            ConnectionState.CONNECTING -> c.warning
                            ConnectionState.ERROR -> c.danger
                            else -> c.textTertiary
                        }
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                when (state) {
                    ConnectionState.CONNECTED -> "GPS"
                    ConnectionState.CONNECTING -> stringResource(R.string.connecting)
                    ConnectionState.ERROR -> stringResource(R.string.error)
                    else -> stringResource(R.string.disconnected)
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }

        if (state == ConnectionState.CONNECTED) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val level = when {
                    satellites >= 12 && hdop < 2.0 -> 5
                    satellites >= 10 -> 4
                    satellites >= 7 -> 3
                    satellites >= 4 -> 2
                    satellites >= 1 -> 1
                    else -> 0
                }
                for (i in 1..5) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height((6 + i * 3).dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (i <= level) c.success else c.textTertiary.copy(alpha = 0.3f)
                            )
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Filled.SatelliteAlt,
                    contentDescription = null,
                    tint = if (satellites > 0) c.textSecondary else c.textTertiary.copy(alpha = 0.3f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun GpsReadyMessage(stage: GpsStage) {
    val c = LocalZero100Colors.current
    val message = when (stage) {
        GpsStage.DISCONNECTED -> stringResource(R.string.gps_waiting)
        GpsStage.CONNECTED -> stringResource(R.string.satellite_waiting)
        GpsStage.SATELLITES -> stringResource(R.string.satellite_receiving)
        GpsStage.UPGRADING -> stringResource(R.string.upgrading_mode)
        GpsStage.STABILIZING -> stringResource(R.string.signal_stabilizing)
        GpsStage.READY -> stringResource(R.string.ready_high_performance)
        GpsStage.INTERNAL_READY -> stringResource(R.string.ready_internal_gps)
    }
    val color = when (stage) {
        GpsStage.DISCONNECTED -> c.textTertiary
        GpsStage.CONNECTED -> c.textSecondary
        GpsStage.SATELLITES, GpsStage.UPGRADING, GpsStage.STABILIZING -> c.warning
        GpsStage.READY, GpsStage.INTERNAL_READY -> c.success
    }

    val alpha = if (stage != GpsStage.READY && stage != GpsStage.INTERNAL_READY && stage != GpsStage.DISCONNECTED) {
        val transition = rememberInfiniteTransition(label = "msgPulse")
        transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "msgAlpha"
        ).value
    } else 1f

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (stage == GpsStage.READY || stage == GpsStage.INTERNAL_READY) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = c.success,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                message,
                color = color.copy(alpha = alpha),
                fontSize = 14.sp,
                fontWeight = if (stage == GpsStage.READY || stage == GpsStage.INTERNAL_READY) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun SpeedDisplay(speed: Double) {
    val c = LocalZero100Colors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = String.format("%.0f", speed),
            style = SpeedDisplayStyle,
            color = c.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "km/h",
            style = SpeedUnitStyle.copy(color = c.textSecondary),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GpsQualityCard(hdop: Double, satellites: Int, fixQuality: Int) {
    val c = LocalZero100Colors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QualityChip(
                label = stringResource(R.string.accuracy),
                value = when {
                    hdop < 1.0 -> stringResource(R.string.accuracy_best)
                    hdop < 1.5 -> stringResource(R.string.accuracy_good)
                    hdop < 2.5 -> stringResource(R.string.accuracy_normal)
                    hdop < 5.0 -> stringResource(R.string.accuracy_low)
                    else -> stringResource(R.string.accuracy_bad)
                },
                color = when {
                    hdop < 1.5 -> c.success
                    hdop < 2.5 -> c.warning
                    else -> c.danger
                }
            )

            QualityChip(
                label = stringResource(R.string.reception),
                value = when (fixQuality) {
                    2 -> stringResource(R.string.reception_precise)
                    1 -> stringResource(R.string.reception_good)
                    4 -> stringResource(R.string.reception_ultra)
                    5 -> stringResource(R.string.reception_correcting)
                    else -> stringResource(R.string.reception_none)
                },
                color = if (fixQuality > 0) c.success else c.danger
            )

            QualityChip(
                label = stringResource(R.string.satellites),
                value = stringResource(R.string.satellites_count, satellites),
                color = when {
                    satellites >= 10 -> c.success
                    satellites >= 5 -> c.warning
                    else -> c.danger
                }
            )
        }
    }
}

@Composable
private fun QualityChip(label: String, value: String, color: Color) {
    val c = LocalZero100Colors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = c.textSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun GpsSourceBadge(source: GpsSource, rawRate: Int) {
    val c = LocalZero100Colors.current
    val (label, badgeColor) = when (source) {
        GpsSource.EXTERNAL -> stringResource(R.string.external_gps_label, rawRate) to c.info
        GpsSource.INTERNAL -> stringResource(R.string.internal_gps) to c.textSecondary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = badgeColor.copy(alpha = 0.15f)
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = badgeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun UpdateBanner(
    info: UpdateInfo,
    progress: Int,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalZero100Colors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.accent.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.update_available, info.versionName),
                color = c.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (info.releaseNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    info.releaseNotes.take(100),
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (progress >= 0) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = c.accent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.update_downloading, progress),
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.update_download), fontSize = 13.sp)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_later), color = c.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(displayTime: String, targetSpeed: Int) {
    val c = LocalZero100Colors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = c.warning,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.best_record_label), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Text(
                    displayTime,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = Rajdhani
                    ),
                    fontWeight = FontWeight.Bold,
                    color = c.info
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "0-${targetSpeed}",
                color = c.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

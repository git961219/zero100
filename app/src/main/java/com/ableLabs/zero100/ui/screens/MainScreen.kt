package com.ableLabs.zero100.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ableLabs.zero100.gps.ConnectionState
import com.ableLabs.zero100.ui.theme.*
import com.ableLabs.zero100.viewmodel.GpsStage
import com.ableLabs.zero100.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToMeasure: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val latestGps by viewModel.latestGps.collectAsState()
    val currentSpeed by viewModel.currentSpeed.collectAsState()
    val rawRate by viewModel.rawRate.collectAsState()
    val bestRecord by viewModel.bestRecord.collectAsState()
    val gpsStage by viewModel.gpsStage.collectAsState()
    val targetSpeed by viewModel.targetSpeedSetting.collectAsState()

    val isReady = gpsStage == GpsStage.READY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── 상단: GPS 신호 상태 (사용자 친화적) ──
        GpsSignalBar(connectionState, latestGps.satellites, latestGps.hdop, latestGps.fixQuality)

        Spacer(modifier = Modifier.height(12.dp))

        // ── GPS 준비 상태 메시지 (단계 대신 한 줄 메시지) ──
        GpsReadyMessage(gpsStage)

        Spacer(modifier = Modifier.height(32.dp))

        // ── 속도 표시 (메인) ──
        SpeedDisplay(currentSpeed)

        Spacer(modifier = Modifier.height(12.dp))

        // ── GPS 신호 품질 (간략, 아이콘 기반) ──
        if (connectionState == ConnectionState.CONNECTED) {
            GpsQualityRow(latestGps.hdop, latestGps.satellites, latestGps.fixQuality)
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── 최고 기록 ──
        bestRecord?.let { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = RacingYellow,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("최고 기록", style = MaterialTheme.typography.labelMedium, color = SpeedGray)
                        Text(
                            record.displayTime,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = RacingYellow
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "0-${targetSpeed}",
                        color = SpeedGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 메인 버튼 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // GPS 연결
            OutlinedButton(
                onClick = {
                    if (connectionState == ConnectionState.CONNECTED) viewModel.disconnectGps()
                    else viewModel.connectGps()
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = when (connectionState) {
                        ConnectionState.CONNECTED -> RacingGreen
                        ConnectionState.ERROR -> RacingRed
                        else -> SpeedWhite
                    }
                )
            ) {
                Icon(
                    when (connectionState) {
                        ConnectionState.CONNECTED -> Icons.Filled.UsbOff
                        ConnectionState.CONNECTING -> Icons.Filled.Sync
                        else -> Icons.Filled.Usb
                    },
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (connectionState) {
                        ConnectionState.CONNECTED -> "연결됨"
                        ConnectionState.CONNECTING -> "연결 중..."
                        ConnectionState.ERROR -> "오류"
                        else -> "GPS 연결"
                    }
                )
            }

            // 측정 시작
            Button(
                onClick = onNavigateToMeasure,
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = isReady,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RacingRed,
                    disabledContainerColor = RacingRed.copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    if (isReady) Icons.Filled.Speed else Icons.Filled.HourglassTop,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isReady) "측정 시작" else "준비 중...",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 하단 링크
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onNavigateToHistory) {
                Icon(Icons.Filled.History, contentDescription = null, tint = SpeedGray)
                Spacer(modifier = Modifier.width(4.dp))
                Text("기록", color = SpeedGray)
            }
            TextButton(onClick = onNavigateToSettings) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = SpeedGray)
                Spacer(modifier = Modifier.width(4.dp))
                Text("설정", color = SpeedGray)
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 사용자 친화적 컴포넌트
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * GPS 신호 바 — 상단에 한 줄로 표시
 * "u-blox M9" | 신호 강도 바 | 위성 아이콘
 */
@Composable
private fun GpsSignalBar(state: ConnectionState, satellites: Int, hdop: Double, fix: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 연결 상태
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            ConnectionState.CONNECTED -> RacingGreen
                            ConnectionState.CONNECTING -> RacingYellow
                            ConnectionState.ERROR -> RacingRed
                            else -> SpeedGray
                        }
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("GPS", style = MaterialTheme.typography.bodySmall, color = SpeedGray)
        }

        // 신호 강도 바 (위성 수 기반, 5단계)
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
                                if (i <= level) RacingGreen else SpeedGray.copy(alpha = 0.2f)
                            )
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Filled.SatelliteAlt,
                    contentDescription = null,
                    tint = if (satellites > 0) SpeedGray else SpeedGray.copy(alpha = 0.3f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * GPS 준비 상태 메시지 — 기술 단계 대신 사람 말로
 */
@Composable
private fun GpsReadyMessage(stage: GpsStage) {
    val (message, color) = when (stage) {
        GpsStage.DISCONNECTED -> "GPS 모듈을 연결해주세요" to SpeedGray
        GpsStage.CONNECTED -> "위성 수신 대기 중..." to SpeedGray
        GpsStage.SATELLITES -> "위성 수신 중, 잠시만 기다려주세요" to RacingYellow
        GpsStage.UPGRADING -> "고속 모드로 전환 중..." to RacingYellow
        GpsStage.STABILIZING -> "신호 안정화 중..." to RacingYellow
        GpsStage.READY -> "측정 준비 완료!" to RacingGreen
    }

    // 준비 중일 때 깜빡임
    val alpha = if (stage != GpsStage.READY && stage != GpsStage.DISCONNECTED) {
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
            if (stage == GpsStage.READY) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = RacingGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                message,
                color = color.copy(alpha = alpha),
                fontSize = 14.sp,
                fontWeight = if (stage == GpsStage.READY) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * GPS 품질 — HDOP/Fix/위성을 사용자 언어로
 */
@Composable
private fun GpsQualityRow(hdop: Double, satellites: Int, fixQuality: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // GPS 정밀도 (HDOP를 좋음/보통/나쁨으로)
        QualityChip(
            label = "정밀도",
            value = when {
                hdop < 1.0 -> "최상"
                hdop < 1.5 -> "좋음"
                hdop < 2.5 -> "보통"
                hdop < 5.0 -> "낮음"
                else -> "나쁨"
            },
            color = when {
                hdop < 1.5 -> RacingGreen
                hdop < 2.5 -> RacingYellow
                else -> RacingRed
            }
        )

        // 수신 상태
        QualityChip(
            label = "수신",
            value = when (fixQuality) {
                2 -> "정밀"
                1 -> "양호"
                4 -> "초정밀"
                5 -> "보정중"
                else -> "없음"
            },
            color = if (fixQuality > 0) RacingGreen else RacingRed
        )

        // 위성 (숫자 + 바)
        QualityChip(
            label = "위성",
            value = "${satellites}개",
            color = when {
                satellites >= 10 -> RacingGreen
                satellites >= 5 -> RacingYellow
                else -> RacingRed
            }
        )
    }
}

@Composable
private fun QualityChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = SpeedGray)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SpeedDisplay(speed: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = String.format("%.0f", speed),
            style = SpeedDisplayStyle,
            color = SpeedWhite,
            textAlign = TextAlign.Center
        )
        Text(text = "km/h", style = SpeedUnitStyle, textAlign = TextAlign.Center)
    }
}

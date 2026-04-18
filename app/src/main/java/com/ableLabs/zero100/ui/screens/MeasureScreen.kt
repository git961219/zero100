package com.ableLabs.zero100.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ableLabs.zero100.measurement.MeasureState
import com.ableLabs.zero100.measurement.SplitTime
import com.ableLabs.zero100.ui.theme.*
import com.ableLabs.zero100.viewmodel.MainViewModel
import kotlinx.coroutines.delay

// --- 진동 헬퍼 ---

private fun getVibrator(context: Context): Vibrator {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}

private fun vibrateShort(context: Context) {
    val vibrator = getVibrator(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(50)
    }
}

private fun vibrateLong(context: Context) {
    val vibrator = getVibrator(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(300)
    }
}

// --- 화면 밝기 헬퍼 ---

private fun setScreenBrightnessMax(context: Context): Float {
    val activity = context as? Activity ?: return -1f
    val lp = activity.window.attributes
    val original = lp.screenBrightness
    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
    activity.window.attributes = lp
    return original
}

private fun restoreScreenBrightness(context: Context, original: Float) {
    val activity = context as? Activity ?: return
    val lp = activity.window.attributes
    lp.screenBrightness = original
    activity.window.attributes = lp
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeasureScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val measureState by viewModel.measureState.collectAsState()
    val currentSpeed by viewModel.currentSpeed.collectAsState()
    val result by viewModel.result.collectAsState()
    val liveSplits by viewModel.engine.liveSplits.collectAsState()
    val targetSpeed by viewModel.targetSpeedSetting.collectAsState()

    var displayElapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(measureState) {
        if (measureState == MeasureState.MEASURING) {
            while (true) {
                displayElapsed = viewModel.engine.getElapsedMs()
                delay(16)
            }
        }
    }

    // --- 화면 밝기: 진입 시 최대, 나가면 복원 ---
    DisposableEffect(Unit) {
        val originalBrightness = setScreenBrightnessMax(context)
        onDispose {
            restoreScreenBrightness(context, originalBrightness)
        }
    }

    // --- 진동 피드백: 상태 변경 감지 ---
    var prevState by remember { mutableStateOf<MeasureState?>(null) }
    LaunchedEffect(measureState) {
        if (prevState != null) {
            when (measureState) {
                MeasureState.MEASURING -> vibrateShort(context) // 출발 감지
                MeasureState.FINISHED -> vibrateLong(context)   // 측정 완료
                else -> {}
            }
        }
        prevState = measureState
    }

    // --- 진동 피드백: 구간 랩 기록 시 ---
    var prevSplitCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(liveSplits) {
        if (liveSplits.size > prevSplitCount && prevSplitCount > 0) {
            vibrateShort(context)
        }
        prevSplitCount = liveSplits.size
    }

    LaunchedEffect(Unit) {
        viewModel.startMeasurement()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 바
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = SpeedWhite)
            }
            Text("0-${targetSpeed} km/h", style = MaterialTheme.typography.titleMedium, color = SpeedWhite)
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        StatusBadge(measureState)
        Spacer(modifier = Modifier.height(24.dp))

        when (measureState) {
            MeasureState.FINISHED -> {
                // 결과 화면 — 스크롤 가능
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    result?.let { r ->
                        // 결과 카드
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = String.format("%.2f", r.elapsedSeconds),
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-2).sp,
                                    color = RacingGreen
                                )
                                Text("초", style = SpeedUnitStyle)

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "최고 속도: ${String.format("%.1f", r.peakSpeed)} km/h",
                                    color = SpeedGray,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 구간 랩타임
                        if (r.splits.isNotEmpty()) {
                            AnimatedSplitTimesDisplay(r.splits)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 속도 그래프
                        SpeedGraph(
                            points = r.speedLog.map { it.timeMs to it.speedKmh },
                            targetSpeed = r.targetSpeed,
                            splits = r.splits,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )
                    }
                }
            }

            MeasureState.MEASURING -> {
                // 타이머 — 더 크게
                Text(
                    text = String.format("%.2f", displayElapsed / 1000.0),
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = RacingYellow
                )
                Text("초", style = SpeedUnitStyle)

                Spacer(modifier = Modifier.height(24.dp))

                // 현재 속도 — 보조 표시
                Text(
                    text = String.format("%.0f", currentSpeed),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpeedWhite.copy(alpha = 0.7f)
                )
                Text("km/h", color = SpeedGray, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(16.dp))

                // 실시간 구간 랩타임 (애니메이션 추가)
                if (liveSplits.isNotEmpty()) {
                    AnimatedSplitTimesDisplay(liveSplits)
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            else -> {
                // 대기/준비 상태
                Text(
                    text = "0.00",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = SpeedGray
                )
                Text("초", style = SpeedUnitStyle)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = String.format("%.0f", currentSpeed),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpeedWhite
                )
                Text("km/h", color = SpeedGray, fontSize = 14.sp)

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // 하단 버튼
        when (measureState) {
            MeasureState.MEASURING -> {
                // "측정 종료" — 길게 누르기로 변경 (실수 방지)
                var isLongPressing by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .combinedClickable(
                            onClick = { /* 짧게 누르면 무시 */ },
                            onLongClick = {
                                viewModel.finishMeasurement()
                            }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = RacingYellow.copy(alpha = 0.8f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, tint = DarkBg)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "길게 눌러 종료",
                            color = DarkBg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            MeasureState.FINISHED -> {
                // 공유 버튼
                result?.let { r ->
                    OutlinedButton(
                        onClick = { shareResult(context, r.elapsedSeconds, r.peakSpeed, r.splits) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("결과 공유")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("돌아가기") }
                    Button(
                        onClick = { viewModel.startMeasurement() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RacingRed)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("다시 측정")
                    }
                }
            }

            else -> {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                ) { Text("취소") }
            }
        }
    }
}

/**
 * 측정 결과를 텍스트로 공유
 */
private fun shareResult(
    context: Context,
    elapsedSeconds: Double,
    peakSpeed: Double,
    splits: List<SplitTime>
) {
    val sb = StringBuilder("Zero100")
    for (split in splits) {
        sb.append(" | 0-${split.speedKmh.toInt()}km/h: ${String.format("%.2f", split.elapsedSeconds)}초")
    }
    sb.append(" | 최고속도: ${String.format("%.1f", peakSpeed)}km/h")

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "결과 공유"))
}

/**
 * 구간 랩타임 표시 — 새 항목이 추가될 때 슬라이드인 애니메이션
 */
@Composable
private fun AnimatedSplitTimesDisplay(splits: List<SplitTime>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            splits.forEachIndexed { index, split ->
                // 각 항목이 나타날 때 애니메이션
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = index * 50
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = index * 50
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "0-${split.speedKmh.toInt()} km/h",
                            color = SpeedGray,
                            fontSize = 14.sp
                        )
                        Text(
                            String.format("%.2f초", split.elapsedSeconds),
                            color = when {
                                split.speedKmh <= 100.0 -> RacingGreen
                                split.speedKmh <= 150.0 -> RacingYellow
                                else -> RacingRed
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(state: MeasureState) {
    val (text, color, bgColor) = when (state) {
        MeasureState.IDLE -> Triple("정차하세요", SpeedGray, DarkCard)
        MeasureState.READY -> Triple("출발하세요!", RacingGreen, Color(0xFF1B5E20))
        MeasureState.MEASURING -> Triple("측정 중...", RacingYellow, Color(0xFF4E3800))
        MeasureState.FINISHED -> Triple("완료!", RacingGreen, Color(0xFF1B5E20))
    }

    val alpha = if (state == MeasureState.READY) {
        val infiniteTransition = rememberInfiniteTransition(label = "blink")
        infiniteTransition.animateFloat(
            initialValue = 0.5f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
            label = "blinkAlpha"
        ).value
    } else 1f

    Surface(shape = RoundedCornerShape(24.dp), color = bgColor.copy(alpha = alpha)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp
        )
    }
}

@Composable
private fun SpeedGraph(
    points: List<Pair<Long, Double>>,
    targetSpeed: Double,
    splits: List<SplitTime>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    val maxTime = points.maxOf { it.first }.toFloat()
    val maxSpeed = maxOf(targetSpeed, points.maxOf { it.second }).toFloat()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            val w = size.width
            val h = size.height
            val padding = 4.dp.toPx()

            // 구간 속도 라인
            for (split in splits) {
                val y = h - (split.speedKmh.toFloat() / maxSpeed * (h - padding * 2)) - padding
                drawLine(SpeedGray.copy(alpha = 0.3f), Offset(0f, y), Offset(w, y), 1f)
            }

            // 속도 곡선
            val path = Path()
            points.forEachIndexed { i, (time, speed) ->
                val x = (time.toFloat() / maxTime) * (w - padding * 2) + padding
                val y = h - (speed.toFloat() / maxSpeed * (h - padding * 2)) - padding
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, RacingGreen, style = Stroke(width = 3.dp.toPx()))

            // 구간 도달 포인트
            for (split in splits) {
                val x = (split.elapsedMs.toFloat() / maxTime) * (w - padding * 2) + padding
                val y = h - (split.speedKmh.toFloat() / maxSpeed * (h - padding * 2)) - padding
                drawCircle(RacingYellow, radius = 5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

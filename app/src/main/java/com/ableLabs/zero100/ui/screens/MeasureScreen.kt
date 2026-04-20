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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ableLabs.zero100.R
import com.ableLabs.zero100.gps.GforceData
import com.ableLabs.zero100.measurement.CombinedPhase
import com.ableLabs.zero100.measurement.DistanceCheckpoint
import com.ableLabs.zero100.measurement.MeasureMode
import com.ableLabs.zero100.measurement.MeasureState
import com.ableLabs.zero100.measurement.SplitTime
import com.ableLabs.zero100.measurement.TrackPoint
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
    val c = LocalZero100Colors.current
    val context = LocalContext.current
    val measureState by viewModel.measureState.collectAsState()
    val currentSpeed by viewModel.currentSpeed.collectAsState()
    val result by viewModel.result.collectAsState()
    val liveSplits by viewModel.engine.liveSplits.collectAsState()
    val targetSpeed by viewModel.targetSpeedSetting.collectAsState()
    val measureMode by viewModel.measureMode.collectAsState()
    val decelStartSpeed by viewModel.decelStartSpeed.collectAsState()
    val gforceData by viewModel.gforceManager.gforceData.collectAsState()
    val combinedPhase by viewModel.engine.combinedPhase.collectAsState()
    val isDecel = measureMode == MeasureMode.DECELERATION
    val isCombined = measureMode == MeasureMode.COMBINED

    var displayElapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(measureState) {
        if (measureState == MeasureState.MEASURING) {
            while (true) {
                displayElapsed = viewModel.engine.getElapsedMs()
                delay(16)
            }
        }
    }

    DisposableEffect(Unit) {
        val originalBrightness = setScreenBrightnessMax(context)
        onDispose {
            restoreScreenBrightness(context, originalBrightness)
        }
    }

    var prevState by remember { mutableStateOf<MeasureState?>(null) }
    LaunchedEffect(measureState) {
        if (prevState != null) {
            when (measureState) {
                MeasureState.MEASURING -> vibrateShort(context)
                MeasureState.FINISHED -> vibrateLong(context)
                else -> {}
            }
        }
        prevState = measureState
    }

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
            .background(c.background)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = c.textPrimary)
            }
            Text(
                when {
                    isCombined -> "0-${targetSpeed}-0 km/h"
                    isDecel -> "${decelStartSpeed}-0 km/h"
                    else -> "0-${targetSpeed} km/h"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Rajdhani),
                color = when {
                    isCombined -> c.accent
                    isDecel -> c.danger
                    else -> c.textPrimary
                }
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        StatusBadge(measureState, measureMode, if (isCombined) combinedPhase else null)
        Spacer(modifier = Modifier.height(24.dp))

        when (measureState) {
            MeasureState.FINISHED -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    result?.let { r ->
                        val resultColor = when {
                            isDecel -> c.danger
                            isCombined -> c.accent
                            else -> c.info
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = resultColor.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = c.card),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = String.format("%.2f", r.elapsedSeconds),
                                    style = TimerDisplayStyle,
                                    color = resultColor
                                )
                                Text(stringResource(R.string.seconds), style = SpeedUnitStyle.copy(color = c.textSecondary))

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isDecel) {
                                    // 감속 모드: 제동 거리 강조
                                    if (r.distanceM > 0) {
                                        Text(
                                            stringResource(R.string.braking_distance_label, String.format("%.1f", r.distanceM)),
                                            color = c.danger,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = Rajdhani
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }

                                Text(
                                    stringResource(R.string.peak_speed_label, String.format("%.1f", r.peakSpeed)),
                                    color = c.textSecondary,
                                    fontSize = 14.sp,
                                    fontFamily = Rajdhani
                                )

                                // Peak G 표시
                                if (r.peakG > 0.05f) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.peak_g_label, String.format("%.2f", r.peakG)),
                                        color = c.warning,
                                        fontSize = 14.sp,
                                        fontFamily = Rajdhani
                                    )
                                }

                                // 복합 테스트: 구간별 시간
                                if (r.measureMode == MeasureMode.COMBINED && r.accelMs > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.combined_accel), color = c.textSecondary, fontSize = 12.sp)
                                            Text(
                                                String.format("%.2fs", r.accelMs / 1000.0),
                                                color = c.info,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = Rajdhani
                                            )
                                            if (r.accelDistance > 0) {
                                                Text(
                                                    String.format("%.0fm", r.accelDistance),
                                                    color = c.textTertiary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.combined_decel), color = c.textSecondary, fontSize = 12.sp)
                                            Text(
                                                String.format("%.2fs", r.decelMs / 1000.0),
                                                color = c.danger,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = Rajdhani
                                            )
                                            if (r.decelDistance > 0) {
                                                Text(
                                                    String.format("%.0fm", r.decelDistance),
                                                    color = c.textTertiary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.combined_total), color = c.textSecondary, fontSize = 12.sp)
                                            Text(
                                                String.format("%.2fs", r.elapsedSeconds),
                                                color = c.accent,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = Rajdhani
                                            )
                                        }
                                    }
                                }

                                if (r.distanceM > 0 && !isDecel) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val distText = if (r.distanceM >= 1000) {
                                        String.format("%.2f km", r.distanceM / 1000.0)
                                    } else {
                                        String.format("%.0f m", r.distanceM)
                                    }
                                    val accText = if (r.distanceAccuracy > 0) {
                                        " (${stringResource(R.string.distance_accuracy, String.format("%.1f", r.distanceAccuracy))})"
                                    } else ""
                                    Text(
                                        stringResource(R.string.distance_label) + " $distText$accText",
                                        color = c.textSecondary,
                                        fontSize = 14.sp,
                                        fontFamily = Rajdhani
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        var selectedTab by remember { mutableIntStateOf(0) }
                        val hasTrackPoints = r.trackPoints.isNotEmpty()
                        val tabSplits = stringResource(R.string.tab_splits)
                        val tabGraph = stringResource(R.string.tab_graph)
                        val tabMap = stringResource(R.string.tab_map)
                        val tabTitles = if (hasTrackPoints) listOf(tabSplits, tabGraph, tabMap) else listOf(tabSplits, tabGraph)

                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = c.card,
                            contentColor = c.info,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            tabTitles.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(title) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when (selectedTab) {
                            0 -> {
                                SplitAndDistanceTabs(
                                    splits = r.splits,
                                    distanceCheckpoints = r.distanceCheckpoints
                                )
                            }
                            1 -> {
                                SpeedGraph(
                                    points = r.speedLog.map { it.timeMs to it.speedKmh },
                                    targetSpeed = r.targetSpeed,
                                    splits = r.splits,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )
                            }
                            2 -> {
                                if (hasTrackPoints) {
                                    MapResultView(
                                        trackPoints = r.trackPoints,
                                        splits = r.splits,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(300.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            MeasureState.MEASURING -> {
                Text(
                    text = String.format("%.2f", displayElapsed / 1000.0),
                    style = TimerDisplayStyle,
                    color = when {
                        isCombined && combinedPhase == CombinedPhase.DECEL -> c.danger
                        isCombined -> c.info
                        isDecel -> c.danger
                        else -> c.info
                    }
                )
                Text(stringResource(R.string.seconds), style = SpeedUnitStyle.copy(color = c.textSecondary))

                // 복합 모드: 현재 단계 표시
                if (isCombined) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (combinedPhase == CombinedPhase.ACCEL) c.info.copy(alpha = 0.15f) else c.danger.copy(alpha = 0.15f)
                    ) {
                        Text(
                            if (combinedPhase == CombinedPhase.ACCEL) stringResource(R.string.combined_phase_accel)
                            else stringResource(R.string.combined_phase_decel),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = if (combinedPhase == CombinedPhase.ACCEL) c.info else c.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isDecel) {
                    // 감속: 역방향 프로그레스바 (현재속도 -> 0)
                    SpeedProgressBar(
                        currentSpeed = decelStartSpeed.toDouble() - currentSpeed,
                        targetSpeed = decelStartSpeed.toDouble(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        barColor = c.danger
                    )
                } else {
                    SpeedProgressBar(
                        currentSpeed = currentSpeed,
                        targetSpeed = targetSpeed.toDouble(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = String.format("%.0f", currentSpeed),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Rajdhani,
                    color = c.textPrimary.copy(alpha = 0.7f)
                )
                Text("km/h", color = c.textSecondary, fontSize = 14.sp)

                // G-force 실시간 표시
                Spacer(modifier = Modifier.height(4.dp))
                GforceDisplay(gforceData)

                Spacer(modifier = Modifier.height(16.dp))

                if (liveSplits.isNotEmpty()) {
                    // 측정 중에는 주요 구간(60/100/150/200)만 기본 표시
                    val majorSplits = liveSplits.filter { it.isMajor }
                    AnimatedSplitTimesDisplay(majorSplits)
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            else -> {
                Text(
                    text = "0.00",
                    style = TimerDisplayStyle,
                    color = c.textTertiary
                )
                Text(stringResource(R.string.seconds), style = SpeedUnitStyle.copy(color = c.textSecondary))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = String.format("%.0f", currentSpeed),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Rajdhani,
                    color = c.textPrimary
                )
                Text("km/h", color = c.textSecondary, fontSize = 14.sp)

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // 하단 버튼
        when (measureState) {
            MeasureState.MEASURING -> {
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
                    color = c.warning.copy(alpha = 0.8f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, tint = c.background)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.long_press_stop),
                            color = c.background,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            MeasureState.FINISHED -> {
                result?.let { r ->
                    val shareTitle = stringResource(R.string.share_result)
                    OutlinedButton(
                        onClick = { shareResult(context, r.elapsedSeconds, r.peakSpeed, r.splits, shareTitle, r.measureMode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.share_result))
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
                    ) { Text(stringResource(R.string.go_back)) }
                    Button(
                        onClick = { viewModel.startMeasurement() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.retry))
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
                    colors = ButtonDefaults.buttonColors(containerColor = c.card)
                ) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun SpeedProgressBar(
    currentSpeed: Double,
    targetSpeed: Double,
    modifier: Modifier = Modifier,
    barColor: Color? = null
) {
    val c = LocalZero100Colors.current
    val effectiveColor = barColor ?: c.info
    val progress = (currentSpeed / targetSpeed).coerceIn(0.0, 1.0).toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(100),
        label = "speedProgress"
    )

    Canvas(modifier = modifier) {
        val barHeight = size.height
        val barWidth = size.width
        val cornerRadius = barHeight / 2

        drawRoundRect(
            color = c.textTertiary.copy(alpha = 0.2f),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )

        if (animatedProgress > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        effectiveColor.copy(alpha = 0.6f),
                        effectiveColor
                    ),
                    endX = barWidth * animatedProgress
                ),
                size = size.copy(width = barWidth * animatedProgress),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
            )
        }
    }
}

private fun shareResult(
    context: Context,
    elapsedSeconds: Double,
    peakSpeed: Double,
    splits: List<SplitTime>,
    chooserTitle: String,
    mode: MeasureMode = MeasureMode.ACCELERATION
) {
    val isDecel = mode == MeasureMode.DECELERATION
    val sb = StringBuilder("Zero100")
    for (split in splits) {
        val label = if (isDecel) "${split.speedKmh.toInt()}-0km/h" else "0-${split.speedKmh.toInt()}km/h"
        sb.append(" | $label: ${String.format("%.2f", split.elapsedSeconds)}s")
    }
    sb.append(" | Peak: ${String.format("%.1f", peakSpeed)}km/h")

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

/**
 * 속도 구간 + 거리 구간 서브탭 (결과 화면용)
 */
@Composable
private fun SplitAndDistanceTabs(
    splits: List<SplitTime>,
    distanceCheckpoints: List<DistanceCheckpoint>
) {
    val c = LocalZero100Colors.current
    var subTab by remember { mutableIntStateOf(0) }
    val hasDistCheckpoints = distanceCheckpoints.isNotEmpty()

    if (hasDistCheckpoints) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                label = { Text("속도 구간", fontSize = 12.sp) }
            )
            FilterChip(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                label = { Text("거리 구간", fontSize = 12.sp) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    when (subTab) {
        0 -> {
            if (splits.isNotEmpty()) {
                SplitTimesWithToggle(splits)
            }
        }
        1 -> {
            if (hasDistCheckpoints) {
                DistanceCheckpointsDisplay(distanceCheckpoints)
            }
        }
    }
}

/**
 * 속도 구간 표시 (주요 구간 기본, 전체 보기 토글)
 */
@Composable
private fun SplitTimesWithToggle(splits: List<SplitTime>) {
    val c = LocalZero100Colors.current
    var showAll by remember { mutableStateOf(false) }
    val displaySplits = if (showAll) splits else splits.filter { it.isMajor }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            displaySplits.forEachIndexed { index, split ->
                SplitTimeRow(split, index)
            }

            // 전체 구간 토글 (10km/h 단위가 있을 때만)
            val hasMinorSplits = splits.any { !it.isMajor }
            if (hasMinorSplits) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text(
                        if (showAll) "주요 구간만 보기" else "전체 구간 보기 (${splits.size}개)",
                        fontSize = 12.sp,
                        color = c.textTertiary
                    )
                }
            }
        }
    }
}

/**
 * 거리 체크포인트 표시
 */
@Composable
private fun DistanceCheckpointsDisplay(checkpoints: List<DistanceCheckpoint>) {
    val c = LocalZero100Colors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            checkpoints.forEachIndexed { index, cp ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }

                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(300, delayMillis = index * 50)
                    ) + fadeIn(animationSpec = tween(300, delayMillis = index * 50))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            cp.label,
                            color = c.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Rajdhani
                        )
                        Row {
                            Text(
                                String.format("%.2fs", cp.elapsedMs / 1000.0),
                                color = c.info,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = Rajdhani
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                String.format("%.0f km/h", cp.speedKmh),
                                color = c.textTertiary,
                                fontSize = 12.sp,
                                fontFamily = Rajdhani
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitTimeRow(split: SplitTime, index: Int, isDecel: Boolean = false) {
    val c = LocalZero100Colors.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300, delayMillis = index * 50)
        ) + fadeIn(animationSpec = tween(300, delayMillis = index * 50))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (split.isMajor) 6.dp else 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (isDecel) "${split.speedKmh.toInt()}-0 km/h" else "0-${split.speedKmh.toInt()} km/h",
                color = if (split.isMajor) c.textPrimary else c.textSecondary,
                fontSize = if (split.isMajor) 14.sp else 12.sp,
                fontWeight = if (split.isMajor) FontWeight.Bold else FontWeight.Normal,
                fontFamily = Rajdhani
            )
            Row {
                Text(
                    stringResource(R.string.split_time_format, split.elapsedSeconds),
                    color = when {
                        split.isMajor && split.speedKmh <= 100.0 -> c.info
                        split.isMajor -> c.accent
                        else -> c.textSecondary
                    },
                    fontWeight = if (split.isMajor) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (split.isMajor) 14.sp else 12.sp,
                    fontFamily = Rajdhani
                )
                if (split.distanceM > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        String.format("%.0fm", split.distanceM),
                        color = c.textTertiary,
                        fontSize = if (split.isMajor) 12.sp else 11.sp,
                        fontFamily = Rajdhani
                    )
                }
            }
        }
    }
}

/**
 * 측정 중 실시간 랩타임 표시 (주요 구간만)
 */
@Composable
private fun AnimatedSplitTimesDisplay(splits: List<SplitTime>) {
    val c = LocalZero100Colors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            splits.forEachIndexed { index, split ->
                SplitTimeRow(split, index)
            }
        }
    }
}

@Composable
private fun GforceDisplay(gforceData: GforceData) {
    val c = LocalZero100Colors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            String.format("%.2fG", gforceData.longitudinal),
            color = if (gforceData.longitudinal >= 0) c.info else c.danger,
            fontSize = 14.sp,
            fontFamily = Rajdhani,
            fontWeight = FontWeight.Medium
        )
        Text(
            String.format("L%.2fG", kotlin.math.abs(gforceData.lateral)),
            color = c.textSecondary,
            fontSize = 12.sp,
            fontFamily = Rajdhani
        )
    }
}

@Composable
private fun StatusBadge(state: MeasureState, mode: MeasureMode = MeasureMode.ACCELERATION, combinedPhase: CombinedPhase? = null) {
    val c = LocalZero100Colors.current
    val isDecel = mode == MeasureMode.DECELERATION
    val isCombined = mode == MeasureMode.COMBINED
    val (text, color, bgColor) = when (state) {
        MeasureState.IDLE -> when {
            isCombined -> Triple(stringResource(R.string.status_stop), c.textSecondary, c.card)
            isDecel -> Triple(stringResource(R.string.status_decel_idle), c.textSecondary, c.card)
            else -> Triple(stringResource(R.string.status_stop), c.textSecondary, c.card)
        }
        MeasureState.READY -> when {
            isCombined -> Triple(stringResource(R.string.status_go), c.success, c.success.copy(alpha = 0.15f))
            isDecel -> Triple(stringResource(R.string.status_decel_ready), c.danger, c.danger.copy(alpha = 0.15f))
            else -> Triple(stringResource(R.string.status_go), c.success, c.success.copy(alpha = 0.15f))
        }
        MeasureState.MEASURING -> when {
            isCombined && combinedPhase == CombinedPhase.DECEL ->
                Triple(stringResource(R.string.status_decel_measuring), c.danger, c.danger.copy(alpha = 0.15f))
            isCombined -> Triple(stringResource(R.string.status_measuring), c.info, c.info.copy(alpha = 0.15f))
            isDecel -> Triple(stringResource(R.string.status_decel_measuring), c.danger, c.danger.copy(alpha = 0.15f))
            else -> Triple(stringResource(R.string.status_measuring), c.info, c.info.copy(alpha = 0.15f))
        }
        MeasureState.FINISHED -> Triple(stringResource(R.string.status_done), c.success, c.success.copy(alpha = 0.15f))
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
    val c = LocalZero100Colors.current
    if (points.size < 2) return

    val maxTime = points.maxOf { it.first }.toFloat()
    val maxSpeed = maxOf(targetSpeed, points.maxOf { it.second }).toFloat()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = c.card),
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

            for (split in splits) {
                val y = h - (split.speedKmh.toFloat() / maxSpeed * (h - padding * 2)) - padding
                drawLine(c.textTertiary.copy(alpha = 0.3f), Offset(0f, y), Offset(w, y), 1f)
            }

            val path = Path()
            points.forEachIndexed { i, (time, speed) ->
                val x = (time.toFloat() / maxTime) * (w - padding * 2) + padding
                val y = h - (speed.toFloat() / maxSpeed * (h - padding * 2)) - padding
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, c.info, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

            for (split in splits) {
                val x = (split.elapsedMs.toFloat() / maxTime) * (w - padding * 2) + padding
                val y = h - (split.speedKmh.toFloat() / maxSpeed * (h - padding * 2)) - padding
                drawCircle(c.warning, radius = 5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

package com.ableLabs.zero100.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
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
import com.ableLabs.zero100.data.MeasurementRecord
import com.ableLabs.zero100.measurement.SplitTime
import com.ableLabs.zero100.measurement.TrackPoint
import com.ableLabs.zero100.ui.theme.*
import com.ableLabs.zero100.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * trackPointsJson 파싱 유틸
 */
fun parseTrackPointsJson(json: String): List<TrackPoint> {
    if (json.isBlank() || json == "[]") return emptyList()
    val pattern = Regex("""\{"t":(\d+),"lat":([0-9.+\-]+),"lon":([0-9.+\-]+),"spd":([0-9.+\-]+)\}""")
    return pattern.findAll(json).map { match ->
        TrackPoint(
            timeMs = match.groupValues[1].toLong(),
            lat = match.groupValues[2].toDouble(),
            lon = match.groupValues[3].toDouble(),
            speedKmh = match.groupValues[4].toDouble()
        )
    }.toList()
}

/**
 * splitsJson 파싱 유틸 (공통)
 */
data class SplitEntry(
    val speed: Int,
    val ms: Long,
    val distanceM: Double = 0.0,
    val isMajor: Boolean = false
) {
    val seconds: Double get() = ms / 1000.0
}

data class DistCheckEntry(
    val distanceM: Double,
    val label: String,
    val ms: Long,
    val speedKmh: Double
) {
    val seconds: Double get() = ms / 1000.0
}

fun parseSplitsJson(json: String): List<SplitEntry> {
    if (json.isBlank() || json == "[]") return emptyList()
    // v7 형식: major 필드 포함
    val patternV7 = Regex("""\{"speed":(\d+),"ms":(\d+),"dist":([0-9.+\-]+),"major":(true|false)\}""")
    val v7Matches = patternV7.findAll(json).map { match ->
        SplitEntry(
            speed = match.groupValues[1].toInt(),
            ms = match.groupValues[2].toLong(),
            distanceM = match.groupValues[3].toDoubleOrNull() ?: 0.0,
            isMajor = match.groupValues[4] == "true"
        )
    }.toList()
    if (v7Matches.isNotEmpty()) return v7Matches

    // v5-v6 형식: dist 포함, major 없음
    val patternNew = Regex("""\{"speed":(\d+),"ms":(\d+),"dist":([0-9.+\-]+)\}""")
    val newMatches = patternNew.findAll(json).map { match ->
        val speed = match.groupValues[1].toInt()
        SplitEntry(
            speed = speed,
            ms = match.groupValues[2].toLong(),
            distanceM = match.groupValues[3].toDoubleOrNull() ?: 0.0,
            isMajor = speed in listOf(60, 100, 150, 200)
        )
    }.toList()
    if (newMatches.isNotEmpty()) return newMatches

    // v1-v4 형식: speed, ms만
    val patternOld = Regex("""\{"speed":(\d+),"ms":(\d+)\}""")
    return patternOld.findAll(json).map { match ->
        val speed = match.groupValues[1].toInt()
        SplitEntry(
            speed = speed,
            ms = match.groupValues[2].toLong(),
            isMajor = speed in listOf(60, 100, 150, 200)
        )
    }.toList()
}

fun parseDistanceCheckpointsJson(json: String): List<DistCheckEntry> {
    if (json.isBlank() || json == "[]") return emptyList()
    val pattern = Regex("""\{"dist":([0-9.+\-]+),"label":"([^"]+)","ms":(\d+),"spd":([0-9.+\-]+)\}""")
    return pattern.findAll(json).map { match ->
        DistCheckEntry(
            distanceM = match.groupValues[1].toDoubleOrNull() ?: 0.0,
            label = match.groupValues[2],
            ms = match.groupValues[3].toLong(),
            speedKmh = match.groupValues[4].toDoubleOrNull() ?: 0.0
        )
    }.toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val c = LocalZero100Colors.current
    var record by remember { mutableStateOf<MeasurementRecord?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(recordId) {
        record = viewModel.getRecordById(recordId)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.detail_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = c.background,
                titleContentColor = c.textPrimary,
                navigationIconContentColor = c.textPrimary
            )
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = c.info)
            }
        } else if (record == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.record_not_found), color = c.textSecondary)
            }
        } else {
            val rec = record!!
            val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA) }
            val splits = remember(rec.splitsJson) { parseSplitsJson(rec.splitsJson) }
            val distCheckpoints = remember(rec.distanceCheckpointsJson) { parseDistanceCheckpointsJson(rec.distanceCheckpointsJson) }
            val trackPoints = remember(rec.trackPointsJson) { parseTrackPointsJson(rec.trackPointsJson) }
            val hasTrackPoints = trackPoints.isNotEmpty()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = String.format("%.2f", rec.elapsedSeconds),
                    style = TimerDisplayStyle,
                    color = c.info
                )
                Text(stringResource(R.string.seconds), style = SpeedUnitStyle.copy(color = c.textSecondary))

                Spacer(modifier = Modifier.height(8.dp))

                val isDecelRecord = rec.measureMode == "DECELERATION"
                Text(
                    if (isDecelRecord) {
                        stringResource(R.string.record_summary_decel, rec.targetSpeed.toInt(), String.format("%.0f", rec.peakSpeed))
                    } else {
                        stringResource(R.string.record_summary, rec.targetSpeed.toInt(), String.format("%.0f", rec.peakSpeed))
                    },
                    color = c.textSecondary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )

                // 감속 기록: 제동거리 강조
                if (isDecelRecord) {
                    val avgDist = if (rec.distanceBySpeed > 0 && rec.distanceByGps > 0) {
                        (rec.distanceBySpeed + rec.distanceByGps) / 2.0
                    } else if (rec.distanceBySpeed > 0) rec.distanceBySpeed else rec.distanceByGps
                    if (avgDist > 0) {
                        Text(
                            stringResource(R.string.braking_distance_label, String.format("%.1f", avgDist)),
                            color = c.danger,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (rec.distanceBySpeed > 0 || rec.distanceByGps > 0) {
                    val avgDist = if (rec.distanceBySpeed > 0 && rec.distanceByGps > 0) {
                        (rec.distanceBySpeed + rec.distanceByGps) / 2.0
                    } else if (rec.distanceBySpeed > 0) rec.distanceBySpeed else rec.distanceByGps
                    val distText = if (avgDist >= 1000) String.format("%.2f km", avgDist / 1000.0)
                    else String.format("%.0f m", avgDist)
                    val accText = if (rec.distanceBySpeed > 0 && rec.distanceByGps > 0) {
                        val smaller = minOf(rec.distanceBySpeed, rec.distanceByGps)
                        val larger = maxOf(rec.distanceBySpeed, rec.distanceByGps)
                        val acc = (smaller / larger) * 100
                        " (${stringResource(R.string.distance_accuracy, String.format("%.1f", acc))})"
                    } else ""
                    Text(
                        stringResource(R.string.distance_label) + " $distText$accText",
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    dateFormat.format(Date(rec.timestamp)),
                    color = c.textSecondary.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )

                // 측정 조건 배지들
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 측정 모드
                    if (rec.measureMode == "DECELERATION") {
                        DetailBadge(
                            text = stringResource(R.string.mode_decel),
                            color = c.danger
                        )
                    }

                    // GPS 소스
                    if (rec.gpsSource.isNotBlank()) {
                        val isExternal = rec.gpsSource == "EXTERNAL"
                        DetailBadge(
                            text = if (isExternal) "외부 GPS" else "내장 GPS",
                            color = if (isExternal) c.info else c.textSecondary
                        )
                    }

                    // 수신률
                    if (rec.updateRateHz > 0) {
                        DetailBadge(
                            text = "${rec.updateRateHz}Hz",
                            color = if (rec.updateRateHz >= 20) c.success else c.textSecondary
                        )
                    }

                    // 위성 수
                    if (rec.avgSatellites > 0) {
                        DetailBadge(
                            text = "${rec.avgSatellites}위성",
                            color = if (rec.avgSatellites >= 10) c.success else c.warning
                        )
                    }

                    // HDOP
                    if (rec.avgHdop > 0) {
                        val hdopLabel = when {
                            rec.avgHdop < 1.5 -> "정밀도:좋음"
                            rec.avgHdop < 3.0 -> "정밀도:보통"
                            else -> "정밀도:낮음"
                        }
                        DetailBadge(
                            text = hdopLabel,
                            color = when {
                                rec.avgHdop < 1.5 -> c.success
                                rec.avgHdop < 3.0 -> c.warning
                                else -> c.danger
                            }
                        )
                    }

                    // 1-foot rollout
                    if (rec.isRolloutApplied) {
                        DetailBadge(text = "1ft 보정", color = c.accent)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                var selectedTab by remember { mutableIntStateOf(0) }
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
                        if (splits.isNotEmpty() || distCheckpoints.isNotEmpty()) {
                            DetailSplitAndDistanceTabs(splits, distCheckpoints, isDecelRecord)
                        } else {
                            Text(
                                stringResource(R.string.no_split_data),
                                color = c.textSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                    1 -> {
                        if (splits.isNotEmpty()) {
                            DetailSplitGraph(
                                splits = splits,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        } else {
                            Text(
                                stringResource(R.string.no_graph_data),
                                color = c.textSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                    2 -> {
                        if (hasTrackPoints) {
                            val splitTimes = splits.map { SplitTime(it.speed.toDouble(), it.ms, it.distanceM, it.isMajor) }
                            MapResultView(
                                trackPoints = trackPoints,
                                splits = splitTimes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val rec2 = record!!
                val splits2 = parseSplitsJson(rec2.splitsJson)
                val shareTitle = stringResource(R.string.share_result)

                OutlinedButton(
                    onClick = {
                        shareDetailResult(context, rec2, splits2, shareTitle)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.share_result))
                }

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.danger)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }

    if (showDeleteDialog && record != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_record_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(record!!)
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.delete), color = c.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DetailSplitGraph(
    splits: List<SplitEntry>,
    modifier: Modifier = Modifier
) {
    val c = LocalZero100Colors.current
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

            if (splits.isEmpty()) return@Canvas

            val maxTime = splits.maxOf { it.ms }.toFloat()
            val maxSpeed = splits.maxOf { it.speed }.toFloat()

            for (split in splits) {
                val y = h - (split.speed.toFloat() / maxSpeed * (h - padding * 2)) - padding
                drawLine(c.textTertiary.copy(alpha = 0.3f), Offset(0f, y), Offset(w, y), 1f)
            }

            val path = Path()
            path.moveTo(padding, h - padding)

            for (split in splits) {
                val x = (split.ms.toFloat() / maxTime) * (w - padding * 2) + padding
                val y = h - (split.speed.toFloat() / maxSpeed * (h - padding * 2)) - padding
                path.lineTo(x, y)
            }

            drawPath(path, c.info, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

            for (split in splits) {
                val x = (split.ms.toFloat() / maxTime) * (w - padding * 2) + padding
                val y = h - (split.speed.toFloat() / maxSpeed * (h - padding * 2)) - padding
                drawCircle(c.warning, radius = 5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

private fun shareDetailResult(
    context: Context,
    record: MeasurementRecord,
    splits: List<SplitEntry>,
    chooserTitle: String
) {
    val isDecel = record.measureMode == "DECELERATION"
    val sb = StringBuilder("Zero100")
    for (split in splits) {
        val label = if (isDecel) "${split.speed}-0km/h" else "0-${split.speed}km/h"
        sb.append(" | $label: ${String.format("%.2f", split.seconds)}s")
    }
    sb.append(" | Peak: ${String.format("%.1f", record.peakSpeed)}km/h")
    if (record.isRolloutApplied) {
        sb.append(" (1ft rollout)")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

/**
 * 속도 구간 + 거리 구간 서브탭 (RecordDetail 결과 화면용)
 */
@Composable
private fun DetailSplitAndDistanceTabs(
    splits: List<SplitEntry>,
    distCheckpoints: List<DistCheckEntry>,
    isDecel: Boolean = false
) {
    val c = LocalZero100Colors.current
    var subTab by remember { mutableIntStateOf(0) }
    val hasDistCheckpoints = distCheckpoints.isNotEmpty()

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
                DetailSplitTimesWithToggle(splits, isDecel)
            }
        }
        1 -> {
            if (hasDistCheckpoints) {
                DetailDistanceCheckpoints(distCheckpoints)
            }
        }
    }
}

/**
 * 속도 구간 표시 -- 주요 구간 기본, 전체 토글
 */
@Composable
private fun DetailSplitTimesWithToggle(splits: List<SplitEntry>, isDecel: Boolean = false) {
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
                            if (isDecel) "${split.speed}-0 km/h" else "0-${split.speed} km/h",
                            color = if (split.isMajor) c.textPrimary else c.textSecondary,
                            fontSize = if (split.isMajor) 14.sp else 12.sp,
                            fontWeight = if (split.isMajor) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                        Row {
                            Text(
                                stringResource(R.string.split_time_format, split.seconds),
                                color = when {
                                    split.isMajor && split.speed <= 100 -> c.info
                                    split.isMajor -> c.accent
                                    else -> c.textSecondary
                                },
                                fontWeight = if (split.isMajor) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (split.isMajor) 14.sp else 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (split.distanceM > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    String.format("%.0fm", split.distanceM),
                                    color = c.textTertiary,
                                    fontSize = if (split.isMajor) 12.sp else 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // 전체 구간 토글
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
 * 거리 체크포인트 표시 (RecordDetail용)
 */
@Composable
private fun DetailDistanceCheckpoints(checkpoints: List<DistCheckEntry>) {
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
                            fontFamily = FontFamily.Monospace
                        )
                        Row {
                            Text(
                                String.format("%.2fs", cp.seconds),
                                color = c.info,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                String.format("%.0f km/h", cp.speedKmh),
                                color = c.textTertiary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

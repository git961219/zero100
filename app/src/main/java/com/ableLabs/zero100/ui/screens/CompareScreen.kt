package com.ableLabs.zero100.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ableLabs.zero100.R
import com.ableLabs.zero100.data.MeasurementRecord
import com.ableLabs.zero100.ui.theme.*
import com.ableLabs.zero100.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    recordId1: Long,
    recordId2: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val c = LocalZero100Colors.current
    var record1 by remember { mutableStateOf<MeasurementRecord?>(null) }
    var record2 by remember { mutableStateOf<MeasurementRecord?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(recordId1, recordId2) {
        record1 = viewModel.getRecordById(recordId1)
        record2 = viewModel.getRecordById(recordId2)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.compare_title)) },
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.info)
            }
        } else if (record1 == null || record2 == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.record_not_found), color = c.textSecondary)
            }
        } else {
            val r1 = record1!!
            val r2 = record2!!
            val splits1 = parseSplitsJson(r1.splitsJson)
            val splits2 = parseSplitsJson(r2.splitsJson)
            val dateFormat = remember { SimpleDateFormat("MM.dd HH:mm", Locale.KOREA) }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 요약 카드
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CompareRecordCard(
                        label = "A",
                        record = r1,
                        color = c.info,
                        dateFormat = dateFormat,
                        modifier = Modifier.weight(1f)
                    )
                    CompareRecordCard(
                        label = "B",
                        record = r2,
                        color = c.warning,
                        dateFormat = dateFormat,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 속도-시간 그래프 오버레이
                Text(
                    stringResource(R.string.compare_graph),
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                CompareSpeedGraph(
                    splits1 = splits1,
                    splits2 = splits2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 구간별 차이표
                Text(
                    stringResource(R.string.compare_splits),
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                CompareSplitsTable(splits1, splits2)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CompareRecordCard(
    label: String,
    record: MeasurementRecord,
    color: Color,
    dateFormat: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    val c = LocalZero100Colors.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                record.displayTime,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "0-${record.targetSpeed.toInt()} km/h",
                color = c.textSecondary,
                fontSize = 12.sp
            )
            Text(
                dateFormat.format(Date(record.timestamp)),
                color = c.textTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CompareSpeedGraph(
    splits1: List<SplitEntry>,
    splits2: List<SplitEntry>,
    modifier: Modifier = Modifier
) {
    val c = LocalZero100Colors.current
    if (splits1.isEmpty() && splits2.isEmpty()) return

    val allSplits = splits1 + splits2
    val maxTime = allSplits.maxOfOrNull { it.ms }?.toFloat() ?: return
    val maxSpeed = allSplits.maxOfOrNull { it.speed }?.toFloat() ?: return

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = c.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                val w = size.width
                val h = size.height
                val pad = 4.dp.toPx()

                // 그래프 A (info 색상)
                if (splits1.isNotEmpty()) {
                    val path = Path()
                    path.moveTo(pad, h - pad)
                    for (split in splits1) {
                        val x = (split.ms.toFloat() / maxTime) * (w - pad * 2) + pad
                        val y = h - (split.speed.toFloat() / maxSpeed * (h - pad * 2)) - pad
                        path.lineTo(x, y)
                    }
                    drawPath(path, c.info, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    for (split in splits1) {
                        val x = (split.ms.toFloat() / maxTime) * (w - pad * 2) + pad
                        val y = h - (split.speed.toFloat() / maxSpeed * (h - pad * 2)) - pad
                        drawCircle(c.info, radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                }

                // 그래프 B (warning 색상)
                if (splits2.isNotEmpty()) {
                    val path = Path()
                    path.moveTo(pad, h - pad)
                    for (split in splits2) {
                        val x = (split.ms.toFloat() / maxTime) * (w - pad * 2) + pad
                        val y = h - (split.speed.toFloat() / maxSpeed * (h - pad * 2)) - pad
                        path.lineTo(x, y)
                    }
                    drawPath(path, c.warning, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    for (split in splits2) {
                        val x = (split.ms.toFloat() / maxTime) * (w - pad * 2) + pad
                        val y = h - (split.speed.toFloat() / maxSpeed * (h - pad * 2)) - pad
                        drawCircle(c.warning, radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                }
            }

            // 범례
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(8.dp)) { drawCircle(c.info) }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("A", color = c.info, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(8.dp)) { drawCircle(c.warning) }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("B", color = c.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CompareSplitsTable(
    splits1: List<SplitEntry>,
    splits2: List<SplitEntry>
) {
    val c = LocalZero100Colors.current
    // 양쪽에 공통으로 존재하는 구간만 비교
    val speeds1 = splits1.associateBy { it.speed }
    val speeds2 = splits2.associateBy { it.speed }
    val allSpeeds = (speeds1.keys + speeds2.keys).sorted()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.compare_speed_col), color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("A", color = c.info, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("B", color = c.warning, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.compare_diff_col), color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }

            for (speed in allSpeeds) {
                val s1 = speeds1[speed]
                val s2 = speeds2[speed]
                val isMajor = speed in listOf(60, 100, 150, 200)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isMajor) 4.dp else 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${speed}",
                        color = if (isMajor) c.textPrimary else c.textSecondary,
                        fontSize = if (isMajor) 13.sp else 12.sp,
                        fontWeight = if (isMajor) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        s1?.let { String.format("%.2f", it.seconds) } ?: "-",
                        color = c.info,
                        fontSize = if (isMajor) 13.sp else 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        s2?.let { String.format("%.2f", it.seconds) } ?: "-",
                        color = c.warning,
                        fontSize = if (isMajor) 13.sp else 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )

                    val diffText = if (s1 != null && s2 != null) {
                        val diff = s1.seconds - s2.seconds
                        val prefix = if (diff > 0) "+" else ""
                        "$prefix${String.format("%.2f", diff)}"
                    } else "-"
                    val diffColor = if (s1 != null && s2 != null) {
                        val diff = s1.seconds - s2.seconds
                        when {
                            diff > 0.1 -> c.danger  // A가 느림
                            diff < -0.1 -> c.success // A가 빠름
                            else -> c.textTertiary
                        }
                    } else c.textTertiary

                    Text(
                        diffText,
                        color = diffColor,
                        fontSize = if (isMajor) 13.sp else 12.sp,
                        fontWeight = if (isMajor) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

package com.ableLabs.zero100.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ableLabs.zero100.R
import com.ableLabs.zero100.data.MeasurementRecord
import com.ableLabs.zero100.measurement.TrackPoint
import com.ableLabs.zero100.ui.theme.*
import com.ableLabs.zero100.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToCompare: (Long, Long) -> Unit = { _, _ -> }
) {
    val c = LocalZero100Colors.current
    val records by viewModel.records.collectAsState()
    val bestRecord by viewModel.bestRecord.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var compareMode by remember { mutableStateOf(false) }
    val selectedForCompare = remember { mutableStateListOf<Long>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        TopAppBar(
            title = {
                Text(
                    if (compareMode) stringResource(R.string.compare_select, selectedForCompare.size)
                    else stringResource(R.string.history_title)
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (compareMode) {
                        compareMode = false
                        selectedForCompare.clear()
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (records.size >= 2 && !compareMode) {
                    IconButton(onClick = {
                        compareMode = true
                        selectedForCompare.clear()
                    }) {
                        Icon(Icons.Filled.CompareArrows, contentDescription = stringResource(R.string.compare), tint = c.info)
                    }
                }
                if (compareMode && selectedForCompare.size == 2) {
                    IconButton(onClick = {
                        onNavigateToCompare(selectedForCompare[0], selectedForCompare[1])
                        compareMode = false
                        selectedForCompare.clear()
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.compare), tint = c.success)
                    }
                }
                if (records.isNotEmpty() && !compareMode) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.delete_all))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = c.background,
                titleContentColor = c.textPrimary,
                navigationIconContentColor = c.textPrimary,
                actionIconContentColor = c.textSecondary
            )
        )

        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_records),
                        color = c.textSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(records) { index, record ->
                    val isBest = bestRecord?.id == record.id
                    val isSelectedForCompare = record.id in selectedForCompare
                    RecordCard(
                        record = record,
                        rank = index + 1,
                        isBest = isBest,
                        onDelete = { viewModel.deleteRecord(record) },
                        onClick = {
                            if (compareMode) {
                                if (isSelectedForCompare) {
                                    selectedForCompare.remove(record.id)
                                } else if (selectedForCompare.size < 2) {
                                    selectedForCompare.add(record.id)
                                }
                            } else {
                                onNavigateToDetail(record.id)
                            }
                        },
                        compareMode = compareMode,
                        isSelectedForCompare = isSelectedForCompare
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        val c2 = LocalZero100Colors.current
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_all)) },
            text = { Text(stringResource(R.string.delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllRecords()
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.delete), color = c2.danger)
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
private fun RecordCard(
    record: MeasurementRecord,
    rank: Int,
    isBest: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit = {},
    compareMode: Boolean = false,
    isSelectedForCompare: Boolean = false
) {
    val c = LocalZero100Colors.current
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA) }
    var expanded by remember { mutableStateOf(false) }
    val splits = remember(record.splitsJson) { parseSplitsJson(record.splitsJson) }
    val trackPoints = remember(record.trackPointsJson) { parseTrackPointsJson(record.trackPointsJson) }
    val hasSplits = splits.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelectedForCompare) Modifier.border(2.dp, c.info, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelectedForCompare -> c.info.copy(alpha = 0.1f)
                isBest -> c.success.copy(alpha = 0.1f)
                else -> c.card
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val isDecelRecord = record.measureMode == "DECELERATION"
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (compareMode) {
                    Checkbox(
                        checked = isSelectedForCompare,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(checkedColor = c.info)
                    )
                } else if (isBest) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = c.warning,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Text(
                        "#$rank",
                        color = c.textSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            record.displayTime,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isBest) c.success else c.textPrimary
                        )
                        if (record.isRolloutApplied) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = c.accent.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = stringResource(R.string.rollout_badge),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = c.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (isDecelRecord) {
                        val avgDist = if (record.distanceBySpeed > 0 && record.distanceByGps > 0) {
                            (record.distanceBySpeed + record.distanceByGps) / 2.0
                        } else if (record.distanceBySpeed > 0) record.distanceBySpeed else record.distanceByGps
                        val distText = if (avgDist > 0) "  ${String.format("%.0f", avgDist)}m" else ""
                        Text(
                            "${record.targetSpeed.toInt()}-0 km/h  |  ${record.displayTime}$distText",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.danger
                        )
                    } else {
                        Text(
                            stringResource(R.string.record_summary, record.targetSpeed.toInt(), String.format("%.0f", record.peakSpeed)),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                    Text(
                        dateFormat.format(Date(record.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary.copy(alpha = 0.6f)
                    )
                }

                if (trackPoints.isNotEmpty()) {
                    MiniTrackPreview(
                        trackPoints = trackPoints,
                        modifier = Modifier
                            .size(width = 80.dp, height = 60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.card)
                            .border(
                                width = 1.dp,
                                color = c.textTertiary,
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (hasSplits) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { expanded = !expanded }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.delete),
                        tint = c.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && hasSplits,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp, start = 44.dp)) {
                    splits.forEach { split ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isDecelRecord) "${split.speed}-0 km/h" else "0-${split.speed} km/h",
                                color = c.textSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                stringResource(R.string.split_time_format, split.seconds),
                                color = when {
                                    split.speed <= 100 -> c.info
                                    split.speed <= 150 -> c.info.copy(alpha = 0.7f)
                                    else -> c.accent
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniTrackPreview(
    trackPoints: List<TrackPoint>,
    modifier: Modifier = Modifier
) {
    val c = LocalZero100Colors.current
    if (trackPoints.size < 2) return

    val lats = trackPoints.map { it.lat }
    val lons = trackPoints.map { it.lon }
    val minLat = lats.min()
    val maxLat = lats.max()
    val minLon = lons.min()
    val maxLon = lons.max()
    val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
    val lonRange = (maxLon - minLon).coerceAtLeast(0.0001)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 4.dp.toPx()
        val drawW = w - pad * 2
        val drawH = h - pad * 2

        for (i in 0 until trackPoints.size - 1) {
            val pt1 = trackPoints[i]
            val pt2 = trackPoints[i + 1]

            val x1 = pad + ((pt1.lon - minLon) / lonRange * drawW).toFloat()
            val y1 = pad + ((maxLat - pt1.lat) / latRange * drawH).toFloat()
            val x2 = pad + ((pt2.lon - minLon) / lonRange * drawW).toFloat()
            val y2 = pad + ((maxLat - pt2.lat) / latRange * drawH).toFloat()

            val color = when {
                pt2.speedKmh < 30 -> Color(0xFF2196F3)
                pt2.speedKmh < 60 -> c.info
                pt2.speedKmh < 100 -> c.success
                pt2.speedKmh < 150 -> c.warning
                pt2.speedKmh < 200 -> Color(0xFFFF9800)
                else -> c.danger
            }

            drawLine(
                color = color,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

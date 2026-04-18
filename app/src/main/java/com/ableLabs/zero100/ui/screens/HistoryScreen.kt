package com.ableLabs.zero100.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ableLabs.zero100.data.MeasurementRecord
import com.ableLabs.zero100.ui.theme.*
import com.ableLabs.zero100.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * splitsJson 파싱: [{"speed":60,"ms":3210},{"speed":100,"ms":5230}]
 * 외부 라이브러리 없이 간단한 정규식으로 파싱
 */
private data class SplitEntry(val speed: Int, val ms: Long) {
    val seconds: Double get() = ms / 1000.0
}

private fun parseSplitsJson(json: String): List<SplitEntry> {
    if (json.isBlank() || json == "[]") return emptyList()
    val pattern = Regex("""\{"speed":(\d+),"ms":(\d+)\}""")
    return pattern.findAll(json).map { match ->
        SplitEntry(
            speed = match.groupValues[1].toInt(),
            ms = match.groupValues[2].toLong()
        )
    }.toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val records by viewModel.records.collectAsState()
    val bestRecord by viewModel.bestRecord.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        // 상단 바
        TopAppBar(
            title = { Text("측정 기록") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            actions = {
                if (records.isNotEmpty()) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "전체 삭제")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBg,
                titleContentColor = SpeedWhite,
                navigationIconContentColor = SpeedWhite,
                actionIconContentColor = SpeedGray
            )
        )

        if (records.isEmpty()) {
            // 빈 상태
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        tint = SpeedGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "아직 측정 기록이 없습니다",
                        color = SpeedGray,
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
                    RecordCard(
                        record = record,
                        rank = index + 1,
                        isBest = isBest,
                        onDelete = { viewModel.deleteRecord(record) }
                    )
                }
            }
        }
    }

    // 전체 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("전체 삭제") },
            text = { Text("모든 측정 기록을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllRecords()
                    showDeleteDialog = false
                }) {
                    Text("삭제", color = RacingRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
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
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA) }
    var expanded by remember { mutableStateOf(false) }
    val splits = remember(record.splitsJson) { parseSplitsJson(record.splitsJson) }
    val hasSplits = splits.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasSplits) Modifier.clickable { expanded = !expanded }
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isBest) Color(0xFF1B3A1B) else DarkCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 순위
                if (isBest) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = RacingYellow,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Text(
                        "#$rank",
                        color = SpeedGray,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 시간 + 날짜
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        record.displayTime,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isBest) RacingGreen else SpeedWhite
                    )
                    Text(
                        "0-${record.targetSpeed.toInt()} km/h  |  최고 ${String.format("%.0f", record.peakSpeed)} km/h",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpeedGray
                    )
                    Text(
                        dateFormat.format(Date(record.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = SpeedGray.copy(alpha = 0.6f)
                    )
                }

                // 펼치기 힌트
                if (hasSplits) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = SpeedGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // 삭제 버튼
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "삭제",
                        tint = SpeedGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 구간 랩타임 확장 영역
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
                                "0-${split.speed} km/h",
                                color = SpeedGray,
                                fontSize = 13.sp
                            )
                            Text(
                                String.format("%.2f초", split.seconds),
                                color = when {
                                    split.speed <= 100 -> RacingGreen
                                    split.speed <= 150 -> RacingYellow
                                    else -> RacingRed
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

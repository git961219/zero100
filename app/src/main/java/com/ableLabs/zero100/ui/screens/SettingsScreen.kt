package com.ableLabs.zero100.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ableLabs.zero100.ui.theme.*
import com.ableLabs.zero100.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val targetSpeed by viewModel.targetSpeedSetting.collectAsState()
    val oneFootRollout by viewModel.oneFootRollout.collectAsState()

    val speedOptions = listOf(60, 100, 150, 200)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        TopAppBar(
            title = { Text("설정") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBg,
                titleContentColor = SpeedWhite,
                navigationIconContentColor = SpeedWhite
            )
        )

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 목표 속도 선택
            Text(
                "목표 속도",
                color = SpeedWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "측정 종료 속도를 선택합니다. 해당 속도에 도달하면 자동으로 측정이 완료됩니다.",
                color = SpeedGray,
                fontSize = 13.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                speedOptions.forEach { speed ->
                    val isSelected = speed == targetSpeed
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setTargetSpeed(speed) },
                        label = {
                            Text(
                                "${speed}",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RacingRed,
                            selectedLabelColor = SpeedWhite,
                            containerColor = DarkCard,
                            labelColor = SpeedGray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Text(
                "현재: 0-${targetSpeed} km/h 측정",
                color = RacingYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            HorizontalDivider(color = DarkCard)

            // 1-foot rollout 보정
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "1-Foot Rollout 보정",
                        color = SpeedWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "드래그 레이스 기준 보정. 켜면 약 0.3초를 차감합니다. "
                                + "차량이 1피트(30cm) 전진한 시점을 기준으로 타이머를 시작하는 방식입니다.",
                        color = SpeedGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = oneFootRollout,
                    onCheckedChange = { viewModel.setOneFootRollout(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpeedWhite,
                        checkedTrackColor = RacingGreen,
                        uncheckedThumbColor = SpeedGray,
                        uncheckedTrackColor = DarkCard
                    )
                )
            }

            if (oneFootRollout) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RacingGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        "1-Foot Rollout 보정 활성: 측정 결과에서 0.3초 차감됩니다",
                        modifier = Modifier.padding(12.dp),
                        color = RacingGreen,
                        fontSize = 13.sp
                    )
                }
            }

            HorizontalDivider(color = DarkCard)

            // 정보
            Text(
                "GPS 정보",
                color = SpeedWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("모듈", "u-blox M9 (USB OTG)")
                InfoRow("측정 레이트", "25Hz (40ms 간격)")
                InfoRow("측정 시작 조건", "25Hz + 위성 8개 이상 + HDOP < 3.0")
                InfoRow("구간 기록", "60 / 100 / 150 / 200 km/h")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = SpeedGray, fontSize = 13.sp)
        Text(value, color = SpeedWhite, fontSize = 13.sp)
    }
}

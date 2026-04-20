package com.ableLabs.zero100.ui.screens

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.ableLabs.zero100.R
import com.ableLabs.zero100.data.Vehicle
import com.ableLabs.zero100.ui.theme.*
import com.ableLabs.zero100.viewmodel.GpsSource
import com.ableLabs.zero100.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val c = LocalZero100Colors.current
    val targetSpeed by viewModel.targetSpeedSetting.collectAsState()
    val decelStartSpeed by viewModel.decelStartSpeed.collectAsState()
    val oneFootRollout by viewModel.oneFootRollout.collectAsState()
    val gpsSource by viewModel.gpsSource.collectAsState()
    val rawRate by viewModel.rawRate.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()
    val updateChecking by viewModel.updateChecking.collectAsState()
    val updateCheckResult by viewModel.updateCheckResult.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsState()

    var showAddVehicleDialog by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<Vehicle?>(null) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zero100_settings", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 언어 옵션
    data class LangOption(val code: String, val label: String)
    val langSystemLabel = stringResource(R.string.lang_system)
    val languageOptions = listOf(
        LangOption("", langSystemLabel),
        LangOption("ko", "한국어"),
        LangOption("en", "English"),
        LangOption("ja", "日本語"),
        LangOption("zh", "中文")
    )
    var selectedLang by remember {
        mutableStateOf(prefs.getString("app_language", "") ?: "")
    }

    val speedOptions = listOf(60, 100, 150, 200)
    val decelSpeedOptions = listOf(60, 100, 150, 200)

    // Snackbar 메시지 문자열
    val msgTargetChanged = stringResource(R.string.snack_target_changed)
    val msgDecelChanged = stringResource(R.string.snack_decel_changed)
    val msgRolloutOn = stringResource(R.string.snack_rollout_on)
    val msgRolloutOff = stringResource(R.string.snack_rollout_off)
    val msgLangChanged = stringResource(R.string.snack_lang_changed)

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = c.surface,
                    contentColor = c.textPrimary
                )
            }
        },
        containerColor = c.background
    ) { paddingValues ->

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(paddingValues)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
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

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── 테마 선택 ──
            Text(
                stringResource(R.string.settings_theme),
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                stringResource(R.string.settings_theme_desc),
                color = c.textSecondary,
                fontSize = 13.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val isSelected = mode == themeMode
                    val label = when (mode) {
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = {
                            Text(
                                label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.accent,
                            selectedLabelColor = c.textPrimary,
                            containerColor = c.card,
                            labelColor = c.textSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            HorizontalDivider(color = c.card)

            // ── 목표 속도 선택 ──
            Text(
                stringResource(R.string.target_speed),
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                stringResource(R.string.target_speed_desc),
                color = c.textSecondary,
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
                        onClick = {
                            viewModel.setTargetSpeed(speed)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    String.format(msgTargetChanged, speed)
                                )
                            }
                        },
                        label = {
                            Text(
                                "${speed}",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.accent,
                            selectedLabelColor = c.textPrimary,
                            containerColor = c.card,
                            labelColor = c.textSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Text(
                stringResource(R.string.current_target, targetSpeed),
                color = c.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            HorizontalDivider(color = c.card)

            // ── 감속 시작 속도 선택 ──
            Text(
                stringResource(R.string.decel_start_speed),
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                stringResource(R.string.decel_start_speed_desc),
                color = c.textSecondary,
                fontSize = 13.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                decelSpeedOptions.forEach { speed ->
                    val isSelected = speed == decelStartSpeed
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.setDecelStartSpeed(speed)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    String.format(msgDecelChanged, speed)
                                )
                            }
                        },
                        label = {
                            Text(
                                "${speed}",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.danger,
                            selectedLabelColor = c.textPrimary,
                            containerColor = c.card,
                            labelColor = c.textSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Text(
                stringResource(R.string.current_decel_target, decelStartSpeed),
                color = c.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            HorizontalDivider(color = c.card)

            // ── 1-foot rollout 보정 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.rollout_title),
                        color = c.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.rollout_desc),
                        color = c.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = oneFootRollout,
                    onCheckedChange = {
                        viewModel.setOneFootRollout(it)
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                if (it) msgRolloutOn else msgRolloutOff
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.textPrimary,
                        checkedTrackColor = c.accent,
                        uncheckedThumbColor = c.textSecondary,
                        uncheckedTrackColor = c.card
                    )
                )
            }

            if (oneFootRollout) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = c.accent.copy(alpha = 0.15f)
                ) {
                    Text(
                        stringResource(R.string.rollout_active),
                        modifier = Modifier.padding(12.dp),
                        color = c.accent,
                        fontSize = 13.sp
                    )
                }
            }

            HorizontalDivider(color = c.card)

            // ── 차량 관리 ──
            Text(
                stringResource(R.string.vehicle_management),
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                stringResource(R.string.vehicle_management_desc),
                color = c.textSecondary,
                fontSize = 13.sp
            )

            if (vehicles.isEmpty()) {
                Text(
                    stringResource(R.string.no_vehicles),
                    color = c.textTertiary,
                    fontSize = 13.sp
                )
            } else {
                vehicles.forEach { vehicle ->
                    val isSelected = vehicle.id == selectedVehicleId
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) c.info.copy(alpha = 0.1f) else c.card
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setDefaultVehicle(if (isSelected) 0L else vehicle.id) },
                                colors = RadioButtonDefaults.colors(selectedColor = c.info)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(vehicle.name, color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                val sub = listOfNotNull(
                                    vehicle.make.takeIf { it.isNotBlank() },
                                    vehicle.model.takeIf { it.isNotBlank() },
                                    vehicle.year.takeIf { it > 0 }?.toString()
                                ).joinToString(" ")
                                if (sub.isNotBlank()) {
                                    Text(sub, color = c.textSecondary, fontSize = 12.sp)
                                }
                            }
                            IconButton(onClick = { editingVehicle = vehicle }) {
                                Icon(Icons.Filled.Edit, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteVehicle(vehicle) }) {
                                Icon(Icons.Filled.Close, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showAddVehicleDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.card),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = c.textPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_vehicle), color = c.textPrimary)
            }

            HorizontalDivider(color = c.card)

            // ── GPS 정보 ──
            Text(
                stringResource(R.string.gps_info),
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val isExternal = gpsSource == GpsSource.EXTERNAL
                InfoRow(
                    stringResource(R.string.current_source),
                    if (isExternal) stringResource(R.string.external_ublox, rawRate) else stringResource(R.string.internal_gps_1hz)
                )
                InfoRow(
                    stringResource(R.string.mode_label),
                    if (isExternal) stringResource(R.string.mode_high) else stringResource(R.string.mode_basic)
                )
                InfoRow(stringResource(R.string.info_splits), stringResource(R.string.info_splits_value))
                if (isExternal) {
                    InfoRow(stringResource(R.string.start_condition), stringResource(R.string.start_condition_external))
                } else {
                    InfoRow(stringResource(R.string.start_condition), stringResource(R.string.start_condition_internal))
                }
            }

            if (gpsSource == GpsSource.INTERNAL) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = c.warning.copy(alpha = 0.15f)
                ) {
                    Text(
                        stringResource(R.string.external_gps_promo),
                        modifier = Modifier.padding(12.dp),
                        color = c.warning,
                        fontSize = 13.sp
                    )
                }
            }

            HorizontalDivider(color = c.card)

            // ── 언어 선택 ──
            Text(
                stringResource(R.string.language),
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                stringResource(R.string.language_desc),
                color = c.textSecondary,
                fontSize = 13.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                languageOptions.forEach { option ->
                    val isSelected = selectedLang == option.code
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedLang = option.code
                            prefs.edit().putString("app_language", option.code).apply()
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(msgLangChanged)
                            }
                            val localeList = if (option.code.isEmpty()) {
                                LocaleListCompat.getEmptyLocaleList()
                            } else {
                                LocaleListCompat.forLanguageTags(option.code)
                            }
                            AppCompatDelegate.setApplicationLocales(localeList)
                        },
                        label = {
                            Text(
                                option.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.accent,
                            selectedLabelColor = c.textPrimary,
                            containerColor = c.card,
                            labelColor = c.textSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            HorizontalDivider(color = c.card)

            // -- 앱 버전 + 업데이트 확인 --
            Text(
                stringResource(R.string.update_current_version),
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            InfoRow(
                stringResource(R.string.update_current_version),
                viewModel.updateChecker.getCurrentVersion()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 업데이트 배너 (새 버전 있을 때)
            updateInfo?.let { info ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = c.accent.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.update_available, info.versionName),
                            color = c.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                info.releaseNotes.take(150),
                                color = c.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (updateProgress >= 0) {
                            LinearProgressIndicator(
                                progress = { updateProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = c.accent
                            )
                            Text(
                                stringResource(R.string.update_downloading, updateProgress),
                                color = c.textSecondary,
                                fontSize = 12.sp
                            )
                        } else {
                            Button(
                                onClick = { viewModel.downloadUpdate() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.accent)
                            ) {
                                Text(stringResource(R.string.update_download))
                            }
                        }
                    }
                }
            }

            // 수동 업데이트 확인 버튼
            if (updateInfo == null) {
                Button(
                    onClick = { viewModel.checkForUpdateManual() },
                    enabled = !updateChecking,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.card),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (updateChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = c.textSecondary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.update_checking),
                            color = c.textSecondary
                        )
                    } else {
                        Text(
                            stringResource(R.string.update_check),
                            color = c.textPrimary
                        )
                    }
                }

                // 확인 결과 메시지
                if (updateCheckResult.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val (icon, msg, color) = when {
                        updateCheckResult == "latest" -> Triple(Icons.Filled.CheckCircle, stringResource(R.string.update_latest), c.success)
                        updateCheckResult == "error" -> Triple(Icons.Filled.ErrorOutline, "업데이트 확인 실패. 네트워크를 확인해주세요.", c.warning)
                        updateCheckResult.startsWith("update:") -> Triple(Icons.Filled.NewReleases, "새 버전 ${updateCheckResult.removePrefix("update:")} 사용 가능!", c.accent)
                        else -> null
                    } ?: Triple(Icons.Filled.Info, "", c.textSecondary)

                    if (msg.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = color.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(msg, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    } // Scaffold

    // 차량 추가 다이얼로그
    if (showAddVehicleDialog) {
        VehicleEditDialog(
            vehicle = null,
            onDismiss = { showAddVehicleDialog = false },
            onSave = { name, make, model, year, notes ->
                viewModel.addVehicle(name, make, model, year, notes)
                showAddVehicleDialog = false
            }
        )
    }

    // 차량 편집 다이얼로그
    editingVehicle?.let { v ->
        VehicleEditDialog(
            vehicle = v,
            onDismiss = { editingVehicle = null },
            onSave = { name, make, model, year, notes ->
                viewModel.updateVehicle(v.copy(name = name, make = make, model = model, year = year, notes = notes))
                editingVehicle = null
            }
        )
    }
}

@Composable
private fun VehicleEditDialog(
    vehicle: Vehicle?,
    onDismiss: () -> Unit,
    onSave: (name: String, make: String, model: String, year: Int, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(vehicle?.name ?: "") }
    var make by remember { mutableStateOf(vehicle?.make ?: "") }
    var model by remember { mutableStateOf(vehicle?.model ?: "") }
    var yearStr by remember { mutableStateOf(if ((vehicle?.year ?: 0) > 0) vehicle!!.year.toString() else "") }
    var notes by remember { mutableStateOf(vehicle?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (vehicle == null) stringResource(R.string.add_vehicle)
                else stringResource(R.string.edit_vehicle)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.vehicle_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = make,
                    onValueChange = { make = it },
                    label = { Text(stringResource(R.string.vehicle_make)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.vehicle_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = yearStr,
                    onValueChange = { yearStr = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.vehicle_year)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.vehicle_notes)) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), make.trim(), model.trim(), yearStr.toIntOrNull() ?: 0, notes.trim())
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = LocalZero100Colors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = c.textSecondary, fontSize = 13.sp)
        Text(value, color = c.textPrimary, fontSize = 13.sp)
    }
}

package com.ableLabs.zero100

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ableLabs.zero100.ui.screens.HistoryScreen
import com.ableLabs.zero100.ui.screens.MainScreen
import com.ableLabs.zero100.ui.screens.MeasureScreen
import com.ableLabs.zero100.ui.screens.OnboardingScreen
import com.ableLabs.zero100.ui.screens.SettingsScreen
import com.ableLabs.zero100.ui.theme.Zero100Theme
import com.ableLabs.zero100.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 화면 상시 켜짐 — 측정 중 화면 꺼짐 방지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()

        val prefs = getSharedPreferences("zero100_settings", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        setContent {
            Zero100Theme {
                Zero100App(
                    viewModel = viewModel,
                    isFirstRun = isFirstRun,
                    onOnboardingDone = {
                        prefs.edit().putBoolean("is_first_run", false).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun Zero100App(
    viewModel: MainViewModel,
    isFirstRun: Boolean,
    onOnboardingDone: () -> Unit
) {
    var showOnboarding by remember { mutableStateOf(isFirstRun) }

    if (showOnboarding) {
        OnboardingScreen(
            onFinish = {
                onOnboardingDone()
                showOnboarding = false
            }
        )
        return
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onNavigateToMeasure = { navController.navigate("measure") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("measure") {
            MeasureScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

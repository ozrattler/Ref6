package com.refsix.wear

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.refsix.wear.ui.screens.*
import com.refsix.wear.ui.theme.Ref6Theme
import com.refsix.wear.viewmodel.MatchUiEvent
import com.refsix.wear.viewmodel.MatchViewModel

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

        val prefs = getSharedPreferences("ref6_prefs", Context.MODE_PRIVATE)
        val airplaneReminderDone = prefs.getBoolean("airplane_reminder_shown", false)

        setContent {
            Ref6Theme {
                var showAirplaneReminder by remember { mutableStateOf(!airplaneReminderDone) }
                // Request location permissions so GPS tracking can start automatically.
                val locationPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { /* ViewModel re-checks on next startMatch */ }
                LaunchedEffect(Unit) {
                    locationPermLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BODY_SENSORS
                    ))
                }

                val navController = rememberSwipeDismissableNavController()
                val matchViewModel: MatchViewModel = viewModel()

                // Track current route to disable swipe-to-dismiss on report screens
                val backStack by navController.currentBackStack.collectAsState()
                val currentRoute = backStack.lastOrNull()?.destination?.route
                val swipeEnabled = currentRoute != "fullTime" &&
                    currentRoute != "report/{index}"

                // Vibrate on sin bin expiry and scheduled half end
                LaunchedEffect(Unit) {
                    matchViewModel.uiEvents.collect { event ->
                        when (event) {
                            is MatchUiEvent.SinBinExpired -> {
                                vibrator?.vibrate(
                                    VibrationEffect.createWaveform(
                                        longArrayOf(0, 300, 150, 300, 150, 300),
                                        -1
                                    )
                                )
                            }
                            is MatchUiEvent.HalfTimeAlert -> {
                                vibrator?.vibrate(
                                    VibrationEffect.createWaveform(
                                        longArrayOf(0, 600, 200, 600, 200, 600),
                                        -1
                                    )
                                )
                            }
                            is MatchUiEvent.FullTimeAlert -> {
                                vibrator?.vibrate(
                                    VibrationEffect.createWaveform(
                                        longArrayOf(0, 600, 150, 600, 150, 600, 150, 600),
                                        -1
                                    )
                                )
                            }
                            is MatchUiEvent.HalfTimeCountdownExpired -> {
                                vibrator?.vibrate(
                                    VibrationEffect.createWaveform(
                                        longArrayOf(0, 500, 150, 500, 150, 500, 150, 500, 150, 500),
                                        -1
                                    )
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "setup",
                    userSwipeEnabled = swipeEnabled
                ) {
                    composable("setup") {
                        SetupScreen(
                            viewModel = matchViewModel,
                            onStartMatch = {
                                navController.navigate("match") {
                                    popUpTo("setup") { inclusive = false }
                                }
                            },
                            onShowHistory = {
                                navController.navigate("history")
                            },
                            onShowSetupList = {
                                navController.navigate("setupList")
                            }
                        )
                    }

                    composable("setupList") {
                        MatchSetupListScreen(
                            viewModel = matchViewModel,
                            onSetupSelected = { navController.popBackStack() },
                            onCancel = { navController.popBackStack() }
                        )
                    }

                    composable("match") {
                        MatchScreen(
                            navController = navController,
                            viewModel = matchViewModel
                        )
                    }

                    composable("goal") {
                        GoalScreen(
                            viewModel = matchViewModel,
                            onGoalRecorded = { navController.popBackStack() }
                        )
                    }

                    composable("card") {
                        CardScreen(
                            viewModel = matchViewModel,
                            onCardRecorded = {
                                navController.popBackStack()
                                matchViewModel.signalReturnToCenter()
                            }
                        )
                    }

                    composable(
                        route = "card/{teamKey}/{cardTypeKey}",
                        arguments = listOf(
                            navArgument("teamKey") { type = NavType.StringType },
                            navArgument("cardTypeKey") { type = NavType.StringType }
                        )
                    ) { entry ->
                        CardScreen(
                            viewModel = matchViewModel,
                            teamKey = entry.arguments?.getString("teamKey"),
                            cardTypeKey = entry.arguments?.getString("cardTypeKey"),
                            onCardRecorded = {
                                navController.popBackStack()
                                matchViewModel.signalReturnToCenter()
                            }
                        )
                    }

                    composable(
                        route = "goalScorer/{teamKey}",
                        arguments = listOf(
                            navArgument("teamKey") { type = NavType.StringType }
                        )
                    ) { entry ->
                        GoalScorerScreen(
                            viewModel = matchViewModel,
                            teamKey = entry.arguments?.getString("teamKey") ?: "home",
                            onDone = {
                                navController.popBackStack()
                                matchViewModel.signalReturnToCenter()
                            }
                        )
                    }

                    composable("sinBin") {
                        SinBinScreen(
                            viewModel = matchViewModel,
                            onDismiss = { navController.popBackStack() }
                        )
                    }

                    composable("halfTime") {
                        HalfTimeScreen(
                            viewModel = matchViewModel,
                            onStopHalfTimeBreak = {
                                navController.navigate("kickOff2ndHalf")
                            }
                        )
                    }

                    composable("kickOff2ndHalf") {
                        KickOff2ndHalfScreen(
                            viewModel = matchViewModel,
                            onStartSecondHalf = {
                                navController.navigate("match") {
                                    popUpTo("match") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("fullTime") {
                        FullTimeScreen(
                            viewModel = matchViewModel,
                            onNewMatch = {
                                navController.navigate("setup") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("history") {
                        MatchHistoryScreen(
                            viewModel = matchViewModel,
                            navController = navController
                        )
                    }

                    composable(
                        route = "report/{index}",
                        arguments = listOf(
                            navArgument("index") { type = NavType.IntType }
                        )
                    ) { entry ->
                        val index = entry.arguments?.getInt("index") ?: 0
                        MatchReportScreen(
                            viewModel = matchViewModel,
                            index = index,
                            onDone = { navController.popBackStack() }
                        )
                    }
                }

                // One-time airplane mode reminder overlay
                if (showAirplaneReminder) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xEE000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A1A2A))
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "AIRPLANE MODE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64B5F6)
                            )
                            Text(
                                text = "Enable Airplane Mode before the match to prevent interruptions.",
                                fontSize = 12.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Chip(
                                label = { Text("Got it", fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showAirplaneReminder = false
                                    prefs.edit().putBoolean("airplane_reminder_shown", true).apply()
                                },
                                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1B4D1B)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                } // end Box
            }
        }
    }
}

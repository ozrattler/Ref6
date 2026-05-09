package com.refsix.wear

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.refsix.wear.data.MatchPhase
import com.refsix.wear.ui.screens.*
import com.refsix.wear.ui.theme.Ref6Theme
import com.refsix.wear.viewmodel.MatchUiEvent
import com.refsix.wear.viewmodel.MatchViewModel

class MainActivity : ComponentActivity() {

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

        setContent {
            Ref6Theme {
                val navController = rememberSwipeDismissableNavController()
                val matchViewModel: MatchViewModel = viewModel()
                val state by matchViewModel.state.collectAsState()
                val halfTimeCountdown by matchViewModel.halfTimeCountdown.collectAsState()

                // Track current route to disable swipe-to-dismiss on report screens
                val backStack by navController.currentBackStack.collectAsState()
                val currentRoute = backStack.lastOrNull()?.destination?.route
                val swipeEnabled = currentRoute != "fullTime" &&
                    currentRoute != "report/{index}"

                // Screen on while clock is running or half-time countdown is ticking.
                // Cleared when paused, at full time, or when the app goes to background
                // (onStop clears the flag unconditionally; LaunchedEffect re-applies on resume).
                val keepScreenOn = state.isRunning ||
                    (state.phase == MatchPhase.HALF_TIME && halfTimeCountdown > 0)
                LaunchedEffect(keepScreenOn) {
                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

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
                            is MatchUiEvent.MatchAbandoned -> { /* navigation handled by callback */ }
                        }
                    }
                }

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
                            viewModel = matchViewModel,
                            onAbandonMatch = {
                                navController.navigate("setup") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
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
            }
        }
    }
}

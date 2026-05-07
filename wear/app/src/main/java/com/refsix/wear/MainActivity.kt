package com.refsix.wear

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.refsix.wear.ui.screens.*
import com.refsix.wear.ui.theme.Ref6Theme
import com.refsix.wear.viewmodel.MatchUiEvent
import com.refsix.wear.viewmodel.MatchViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vibrator = getSystemService(Vibrator::class.java)

        setContent {
            Ref6Theme {
                val navController = rememberSwipeDismissableNavController()
                val matchViewModel: MatchViewModel = viewModel()
                val state by matchViewModel.state.collectAsState()

                // Keep screen on while the match clock is running
                LaunchedEffect(state.isRunning) {
                    if (state.isRunning) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Vibrate when a sin bin expires — works regardless of which screen is showing
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
                        }
                    }
                }

                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "setup"
                ) {
                    composable("setup") {
                        SetupScreen(
                            viewModel = matchViewModel,
                            onStartMatch = {
                                navController.navigate("match") {
                                    popUpTo("setup") { inclusive = false }
                                }
                            }
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
                            onCardRecorded = { navController.popBackStack() }
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
                }
            }
        }
    }
}

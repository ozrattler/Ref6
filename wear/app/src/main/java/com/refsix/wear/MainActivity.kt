package com.refsix.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.refsix.wear.ui.screens.*
import com.refsix.wear.ui.theme.Ref6Theme
import com.refsix.wear.viewmodel.MatchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Ref6Theme {
                val navController = rememberSwipeDismissableNavController()
                val matchViewModel: MatchViewModel = viewModel()

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

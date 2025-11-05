package com.browniepoints.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.browniepoints.app.presentation.ui.screen.ConnectionScreen
import com.browniepoints.app.presentation.ui.screen.DeductPointsScreen
import com.browniepoints.app.presentation.ui.screen.GivePointsScreen
import com.browniepoints.app.presentation.ui.screen.MainScreen
import com.browniepoints.app.presentation.ui.screen.SignInScreen
import com.browniepoints.app.presentation.ui.screen.TimeoutHistoryScreen
import com.browniepoints.app.presentation.ui.screen.TransactionHistoryScreen
import com.browniepoints.app.presentation.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignInClient

/**
 * Main navigation component for the Brownie Points app
 * Handles navigation between authentication and main app screens with proper screen transitions
 */
@Composable
fun BrowniePointsNavigation(
    navController: NavHostController = rememberNavController(),
    googleSignInClient: GoogleSignInClient,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()

    // Observe authentication state changes and navigate accordingly
    LaunchedEffect(authState.isAuthenticated, authState.isSignedIn) {
        android.util.Log.d("Navigation", "Auth state changed: isAuthenticated=${authState.isAuthenticated}, isSignedIn=${authState.isSignedIn}, currentUser=${authState.currentUser?.uid}")
        
        if (authState.isAuthenticated && authState.isSignedIn) {
            // User signed in, navigate to main if not already there
            val currentRoute = navController.currentDestination?.route
            android.util.Log.d("Navigation", "User authenticated, current route: $currentRoute")
            
            if (currentRoute != BrowniePointsDestinations.MAIN) {
                android.util.Log.d("Navigation", "Navigating to main screen")
                navController.navigate(BrowniePointsDestinations.MAIN) {
                    popUpTo(BrowniePointsDestinations.SIGN_IN) {
                        inclusive = true
                    }
                }
            }
        } else if (!authState.isLoading && !authState.isSignedIn) {
            // User signed out or not authenticated, navigate to sign in if not already there
            val currentRoute = navController.currentDestination?.route
            android.util.Log.d("Navigation", "User not authenticated, current route: $currentRoute")
            
            if (currentRoute != BrowniePointsDestinations.SIGN_IN) {
                android.util.Log.d("Navigation", "Navigating to sign-in screen")
                navController.navigate(BrowniePointsDestinations.SIGN_IN) {
                    popUpTo(0) {
                        inclusive = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = BrowniePointsDestinations.SIGN_IN
    ) {
        composable(BrowniePointsDestinations.SIGN_IN) {
            SignInScreen(
                onNavigateToMain = {
                    navController.navigate(BrowniePointsDestinations.MAIN) {
                        popUpTo(BrowniePointsDestinations.SIGN_IN) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        composable(BrowniePointsDestinations.MAIN) {
            // Handle back button on main screen - prevent accidental exit
            BackHandler {
                // Do nothing - user must explicitly sign out
            }
            
            MainScreen(
                onSignOut = {
                    authViewModel.signOut()
                    // Navigation will be handled by LaunchedEffect observing auth state
                },
                onNavigateToConnection = {
                    navController.navigate(BrowniePointsDestinations.CONNECTION)
                },
                onNavigateToGivePoints = {
                    navController.navigate(BrowniePointsDestinations.GIVE_POINTS)
                },
                onNavigateToDeductPoints = {
                    navController.navigate(BrowniePointsDestinations.DEDUCT_POINTS)
                },
                onNavigateToHistory = {
                    navController.navigate(BrowniePointsDestinations.TRANSACTION_HISTORY)
                },
                onNavigateToTimeoutHistory = {
                    navController.navigate(BrowniePointsDestinations.TIMEOUT_HISTORY)
                }
            )
        }

        composable(BrowniePointsDestinations.CONNECTION) {
            ConnectionScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onConnectionSuccess = {
                    // Navigate back to main screen after successful connection
                    navController.popBackStack(
                        route = BrowniePointsDestinations.MAIN,
                        inclusive = false
                    )
                }
            )
        }

        composable(BrowniePointsDestinations.GIVE_POINTS) {
            GivePointsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPointsGiven = {
                    // Navigate back to main screen and clear this screen from back stack
                    navController.popBackStack(
                        route = BrowniePointsDestinations.MAIN,
                        inclusive = false
                    )
                }
            )
        }

        composable(BrowniePointsDestinations.DEDUCT_POINTS) {
            DeductPointsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPointsDeducted = {
                    // Navigate back to main screen and clear this screen from back stack
                    navController.popBackStack(
                        route = BrowniePointsDestinations.MAIN,
                        inclusive = false
                    )
                }
            )
        }

        composable(BrowniePointsDestinations.TRANSACTION_HISTORY) {
            TransactionHistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(BrowniePointsDestinations.TIMEOUT_HISTORY) {
            TimeoutHistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * Navigation destinations for the app
 * Defines all available screens and their route constants
 */
object BrowniePointsDestinations {
    const val SIGN_IN = "sign_in"
    const val MAIN = "main"
    const val CONNECTION = "connection"
    const val GIVE_POINTS = "give_points"
    const val DEDUCT_POINTS = "deduct_points"
    const val TRANSACTION_HISTORY = "transaction_history"
    const val TIMEOUT_HISTORY = "timeout_history"
}
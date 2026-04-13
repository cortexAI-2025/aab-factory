package com.aabfactory.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aabfactory.app.presentation.auth.AuthScreen
import com.aabfactory.app.presentation.auth.AuthViewModel
import com.aabfactory.app.presentation.build.BuildScreen
import com.aabfactory.app.presentation.editor.EditorScreen
import com.aabfactory.app.presentation.home.HomeScreen
import com.aabfactory.app.presentation.onboarding.OnboardingScreen
import com.aabfactory.app.presentation.preview.PreviewScreen
import com.aabfactory.app.presentation.subscription.SubscriptionScreen

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Onboarding : Screen("onboarding")
    object Editor : Screen("editor/{projectId}") {
        fun createRoute(projectId: String) = "editor/$projectId"
    }
    object Preview : Screen("preview/{projectId}") {
        fun createRoute(projectId: String) = "preview/$projectId"
    }
    object Build : Screen("build/{projectId}") {
        fun createRoute(projectId: String) = "build/$projectId"
    }
    object Subscription : Screen("subscription")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()

    // Route to auth or home based on current auth state
    LaunchedEffect(authState.isLoggedIn) {
        if (!authState.isLoggedIn) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (authState.isLoggedIn) Screen.Home.route else Screen.Auth.route
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNewProject = {
                    navController.navigate(Screen.Onboarding.route)
                },
                onOpenProject = { projectId ->
                    navController.navigate(Screen.Editor.createRoute(projectId))
                },
                onUpgrade = {
                    navController.navigate(Screen.Subscription.route)
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onProjectCreated = { projectId ->
                    navController.navigate(Screen.Editor.createRoute(projectId)) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            EditorScreen(
                projectId = projectId,
                onPreview = { navController.navigate(Screen.Preview.createRoute(projectId)) },
                onBuild = { navController.navigate(Screen.Build.createRoute(projectId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Preview.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            PreviewScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onBuild = { navController.navigate(Screen.Build.createRoute(projectId)) }
            )
        }

        composable(
            route = Screen.Build.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            BuildScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onUpgrade = { navController.navigate(Screen.Subscription.route) }
            )
        }

        composable(Screen.Subscription.route) {
            SubscriptionScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

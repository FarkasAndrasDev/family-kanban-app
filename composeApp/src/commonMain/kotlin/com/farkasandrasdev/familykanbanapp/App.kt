package com.farkasandrasdev.familykanbanapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.farkasandrasdev.familykanbanapp.ui.LoginScreen
import com.farkasandrasdev.familykanbanapp.ui.ProfileMenuIcon
import com.farkasandrasdev.familykanbanapp.ui.ProfileScreen

private object Routes {
    const val BOARD = "board"
    const val PROFILE = "profile"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(authViewModel: AuthViewModel = viewModel { AuthViewModel() }) {
    val authState by authViewModel.state.collectAsState()

    MaterialTheme {
        when (val state = authState) {
            is AuthState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is AuthState.Unauthenticated, is AuthState.Error -> {
                LoginScreen(
                    isLoading = state is AuthState.Loading,
                    errorMessage = (state as? AuthState.Error)?.message,
                    onSignIn = { email, password -> authViewModel.signIn(email, password) }
                )
            }

            is AuthState.Authenticated -> {
                val navController = rememberNavController()
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Family Kanban") },
                            actions = {
                                if (currentRoute == Routes.BOARD) {
                                    ProfileMenuIcon(
                                        profile = state.profile,
                                        onOpenProfile = { navController.navigate(Routes.PROFILE) }
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.BOARD,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Routes.BOARD) {
                            // TODO: replace with real KanbanBoardScreen
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Kanban Board — coming soon")
                            }
                        }
                        composable(Routes.PROFILE) {
                            ProfileScreen(
                                profile = state.profile,
                                onBack = { navController.popBackStack() },
                                onSignOut = { authViewModel.signOut() }
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.knovik.skillvault.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.knovik.skillvault.ui.resume_list.ResumeListScreen
import com.knovik.skillvault.ui.search.SearchScreen
import com.knovik.skillvault.ui.theme.SkillVaultTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity for SkillVault app.
 * Uses Jetpack Compose with bottom navigation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkillVaultTheme {
                MainScreen()
            }
        }
    }
}

/**
 * Navigation destinations.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object ResumeList : Screen("resume_list", "Resumes", Icons.Filled.Home)
    data object Search : Screen("search", "Search", Icons.Filled.Search)
}

/**
 * Main screen with bottom navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(Screen.ResumeList, Screen.Search)
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.ResumeList.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.ResumeList.route) {
                ResumeListScreen()
            }
            composable(Screen.Search.route) {
                SearchScreen()
            }
        }
    }
}

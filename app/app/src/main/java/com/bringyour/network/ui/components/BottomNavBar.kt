package com.bringyour.network.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black

data class BottomNavigationItem(
    val selectedIcon: Int,
    val unselectedIcon: Int,
    val route: String,
)

@Composable
fun BottomNavBar(navController: NavHostController) {

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        BottomNavigationItem(
            selectedIcon = R.drawable.main_nav_globe_filled,
            unselectedIcon = R.drawable.main_nav_globe,
            route = "connect"
        ),
        BottomNavigationItem(
            selectedIcon = R.drawable.main_nav_user_filled,
            unselectedIcon = R.drawable.main_nav_user,
            route = "account"
        ),
        BottomNavigationItem(
            selectedIcon = R.drawable.main_nav_chat_filled,
            unselectedIcon = R.drawable.main_nav_chat,
            route = "support"
        ),
    )

    NavigationBar(
        containerColor = Black
    ) {
        items.forEach { screen ->
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        // from the docs at https://developer.android.com/develop/ui/compose/navigation

                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                },
                icon = {
                    val iconRes = if (currentDestination?.hierarchy?.any { it.route == screen.route } == true) {
                        screen.selectedIcon
                    } else {
                        screen.unselectedIcon
                    }
                    Icon(painterResource(id = iconRes), contentDescription = "Connect") },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent // Transparent to remove the indicator
                )
            )
        }
    }
}
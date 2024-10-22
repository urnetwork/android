package com.bringyour.network.ui

import androidx.lifecycle.ViewModel
import com.bringyour.network.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlinx.serialization.Serializable

@HiltViewModel
class MainNavViewModel @Inject constructor(): ViewModel() {
    private val _currentTopLevelRoute = MutableStateFlow(TopLevelScaffoldRoutes.CONNECT_CONTAINER)
    val currentTopLevelRoute: StateFlow<TopLevelScaffoldRoutes> = _currentTopLevelRoute.asStateFlow()

    val setCurrentTopLevelRoute: (TopLevelScaffoldRoutes) -> Unit = { route ->
        _currentTopLevelRoute.value = route
    }

    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute.asStateFlow()

    val setCurrentRoute: (Route?) -> Unit = { route ->
        _previousRoute.value = _currentRoute.value
        _currentRoute.value = route
    }

    private val _previousRoute = MutableStateFlow<Route?>(null)
    val previousRoute: StateFlow<Route?> = _previousRoute.asStateFlow()
}

@Serializable
sealed class Route {

    // this is from https://stackoverflow.com/questions/78489838/unable-to-get-route-object-from-currentbackstackentry-in-compose-navigation-outs
    // in order to compare NavDestination.destination with our routes
    companion object {
        fun fromString(route: String): Route? {
            return Route::class.sealedSubclasses.firstOrNull {
                route.contains(it.qualifiedName.toString())
            }?.objectInstance
        }
    }

    @Serializable object ConnectContainer: Route()
    @Serializable object Connect : Route()
    @Serializable object BrowseLocations : Route()
    @Serializable object AccountContainer : Route()
    @Serializable object Account : Route()
    @Serializable object Support : Route()
    @Serializable object Profile : Route()
    @Serializable object Settings : Route()
    @Serializable object Wallets : Route()
    @Serializable data class Wallet(val id: String) : Route()
}


enum class TopLevelScaffoldRoutes(
    val selectedIcon: Int,
    val unselectedIcon: Int,
    val description: String,
    val route: Route
) {
    CONNECT_CONTAINER(
        R.drawable.main_nav_globe_filled,
        R.drawable.main_nav_globe,
        "Connect",
        route = Route.ConnectContainer
    ),
    ACCOUNT_CONTAINER(
        R.drawable.main_nav_user_filled,
        R.drawable.main_nav_user,
        "Account",
        route = Route.AccountContainer
    ),
    SUPPORT(
        R.drawable.main_nav_chat_filled,
        R.drawable.main_nav_chat,
        "Support",
        route = Route.Support
    )
}
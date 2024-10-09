package com.bringyour.network.ui

import androidx.lifecycle.ViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.connect.FetchLocationsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainNavViewModel @Inject constructor(): ViewModel() {
    private val _currentTopLevelRoute = MutableStateFlow(TopLevelScaffoldRoutes.CONNECT)
    val currentTopLevelRoute: StateFlow<TopLevelScaffoldRoutes> = _currentTopLevelRoute.asStateFlow()

    val setCurrentTopLevelRoute: (TopLevelScaffoldRoutes) -> Unit = { route ->
        _currentTopLevelRoute.value = route
    }

    private val _lastAccountRoute = MutableStateFlow(Route.ACCOUNT)
    val lastAccountRoute: StateFlow<Route> = _lastAccountRoute.asStateFlow()

    val setLastAccountRoute: (Route) -> Unit = { accountRoute ->
        _lastAccountRoute.value = accountRoute
    }

}

enum class Route {
    CONNECT,
    ACCOUNT_CONTAINER,
    ACCOUNT,
    SUPPORT,
    PROFILE,
    SETTINGS,
    WALLETS
}

enum class TopLevelScaffoldRoutes(
    val selectedIcon: Int,
    val unselectedIcon: Int,
    val description: String,
    val route: Route
) {
    CONNECT(R.drawable.main_nav_globe_filled, R.drawable.main_nav_globe, "Connect", route = Route.CONNECT),
    ACCOUNT(R.drawable.main_nav_user_filled, R.drawable.main_nav_user, "Account", route = Route.ACCOUNT_CONTAINER),
    SUPPORT(R.drawable.main_nav_chat_filled, R.drawable.main_nav_chat, "Support", route = Route.SUPPORT)
}
package com.bringyour.network.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bringyour.network.MainApplication
import com.bringyour.network.widgets.WidgetEntry
import com.bringyour.network.widgets.WidgetSnapshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.widgets.QuickConnectAndWidgets
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme

/**
 * Account > Widgets: the quick connect tile and the home screen widgets with
 * the way to add each, exactly as the last onboarding page shows them
 * ([QuickConnectAndWidgets] is the one body both render).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // the live entry: what the pinned widgets render right now, re-read on
    // every snapshot publish while the screen is resumed
    var entry by remember { mutableStateOf<WidgetEntry?>(null) }
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        // while we are on screen the writer treats every widget as placed, so
        // the providers and contracts a widget would show are live here too
        val writer = (context.applicationContext as? MainApplication)?.widgetSnapshotWriter
        writer?.setPreviewVisible(true)
        try {
            WidgetSnapshotStore.changes.collect {
                entry = withContext(Dispatchers.IO) { WidgetEntry.loadOrSample(context) }
            }
        } finally {
            writer?.setPreviewVisible(false)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.widgets),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(id = R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
                actions = {},
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            entry?.let { QuickConnectAndWidgets(entry = it) }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview
@Composable
private fun WidgetsScreenPreview() {
    URNetworkTheme {
        WidgetsScreen(navController = rememberNavController())
    }
}

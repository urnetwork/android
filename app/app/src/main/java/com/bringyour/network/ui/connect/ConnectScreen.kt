package com.bringyour.network.ui.connect

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.bringyour.client.ConnectLocation
import com.bringyour.client.Sub
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
) {

    val scaffoldState = rememberBottomSheetScaffoldState()

    val context = LocalContext.current
    val application = context.applicationContext as MainApplication
    val activity = context as? MainActivity

    val connectVc = application.connectVc
    var activeLocation by remember { mutableStateOf<ConnectLocation?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    val subs = remember { mutableListOf<Sub>() }

    val setActiveLocation: (ConnectLocation?) -> Unit = { location ->
        activeLocation = location
        isConnecting = false
    }

    val addConnectionListener = {
        if (connectVc != null) {
            subs.add(connectVc.addConnectionListener { location ->

                runBlocking(Dispatchers.Main.immediate) {

                    setActiveLocation(location)

                    if (application.isVpnRequestStart()) {
                        // user might need to grant permissions
                        activity?.requestPermissionsThenStartVpnServiceWithRestart()
                    }
                }
            })
        }
    }

    DisposableEffect(Unit) {

        Log.i("ConnectScreen", "DisposableEffect called")

        // init subs
        addConnectionListener()

        // when closing
        onDispose {

            Log.i("ConnectScreen", "DisposableEffect onDispose called")

            subs.forEach { sub ->
                sub.close()
            }
            subs.clear()
        }

    }


    ProvidersBottomSheetScaffold(
        scaffoldState,
        connectVc,
        activeLocation,
        onLocationSelect = {
            isConnecting = true
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Black),
            contentAlignment = Alignment.Center
        ) {
            Column {
                Text("Is connecting: $isConnecting")

                if (activeLocation != null) {
                    Text("Connected to ${activeLocation?.name}")
                    Button(
                        onClick = {
                            connectVc?.disconnect()
                        }
                    ) {
                        Text("Disconnect")
                    }
                } else {
                    Text("Not connected")
                }
            }
        }
    }
}

@Preview
@Composable
fun ConnectPreview() {

    URNetworkTheme {
        ConnectScreen(
        )
    }
}
package com.bringyour.network.ui.connect

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.client.ConnectLocation
import com.bringyour.client.Sub
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.bringyour.network.R

enum class ConnectStatus {
    DISCONNECTED, CONNECTING, CONNECTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
) {

    val scaffoldState = rememberBottomSheetScaffoldState()

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val activity = context as? MainActivity

    val connectVc = application?.connectVc
    var activeLocation by remember { mutableStateOf<ConnectLocation?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    val subs = remember { mutableListOf<Sub>() }
    var connectStatus by remember { mutableStateOf<ConnectStatus>(ConnectStatus.DISCONNECTED) }

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

    LaunchedEffect(isConnecting, activeLocation) {
        if (isConnecting) {
            connectStatus = ConnectStatus.CONNECTING
        }

        if (!isConnecting && activeLocation == null) {
            connectStatus = ConnectStatus.DISCONNECTED
        }

        if (!isConnecting && activeLocation != null) {
            connectStatus = ConnectStatus.CONNECTED
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
                // Text("Is connecting: $isConnecting")

                if (activeLocation != null) {
                    Text("Connected to ${activeLocation?.name}")
                    Button(
                        onClick = {
                            connectVc?.disconnect()
                        }
                    ) {

                        // Icon(painterResource(id = R.drawable.connect_button_bg), contentDescription = "Connect")
                        Text("Disconnect")

                    }
                } else {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center

                    ) {
                        ConnectButton(
                            onClick = {},
                            connectStatus = connectStatus
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    ConnectStatusIndicator(
                        text = when(connectStatus) {
                            ConnectStatus.CONNECTED -> "Connected to ${activeLocation?.providerCount ?: 0} providers"
                            ConnectStatus.CONNECTING -> "Connecting to providers..."
                            // todo - username
                            ConnectStatus.DISCONNECTED -> "username is ready to connect"
                        },
                        status = connectStatus
                    )

                }
            }
        }
    }
}

@Preview
@Composable
fun ConnectPreview() {

    URNetworkTheme {
        ConnectScreen()
    }
}
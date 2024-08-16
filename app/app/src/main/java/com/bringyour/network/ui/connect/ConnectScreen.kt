package com.bringyour.network.ui.connect

import android.util.Log
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.client.ConnectLocation
import com.bringyour.client.Sub
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

enum class ConnectStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CANCELING;

    companion object {
        fun fromString(value: String): ConnectStatus? {
            return when (value.uppercase()) {
                "DISCONNECTED" -> DISCONNECTED
                "CONNECTING" -> CONNECTING
                "CONNECTED" -> CONNECTED
                "CANCELING" ->CANCELING
                else -> null // or throw IllegalArgumentException("Unknown ProvideMode: $value")
            }
        }
    }
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
    var selectedLocation by remember { mutableStateOf<ConnectLocation?>(null) }
    var connectedProviderCount by remember { mutableIntStateOf(0) }
    val subs = remember { mutableListOf<Sub>() }
    var connectStatus by remember { mutableStateOf(ConnectStatus.DISCONNECTED) }
    var networkName by remember { mutableStateOf<String?>(null) }

    val populateNetworkName = {
        application?.asyncLocalState?.parseByJwt { byJwt, ok ->
            runBlocking(Dispatchers.Main.immediate) {
                if (ok) {
                    networkName = byJwt.networkName
                }
            }
        }
    }

    val initSelectedLocation = {
        selectedLocation = connectVc?.selectedLocation
    }

    val initConnectedProviderCount = {
        connectedProviderCount = connectVc?.connectedProviderCount ?: 0
    }

    val getConnectionStatus = {
        val status = connectVc?.connectionStatus
        if (status != null) {
            val statusFromStr = ConnectStatus.fromString(status)
            if (statusFromStr != null) {
                connectStatus = statusFromStr
            }
        }
    }

    val addSelectedLocationListener = {
        if (connectVc != null) {
            subs.add(connectVc.addSelectedLocationListener { location ->
                runBlocking(Dispatchers.Main.immediate) {
                    selectedLocation = location
                }
            })
        }
    }

    val addConnectionStatusListener = {
        if (connectVc != null) {
            subs.add(connectVc.addConnectionStatusListener { status ->
                runBlocking(Dispatchers.Main.immediate) {
                    val statusFromStr = ConnectStatus.fromString(status)
                    if (statusFromStr != null) {
                        connectStatus = statusFromStr

                        if (connectStatus == ConnectStatus.CONNECTED && application.isVpnRequestStart()) {
                            activity?.requestPermissionsThenStartVpnServiceWithRestart()
                        }
                    }
                }
            })
        }
    }

    val addConnectedProviderCountListener = {
        if (connectVc != null) {
            subs.add(connectVc.addConnectedProviderCountListener { count ->
                runBlocking(Dispatchers.Main.immediate) {
                    connectedProviderCount = count
                }
            })
        }
    }

    LaunchedEffect(Unit) {
        populateNetworkName()
        initSelectedLocation()
        getConnectionStatus()
        initConnectedProviderCount()
    }

    DisposableEffect(Unit) {

        Log.i("ConnectScreen", "DisposableEffect called")

        // init subs
        addConnectionStatusListener()
        addSelectedLocationListener()
        addConnectedProviderCountListener()

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
        selectedLocation,
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
                .padding(16.dp),
            // contentAlignment = Alignment.Center
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AccountSwitcher(loginMode = LoginMode.Authenticated)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center

                ) {
                    ConnectButton(
                        onClick = {
                            if (connectStatus == ConnectStatus.DISCONNECTED) {
                                
                                if (selectedLocation != null) {
                                    connectVc?.connect(selectedLocation)
                                } else {
                                    connectVc?.connectBestAvailable()
                                }

                            }
                        },
                        connectStatus = connectStatus
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                ConnectStatusIndicator(
                    text = when(connectStatus) {
                        ConnectStatus.CONNECTED -> "Connected to $connectedProviderCount providers"
                        ConnectStatus.CONNECTING -> "Connecting to providers..."
                        ConnectStatus.CANCELING -> "Canceling connection..."
                        // todo - username
                        ConnectStatus.DISCONNECTED -> if (networkName != null) "$networkName is ready to connect"
                            else "ready to connect"
                    },
                    status = connectStatus
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (connectStatus == ConnectStatus.CONNECTED) {

                        URButton(
                            onClick = {
                                connectVc?.disconnect()
                            },
                            style = ButtonStyle.OUTLINE
                        ) { buttonTextStyle ->
                            Text("Disconnect", style = buttonTextStyle)
                        }

                    } else if (connectStatus == ConnectStatus.CONNECTING || connectStatus == ConnectStatus.CANCELING) {

                        // todo - we should only show cancel after connecting is over ~2 seconds
                        URButton(
                            onClick = {
                                connectVc?.cancelConnection()
                            },
                            style = ButtonStyle.OUTLINE,
                            enabled = connectStatus == ConnectStatus.CONNECTING
                        ) { buttonTextStyle ->
                            Text("Cancel", style = buttonTextStyle)
                        }
                    }
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
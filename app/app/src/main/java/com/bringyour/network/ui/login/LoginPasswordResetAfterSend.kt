package com.bringyour.network.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.client.AuthPasswordResetArgs
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginPasswordResetAfterSend(
    userAuth: String,
    navController: NavController
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MainApplication
    var markAsSent by remember { mutableStateOf(false) }
    var inProgress by remember { mutableStateOf(false) }
    var passwordResetError by remember { mutableStateOf<String?>(null) }
    val isBtnEnabled by remember {
        derivedStateOf {
            !inProgress && !markAsSent
        }
    }

    val sendResetLink = {
        val args = AuthPasswordResetArgs()
        args.userAuth = userAuth.trim()

        inProgress = true

        app?.byApi?.authPasswordReset(args) { _, err ->
            runBlocking(Dispatchers.Main.immediate) {
                inProgress = false

                if (err != null) {
                    passwordResetError = err.message
                } else {
                    passwordResetError = null

                    markAsSent = true
                }
            }
        }
    }

    LaunchedEffect(markAsSent) {
        if (markAsSent) {
            delay(5000L)
            markAsSent = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Reset link sent", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(64.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    "Reset link sent to $userAuth",
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "You may need to check your spam folder or unblock no-reply@bringyour.com",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = TextMuted
                )
            )
            Spacer(modifier = Modifier.height(32.dp))

            URButton(
                onClick = {
                    sendResetLink()
                },
                enabled = isBtnEnabled
            ) { buttonTextStyle ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (markAsSent) "Sent!" else "Resend Link",
                        style = buttonTextStyle
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun LoginPasswordResetAfterSendPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LoginPasswordResetAfterSend(
                    userAuth = "hello@bringyour.com",
                    navController
                )
            }
        }
    }
}
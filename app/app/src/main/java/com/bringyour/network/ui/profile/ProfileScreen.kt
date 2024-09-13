package com.bringyour.network.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.client.AuthPasswordResetArgs
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.SnackBarType
import com.bringyour.network.ui.components.URSnackBar
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking


@Composable
fun ProfileScreen(
    navController: NavController,
    accountViewModel: AccountViewModel,
) {

    ProfileScreen(
        navController = navController,
        currentNetworkName = accountViewModel.networkName,
        loginMode = accountViewModel.loginMode,
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    currentNetworkName: String?,
    loginMode: LoginMode,
) {
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication

    var networkName by remember { mutableStateOf(currentNetworkName) }
    var sendingResetPassLink by remember { mutableStateOf(false) }
    var passwordResetError by remember { mutableStateOf<String?>(null) }
    // todo populate this from the api
    var userAuth by remember { mutableStateOf<String?>(null) }
    var markPasswordResetAsSent by remember { mutableStateOf(false) }
    val resendBtnEnabled by remember {
        derivedStateOf {
            passwordResetError == null &&
                    userAuth != null &&
                    !sendingResetPassLink &&
                    !markPasswordResetAsSent
        }
    }

    val sendResetLink = {
        if (userAuth != null) {
            val args = AuthPasswordResetArgs()
            args.userAuth = userAuth?.trim()

            sendingResetPassLink = true

            application?.api?.authPasswordReset(args) { _, err ->
                runBlocking(Dispatchers.Main.immediate) {
                    sendingResetPassLink = false

                    if (err != null) {
                        passwordResetError = err.message
                    } else {
                        passwordResetError = null
                        markPasswordResetAsSent = true
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Profile", style = TopBarTitleTextStyle)
                },
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
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Profile", style = MaterialTheme.typography.headlineSmall)
                AccountSwitcher(loginMode = loginMode)
            }
            Spacer(modifier = Modifier.height(64.dp))

            URTextInput(
                value = TextFieldValue(""),
                onValueChange = {},
                enabled = false,
                label = "Name"
            )

            URTextInput(
                value = TextFieldValue(""),
                onValueChange = {},
                enabled = false,
                label = "Email or phone number"
            )

            URTextInput(
                value = TextFieldValue(networkName ?: ""),
                onValueChange = {},
                enabled = false,
                label = "Network name"
            )

            ClickableText(
                text = AnnotatedString("Edit profile"),
                onClick = {},
                style = TextStyle(
                    color = BlueMedium
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            URTextInput(
                value = TextFieldValue(""),
                onValueChange = {},
                enabled = false,
                label = "Password",
                isPassword = true,
            )

            ClickableText(
                text = AnnotatedString("Change Password"),
                onClick = {
                    if (resendBtnEnabled) {
                        sendResetLink()
                    }
                },
                style = TextStyle(
                    color = BlueMedium
                )
            )
        }
        URSnackBar(
            type = if (markPasswordResetAsSent) SnackBarType.SUCCESS else SnackBarType.ERROR,
            isVisible = markPasswordResetAsSent || passwordResetError != null,
            onDismiss = {
                if (passwordResetError != null) {
                    passwordResetError = null
                }
                if (markPasswordResetAsSent) {
                    markPasswordResetAsSent = false
                }
            }
        ) {
            if (markPasswordResetAsSent) {
                Column() {
                    Text("Verification email sent to $userAuth")
                }
            } else {
                Column() {
                    Text("Something went wrong.")
                    Text("Please wait a few minutes and try again.")
                }
            }
        }
    }
}

@Preview
@Composable
fun ProfileScreenPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        ProfileScreen(
            navController,
            loginMode = LoginMode.Authenticated,
            currentNetworkName = "my_network"
        )
    }
}
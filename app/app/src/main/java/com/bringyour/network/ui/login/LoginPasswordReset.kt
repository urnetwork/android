package com.bringyour.network.ui.login

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.sdk.AuthPasswordResetArgs
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginPasswordReset(
    userAuth: String,
    navController: NavController
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MainApplication
    var user by remember { mutableStateOf(TextFieldValue(userAuth)) }
    var inProgress by remember { mutableStateOf(false) }
    var passwordResetError by remember { mutableStateOf<String?>(null) }
    val isBtnEnabled by remember {
        derivedStateOf {
            !inProgress && (Patterns.EMAIL_ADDRESS.matcher(user.text).matches() ||
                    Patterns.PHONE.matcher(user.text).matches())
        }
    }
    val titleSize: TextUnit = dimensionResource(id = R.dimen.login_title_size).value.sp

    val sendResetLink = {
        val args = AuthPasswordResetArgs()
        args.userAuth = user.text.trim()

        inProgress = true

        app?.api?.authPasswordReset(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {
                inProgress = false

                if (err != null) {
                    passwordResetError = err.message
                } else {
                    passwordResetError = null

                    navController.navigate("reset-password-after-send/${userAuth}") {
                        popUpTo("login-initial") { inclusive = false }
                    }
                }
            }
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
        },
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 512.dp)
                    .padding(top = 16.dp, start = 16.dp, bottom = 124.dp, end = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Column(
                    modifier = Modifier
                        .imePadding()
                ) {
                    Text(
                        stringResource(id = R.string.forgot_password),
                        style = MaterialTheme.typography.headlineLarge,
                        fontSize = titleSize
                    )

                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.login_margin_lg)))

                    URTextInput(
                        value = user,
                        onValueChange = { newValue ->
                            user = newValue
                        },
                        placeholder = stringResource(id = R.string.user_auth_placeholder),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Go
                        ),
                        onGo = {
                            sendResetLink()
                        },
                        label = stringResource(id = R.string.user_auth_label)
                    )


                    Text(
                        stringResource(id = R.string.check_junk_mail),
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
                            Text(stringResource(id = R.string.send_reset_link), style = buttonTextStyle)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Right Arrow",
                                modifier = Modifier.size(16.dp),
                                tint = if (isBtnEnabled) Color.White else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun LoginPasswordResetPreview() {
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
                LoginPasswordReset(
                    userAuth = "hello@urnetwork.com",
                    navController
                )
            }
        }
    }
}


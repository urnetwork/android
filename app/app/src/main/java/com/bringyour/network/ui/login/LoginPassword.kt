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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.client.AuthLoginWithPasswordArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginPassword(
    userAuth: String,
    navController: NavController
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MainApplication
    val loginActivity = context as? LoginActivity
    var user by remember { mutableStateOf(TextFieldValue(userAuth)) }
    var password by remember { mutableStateOf(TextFieldValue()) }
    var inProgress by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    val login = {
        inProgress = true

        val args = AuthLoginWithPasswordArgs()
        args.userAuth = user.text
        args.password = password.text

        app?.byApi?.authLoginWithPassword(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {
                inProgress = false

                if (err != null) {
                    loginError = err.message
                } else if (result.error != null) {
                    loginError = result.error.message
                } else if (result.network != null) {
                    loginError = null

                    if (result.verificationRequired != null) {

                        navController.navigate("verify/${userAuth}") {
                            popUpTo("login-initial") { inclusive = false }
                        }

                    } else {
                        app.login(result.network.byJwt)

                        inProgress = true

                        loginActivity?.authClientAndFinish { error ->
                            inProgress = false
                            loginError = error
                        }
                    }

                } else {
                    loginError = context.getString(R.string.login_error)
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
            Text("It's nice to", style = MaterialTheme.typography.headlineLarge)
            Text("see you again", style = MaterialTheme.typography.headlineLarge)

            Spacer(modifier = Modifier.height(64.dp))

            URTextInput(
                value = user,
                onValueChange = { newValue ->
                    user = newValue
                },
                placeholder = "Enter your phone number or email",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                label = "Email or phone"
            )

            URTextInput(
                value = password,
                onValueChange = { newValue ->
                    password = newValue
                },
                placeholder = "*****************",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                isPassword = true,
                label = "Password"
            )

            Spacer(modifier = Modifier.height(16.dp))

            URButton(
                onClick = {
                    login()
                },
                enabled = !inProgress
            ) { buttonTextStyle ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Continue", style = buttonTextStyle)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Right Arrow",
                        modifier = Modifier.size(16.dp),
                        tint = if (!inProgress) Color.White else Color.Gray
                    )
                }
            }

            if (loginError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("$loginError")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Forget your password?")
                Spacer(modifier = Modifier.width(4.dp))
                ClickableText(
                    text = AnnotatedString("Reset it."),
                    onClick = {
                        navController.navigate("reset-password/${userAuth}")
                    },
                    style = TextStyle(
                        color = BlueMedium,
                        fontSize = 16.sp
                    )
                )
            }
        }
    }

}

@Preview()
@Composable
private fun LoginPasswordPreview() {

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
                LoginPassword(
                    userAuth = "hello@urnetwork.com",
                    navController
                )
            }
        }
    }
}
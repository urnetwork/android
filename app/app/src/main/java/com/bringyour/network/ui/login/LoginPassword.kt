package com.bringyour.network.ui.login

import android.view.View
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.bringyour.client.AuthLoginWithPasswordArgs
import com.bringyour.client.BringYourApi
import com.bringyour.network.LoginActivity
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Composable
fun LoginPassword(
    userAuth: String,
    appLogin: (String) -> Unit,
    onResetPassword: () -> Unit,
    loginActivity: LoginActivity?,
    byApi: BringYourApi?,
) {
    val context = LocalContext.current
    var user by remember { mutableStateOf(TextFieldValue(userAuth)) }
    var password by remember { mutableStateOf(TextFieldValue()) }
    var inProgress by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    val login = {
        inProgress = true

        val args = AuthLoginWithPasswordArgs()
        args.userAuth = user.text
        args.password = password.text

        byApi?.authLoginWithPassword(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {
                inProgress = false

                if (err != null) {
                    loginError = err.message
                } else if (result.error != null) {
                    loginError = result.error.message
                } else if (result.network != null) {
                    // now create a client id for the network
                    loginError = null

                    appLogin(result.network.byJwt)

                    inProgress = true

                    loginActivity?.authClientAndFinish { error ->
                        inProgress = false
                        loginError = error
                    }
                } else {
                    loginError = context.getString(R.string.login_error)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("It's nice to", style = MaterialTheme.typography.headlineLarge)
        Text("see you again", style = MaterialTheme.typography.headlineLarge)

        Spacer(modifier = Modifier.height(64.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                "Email or phone",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = TextMuted
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

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
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                "Password",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = TextMuted
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

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
        )

        Spacer(modifier = Modifier.height(32.dp))
        
        URButton(onClick = {
            login()
        }) { buttonTextStyle ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Continue", style = buttonTextStyle)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Right Arrow",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Forget your password?")
            Spacer(modifier = Modifier.width(4.dp))
            ClickableText(
                text = AnnotatedString("Reset it."),
                onClick = {onResetPassword()},
                style = TextStyle(
                    color = BlueMedium,
                    fontSize = 16.sp
                )
            )
        }
    }
}

@Preview()
@Composable
fun LoginPasswordPreview() {
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
                    byApi = null,
                    loginActivity = null,
                    appLogin = {},
                    onResetPassword = {}
                )
            }
        }
    }
}
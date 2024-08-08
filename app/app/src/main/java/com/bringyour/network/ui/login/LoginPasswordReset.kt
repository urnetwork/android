package com.bringyour.network.ui.login

import android.util.Patterns
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.client.AuthPasswordResetArgs
import com.bringyour.client.BringYourApi
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Composable
fun LoginPasswordReset(
    userAuth: String,
    byApi: BringYourApi?,
    onResetLinkSuccess: (String) -> Unit
) {
    var user by remember { mutableStateOf(TextFieldValue(userAuth)) }
    var inProgress by remember { mutableStateOf(false) }
    var passwordResetError by remember { mutableStateOf<String?>(null) }
    val isBtnEnabled by remember {
        derivedStateOf {
            !inProgress && (Patterns.EMAIL_ADDRESS.matcher(user.text).matches() ||
                    Patterns.PHONE.matcher(user.text).matches())
        }
    }

    val sendResetLink = {
        val args = AuthPasswordResetArgs()
        args.userAuth = user.text.trim()

        inProgress = true

        byApi?.authPasswordReset(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {
                inProgress = false

                if (err != null) {
                    passwordResetError = err.message
                } else {
                    passwordResetError = null
                    onResetLinkSuccess(result.userAuth)
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

        Text("Forgot your password?", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(64.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                "Enter your email or phone",
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
            placeholder = "",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
        )


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
                Text("Send Reset Link", style = buttonTextStyle)
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

@Preview
@Composable
fun LoginPasswordResetPreview() {
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
                    byApi = null,
                    onResetLinkSuccess = {}
                )
            }
        }
    }
}


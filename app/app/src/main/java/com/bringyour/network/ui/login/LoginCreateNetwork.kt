package com.bringyour.network.ui.login

import android.os.Bundle
import android.util.Log
import android.util.Patterns
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bringyour.client.BringYourApi
import com.bringyour.client.LoginViewController
import com.bringyour.client.NetworkCreateArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URLinkText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Composable
fun LoginCreateNetwork(
    userAuth: String,
    appLogin: (String) -> Unit,
    onVerificationRequired: (String) -> Unit,
    byApi: BringYourApi?,
    loginVc: LoginViewController?,
    loginActivity: LoginActivity?
) {
    val context = LocalContext.current
    var emailOrPhone by remember { mutableStateOf(TextFieldValue(userAuth)) }
    var userName by remember { mutableStateOf(TextFieldValue()) }
    var userPassword by remember { mutableStateOf(TextFieldValue()) }
    var termsAgreed by remember { mutableStateOf(false) }
    var inProgress by remember { mutableStateOf(false) }
    var createNetworkError by remember { mutableStateOf<String?>(null) }

    // handling network name + validation
    var networkName by remember { mutableStateOf(TextFieldValue()) }
    var isValidatingNetworkName by remember { mutableStateOf(false) }
    var networkNameAvailable by remember { mutableStateOf(false) }
    var networkNameErrorMsg by remember { mutableStateOf<String?>(null) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()


    val isBtnEnabled by remember {
        derivedStateOf {
            !inProgress &&
                    (Patterns.EMAIL_ADDRESS.matcher(emailOrPhone.text).matches() ||
                    Patterns.PHONE.matcher(emailOrPhone.text).matches()) &&
                    (networkName.text.length >= 6) &&
                    (userPassword.text.length >= 12) &&
                    (userName.text.isNotEmpty()) &&
                    !isValidatingNetworkName
        }
    }

    val checkNetworkName = {

        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(1000L)
            Log.i("LoginCreateNetwork", "checking network name")
            isValidatingNetworkName = true

            loginVc?.networkCheck(networkName.text) { result, err ->
                runBlocking(Dispatchers.Main.immediate) {

                    if (err == null) {
                        if (result.available) {
                            networkNameAvailable = true
                            networkNameErrorMsg = null
                        } else {
                            networkNameAvailable = false
                            networkNameErrorMsg = context.getString(R.string.network_name_check_error)
                        }
                    } else {
                        networkNameAvailable = false
                        networkNameErrorMsg = context.getString(R.string.network_name_check_error)
                    }

                    isValidatingNetworkName = false
                }
            }
        }
    }

    val createNetwork = {
        val args = NetworkCreateArgs()
        args.userName = userName.text.trim()
        args.userAuth = emailOrPhone.text.trim()
        args.password = userPassword.text
        args.networkName = networkName.text
        args.terms = termsAgreed

        byApi?.networkCreate(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {
                inProgress = false

                if (err != null) {
                    createNetworkError = err.message
                } else if (result.error != null) {
                    createNetworkError = result.error.message
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    createNetworkError = null

                    appLogin(result.network.byJwt)

                    inProgress = true

                    loginActivity?.authClientAndFinish { error ->
                        inProgress = false

                        createNetworkError = error
                    }
                } else if (result.verificationRequired != null) {
                    createNetworkError = null
                    onVerificationRequired(userAuth)
                } else {
                    createNetworkError = context.getString(R.string.create_network_error)
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

        Row {
           Text("Join", style = MaterialTheme.typography.headlineLarge)
        }

        Row {
            Text("URnetwork", style = MaterialTheme.typography.headlineLarge)
        }

        Spacer(modifier = Modifier.height(64.dp))

        URTextInputLabel(text = "Name")

        URTextInput(
            value = userName,
            onValueChange = { newValue ->
                userName = newValue
            },
            placeholder = "Your name",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
        )

        URTextInputLabel(text = "Email or phone")

        URTextInput(
            value = emailOrPhone,
            onValueChange = { newValue ->
                emailOrPhone = newValue
            },
            placeholder = "Enter your phone number or email",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
        )

        URTextInputLabel(text = "Network name")

        URTextInput(
            value = networkName,
            onValueChange = { newValue ->
                val originalCursorPosition = newValue.selection.start

                val filteredText = NetworkNameInputFilter(newValue.text)
                val cursorOffset = newValue.text.length - filteredText.length
                val newCursorPosition = (originalCursorPosition - cursorOffset).coerceIn(0, filteredText.length)

                networkName = newValue.copy(
                    text = filteredText,
                    selection = TextRange(newCursorPosition)
                )
                checkNetworkName()
            },
            placeholder = "Your network name",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            isValidating = isValidatingNetworkName,
            error = networkNameErrorMsg,
        )

        URTextInputLabel(text = "Password")

        URTextInput(
            value = userPassword,
            onValueChange = { newValue ->
                userPassword = newValue
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            isPassword = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                modifier = Modifier.size(16.dp),
                checked = termsAgreed,
                onCheckedChange = { termsAgreed = it },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("I agree to URnetwork's")
            Spacer(modifier = Modifier.width(1.dp))
            URLinkText(text = "Terms and Services", url = "https://ur.io/terms")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(30.dp))
            Text("and")
            Spacer(modifier = Modifier.width(1.dp))
            URLinkText(text = "Privacy Policy", url = "https://ur.io/privacy")
        }

        Spacer(modifier = Modifier.height(36.dp))

        URButton(
            onClick = {
                createNetwork()
            },
            enabled = isBtnEnabled
        ) { buttonTextStyle ->
            Text("Continue", style = buttonTextStyle)
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

@Preview
@Composable
fun LoginNetworkCreatePreview() {
    URNetworkTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LoginCreateNetwork(
                    userAuth = "hello@urnetwork.com",
                    byApi = null,
                    loginVc = null,
                    loginActivity =  null,
                    onVerificationRequired = {},
                    appLogin = {}
                )
            }
        }
    }
}
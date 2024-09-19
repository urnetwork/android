package com.bringyour.network.ui.login

import android.util.Log
import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.client.NetworkCreateArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.TermsCheckbox
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedOverlayLogin
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// Base class with common parameters
open class CommonLoginParams(
    val userAuth: String,
)

// Sealed class with specific parameters, properly initializing the base class
sealed class LoginCreateNetworkParams(
    userAuth: String,
) : CommonLoginParams(
    userAuth,
) {
     class LoginCreateUserAuthParams(
        userAuth: String,
    ) : LoginCreateNetworkParams(
        userAuth,
    )

     class LoginCreateAuthJwtParams(
        val authJwt: String,
        val authJwtType: String,
        val userName: String,
        userAuth: String,
    ) : LoginCreateNetworkParams(
         userAuth,
    )
}

@Composable
fun LoginCreateNetwork(
    params: LoginCreateNetworkParams,
    navController: NavController,
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val loginVc = application?.loginVc
    val loginActivity = context as? LoginActivity
    var emailOrPhone by remember { mutableStateOf(TextFieldValue()) }
    var userPassword by remember { mutableStateOf(TextFieldValue()) }
    var userName by remember { mutableStateOf(TextFieldValue()) }

    when(params) {
        is LoginCreateNetworkParams.LoginCreateUserAuthParams -> {
            emailOrPhone = TextFieldValue(params.userAuth)
        }
        is LoginCreateNetworkParams.LoginCreateAuthJwtParams -> {
            userName = TextFieldValue(params.userName)
        }
    }

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

    var welcomeOverlayVisible by remember { mutableStateOf(false) }
    var isContentVisible by remember { mutableStateOf(true) }

    val isBtnEnabled by remember {
        derivedStateOf {
            when(params) {
                is LoginCreateNetworkParams.LoginCreateUserAuthParams -> {
                    !inProgress &&
                            (Patterns.EMAIL_ADDRESS.matcher(emailOrPhone.text).matches() ||
                                    Patterns.PHONE.matcher(emailOrPhone.text).matches()) &&
                            (networkName.text.length >= 6) &&
                            (userPassword.text.length >= 12) &&
                            (userName.text.isNotEmpty()) &&
                            !isValidatingNetworkName &&
                            networkNameErrorMsg.isNullOrBlank() &&
                            termsAgreed
                }
                is LoginCreateNetworkParams.LoginCreateAuthJwtParams -> {
                    !inProgress &&
                            (Patterns.EMAIL_ADDRESS.matcher(params.userAuth).matches()) &&
                            (networkName.text.length >= 6) &&
                            (params.authJwt.isNotEmpty()) &&
                            (params.authJwtType.isNotEmpty()) &&
                            (userName.text.isNotEmpty()) &&
                            !isValidatingNetworkName &&
                            networkNameErrorMsg.isNullOrBlank() &&
                            termsAgreed
                }
            }
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

    val createNetworkArgs = {
        val args = NetworkCreateArgs()

        args.userName = userName.text.trim()
        args.networkName = networkName.text
        args.terms = termsAgreed

        when(params) {
            is LoginCreateNetworkParams.LoginCreateUserAuthParams -> {
                args.userAuth = emailOrPhone.text.trim()
                args.password = userPassword.text
            }
            is LoginCreateNetworkParams.LoginCreateAuthJwtParams -> {
                args.authJwt = params.authJwt
                args.authJwtType = params.authJwtType
            }
        }

        args
    }

    val createNetwork = {
        val args = createNetworkArgs()

        application?.api?.networkCreate(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {
                inProgress = false

                if (err != null) {
                    createNetworkError = err.message
                } else if (result.error != null) {
                    createNetworkError = result.error.message
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    createNetworkError = null

                    application.login(result.network.byJwt)

                    inProgress = true

                    isContentVisible = false

                    delay(500)

                    welcomeOverlayVisible = true

                    delay(500)

                    loginActivity?.authClientAndFinish { error ->
                        inProgress = false

                        createNetworkError = error
                    }
                } else if (result.verificationRequired != null) {
                    createNetworkError = null

                    // this might be unnecessary
                    // but following the current fragments
                    // can probably just use result.verificationRequired.userAuth
                    val userAuth = when (params) {
                        is LoginCreateNetworkParams.LoginCreateUserAuthParams -> {
                            params.userAuth
                        }

                        is LoginCreateNetworkParams.LoginCreateAuthJwtParams -> {
                            result.verificationRequired.userAuth
                        }
                    }

                    navController.navigate("verify/${userAuth}")

                } else {
                    createNetworkError = context.getString(R.string.create_network_error)
                }
            }
        }
    }

    AnimatedVisibility(
        visible = isContentVisible,
        enter = EnterTransition.None,
        exit = fadeOut()
    ) {

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

            Spacer(modifier = Modifier.height(48.dp))

            URTextInput(
                label = "Name",
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

            if (params is LoginCreateNetworkParams.LoginCreateUserAuthParams) {
                URTextInput(
                    label = "Email or phone",
                    value = emailOrPhone,
                    onValueChange = { newValue ->
                        val filteredText = newValue.text.filter { it != ' ' }
                        val filteredTextFieldValue = newValue.copy(text = filteredText)
                        emailOrPhone = filteredTextFieldValue
                    },
                    placeholder = "Enter your phone number or email",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                )
            }

            URTextInput(
                label = "Network name",
                value = networkName,
                onValueChange = { newValue ->
                    val originalCursorPosition = newValue.selection.start

                    val filteredText = networkNameInputFilter(newValue.text)
                    val cursorOffset = newValue.text.length - filteredText.length
                    val newCursorPosition =
                        (originalCursorPosition - cursorOffset).coerceIn(0, filteredText.length)

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
                isValid = networkNameErrorMsg == null,
                supportingText = networkNameErrorMsg ?: "Network names must be 6 characters or more"
            )

            if (params is LoginCreateNetworkParams.LoginCreateUserAuthParams) {

                URTextInput(
                    label = "Password",
                    value = userPassword,
                    onValueChange = { newValue ->
                        userPassword = newValue
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    isPassword = true,
                    supportingText = "Password must be at least 12 characters"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TermsCheckbox(
                checked = termsAgreed,
                onCheckChanged = { it ->
                    termsAgreed = it
                }
            )

            Spacer(modifier = Modifier.height(48.dp))

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

    WelcomeAnimatedOverlayLogin(
        isVisible = welcomeOverlayVisible
    )
}

@Preview
@Composable
private fun LoginNetworkCreatePreview() {

    val params = LoginCreateNetworkParams.LoginCreateUserAuthParams(
        userAuth = "hello@urnetwork.com",
    )

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
                LoginCreateNetwork(
                    params,
                    navController
                )
            }
        }
    }
}
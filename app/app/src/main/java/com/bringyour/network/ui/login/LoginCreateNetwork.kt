package com.bringyour.network.ui.login

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.relocation.BringIntoViewRequester
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.client.NetworkCreateArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.TermsCheckbox
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedOverlayLogin
import com.bringyour.network.utils.isTv

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
    loginCreateNetworkViewModel: LoginCreateNetworkViewModel = hiltViewModel()
) {

    LoginCreateNetwork(
        params,
        navController,
        validateNetworkName = loginCreateNetworkViewModel.validateNetworkName,
        isValidatingNetworkName = loginCreateNetworkViewModel.isValidatingNetworkName,
        emailOrPhone = loginCreateNetworkViewModel.emailOrPhone,
        setEmailOrPhone = loginCreateNetworkViewModel.setEmailOrPhone,
        networkName = loginCreateNetworkViewModel.networkName,
        setNetworkName = loginCreateNetworkViewModel.setNetworkName,
        networkNameErrorExists = loginCreateNetworkViewModel.networkNameErrorExists,
        password = loginCreateNetworkViewModel.password,
        setPassword = loginCreateNetworkViewModel.setPassword,
        termsAgreed = loginCreateNetworkViewModel.termsAgreed,
        setTermsAgreed = loginCreateNetworkViewModel.setTermsAgreed,
        createNetworkArgs = loginCreateNetworkViewModel.createNetworkArgs,
        networkNameIsValid = loginCreateNetworkViewModel.networkNameIsValid,
        networkNameSupportingText = loginCreateNetworkViewModel.networkNameSupportingText,
        setNetworkNameSupportingText = loginCreateNetworkViewModel.setNetworkNameSupportingText
   )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginCreateNetwork(
    params: LoginCreateNetworkParams,
    navController: NavController,
    networkName: TextFieldValue,
    setNetworkName: (TextFieldValue) -> Unit,
    validateNetworkName: (String) -> Unit,
    isValidatingNetworkName: Boolean,
    emailOrPhone: TextFieldValue,
    setEmailOrPhone: (TextFieldValue) -> Unit,
    password: TextFieldValue,
    setPassword: (TextFieldValue) -> Unit,
    networkNameErrorExists: Boolean,
    termsAgreed: Boolean,
    setTermsAgreed: (Boolean) -> Unit,
    networkNameIsValid: Boolean,
    createNetworkArgs: (LoginCreateNetworkParams) -> NetworkCreateArgs,
    setNetworkNameSupportingText: (String) -> Unit,
    networkNameSupportingText: String
) {
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val loginActivity = context as? LoginActivity

    when(params) {
        is LoginCreateNetworkParams.LoginCreateUserAuthParams -> {
            setEmailOrPhone(TextFieldValue(params.userAuth))
        }
        else -> Unit
    }

    var isBtnEnabled by remember { mutableStateOf(false) }
    var inProgress by remember { mutableStateOf(false) }
    var welcomeOverlayVisible by remember { mutableStateOf(false) }
    var isContentVisible by remember { mutableStateOf(true) }

    LaunchedEffect(inProgress, params, networkName.text, password.text, termsAgreed) {
        isBtnEnabled  = when(params) {
            is LoginCreateNetworkParams.LoginCreateUserAuthParams -> {
                !inProgress &&
                        (Patterns.EMAIL_ADDRESS.matcher(emailOrPhone.text).matches() ||
                                Patterns.PHONE.matcher(emailOrPhone.text).matches()) &&
                        (networkName.text.length >= 6) &&
                        (password.text.length >= 12) &&
                        !isValidatingNetworkName &&
                        !networkNameErrorExists &&
                        networkNameIsValid &&
                        termsAgreed
            }
            is LoginCreateNetworkParams.LoginCreateAuthJwtParams -> {
                !inProgress &&
                        (Patterns.EMAIL_ADDRESS.matcher(params.userAuth).matches()) &&
                        (networkName.text.length >= 6) &&
                        (params.authJwt.isNotEmpty()) &&
                        (params.authJwtType.isNotEmpty()) &&
                        !isValidatingNetworkName &&
                        !networkNameErrorExists &&
                        networkNameIsValid &&
                        termsAgreed
            }
        }
    }

    var createNetworkError by remember { mutableStateOf<String?>(null) }

    val createNetwork = {
        val args = createNetworkArgs(params)

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

                    delay(250)

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

    val networkNameUnavailable = stringResource(id = R.string.network_name_unavailable)
    val invalidNetworkNameLength = stringResource(id = R.string.network_name_length_error)
    val networkNameAvailable = stringResource(id = R.string.available)

    LaunchedEffect(networkNameErrorExists, networkNameIsValid, networkName.text) {
        if (networkName.text.isEmpty()) {
            setNetworkNameSupportingText("")
        } else if (networkName.text.length < 6) {
            setNetworkNameSupportingText(invalidNetworkNameLength)
        } else if (networkNameErrorExists) {
            setNetworkNameSupportingText(networkNameUnavailable)
        } else if (networkNameIsValid) {
            setNetworkNameSupportingText(networkNameAvailable)
        } else {
            setNetworkNameSupportingText("")
        }
    }

    AnimatedVisibility(
        visible = isContentVisible,
        enter = EnterTransition.None,
        exit = fadeOut()
    ) {

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Black
                    ),
                    actions = {},
                )
            }
        ) { innerPadding ->

            if (isTv()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding) // need to debug why this is 0
                        .padding(16.dp)
                        .imePadding(),
                ) {

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            "Join\nURnetwork",
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(64.dp))
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(end = 64.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {

                        NetworkCreateForm(
                            params = params,
                            emailOrPhone = emailOrPhone,
                            networkName = networkName,
                            setNetworkName = setNetworkName,
                            validateNetworkName = validateNetworkName,
                            isValidatingNetworkName = isValidatingNetworkName,
                            networkNameErrorExists = networkNameErrorExists,
                            password = password,
                            setPassword = setPassword,
                            termsAgreed = termsAgreed,
                            setTermsAgreed = setTermsAgreed,
                            isBtnEnabled = isBtnEnabled,
                            onCreateNetwork = {
                                createNetwork()
                            },
                            networkNameSupportingText = networkNameSupportingText,
                        )
                    }
                }
            } else {
                // mobile or tablet
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                        .padding(16.dp),
                    // .imePadding(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Row {
                        Text(
                            "Join\nURnetwork",
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    NetworkCreateForm(
                        params = params,
                        emailOrPhone = emailOrPhone,
                        networkName = networkName,
                        setNetworkName = setNetworkName,
                        validateNetworkName = validateNetworkName,
                        isValidatingNetworkName = isValidatingNetworkName,
                        networkNameErrorExists = networkNameErrorExists,
                        password = password,
                        setPassword = setPassword,
                        termsAgreed = termsAgreed,
                        setTermsAgreed = setTermsAgreed,
                        isBtnEnabled = isBtnEnabled,
                        onCreateNetwork = {
                            createNetwork()
                        },
                        networkNameSupportingText = networkNameSupportingText,
                    )
                }
            }
        }
    }


    WelcomeAnimatedOverlayLogin(
        isVisible = welcomeOverlayVisible
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NetworkCreateForm(
    params: LoginCreateNetworkParams,
    emailOrPhone: TextFieldValue,
    networkName: TextFieldValue,
    setNetworkName: (TextFieldValue) -> Unit,
    validateNetworkName: (String) -> Unit,
    isValidatingNetworkName: Boolean,
    networkNameErrorExists: Boolean,
    networkNameSupportingText: String,
    password: TextFieldValue,
    setPassword: (TextFieldValue) -> Unit,
    termsAgreed: Boolean,
    setTermsAgreed: (Boolean) -> Unit,
    isBtnEnabled: Boolean,
    onCreateNetwork: () -> Unit
) {
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .widthIn(max = 512.dp)
        ) {

            if (params is LoginCreateNetworkParams.LoginCreateUserAuthParams) {
                URTextInput(
                    label = stringResource(id = R.string.user_auth_label),
                    value = emailOrPhone,
                    onValueChange = {},
                    enabled = false,
                    placeholder = stringResource(id = R.string.user_auth_placeholder),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                )
            }

            URTextInput(
                label = stringResource(id = R.string.network_name_label),
                value = networkName,
                onValueChange = { newValue ->
                    val originalCursorPosition = newValue.selection.start

                    val filteredText = networkNameInputFilter(newValue.text)
                    val cursorOffset = newValue.text.length - filteredText.length
                    val newCursorPosition =
                        (originalCursorPosition - cursorOffset).coerceIn(0, filteredText.length)

                    val newNetworkName = newValue.copy(
                        text = filteredText,
                        selection = TextRange(newCursorPosition)
                    )

                    setNetworkName(newNetworkName)

                    debounceJob?.cancel()
                    debounceJob = coroutineScope.launch {
                        delay(500L)
                        validateNetworkName(newNetworkName.text)
                    }
                },
                placeholder = stringResource(id = R.string.network_name_placeholder),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = if (params is LoginCreateNetworkParams.LoginCreateUserAuthParams)
                        ImeAction.Next else ImeAction.Done
                ),
                isValidating = isValidatingNetworkName,
                isValid = !networkNameErrorExists,
                supportingText = networkNameSupportingText
            )

            if (params is LoginCreateNetworkParams.LoginCreateUserAuthParams) {

                URTextInput(
                    label = stringResource(id = R.string.password_label),
                    value = password,
                    onValueChange = setPassword,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    isPassword = true,
                    supportingText = stringResource(id = R.string.password_support_txt)
                )
            }


            Spacer(modifier = Modifier.height(16.dp))

            Row {

                TermsCheckbox(
                    checked = termsAgreed,
                    onCheckChanged = setTermsAgreed
                )

            }

            Spacer(modifier = Modifier.height(48.dp))

            URButton(
                onClick = {
                    // createNetwork()
                    onCreateNetwork()
                },
                enabled = isBtnEnabled
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.continue_txt), style = buttonTextStyle)
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
private fun LoginNetworkCreatePreview() {

    val params = LoginCreateNetworkParams.LoginCreateUserAuthParams(
        userAuth = "hello@urnetwork.com",
    )

    val navController = rememberNavController()

    val mockCreateNetworkArgs: (LoginCreateNetworkParams) -> NetworkCreateArgs = {
        NetworkCreateArgs()
    }

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
                    navController,
                    validateNetworkName = {},
                    isValidatingNetworkName = false,
                    emailOrPhone = TextFieldValue("hello@ur.io"),
                    setEmailOrPhone = {},
                    networkName = TextFieldValue("hello-world"),
                    setNetworkName = {},
                    networkNameErrorExists = false,
                    password = TextFieldValue("abcdefghijk"),
                    setPassword = {},
                    termsAgreed = false,
                    setTermsAgreed = {},
                    createNetworkArgs = mockCreateNetworkArgs,
                    networkNameIsValid = true,
                    networkNameSupportingText = "",
                    setNetworkNameSupportingText = {}
                )
            }
        }
    }
}
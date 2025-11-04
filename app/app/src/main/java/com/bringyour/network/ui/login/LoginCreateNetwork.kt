package com.bringyour.network.ui.login

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.sdk.NetworkCreateArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.TermsCheckbox
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import androidx.compose.ui.Alignment
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedOverlayLogin
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.ppNeueBitBold
import com.bringyour.sdk.Api

// Base class with common parameters
open class CommonLoginParams(
    var referralCode: String?
)

// Sealed class with specific parameters, properly initializing the base class
sealed class LoginCreateNetworkParams(
    referralCode: String?
) : CommonLoginParams(
    referralCode
) {
     class LoginCreateUserAuthParams(
        val userAuth: String,
        referralCode: String?
    ) : LoginCreateNetworkParams(
        // userAuth,
        referralCode
    )

     class LoginCreateAuthJwtParams(
        val authJwt: String,
        val authJwtType: String,
        val userName: String,
        val userAuth: String,
        referralCode: String?
    ) : LoginCreateNetworkParams(
         // userAuth,
         referralCode
    )

    class LoginCreateSolanaParams(
        val publicKey: String,
        val signedMessage: String,
        val signature: String,
        referralCode: String?
    ) : LoginCreateNetworkParams(
        referralCode
    )
}

@Composable
fun LoginCreateNetwork(
    params: LoginCreateNetworkParams,
    navController: NavController,
    loginCreateNetworkViewModel: LoginCreateNetworkViewModel = hiltViewModel()
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val presentReferralSheet by loginCreateNetworkViewModel.presentBonusSheet.collectAsState()

    LaunchedEffect(params.referralCode) {

        params.referralCode?.let { code ->
            if (code.isNotEmpty()) {
                loginCreateNetworkViewModel.setReferralCode(TextFieldValue(code))
                loginCreateNetworkViewModel.validateReferralCode(application?.api, {})
            }
        }
    }

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
        setNetworkNameSupportingText = loginCreateNetworkViewModel.setNetworkNameSupportingText,
        presentBonusSheet = presentReferralSheet,
        setPresentBonusSheet = loginCreateNetworkViewModel.setPresentBonusSheet,
        referralCode = loginCreateNetworkViewModel.referralCode,
        setReferralCode = loginCreateNetworkViewModel.setReferralCode,
        validateReferralCode = loginCreateNetworkViewModel.validateReferralCode,
        isValidReferralCode = loginCreateNetworkViewModel.isValidReferralCode,
        isValidatingReferralCode = loginCreateNetworkViewModel.isValidatingReferralCode,
        referralValidationComplete = loginCreateNetworkViewModel.referralValidationComplete,
        referralCodeInputSupportingTextRes = loginCreateNetworkViewModel.referralCodeInputSupportingTextRes.collectAsState().value,
        isReferralCodeCapped = loginCreateNetworkViewModel.referralCodeIsCapped.collectAsState().value
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
    networkNameSupportingText: String,
    presentBonusSheet: Boolean,
    setPresentBonusSheet: (Boolean) -> Unit,
    referralCode: TextFieldValue,
    setReferralCode: (TextFieldValue) -> Unit,
    validateReferralCode: (Api?, (Boolean) -> Unit) -> Unit,
    isValidReferralCode: Boolean,
    isValidatingReferralCode: Boolean,
    isReferralCodeCapped: Boolean,
    referralValidationComplete: Boolean,
    referralCodeInputSupportingTextRes: Int?
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

    LaunchedEffect(
        inProgress,
        params,
        networkName.text,
        password.text,
        termsAgreed,
        networkNameIsValid,
        networkNameErrorExists
    ) {

        isBtnEnabled = when(params) {
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
            is LoginCreateNetworkParams.LoginCreateSolanaParams -> {
                    (networkName.text.length >= 6) &&
                    (params.publicKey.isNotEmpty()) &&
                    (params.signature.isNotEmpty()) &&
                    (params.signedMessage.isNotEmpty()) &&
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
        inProgress = true

        application?.api?.networkCreate(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {

                if (err != null) {
                    createNetworkError = err.message
                    inProgress = false
                } else if (result.error != null) {
                    createNetworkError = result.error.message
                    inProgress = false
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    createNetworkError = null

                    application.login(result.network.byJwt)

                    isContentVisible = false

                    delay(500)

                    welcomeOverlayVisible = true

                    delay(2250)

                    loginActivity?.authClientAndFinish(
                        { error ->
                            inProgress = false

                            createNetworkError = error
                        }
                    )
                } else if (result.verificationRequired != null) {
                    createNetworkError = null

                    var userAuth: String? = null
                    if (params is LoginCreateNetworkParams.LoginCreateUserAuthParams) {
                        userAuth = params.userAuth
                    } else if (params is LoginCreateNetworkParams.LoginCreateAuthJwtParams) {
                        userAuth = result.verificationRequired.userAuth
                    }

                    userAuth?.let {
                        navController.navigate("verify/${it}")
                    } ?: run {
                        createNetworkError = "There was a problem parsing user auth for verification"
                    }

                    inProgress = false

                } else {
                    createNetworkError = context.getString(R.string.create_network_error)
                    inProgress = false
                }
            }
        }

    }

    val networkNameUnavailable = stringResource(id = R.string.network_name_unavailable)
    val invalidNetworkNameLength = stringResource(id = R.string.network_name_length_error)
    val networkNameAvailable = stringResource(id = R.string.available)

    val sheetState = rememberModalBottomSheetState(
//        skipPartiallyExpanded = false,
//        confirmValueChange = { sheetValue ->
//            sheetValue != SheetValue.Expanded
//        }
    )

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

            // mobile or tablet
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(16.dp),
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
                    setPresentBonusSheet = setPresentBonusSheet,
                    isValidReferralCode = isValidReferralCode,
                    referralCode = referralCode,
                    isInProgress = inProgress
                )
            }

            if (presentBonusSheet) {
                /**
                 * Referral sheet
                 */
                ModalBottomSheet(
                    modifier = Modifier.wrapContentHeight(),
                    sheetState = sheetState,
                    onDismissRequest = { setPresentBonusSheet(false) },
                    dragHandle = {}
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(id = R.string.add_referral_extra_rewards),
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        URTextInput(
                            label = stringResource(id = R.string.referral_code),
                            value = referralCode,
                            onValueChange = setReferralCode,
                            supportingText = if (referralCodeInputSupportingTextRes != null) stringResource(id = referralCodeInputSupportingTextRes) else "",
                            isValidating = isValidatingReferralCode,
                            isValid = (!referralValidationComplete || (isValidReferralCode && !isReferralCodeCapped))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        URButton(
                            onClick = {
                                validateReferralCode(application?.api) { valid ->
                                    if (valid) {
                                        setPresentBonusSheet(false)
                                    }
                                }
                            },
                            enabled = !isValidatingReferralCode && referralCode.text.isNotEmpty()
                        ) { buttonTextStyle ->
                            Text(
                                stringResource(id = R.string.apply_bonus),
                                style = buttonTextStyle
                            )
                        }
                    }
                }
            }
        }
    }


    if (welcomeOverlayVisible) {
        WelcomeAnimatedOverlayLogin()
    }

}

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
    isInProgress: Boolean,
    onCreateNetwork: () -> Unit,
    setPresentBonusSheet: (Boolean) -> Unit,
    isValidReferralCode: Boolean,
    referralCode: TextFieldValue,
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
                    imeAction = ImeAction.Next
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

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {

                if (isValidReferralCode) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Valid referral code",
                        tint = Green,
                        // modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(stringResource(id = R.string.referral_bonus_applied),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                } else {
                    Text("")
                }

            }

            Spacer(modifier = Modifier.height(48.dp))

            URButton(
                onClick = {
                    onCreateNetwork()
                },
                enabled = isBtnEnabled && !isInProgress,
                isProcessing = isInProgress
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

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (referralCode.text.isEmpty()) stringResource(id = R.string.add_referral_code)
                        else stringResource(id = R.string.edit_referral_code),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable() {
                            setPresentBonusSheet(true)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = TextStyle(
                        color = TextMuted,
                        fontFamily = ppNeueBitBold,
                        fontSize = 24.sp
                    )
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
        referralCode = "1234567890"
    )

    val navController = rememberNavController()

    val mockCreateNetworkArgs: (LoginCreateNetworkParams) -> NetworkCreateArgs = {
        NetworkCreateArgs()
    }

    val mockValidateReferralCode: (Api?, (Boolean) -> Unit) -> Unit = { _, _ -> }

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
                    setNetworkNameSupportingText = {},
                    presentBonusSheet = false,
                    setPresentBonusSheet = {},
                    referralCode = TextFieldValue(""),
                    setReferralCode = {},
                    validateReferralCode = mockValidateReferralCode,
                    isValidReferralCode = true,
                    isValidatingReferralCode = false,
                    referralValidationComplete = false,
                    referralCodeInputSupportingTextRes = null,
                    isReferralCodeCapped = false
                )
            }
        }
    }
}
package com.bringyour.network.ui.profile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.R
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.ResetPasswordFunction
import com.bringyour.network.ui.shared.viewmodels.ResetPasswordViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun ProfileScreen(
    navController: NavController,
    accountViewModel: AccountViewModel,
    profileViewModel: ProfileViewModel,
    overlayViewModel: OverlayViewModel,
    resetPasswordViewModel: ResetPasswordViewModel = hiltViewModel()
) {

    val networkUser by accountViewModel.networkUser.collectAsState()
    val isSavingNetworkName by profileViewModel.isSavingNetworkName.collectAsState()
    val networkNameError by profileViewModel.networkNameError.collectAsState()
    val needsNameClaim by profileViewModel.needsNameClaim.collectAsState()

    LaunchedEffect(networkUser) {
        profileViewModel.setNetworkUser(networkUser)
    }

    ProfileScreen(
        navController = navController,
        loginMode = accountViewModel.loginMode,
        isSendingResetPassLink = resetPasswordViewModel.isSendingResetPassLink,
        sendResetLink = resetPasswordViewModel.sendResetLink,
        networkName = networkUser?.networkName ?: "",
        networkNameTextFieldValue = profileViewModel.networkNameTextFieldValue,
        setNetworkName = profileViewModel.setNetworkNameTextFieldValue,
        userAuth = networkUser?.userAuth,
        isEditingProfile = profileViewModel.isEditingProfile,
        setIsEditingProfile = profileViewModel.setIsEditingProfile,
        cancelEdits = profileViewModel.cancelEdits,
        needsNameClaim = needsNameClaim,
        saveNetworkName = profileViewModel.saveNetworkName,
        claimNetworkName = profileViewModel.claimNetworkName,
        isSavingNetworkName = isSavingNetworkName,
        networkNameError = networkNameError,
        networkNameIsValid = profileViewModel.networkNameIsValid,
        networkNameIsValidating = profileViewModel.isValidatingNetworkName,
        validateNetworkName = profileViewModel.validateNetworkName,
        onSaved = {
            accountViewModel.refreshNetworkUser()
            profileViewModel.setIsEditingProfile(false)
        },
        launchOverlay = overlayViewModel.launch
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    loginMode: LoginMode,
    isSendingResetPassLink: Boolean,
    sendResetLink: ResetPasswordFunction,
    networkName: String,
    networkNameTextFieldValue: TextFieldValue,
    setNetworkName: (TextFieldValue) -> Unit,
    userAuth: String?,
    isEditingProfile: Boolean,
    setIsEditingProfile: (Boolean) -> Unit,
    cancelEdits: () -> Unit,
    needsNameClaim: Boolean,
    saveNetworkName: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    claimNetworkName: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onSaved: () -> Unit,
    isSavingNetworkName: Boolean,
    networkNameError: String?,
    validateNetworkName: (String) -> Unit,
    networkNameIsValid: Boolean,
    networkNameIsValidating: Boolean,
    launchOverlay: (OverlayMode) -> Unit
) {

    val context = LocalContext.current
    var lastResetTime by remember { mutableStateOf(0L) }
    var cooldownTrigger by remember { mutableStateOf(0) }
    val cooldownPeriod = 15_000L // 15 seconds

    // disable send reset email for 15 seconds after successfully sending
    LaunchedEffect(lastResetTime) {
        if (lastResetTime > 0L) {
            delay(cooldownPeriod)
            cooldownTrigger++ // trigger recomposition
        }
    }

    val resendBtnEnabled by remember {
        derivedStateOf {
            cooldownTrigger
            userAuth != null &&
                    !isSendingResetPassLink &&
                    (System.currentTimeMillis() - lastResetTime > cooldownPeriod)
        }
    }

    var debounceJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val resetPasswordErr = stringResource(id = R.string.something_went_wrong)
    val resetPasswordEmailSentMsg = stringResource(id = R.string.reset_password_email_sent, userAuth ?: stringResource(id = R.string.unknown))

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.profile),
                        style = TopBarTitleTextStyle
                    )
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
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.profile),
                    style = MaterialTheme.typography.headlineSmall
                )
                AccountSwitcher(
                    loginMode = loginMode,
                    // todo - this should be the current network name, not the one being edited
                    networkName = networkName,
                    launchOverlay = launchOverlay
                )
            }
            Spacer(modifier = Modifier.height(64.dp))

            if (isEditingProfile) {
                URTextInput(
                    value = networkNameTextFieldValue,
                    onValueChange = {
                        setNetworkName(it)

                        debounceJob?.cancel()
                        debounceJob = coroutineScope.launch {
                            delay(500L)
                            validateNetworkName(it.text)
                        }

                    },
                    enabled = !isSavingNetworkName,
                    label = stringResource(id = R.string.network_name_label),
                    isValidating = networkNameIsValidating,
                    isValid = networkNameIsValid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                )

                if (networkNameError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    URInlineErrorText(networkNameError)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        URButton(
                            onClick = {
                                if (needsNameClaim) {
                                    claimNetworkName(onSaved) { }
                                } else {
                                    saveNetworkName(onSaved) { }
                                }
                            },
                            enabled = !isSavingNetworkName && networkNameTextFieldValue.text.isNotBlank(),
                            isProcessing = isSavingNetworkName
                        ) { buttonTextStyle ->
                            Text(stringResource(id = R.string.save), style = buttonTextStyle)
                        }
                    }
                    TextButton(onClick = cancelEdits) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setIsEditingProfile(true) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        networkName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(id = R.string.edit_network_name),
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (needsNameClaim) {
                        stringResource(id = R.string.claim_network_name_hint)
                    } else {
                        stringResource(id = R.string.change_network_name_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInput(
                value = TextFieldValue(""),
                onValueChange = {},
                enabled = false,
                label = stringResource(id = R.string.password_label),
                isPassword = true,
            )

            if (userAuth != null) {

                Text(
                    stringResource(id = R.string.change_password),
                    modifier = Modifier
                        .clickable(enabled = resendBtnEnabled) {
                            sendResetLink(
                                userAuth,
                                {
                                    lastResetTime = System.currentTimeMillis()
                                    Toast.makeText(
                                        context,
                                        resetPasswordEmailSentMsg,
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                {
                                    Toast.makeText(
                                        context,
                                        resetPasswordErr,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    ,
                    style = TextStyle(
                        color = if (resendBtnEnabled) BlueMedium else TextMuted
                    )
                )

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
            isSendingResetPassLink = false,
            sendResetLink = {_, _, _ ->},
            userAuth = "hello@bringyour.com",
            networkName = "my_network",
            networkNameTextFieldValue = TextFieldValue("my_network"),
            setNetworkName = {},
            isEditingProfile = false,
            setIsEditingProfile = {},
            cancelEdits = {},
            needsNameClaim = false,
            saveNetworkName = { _, _ -> },
            claimNetworkName = { _, _ -> },
            onSaved = {},
            isSavingNetworkName = false,
            networkNameError = null,
            networkNameIsValid = true,
            networkNameIsValidating = false,
            validateNetworkName = {},
            launchOverlay = {}
        )
    }
}

@Preview
@Composable
fun ProfileScreenEditingPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        ProfileScreen(
            navController,
            loginMode = LoginMode.Authenticated,
            isSendingResetPassLink = false,
            sendResetLink = {_, _, _ ->},
            userAuth = "hello@bringyour.com",
            networkName = "my_network",
            networkNameTextFieldValue = TextFieldValue("my_network"),
            setNetworkName = {},
            isEditingProfile = true,
            setIsEditingProfile = {},
            cancelEdits = {},
            needsNameClaim = false,
            saveNetworkName = { _, _ -> },
            claimNetworkName = { _, _ -> },
            onSaved = {},
            isSavingNetworkName = false,
            networkNameError = null,
            networkNameIsValid = false,
            networkNameIsValidating = false,
            validateNetworkName = {},
            launchOverlay = {}
        )
    }
}

@Preview
@Composable
fun ProfileScreenClaimingPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        ProfileScreen(
            navController,
            loginMode = LoginMode.Authenticated,
            isSendingResetPassLink = false,
            sendResetLink = {_, _, _ ->},
            userAuth = null,
            networkName = "auto_generated_name_123",
            networkNameTextFieldValue = TextFieldValue("auto_generated_name_123"),
            setNetworkName = {},
            isEditingProfile = false,
            setIsEditingProfile = {},
            cancelEdits = {},
            needsNameClaim = true,
            saveNetworkName = { _, _ -> },
            claimNetworkName = { _, _ -> },
            onSaved = {},
            isSavingNetworkName = false,
            networkNameError = null,
            networkNameIsValid = true,
            networkNameIsValidating = false,
            validateNetworkName = {},
            launchOverlay = {}
        )
    }
}

@Preview
@Composable
fun ProfileScreenErrorPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        ProfileScreen(
            navController,
            loginMode = LoginMode.Authenticated,
            isSendingResetPassLink = false,
            sendResetLink = {_, _, _ ->},
            userAuth = "hello@bringyour.com",
            networkName = "my_network",
            networkNameTextFieldValue = TextFieldValue("my_network"),
            setNetworkName = {},
            isEditingProfile = true,
            setIsEditingProfile = {},
            cancelEdits = {},
            needsNameClaim = false,
            saveNetworkName = { _, _ -> },
            claimNetworkName = { _, _ -> },
            onSaved = {},
            isSavingNetworkName = false,
            networkNameError = "That name is already taken",
            networkNameIsValid = true,
            networkNameIsValidating = false,
            validateNetworkName = {},
            launchOverlay = {}
        )
    }
}

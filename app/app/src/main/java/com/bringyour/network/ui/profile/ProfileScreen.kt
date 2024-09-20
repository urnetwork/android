package com.bringyour.network.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.SnackBarType
import com.bringyour.network.ui.components.URSnackBar
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.shared.viewmodels.ResetPasswordViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme


@Composable
fun ProfileScreen(
    navController: NavController,
    accountViewModel: AccountViewModel,
    profileViewModel: ProfileViewModel,
    resetPasswordViewModel: ResetPasswordViewModel = hiltViewModel()
) {

    ProfileScreen(
        navController = navController,
        loginMode = accountViewModel.loginMode,
        isSendingResetPassLink = resetPasswordViewModel.isSendingResetPassLink,
        sendResetLink = resetPasswordViewModel.sendResetLink,
        passwordResetError = resetPasswordViewModel.passwordResetError,
        markPasswordResetAsSent = resetPasswordViewModel.markPasswordResetAsSent,
        setPasswordResetError = resetPasswordViewModel.setPasswordResetError,
        setMarkPasswordResetAsSent = resetPasswordViewModel.setMarkPasswordResetAsSent,
        networkName = profileViewModel.networkNameTextFieldValue,
        setNetworkName = profileViewModel.setNetworkNameTextFieldValue,
        name = profileViewModel.nameTextFieldValue,
        setName = profileViewModel.setNameTextFieldValue,
        userAuth = profileViewModel.networkUser?.userAuth,
        isEditingProfile = profileViewModel.isEditingProfile,
        setIsEditingProfile = profileViewModel.setIsEditingProfile,
        cancelEdits = profileViewModel.cancelEdits,
        updateProfile = profileViewModel.updateProfile
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    loginMode: LoginMode,
    isSendingResetPassLink: Boolean,
    sendResetLink: (String) -> Unit,
    passwordResetError: String?,
    markPasswordResetAsSent: Boolean,
    setPasswordResetError: (String?) -> Unit,
    setMarkPasswordResetAsSent: (Boolean) -> Unit,
    networkName: TextFieldValue,
    setNetworkName: (TextFieldValue) -> Unit,
    name: TextFieldValue,
    setName: (TextFieldValue) -> Unit,
    userAuth: String?,
    isEditingProfile: Boolean,
    setIsEditingProfile: (Boolean) -> Unit,
    cancelEdits: () -> Unit,
    updateProfile: () -> Unit
) {

    val resendBtnEnabled by remember {
        derivedStateOf {
            passwordResetError == null &&
                    userAuth != null &&
                    !isSendingResetPassLink &&
                    !markPasswordResetAsSent
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
                AccountSwitcher(
                    loginMode = loginMode,
                    // todo - this should be the current network name, not the one being edited
                    networkName = networkName.text
                )
            }
            Spacer(modifier = Modifier.height(64.dp))

            URTextInput(
                value = name,
                onValueChange = {
                    setName(it)
                },
                enabled = isEditingProfile,
                label = "Name"
            )

            URTextInput(
                value = networkName,
                onValueChange = {
                    setNetworkName(it)
                },
                enabled = isEditingProfile,
                label = "Network name"
            )

            if (isEditingProfile) {

                Row {
                    ClickableText(
                        text = AnnotatedString("Cancel"),
                        onClick = {
                            cancelEdits()
                        },
                        style = TextStyle(
                            color = BlueMedium
                        )
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    ClickableText(
                        text = AnnotatedString("Save"),
                        onClick = {
                            updateProfile()
                        },
                        style = TextStyle(
                            color = BlueMedium
                        )
                    )
                }

            } else {
                ClickableText(
                    text = AnnotatedString("Edit profile"),
                    onClick = {
                        setIsEditingProfile(true)
                    },
                    style = TextStyle(
                        color = BlueMedium
                    )
                )
            }

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
                    if (resendBtnEnabled && userAuth != null) {
                        sendResetLink(userAuth)
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
                    setPasswordResetError(null)
                }
                if (markPasswordResetAsSent) {
                    setMarkPasswordResetAsSent(false)
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
            isSendingResetPassLink = false,
            sendResetLink = {},
            passwordResetError = null,
            markPasswordResetAsSent = false,
            setPasswordResetError = {},
            setMarkPasswordResetAsSent = {},
            userAuth = "hello@bringyour.com",
            networkName = TextFieldValue("my_network"),
            setNetworkName = {},
            name = TextFieldValue("Lorem Ipsum"),
            setName = {},
            isEditingProfile = false,
            setIsEditingProfile = {},
            cancelEdits = {},
            updateProfile = {}
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
            sendResetLink = {},
            passwordResetError = null,
            markPasswordResetAsSent = false,
            setPasswordResetError = {},
            setMarkPasswordResetAsSent = {},
            userAuth = "hello@bringyour.com",
            networkName = TextFieldValue("my_network"),
            setNetworkName = {},
            name = TextFieldValue("Lorem Ipsum"),
            setName = {},
            isEditingProfile = true,
            setIsEditingProfile = {},
            cancelEdits = {},
            updateProfile = {}
        )
    }
}
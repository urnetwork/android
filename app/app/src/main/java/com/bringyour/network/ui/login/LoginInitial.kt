package com.bringyour.network.ui.login

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.URNetworkTheme
import androidx.compose.ui.res.painterResource
import com.bringyour.network.ui.theme.TextMuted
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.client.AuthLoginResult
import com.bringyour.client.BringYourApi
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.SnackBarType
import com.bringyour.network.ui.components.URSnackBar
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

@Composable()
fun LoginInitial(
    navController: NavController,
    loginViewModel: LoginViewModel,
    overlayViewModel: OverlayViewModel
) {

    LoginInitial(
        navController,
        userAuth = loginViewModel.userAuth,
        setUserAuth = loginViewModel.setUserAuth,
        userAuthInProgress = loginViewModel.userAuthInProgress,
        isValidUserAuth = loginViewModel.isValidUserAuth,
        login = loginViewModel.login,
        googleLogin = loginViewModel.googleLogin,
        loginError = loginViewModel.loginError,
        setGoogleAuthInProgress = loginViewModel.setGoogleAuthInProgress,
        setLoginError = loginViewModel.setLoginError,
        googleAuthInProgress = loginViewModel.googleAuthInProgress,
        launchDialog = overlayViewModel.launch
    )

}

@Composable()
fun LoginInitial(
    navController: NavController,
    userAuth: TextFieldValue,
    setUserAuth: (TextFieldValue) -> Unit,
    userAuthInProgress: Boolean,
    isValidUserAuth: Boolean,
    login: (
        ctx: Context,
        api: BringYourApi?,
        onLogin: (AuthLoginResult) -> Unit,
        onNewNetwork: (AuthLoginResult) -> Unit,
    ) -> Unit,
    googleLogin: (
        context: Context,
        api: BringYourApi?,
        account: GoogleSignInAccount,
        onLogin: (AuthLoginResult) -> Unit,
        onCreateNetwork: (email: String?, authJwt: String?, userName: String) -> Unit,
    ) -> Unit,
    loginError: String?,
    setLoginError: (String?) -> Unit,
    googleAuthInProgress: Boolean,
    setGoogleAuthInProgress: (Boolean) -> Unit,
    launchDialog: (OverlayMode) -> Unit
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
//    val overlayVc = application?.overlayVc
//    Log.i("LoginInitial", "overlayVc is: $overlayVc")

    val loginActivity = context as? LoginActivity

    val guestModeStr = buildAnnotatedString {
        append(stringResource(id = R.string.commitment_issues))

        pushStringAnnotation(
            tag = "GUEST_MODE",
            annotation = "Guest Mode"
        )
        withStyle(
            style = SpanStyle(
                color = Color.White
            )
        ) {
            append(" ${stringResource(id = R.string.try_guest_mode)}")
        }
        pop()

    }

    val onLogin: (AuthLoginResult) -> Unit = { result ->
        navController.navigate("login-password/${result.userAuth}")
    }

    val onNewNetwork: (AuthLoginResult) -> Unit = { result ->
        navController.navigate("create-network/${result.userAuth}")
    }

    val googleClientId = context.getString(R.string.google_client_id)
    val googleSignInOpts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(googleClientId)
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOpts)

    val onLoginGoogle: (AuthLoginResult) -> Unit = { result ->
        application?.login(result.network.byJwt)

        loginActivity?.authClientAndFinish { error ->
            setLoginError(error)
        }
    }

    LaunchedEffect(Unit) {
        googleSignInClient.signOut()
        setGoogleAuthInProgress(false)
    }

    val onNetworkCreateGoogle: (
        email: String?,
        authJwt: String?,
        userName: String
            ) -> Unit = { email, authJwt, userName ->
        navController.navigate("create-network-jwt/${email}/$authJwt/$userName")
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)

            googleLogin(
                context,
                application?.api,
                account,
                onLoginGoogle,
                onNetworkCreateGoogle
            )

        } catch (e: ApiException) {
            setLoginError("Error signing in with Google")
        }
    }

    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // need to debug why this is 0
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            OnboardingCarousel()

            Spacer(modifier = Modifier.height(64.dp))

            URTextInput(
                value = userAuth,
                onValueChange = {
                    setUserAuth(it)
                },
                placeholder = stringResource(id = R.string.user_auth_placeholder),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Go
                ),
                onGo = {
                    login(
                        context,
                        application?.api,
                        onLogin,
                        onNewNetwork,
                    )
                },
                label = stringResource(id = R.string.user_auth_label)
            )

            Spacer(modifier = Modifier.height(16.dp))

            URButton(
                onClick = {
                    login(
                        context,
                        application?.api,
                        onLogin,
                        onNewNetwork,
                    )
                },
                enabled = !userAuthInProgress && isValidUserAuth,
                isProcessing = userAuthInProgress
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.get_started), style = buttonTextStyle)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("or",
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            URButton(
                style = ButtonStyle.SECONDARY,
                onClick = {
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                },
                enabled = !googleAuthInProgress
            ) { buttonTextStyle ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // todo - this looks a little blurry
                    Image(
                        painter = painterResource(id = R.drawable.google_login_icon),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(stringResource(id = R.string.google_auth_btn_text), style = buttonTextStyle)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row() {

                ClickableText(
                    text = guestModeStr,
                    onClick = { offset ->
                        guestModeStr.getStringAnnotations(
                            tag = "GUEST_MODE", start = offset, end = offset
                        ).firstOrNull()?.let {

                            // Log.i("LoginInitial", "overlay VC is: $overlayVc")

                            // overlayViewModel
                            launchDialog(OverlayMode.OnboardingGuestMode)

                            // overlayVc?.openOverlay(OverlayMode.OnboardingGuestMode.toString())
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(color = TextMuted)
                )
            }
        }
        URSnackBar(
            type = SnackBarType.ERROR,
            isVisible = loginError != null,
            onDismiss = {
                setLoginError(null)
            }
        ) {
            Column() {
                Text(stringResource(id = R.string.something_went_wrong))
                Text(stringResource(id = R.string.please_wait))
            }
        }

    }
}

@Preview()
@Composable
private fun LoginInitialPreview() {

    val navController = rememberNavController()

    val login: (
        Context,
        BringYourApi?,
        (AuthLoginResult) -> Unit,
        (AuthLoginResult) -> Unit,
    ) -> Unit = { context, api, onLogin, onNewNetwork ->

    }

    val googleLogin : (
        Context,
        BringYourApi?,
        GoogleSignInAccount,
        (AuthLoginResult) -> Unit,
        (email: String?, authJwt: String?, userName: String) -> Unit,
    ) -> Unit = { context, api, account, onLogin, onNewNetwork ->

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
                LoginInitial(
                    navController = navController,
                    userAuth = TextFieldValue("hello@ur.io"),
                    setUserAuth = {},
                    userAuthInProgress = false,
                    isValidUserAuth = true,
                    login = login,
                    googleLogin = googleLogin,
                    loginError = null,
                    setLoginError = {},
                    googleAuthInProgress = false,
                    setGoogleAuthInProgress = {},
                    launchDialog = {}
                )
            }
        }
    }
}

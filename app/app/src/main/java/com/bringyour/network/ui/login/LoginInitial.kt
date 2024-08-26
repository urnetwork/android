package com.bringyour.network.ui.login

import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import com.bringyour.client.AuthLoginArgs
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.SnackBarType
import com.bringyour.network.ui.components.URSnackBar
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

@Composable()
fun LoginInitial(
    navController: NavController,
) {
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val loginActivity = context as? LoginActivity
    val overlayVc = application?.overlayVc
    // val loginVc = application?.loginVc
    var userAuth by remember { mutableStateOf(TextFieldValue()) }
    var inProgress by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    val isUserAuthBtnEnabled = !inProgress && (Patterns.EMAIL_ADDRESS.matcher(userAuth.text).matches() ||
            Patterns.PHONE.matcher(userAuth.text).matches())

    val guestModeStr = buildAnnotatedString {
        append("Commitment issues? ")

        pushStringAnnotation(
            tag = "GUEST_MODE",
            annotation = "Guest Mode"
        )
        withStyle(
            style = SpanStyle(
                color = Color.White
            )
        ) {
            append(" Try Guest Mode")
        }
        pop()

    }

    val googleClientId = context.getString(R.string.google_client_id)
    val googleSignInOpts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(googleClientId)
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOpts)

    val googleLogin = { account: GoogleSignInAccount ->
        Log.i("LoginInitialFragment", "GOOGLE LOGIN")

        inProgress = true

        val args = AuthLoginArgs()
        args.authJwt = account.idToken
        args.authJwtType = "google"

        application?.byApi?.authLogin(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {
                inProgress = false

                if (err != null) {
                    loginError = err.message
                } else if (result.error != null) {
                    loginError = result.error.message
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    loginError = null

                    application?.login(result.network.byJwt)

                    inProgress = true

                    loginActivity?.authClientAndFinish { error ->
                        inProgress = false

                        if (error == null) {
                            loginError = null
                        } else {
                            loginError = error
                        }
                    }
                } else if (result.authAllowed != null) {
                    val authAllowed = mutableListOf<String>()
                    for (i in 0 until result.authAllowed.len()) {
                        authAllowed.add(result.authAllowed.get(i))
                    }

                    loginError = context.getString(R.string.login_error_auth_allowed, authAllowed.joinToString(","))
                } else {
                    loginError = null

                    val authJwt = account.idToken
                    val userName = result.userName

                    navController.navigate("create-network-jwt/${account.email}/$authJwt/$userName")

                }
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            googleLogin(account)
        } catch (e: ApiException) {
            loginError = "Error signing in with Google"
        }
    }

    fun isValidUserAuth(userAuth: String): Boolean {
        return userAuth.isNotEmpty() &&
                (Patterns.EMAIL_ADDRESS.matcher(userAuth).matches() ||
                        Patterns.PHONE.matcher(userAuth).matches())
    }

    val login = {

        when {
            !isValidUserAuth(userAuth.text) -> {}
            else -> {
                inProgress = true

                val args = AuthLoginArgs()
                args.userAuth = userAuth.text.trim()

                application?.byApi?.authLogin(args) { result, err ->
                    runBlocking(Dispatchers.Main.immediate) {

                        Log.i("LoginInitialFragment", "GOT RESULT " + result)

                        if (err != null) {
                            loginError = err.message
                        } else if (result.error != null) {
                            loginError = result.error.message
                        } else if (result.authAllowed != null) {

                            if (result.authAllowed.contains("password")) {
                                // to the login password screen
                                loginError = null

                                navController.navigate("login-password/${result.userAuth}")
                            } else {
                                val authAllowed = mutableListOf<String>()
                                for (i in 0 until result.authAllowed.len()) {
                                    authAllowed.add(result.authAllowed.get(i))
                                }

                                loginError = context.getString(R.string.login_error_auth_allowed, authAllowed.joinToString(","))
                            }
                        } else {
                            // new network
                            navController.navigate("create-network/${result.userAuth}")

                        }

                        inProgress = false
                    }
                }
            }
        }
    }

    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // need to debug why this is 0
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            OnboardingCarousel()

            Spacer(modifier = Modifier.height(64.dp))

            // todo - input filter no spaces
            URTextInput(
                value = userAuth,
                onValueChange = { newValue ->
                    userAuth = newValue
                },
                placeholder = "Enter your phone number or email",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Send
                ),
                onSend = {
                    if (isUserAuthBtnEnabled) {
                        login()
                    }
                },
                label = "Email or phone"
            )

            Spacer(modifier = Modifier.height(16.dp))

            URButton(
                onClick = {
                    login()
                },
                enabled = isUserAuthBtnEnabled
            ) { buttonTextStyle ->
                Text("Get Started", style = buttonTextStyle)
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
                enabled = !inProgress
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

                    Text("Log in with Google", style = buttonTextStyle)
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
                            Log.i("Login Initial", "overlayVc is null? ${overlayVc == null}")
                            overlayVc?.openOverlay(OverlayMode.OnboardingGuestMode.toString())
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
                loginError = null
            }
        ) {
            Column() {
                Text("Something went wrong.")
                Text("Please wait a few minutes and try again.")
            }
        }

    }

}

@Preview()
@Composable
private fun LoginInitialPreview() {

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
                LoginInitial(
                    navController = navController
                )
            }
        }
    }
}
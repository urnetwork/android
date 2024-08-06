package com.bringyour.network

import android.os.Bundle
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.URNetworkTheme
import androidx.compose.ui.res.painterResource
import com.bringyour.network.ui.theme.TextMuted
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import com.bringyour.client.AuthLoginArgs
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.bringyour.client.BringYourApi
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

@Composable()
fun LoginInitialActivity(
    appLogin: (String) -> Unit,
    navigate: (Int, Bundle) -> Unit,
    byApi: BringYourApi?,
    loginActivity: LoginActivity?,
) {
    val context = LocalContext.current
    var userAuth by remember { mutableStateOf(TextFieldValue()) }
    var inProgress by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var googleBtnText by remember { mutableStateOf("Log in with Google") }

    val googleClientId = context.getString(R.string.google_client_id)

    val googleSignInOpts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(googleClientId)
        .requestEmail()
        .build()

    val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOpts)
    val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
    if (googleAccount != null) {
        googleBtnText = "Continue as ${googleAccount.email}"
    }

    val googleLogin = { account: GoogleSignInAccount ->
        Log.i("LoginInitialFragment", "GOOGLE LOGIN")

        inProgress = true

        val args = AuthLoginArgs()
        args.authJwt = account.idToken
        args.authJwtType = "google"

        byApi?.authLogin(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {
                inProgress = false

                if (err != null) {
                    loginError = err.message
                } else if (result.error != null) {
                    loginError = result.error.message
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    loginError = null

                    appLogin(result.network.byJwt)

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

                    val navArgs = Bundle()
                    navArgs.putString("authJwtType", "google")
                    navArgs.putString("authJwt", account.idToken)
                    navArgs.putString("userName", result.userName)
                    navArgs.putString("userAuth", account.email)

                    navigate(R.id.navigation_create_network_auth_jwt, navArgs)
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

                byApi?.authLogin(args) { result, err ->
                    runBlocking(Dispatchers.Main.immediate) {
                        inProgress = false

                        Log.i("LoginInitialFragment", "GOT RESULT " + result)

                        if (err != null) {
                            loginError = err.message
                        } else if (result.error != null) {
                            loginError = result.error.message
                        } else if (result.authAllowed != null) {

                            if (result.authAllowed.contains("password")) {
                                // to the login password screen
                                loginError = null
                                val navArgs = Bundle()
                                navArgs.putString("userAuth", result.userAuth)

                                navigate(R.id.navigation_password, navArgs)
                            } else {
                                val authAllowed = mutableListOf<String>()
                                for (i in 0 until result.authAllowed.len()) {
                                    authAllowed.add(result.authAllowed.get(i))
                                }

                                loginError = context.getString(R.string.login_error_auth_allowed, authAllowed.joinToString(","))
                            }
                        } else {
                            // new network
                            val navArgs = Bundle()
                            navArgs.putString("userAuth", result.userAuth)

                            navigate(R.id.navigation_create_network, navArgs)
                        }
                    }
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

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {

            Image(
                painter = painterResource(id = R.drawable.initial_login_1),
                contentDescription = "See all the world's content with URnetwork",
                modifier = Modifier.size(256.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("See all the", style = MaterialTheme.typography.headlineLarge)
                Text("world's content", style = MaterialTheme.typography.headlineLarge)
                Text("with URnetwork", style = MaterialTheme.typography.headlineMedium)
            }

        }

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
            value = userAuth,
            onValueChange = { newValue ->
                userAuth = newValue
            },
            placeholder = "Enter your phone number or email",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
        )

        Spacer(modifier = Modifier.height(32.dp))

        URButton(
            onClick = {
                login()
            }
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

                Text(googleBtnText, style = buttonTextStyle)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row() {
            Text(
                "Commitment issues?",
                color = TextMuted
            )
            Spacer(
                modifier = Modifier.width(4.dp)
            )
            Text("Try Guest Mode")
        }
    }
}

@Preview()
@Composable
fun LoginInitialPreview() {
    URNetworkTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LoginInitialActivity(
                    appLogin = {},
                    navigate = { id, navArgs -> },
                    byApi = null,
                    loginActivity = null,
                )
            }
        }
    }
}
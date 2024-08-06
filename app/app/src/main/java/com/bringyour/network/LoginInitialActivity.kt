package com.bringyour.network

import android.os.Bundle
import android.util.Log
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
import com.bringyour.client.AuthLoginArgs
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.*
import com.bringyour.client.BringYourApi
import com.google.android.gms.auth.api.signin.GoogleSignInOptions


@Composable()
fun LoginInitialActivity(
    appLogin: (String) -> Unit,
    loginSuccess: (Bundle) -> Unit,
    byApi: BringYourApi?,
    loginActivity: LoginActivity?,
) {
    val context = LocalContext.current
    var emailState by remember { mutableStateOf(TextFieldValue()) }
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

                    loginSuccess(navArgs)
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
            value = emailState,
            onValueChange = { newValue ->
                emailState = newValue
            },
            placeholder = "Enter your phone number or email"
        )

        Spacer(modifier = Modifier.height(32.dp))

        URButton(
            onClick = {}
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
            }
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
                    loginSuccess = {},
                    byApi = null,
                    loginActivity = null,
                )
            }
        }
    }
}
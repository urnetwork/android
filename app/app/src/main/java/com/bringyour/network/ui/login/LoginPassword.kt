package com.bringyour.network.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedOverlayLogin
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.client.AuthLoginWithPasswordArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.isTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginPassword(
    userAuth: String,
    navController: NavController
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MainApplication
    val loginActivity = context as? LoginActivity
    // var user by remember { mutableStateOf(TextFieldValue(userAuth)) }
    val user = TextFieldValue(userAuth)
    var password by remember { mutableStateOf(TextFieldValue()) }
    var inProgress by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var welcomeOverlayVisible by remember { mutableStateOf(false) }
    var isContentVisible by remember { mutableStateOf(true) }

    val isTv = isTv()

    LaunchedEffect(Unit) {
        inProgress = false
    }

    val login = {
        inProgress = true

        val args = AuthLoginWithPasswordArgs()
        args.userAuth = user.text
        args.password = password.text

        app?.api?.authLoginWithPassword(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {

                if (err != null) {
                    inProgress = false
                    loginError = err.message
                } else if (result.error != null) {
                    inProgress = false
                    loginError = result.error.message
                } else if (result.network != null) {
                    loginError = null

                    if (result.verificationRequired != null) {
                        
                        navController.navigate("verify/${userAuth}")

                    } else {
                        app.login(result.network.byJwt)

                        isContentVisible = false

                        delay(500)

                        if (!isTv) {
                            welcomeOverlayVisible = true

                            delay(250)
                        }

                        loginActivity?.authClientAndFinish { error ->
                            if (error != null) {
                                inProgress = false
                            }

                            loginError = error
                        }
                    }

                } else {
                    loginError = context.getString(R.string.login_error)
                }
            }
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
                        .padding(innerPadding)
                        .padding(16.dp),
                ) {

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            stringResource(id = R.string.login_password_header),
                            style = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center)
                        )
                        Spacer(modifier = Modifier.width(64.dp))
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 64.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {

                        LoginPasswordForm(
                            user = user,
                            password = password,
                            setPassword = { tfv ->
                                password = tfv
                            },
                            login = login,
                            inProgress = inProgress,
                            loginError = loginError,
                            onResetPassword = {
                                navController.navigate("reset-password/${userAuth}")
                            }
                        )
                    }
                }


            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        stringResource(id = R.string.login_password_header),
                        style = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center)
                    )

                    Spacer(modifier = Modifier.height(64.dp))

                    LoginPasswordForm(
                        user = user,
                        password = password,
                        setPassword = { tfv ->
                            password = tfv
                        },
                        login = login,
                        inProgress = inProgress,
                        loginError = loginError,
                        onResetPassword = {
                            navController.navigate("reset-password/${userAuth}")
                        }
                    )
                }
            }

        }
    }

    WelcomeAnimatedOverlayLogin(
        isVisible = welcomeOverlayVisible
    )
}

@Composable
fun LoginPasswordForm(
    user: TextFieldValue,
    password: TextFieldValue,
    setPassword: (TextFieldValue) -> Unit,
    login: () -> Unit?,
    inProgress: Boolean,
    loginError: String?,
    onResetPassword: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 512.dp)
            .imePadding()
    ) {
        URTextInput(
            value = user,
            onValueChange = {},
            placeholder = stringResource(id = R.string.user_auth_placeholder),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = if (password.text.isEmpty()) ImeAction.Done else ImeAction.Next
            ),
            label = stringResource(id = R.string.user_auth_label),
            enabled = false
        )

        URTextInput(
            value = password,
            onValueChange = { newValue ->
                // password = newValue
                setPassword(newValue)
            },
            placeholder = "*****************",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go
            ),
            isPassword = true,
            label = stringResource(id = R.string.password_label),
            onGo = {
                login()
            }
        )
        // }

        Spacer(modifier = Modifier.height(16.dp))

        URButton(
            onClick = {
                login()
            },
            enabled = !inProgress,
            isProcessing = inProgress
        ) { buttonTextStyle ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(id = R.string.continue_txt), style = buttonTextStyle)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Right Arrow",
                    modifier = Modifier.size(16.dp),
                    tint = if (!inProgress) Color.White else Color.Gray
                )
            }
        }

        if (loginError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("$loginError")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(id = R.string.forgot_password))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(id = R.string.reset_it),
                style = TextStyle(
                    color = BlueMedium,
                    fontSize = 16.sp
                ),
                modifier = Modifier.clickable {
                    onResetPassword()
                }
            )
//            ClickableText(
//                text = AnnotatedString(stringResource(id = R.string.reset_it)),
//                onClick = {
//                    // navController.navigate("reset-password/${userAuth}")
//                    onResetPassword()
//                },
//                style = TextStyle(
//                    color = BlueMedium,
//                    fontSize = 16.sp
//                )
//            )
        }
    }
}

@Preview()
@Composable
private fun LoginPasswordPreview() {

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
                LoginPassword(
                    userAuth = "hello@urnetwork.com",
                    navController
                )
            }
        }
    }
}

//@Preview(
//    name = "Landscape Preview",
//    device = "spec:width=1920dp,height=1080dp,dpi=480"
//)
//@Composable
//private fun LoginPasswordLandscapePreview() {
//    val navController = rememberNavController()
//
//    URNetworkTheme {
//        Scaffold(
//            modifier = Modifier.fillMaxSize()
//        ) { innerPadding ->
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(innerPadding)
//            ) {
//                LoginPassword(
//                    userAuth = "hello@urnetwork.com",
//                    navController
//                )
//            }
//        }
//    }
//}
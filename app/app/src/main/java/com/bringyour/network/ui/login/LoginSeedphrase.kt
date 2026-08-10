package com.bringyour.network.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSeedphrase(
    onLoginSuccess: (String) -> Unit,
    onBack: () -> Unit,
    loginViewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    var seedphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // the view model clears its in-progress flag as soon as authLogin returns,
    // but the login isn't done until the activity finishes -- keep the button
    // spinning until then so a second tap can't start a duplicate login
    var isFinishing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
                actions = {}
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Sign in with Seedphrase",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.widthIn(max = 512.dp)
            ) {
                OutlinedTextField(
                    value = seedphrase,
                    onValueChange = {
                        seedphrase = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("acceptance.secret.input"),
                    maxLines = 6,
                    // password keyboard type keeps IMEs from autocorrecting
                    // BIP39 words or learning the phrase into their dictionary
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    placeholder = {
                        Text(
                            "Paste your 12 or 24-word seedphrase here",
                            color = TextMuted
                        )
                    },
                    label = { Text("Seedphrase") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                URButton(
                    onClick = {
                        val trimmed = seedphrase.trim()
                        if (trimmed.isEmpty()) {
                            error = "Please enter a seedphrase"
                            return@URButton
                        }
                        val normalized = trimmed.lowercase()
                            .replace(Regex("\\s+"), " ")
                        val words = normalized.split(" ")
                        if (words.size != 12 && words.size != 24) {
                            error = "Seedphrase must be 12 or 24 words"
                            return@URButton
                        }
                        loginViewModel.loginWithSeedphrase(
                            ctx = context,
                            api = application?.api,
                            seedphrase = normalized,
                            onSuccess = { jwt ->
                                isFinishing = true
                                onLoginSuccess(jwt)
                            },
                            onError = { msg ->
                                isFinishing = false
                                error = msg
                            }
                        )
                    },
                    enabled = seedphrase.isNotBlank() && !isFinishing,
                    isProcessing = loginViewModel.seedphraseAuthInProgress || isFinishing,
                    modifier = Modifier.testTag("acceptance.secret.submit")
                ) { buttonTextStyle ->
                    Text("Sign In", style = buttonTextStyle)
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    URInlineErrorText(error)
                }
            }
        }
    }
}

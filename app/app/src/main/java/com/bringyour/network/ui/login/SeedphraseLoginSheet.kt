package com.bringyour.network.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.launch

/**
 * Sign in with an instant account's seed: a slide-up sheet with the same
 * presentation, sizing and dismiss behavior as [AuthCodeLoginSheet]. The seed
 * tile of the login stack opens it; the form, validation and login call are the
 * ones the full-screen seed login used to own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedphraseLoginSheet(
    isPresenting: Boolean,
    setIsPresenting: (Boolean) -> Unit,
    onLogin: (String) -> Unit, // network jwt
    loginViewModel: LoginViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication

    var seedphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // the view model clears its in-progress flag as soon as authLogin returns,
    // but the login isn't done until the activity finishes -- keep the button
    // spinning until then so a second tap can't start a duplicate login
    var isFinishing by remember { mutableStateOf(false) }

    LaunchedEffect(isPresenting) {
        if (!isPresenting) {
            seedphrase = ""
            error = null
            isFinishing = false
        }
    }

    val processing = loginViewModel.seedphraseAuthInProgress || isFinishing
    // resolved in composition: the validation runs inside the button's click lambda
    val requiredText = stringResource(id = R.string.seedphrase_required)
    val invalidWordCountText = stringResource(id = R.string.seedphrase_invalid_word_count)

    if (isPresenting) {

        ModalBottomSheet(
            onDismissRequest = {
                setIsPresenting(false)
            },
            sheetState = sheetState,
        ) {

            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {

                Text(
                    stringResource(id = R.string.sign_in_with_seedphrase),
                    style = MaterialTheme.typography.headlineLarge,
                )

                Text(
                    stringResource(id = R.string.step_into_the_internet),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(32.dp))

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
                    enabled = !processing,
                    // password keyboard type keeps IMEs from autocorrecting
                    // BIP39 words or learning the phrase into their dictionary
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    placeholder = {
                        Text(
                            stringResource(id = R.string.seedphrase_paste_hint),
                            color = TextMuted
                        )
                    },
                    label = { Text(stringResource(id = R.string.seedphrase)) }
                )

                Spacer(modifier = Modifier.height(48.dp))

                URButton(
                    onClick = {
                        val trimmed = seedphrase.trim()
                        if (trimmed.isEmpty()) {
                            error = requiredText
                            return@URButton
                        }
                        val normalized = trimmed.lowercase()
                            .replace(Regex("\\s+"), " ")
                        val words = normalized.split(" ")
                        if (words.size != 12 && words.size != 24) {
                            error = invalidWordCountText
                            return@URButton
                        }
                        loginViewModel.loginWithSeedphrase(
                            ctx = context,
                            api = application?.api,
                            seedphrase = normalized,
                            onSuccess = { jwt ->
                                isFinishing = true
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) {
                                        setIsPresenting(false)
                                    }
                                    onLogin(jwt)
                                }
                            },
                            onError = { msg ->
                                isFinishing = false
                                error = msg
                            }
                        )
                    },
                    borderColor = if (seedphrase.isNotBlank()) Black else TextMuted,
                    enabled = seedphrase.isNotBlank() && !processing,
                    isProcessing = processing,
                    modifier = Modifier.testTag("acceptance.secret.submit")
                ) { buttonTextStyle ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(id = R.string.sign_in),
                            style = buttonTextStyle,
                            color = if (seedphrase.isNotBlank()) Black else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                URInlineErrorText(error)

                Spacer(modifier = Modifier.height(16.dp))

            }

        }

    }
}

@Preview
@Composable
private fun SeedphraseLoginSheetPreview() {
    URNetworkTheme {

        Scaffold { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                SeedphraseLoginSheet(
                    isPresenting = true,
                    setIsPresenting = {},
                    onLogin = {}
                )
            }
        }
    }
}

package com.bringyour.network.ui.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.login.SolanaChallengeSignResult
import com.bringyour.network.ui.login.requestAndSignSolanaChallenge
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.AddAuthArgs
import com.bringyour.sdk.WalletAuthArgs
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch

private enum class AddAuthMethod { GOOGLE, WALLET, EMAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAuthMethodSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    showGoogleOption: Boolean,
    activityResultSender: ActivityResultSender?,
    isAddingAuth: Boolean,
    addAuth: (AddAuthArgs, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onAdded: () -> Unit,
) {
    if (!visible) {
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val methods = remember(showGoogleOption) {
        if (showGoogleOption) {
            listOf(AddAuthMethod.GOOGLE, AddAuthMethod.WALLET, AddAuthMethod.EMAIL)
        } else {
            listOf(AddAuthMethod.WALLET, AddAuthMethod.EMAIL)
        }
    }
    var selectedMethod by remember(methods) { mutableStateOf(methods.first()) }

    var email by remember { mutableStateOf(TextFieldValue("")) }
    var password by remember { mutableStateOf(TextFieldValue("")) }
    var addError by remember { mutableStateOf<String?>(null) }

    var walletConnectJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isConnectingWallet by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            walletConnectJob?.cancel()
        }
    }

    val formValid = when (selectedMethod) {
        AddAuthMethod.EMAIL -> email.text.isNotBlank() && password.text.length >= 12
        else -> true
    }

    val onAddClick: () -> Unit = {
        addError = null
        when (selectedMethod) {
            AddAuthMethod.EMAIL -> {
                val args = AddAuthArgs()
                args.userAuth = email.text
                args.password = password.text
                addAuth(
                    args,
                    {
                        Toast.makeText(context, "Sign-in method added successfully", Toast.LENGTH_SHORT).show()
                        onAdded()
                    },
                    { msg -> addError = msg }
                )
            }
            else -> { /* Google/Wallet complete on their own callback, no explicit Add click */ }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Add a sign-in method",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "Link another way to sign in to your account.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                methods.forEach { method ->
                    // URButton has no `modifier` parameter (checked against its
                    // real signature in URButton.kt) — wrap it in a weighted Box
                    // instead of trying to pass modifier through to URButton itself.
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                        URButton(
                            style = if (method == selectedMethod) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                            onClick = {
                                selectedMethod = method
                                addError = null
                            }
                        ) { buttonTextStyle ->
                            Text(
                                when (method) {
                                    AddAuthMethod.GOOGLE -> "Google"
                                    AddAuthMethod.WALLET -> "Wallet"
                                    AddAuthMethod.EMAIL -> "Email"
                                },
                                style = buttonTextStyle
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedMethod) {
                AddAuthMethod.GOOGLE -> {
                    // Google Sign-In is per-flavor code (com.google.android.gms.*
                    // is not on github's classpath) -- every flavor's source set
                    // provides its own GoogleAddAuthButton with this exact
                    // signature (real impl on google/solana_dapp/ethos_dapp,
                    // no-op stub on ungoogle/github).
                    GoogleAddAuthButton(
                        addAuth = addAuth,
                        isAddingAuth = isAddingAuth,
                        onAdded = onAdded,
                        onError = { msg -> addError = msg }
                    )
                }
                AddAuthMethod.WALLET -> {
                    Text(
                        "Connect a Solana wallet to add it as a sign-in method.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    URButton(
                        onClick = {
                            walletConnectJob = scope.launch {
                                activityResultSender?.let { sender ->
                                    val api = (context.applicationContext as? MainApplication)?.api
                                    if (api == null) {
                                        addError = "Error connecting to wallet"
                                        return@launch
                                    }
                                    isConnectingWallet = true
                                    when (val result = requestAndSignSolanaChallenge(sender, api)) {
                                        is SolanaChallengeSignResult.Success -> {
                                            val walletAuth = WalletAuthArgs()
                                            walletAuth.publicKey = result.signed.publicKey
                                            walletAuth.signature = result.signed.signature
                                            walletAuth.message = result.signed.message
                                            walletAuth.blockchain = "solana"
                                            val args = AddAuthArgs()
                                            args.walletAuth = walletAuth
                                            addAuth(
                                                args,
                                                {
                                                    Toast.makeText(context, "Wallet sign-in method added", Toast.LENGTH_SHORT).show()
                                                    onAdded()
                                                },
                                                { msg -> addError = msg }
                                            )
                                        }
                                        is SolanaChallengeSignResult.NoWalletFound -> {
                                            addError = "No compatible wallet app found on this device."
                                        }
                                        is SolanaChallengeSignResult.Failure -> {
                                            Log.i("AddAuthMethodSheet", "Error connecting to wallet: ${result.error}")
                                            addError = "Error connecting to wallet"
                                        }
                                    }
                                    isConnectingWallet = false
                                }
                            }
                        },
                        enabled = !isAddingAuth && !isConnectingWallet,
                        isProcessing = isConnectingWallet
                    ) { buttonTextStyle ->
                        Text("Connect Wallet", style = buttonTextStyle)
                    }
                }
                AddAuthMethod.EMAIL -> {
                    URTextInput(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        placeholder = "your@email.com",
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    URTextInput(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        placeholder = "Enter a password",
                        isPassword = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Password must be at least 12 characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            if (addError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                URInlineErrorText(addError)
            }

            if (selectedMethod == AddAuthMethod.EMAIL) {
                Spacer(modifier = Modifier.height(16.dp))
                URButton(
                    onClick = onAddClick,
                    enabled = !isAddingAuth && formValid,
                    isProcessing = isAddingAuth
                ) { buttonTextStyle ->
                    Text("Add Sign-In Method", style = buttonTextStyle)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

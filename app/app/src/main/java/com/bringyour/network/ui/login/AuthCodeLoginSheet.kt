package com.bringyour.network.ui.login

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthCodeLoginSheet(
    isPresenting: Boolean,
    setIsPresenting: (Boolean) -> Unit,
    onLogin: (String) -> Unit, // network jwt
    viewModel: AuthCodeLoginSheetViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    LaunchedEffect(isPresenting) {
        if (!isPresenting) {
            viewModel.clearAuthCode()
        }
    }

    AuthCodeLoginSheet(
        isPresenting = isPresenting,
        setIsPresenting = setIsPresenting,
        authCode = viewModel.authCode,
        setAuthCode = viewModel.setAuthCode,
        authCodeLogin = viewModel.authCodeLogin,
        authCodeLoginLoading = viewModel.isLoading,
        sheetState = sheetState,
        onLogin = onLogin
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthCodeLoginSheet(
    isPresenting: Boolean,
    setIsPresenting: (Boolean) -> Unit,
    authCode: TextFieldValue,
    setAuthCode: (TextFieldValue) -> Unit,
    authCodeLogin: AuthCodeLoginFunction,
    authCodeLoginLoading: Boolean,
    sheetState: SheetState,
    onLogin: (String) -> Unit, // network jwt
) {

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val authCodeErrorText = stringResource(id = R.string.auth_code_error)

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
                    stringResource(id = R.string.auth_code_login_sheet_header),
                    style = MaterialTheme.typography.headlineLarge,
                )

                Text(
                    stringResource(id = R.string.step_into_the_internet),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(32.dp))

                URTextInput(
                    value = authCode,
                    onValueChange = {setAuthCode(it)},
                    label = stringResource(id = R.string.authentication_code),
                    placeholder = stringResource(id = R.string.authentication_code_input_placeholder)
                )

                Spacer(modifier = Modifier.height(48.dp))

                URButton(
                    onClick = {
                        authCodeLogin(
                            application?.api,
                            {
                                Toast.makeText(context, authCodeErrorText, Toast.LENGTH_SHORT).show()
                            },
                            { result ->

                                scope.launch { sheetState.hide() }.invokeOnCompletion {

                                    if (!sheetState.isVisible) {
                                        setIsPresenting(false)
                                    }

                                    onLogin(result.jwt)
                                }
                            }
                        )

                    },
                    borderColor = if (authCode.text.isNotEmpty()) Black else TextMuted,
                    enabled = authCode.text.isNotEmpty() && !authCodeLoginLoading
                ) { buttonTextStyle ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(id = R.string.launch),
                            style = buttonTextStyle,
                            color = if (authCode.text.isNotEmpty()) Black else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

            }

        }

    }
}

@Preview
@Composable
private fun OnboardingGuestModeSheetPreview() {
    URNetworkTheme {

        Scaffold { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                AuthCodeLoginSheet(
                    isPresenting = true,
                    setIsPresenting = {},
                    onLogin = {}
                )
            }
        }
    }
}

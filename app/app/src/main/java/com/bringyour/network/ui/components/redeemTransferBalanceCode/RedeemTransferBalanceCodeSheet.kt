package com.bringyour.network.ui.components.redeemTransferBalanceCode

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.ppNeueBitBold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemTransferBalanceCodeSheet(
    sheetState: SheetState,
    setIsPresenting: (Boolean) -> Unit,
    onSuccess: () -> Unit,
    viewModel: RedeemTransferBalanceCodeViewModel = hiltViewModel()
) {

    val context = LocalContext.current
    val errorMsg = stringResource(id = R.string.error_redeeming_transfer_balance_code)
    val codeIsValid by viewModel.codeIsValid.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    ModalBottomSheet(
        onDismissRequest = { setIsPresenting(false) },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {

            Text(
                stringResource(id = R.string.redeem_balance_code),
                style = TextStyle(
                    fontFamily = ppNeueBitBold,
                    fontSize = 24.sp
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            URTextInput(
                value = viewModel.code,
                onValueChange = { viewModel.onTextChanged(it) },
                label = stringResource(id = R.string.balance_code),
                placeholder = stringResource(id = R.string.enter_balance_code)
            )

            Spacer(modifier = Modifier.height(12.dp))

            URButton(
                onClick = {
                    viewModel.redeem(
                        {
                            setIsPresenting(false)
                            onSuccess()
                        },
                        {
                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    )
                          },
                enabled = codeIsValid && !isLoading,
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.redeem), style = buttonTextStyle)
            }

        }
    }

}
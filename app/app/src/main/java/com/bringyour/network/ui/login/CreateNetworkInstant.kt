package com.bringyour.network.ui.login

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.TermsCheckbox
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.referral.ReferralAppliedChip
import com.bringyour.network.ui.components.referral.ReferralBonusSheet
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.ppNeueBitBold

internal const val ACCEPTANCE_INSTANT_TERMS_TAG = "acceptance.instant.terms"
internal const val ACCEPTANCE_INSTANT_CREATE_TAG = "acceptance.instant.create"
internal const val ACCEPTANCE_INSTANT_ERROR_TAG = "acceptance.instant.error"

/**
 * Connects the instant-account screen to its view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNetworkInstant(
    appLogin: (byJwt: String, newNetwork: Boolean) -> Unit,
    onBack: () -> Unit,
    createNetworkInstantViewModel: CreateNetworkInstantViewModel = hiltViewModel(),
) {
    var termsAgreed by rememberSaveable { mutableStateOf(false) }
    val inProgress by createNetworkInstantViewModel.inProgress.collectAsState()
    val error by createNetworkInstantViewModel.error.collectAsState()
    val presentBonusSheet by createNetworkInstantViewModel.presentBonusSheet.collectAsState()
    val referralInput = createNetworkInstantViewModel.referralInput

    CreateNetworkInstantContent(
        termsAgreed = termsAgreed,
        onTermsAgreedChanged = { termsAgreed = it },
        inProgress = inProgress,
        error = error,
        onCreate = {
            createNetworkInstantViewModel.createNetwork(termsAgreed, appLogin)
        },
        onBack = onBack,
        referralCode = referralInput.code,
        setReferralCode = referralInput.setCode,
        isValidatingReferralCode = referralInput.isValidating,
        isValidReferralCode = referralInput.isValid,
        isReferralCodeCapped = referralInput.isCapped,
        referralValidationComplete = referralInput.validationComplete,
        referralCodeInputSupportingTextRes = referralInput.supportingTextRes,
        presentBonusSheet = presentBonusSheet,
        setPresentBonusSheet = createNetworkInstantViewModel.setPresentBonusSheet,
        validateReferralCode = { onComplete ->
            createNetworkInstantViewModel.validateReferralCode(onComplete)
        },
    )
}

/**
 * Renders the instant-account controls with the acceptance tag on the exact
 * checkbox action rather than the linked terms row that contains it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateNetworkInstantContent(
    termsAgreed: Boolean,
    onTermsAgreedChanged: (Boolean) -> Unit,
    inProgress: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    referralCode: TextFieldValue = TextFieldValue(""),
    setReferralCode: (TextFieldValue) -> Unit = {},
    isValidatingReferralCode: Boolean = false,
    isValidReferralCode: Boolean = false,
    isReferralCodeCapped: Boolean = false,
    referralValidationComplete: Boolean = false,
    referralCodeInputSupportingTextRes: Int? = null,
    presentBonusSheet: Boolean = false,
    setPresentBonusSheet: (Boolean) -> Unit = {},
    validateReferralCode: ((Boolean) -> Unit) -> Unit = { it(false) },
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(id = R.string.back)
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
            Column(
                modifier = Modifier.widthIn(max = 512.dp)
            ) {
                Text(
                    stringResource(id = R.string.create_instant_account),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(id = R.string.instant_account_no_email_password),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                TermsCheckbox(
                    checked = termsAgreed,
                    onCheckChanged = onTermsAgreedChanged,
                    enabled = !inProgress,
                    checkboxModifier = Modifier.testTag(ACCEPTANCE_INSTANT_TERMS_TAG),
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isValidReferralCode && !isReferralCodeCapped) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ReferralAppliedChip()
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                URButton(
                    onClick = onCreate,
                    enabled = termsAgreed && !inProgress,
                    isProcessing = inProgress,
                    modifier = Modifier.testTag(ACCEPTANCE_INSTANT_CREATE_TAG),
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.create_account_2), style = buttonTextStyle)
                }

                error?.let { refusal ->
                    Spacer(modifier = Modifier.height(8.dp))
                    CreateNetworkInstantError(refusal)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (referralCode.text.isEmpty()) stringResource(id = R.string.add_referral_code)
                            else stringResource(id = R.string.edit_referral_code),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                if (!inProgress) {
                                    setPresentBonusSheet(true)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        style = TextStyle(
                            color = TextMuted,
                            fontFamily = ppNeueBitBold,
                            fontSize = 24.sp
                        )
                    )
                }
            }
        }

        /**
         * Referral sheet: accepting a code flips the sheet into the gold
         * royal-welcome moment before it dismisses itself
         */
        ReferralBonusSheet(
            presented = presentBonusSheet,
            onDismiss = { setPresentBonusSheet(false) },
            referralCode = referralCode,
            setReferralCode = setReferralCode,
            isValidating = isValidatingReferralCode,
            isValid = isValidReferralCode,
            isCapped = isReferralCodeCapped,
            validationComplete = referralValidationComplete,
            supportingTextRes = referralCodeInputSupportingTextRes,
            validate = validateReferralCode,
        )
    }
}

/** Exposes a server refusal at a stable acceptance-test boundary. */
@Composable
internal fun CreateNetworkInstantError(error: String) {
    URInlineErrorText(
        error,
        Modifier.testTag(ACCEPTANCE_INSTANT_ERROR_TAG),
    )
}

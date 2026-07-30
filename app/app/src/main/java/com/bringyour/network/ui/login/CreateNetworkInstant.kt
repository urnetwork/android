package com.bringyour.network.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.ui.components.TermsCheckbox
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.theme.Black

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNetworkInstant(
    appLogin: (String) -> Unit,
    onBack: () -> Unit,
    createNetworkInstantViewModel: CreateNetworkInstantViewModel = hiltViewModel(),
) {
    var termsAgreed by rememberSaveable { mutableStateOf(false) }
    val inProgress by createNetworkInstantViewModel.inProgress.collectAsState()
    val error by createNetworkInstantViewModel.error.collectAsState()

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
            Column(
                modifier = Modifier.widthIn(max = 512.dp)
            ) {
                Text(
                    "Create Instant Account",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "No email or password needed. Your account is secured by a seedphrase.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                TermsCheckbox(
                    checked = termsAgreed,
                    onCheckChanged = { termsAgreed = it },
                    enabled = !inProgress
                )

                Spacer(modifier = Modifier.height(16.dp))

                URButton(
                    onClick = {
                        createNetworkInstantViewModel.createNetwork(termsAgreed, appLogin)
                    },
                    enabled = termsAgreed && !inProgress,
                    isProcessing = inProgress
                ) { buttonTextStyle ->
                    Text("Create Account", style = buttonTextStyle)
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    URInlineErrorText(error)
                }
            }
        }
    }
}

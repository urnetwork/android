package com.bringyour.network.ui.upgrade

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.SectionLoadError
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted

/**
 * The upgrade route while the guest status is not known
 * (GuestAccount.upgradeEntry, UpgradeEntry.WaitForGuestStatus): a refreshed legacy
 * guest reads as an account until the server's `guest` has loaded, and a Play
 * purchase cannot be refused once it is paid (support inbox 7334), so no plan is
 * offered yet. The balance is read on entry; a spinner shows while a read is on its
 * way, and the read's error with a retry once it failed. When the balance answers,
 * the route shows the add-sign-in sheet or the plans by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeGuestStatusPending(
    readFailed: Boolean,
    readBalance: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        readBalance()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (readFailed) {
                SectionLoadError(onRetry = readBalance)
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(24.dp)
                        .height(24.dp),
                    color = TextMuted,
                    trackColor = TextFaint,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

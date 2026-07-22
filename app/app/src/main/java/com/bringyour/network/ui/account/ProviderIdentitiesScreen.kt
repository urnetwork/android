package com.bringyour.network.ui.account

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.Identicon
import com.bringyour.network.ui.shared.viewmodels.PostQuantumIdentityViewModel
import com.bringyour.network.ui.shared.viewmodels.ProviderIdentityRowUi
import com.bringyour.network.ui.shared.viewmodels.formatIdentityKeyHashForDisplay
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

/**
 * Provider Identities: a live list with one row per provider with an
 * established, identity-verified e2e session. Each row shows the provider's
 * identity key identicon, its canonical hash and its client id; tapping the
 * hash copies the full hash, tapping the client id copies the client id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderIdentitiesScreen(
    navController: NavController,
    viewModel: PostQuantumIdentityViewModel = hiltViewModel(),
) {

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.provider_identities),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(
                viewModel.providerIdentities,
                key = { it.clientId }
            ) { row ->
                Column(
                    modifier = Modifier.animateItem()
                ) {
                    ProviderIdentityRowView(row = row)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ProviderIdentityRowView(
    row: ProviderIdentityRowUi,
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Identicon(
            image = row.identicon,
            size = PostQuantumIdentityViewModel.ROW_IDENTICON_SIZE.dp
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            // the identity key hash, tap to copy the full hash
            Text(
                formatIdentityKeyHashForDisplay(row.publicKeyHash),
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                ),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clipboardManager.setText(AnnotatedString(row.publicKeyHash))
                        Toast.makeText(context, R.string.identity_key_hash_copied, Toast.LENGTH_SHORT).show()
                    }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // the client id, tap to copy
            Text(
                row.clientId,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = TextFaint,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clipboardManager.setText(AnnotatedString(row.clientId))
                        Toast.makeText(context, R.string.client_id_copied, Toast.LENGTH_SHORT).show()
                    }
            )
        }
    }
}

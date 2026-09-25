package com.bringyour.network.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Outbound
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme

/**
 * Account -> Settings -> Licenses: the data attributions and open source
 * software the app ships, from the list the SDK embeds. Tapping a row opens
 * [LicenseDetailScreen].
 */
@Composable
fun LicensesScreen(
    navController: NavController,
    viewModel: LicensesViewModel = hiltViewModel(),
) {
    val licenses by viewModel.licenses.collectAsState()
    LicensesScreen(
        licenses = licenses,
        onBack = { navController.popBackStack() },
        onOpen = { index -> navController.navigate(Route.LicenseDetail(index)) },
    )
}

/**
 * One license in full. Shares the [LicensesViewModel] of the list entry below
 * it on the back stack, so the list is not loaded again.
 */
@Composable
fun LicenseDetailScreen(
    navController: NavController,
    index: Int,
) {
    val parentEntry = remember(navController) { navController.getBackStackEntry(Route.Licenses) }
    val viewModel: LicensesViewModel = hiltViewModel(parentEntry)
    val licenses by viewModel.licenses.collectAsState()
    LicenseDetailScreen(
        license = licenses?.getOrNull(index),
        loading = licenses == null,
        onBack = { navController.popBackStack() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensesScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        title,
                        style = TopBarTitleTextStyle,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black,
        content = content,
    )
}

@Composable
private fun LicensesScreen(
    licenses: List<LicenseUi>?,
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    LicensesScaffold(
        title = stringResource(id = R.string.licenses),
        onBack = onBack,
    ) { innerPadding ->
        if (licenses == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TextMuted)
            }
            return@LicensesScaffold
        }

        // the SDK returns data attributions first, then software and fonts;
        // keep each entry's index into the full list for the detail route
        val indexed = remember(licenses) { licenses.withIndex().toList() }
        val data = remember(indexed) { indexed.filter { it.value.isData } }
        val software = remember(indexed) { indexed.filter { !it.value.isData } }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .tabletReadableColumn()
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item(key = "intro") {
                Text(
                    stringResource(id = R.string.licenses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            if (data.isNotEmpty()) {
                item(key = "data-header") {
                    URTextInputLabel(stringResource(id = R.string.licenses_data_header))
                }
                itemsIndexed(
                    data,
                    key = { i, entry -> indexedLazyListKey("license-data", i, entry.value.name) }
                ) { _, entry ->
                    LicenseRow(entry.value, onClick = { onOpen(entry.index) })
                    HorizontalDivider(color = MainBorderBase)
                }
                item(key = "data-spacer") {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            if (software.isNotEmpty()) {
                item(key = "software-header") {
                    URTextInputLabel(stringResource(id = R.string.licenses_software_header))
                }
                itemsIndexed(
                    software,
                    key = { i, entry -> indexedLazyListKey("license-software", i, entry.value.name) }
                ) { _, entry ->
                    LicenseRow(entry.value, onClick = { onOpen(entry.index) })
                    HorizontalDivider(color = MainBorderBase)
                }
            }

            item(key = "bottom-spacer") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LicenseRow(
    license: LicenseUi,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                license.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            val subtitle = license.subtitle
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            // a required attribution notice is shown in the list itself
            if (license.notice.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    license.notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted
        )
    }
}

@Composable
private fun LicenseDetailScreen(
    license: LicenseUi?,
    loading: Boolean,
    onBack: () -> Unit,
) {
    LicensesScaffold(
        title = stringResource(id = R.string.licenses),
        onBack = onBack,
    ) { innerPadding ->
        if (license == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(color = TextMuted)
                }
            }
            return@LicensesScaffold
        }

        val uriHandler = LocalUriHandler.current

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .tabletReadableColumn()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SelectionContainer {
                Column {
                    Text(
                        license.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    val subtitle = license.subtitle
                    if (subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }

                    if (license.notice.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            license.notice,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MainBorderBase)
                                .padding(12.dp)
                        )
                    }

                    if (license.copyright.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            license.copyright,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            if (license.url.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .clickable { uriHandler.openUri(license.url) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.licenses_project_page),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BlueMedium
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.Outbound,
                        contentDescription = null,
                        tint = BlueMedium
                    )
                }
            }

            if (license.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                // license texts are preformatted; scroll wide lines rather than rewrap them
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MainBorderBase)
                ) {
                    Text(
                        license.text,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        ),
                        color = Color.White,
                        softWrap = false,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private val previewLicenses = listOf(
    LicenseUi(
        name = "GeoLite2 by MaxMind",
        version = "",
        kind = "data",
        url = "https://www.maxmind.com",
        spdx = "CC-BY-SA-4.0",
        copyright = "Copyright MaxMind, Inc.",
        notice = "This product includes GeoLite2 data created by MaxMind, available from https://www.maxmind.com.",
        text = "Creative Commons Attribution-ShareAlike 4.0 International",
    ),
    LicenseUi(
        name = "androidx.compose.ui:ui",
        version = "1.7.0",
        kind = "software",
        url = "https://developer.android.com/jetpack/androidx/releases/compose-ui",
        spdx = "Apache-2.0",
        copyright = "",
        notice = "",
        text = "Apache License\nVersion 2.0, January 2004\nhttp://www.apache.org/licenses/",
    ),
)

@Preview
@Composable
private fun LicensesScreenPreview() {
    URNetworkTheme {
        LicensesScreen(licenses = previewLicenses, onBack = {}, onOpen = {})
    }
}

@Preview
@Composable
private fun LicenseDetailScreenPreview() {
    URNetworkTheme {
        LicenseDetailScreen(license = previewLicenses[0], loading = false, onBack = {})
    }
}

package com.bringyour.network.ui.account

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

/**
 * The Extenders section of the account screen (EXTENDER.md K6): the three
 * settings of this network space, the legacy private extender behind the
 * advanced expander, and the share and import actions of K7.
 *
 * An empty field means the derived default, which the box shows as its
 * placeholder — clearing a box is how a user goes back to the default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtendersScreen(
    navController: NavController,
    viewModel: ExtendersViewModel = hiltViewModel(),
) {

    val context = LocalContext.current
    val settings = viewModel.settings
    val privateExtender = viewModel.privateExtender

    var dnsName by remember { mutableStateOf(TextFieldValue()) }
    var gossipUrl by remember { mutableStateOf(TextFieldValue()) }
    var hosts by remember { mutableStateOf(TextFieldValue()) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var privateIp by remember { mutableStateOf(TextFieldValue()) }
    var privateSecret by remember { mutableStateOf(TextFieldValue()) }

    // the form follows the sdk's settings as they load and as an import
    // replaces them
    LaunchedEffect(settings) {
        settings ?: return@LaunchedEffect
        dnsName = TextFieldValue(settings.dnsNameField)
        gossipUrl = TextFieldValue(settings.gossipUrlField)
        hosts = TextFieldValue(extenderHostsText(settings.hosts))
    }

    LaunchedEffect(privateExtender) {
        privateIp = TextFieldValue(privateExtender.ip)
        privateSecret = TextFieldValue(privateExtender.secret)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.extenders),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .tabletReadableColumn()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp)),
        ) {

            Text(
                stringResource(id = R.string.extender_settings),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExtenderSettingField(
                label = stringResource(id = R.string.extender_dns_name),
                value = dnsName,
                onValueChange = { dnsName = it },
                defaultValue = settings?.dnsNamePlaceholder ?: "",
                enabled = viewModel.editable,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExtenderSettingField(
                label = stringResource(id = R.string.gossip_url),
                value = gossipUrl,
                onValueChange = { gossipUrl = it },
                defaultValue = settings?.gossipUrlPlaceholder ?: "",
                enabled = viewModel.editable,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExtenderSettingField(
                label = stringResource(id = R.string.extender_hosts),
                value = hosts,
                onValueChange = { hosts = it },
                defaultValue = "",
                supportingText = stringResource(id = R.string.extender_hosts_hint),
                enabled = viewModel.editable,
                maxLines = 5,
            )

            Spacer(modifier = Modifier.height(24.dp))

            URButton(
                onClick = {
                    val saved = viewModel.saveSettings(
                        dnsName = dnsName.text,
                        gossipUrl = gossipUrl.text,
                        hosts = extenderHostsFromText(hosts.text),
                    )
                    if (saved) {
                        Toast.makeText(
                            context,
                            R.string.extender_settings_saved,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = viewModel.editable,
            ) { style ->
                Text(stringResource(id = R.string.save), style = style)
            }

            Spacer(modifier = Modifier.height(24.dp))

            /**
             * Advanced: the legacy single private extender with its secret,
             * which overrides every discovered extender while it is set.
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advancedExpanded = !advancedExpanded }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(id = R.string.advanced))
                Icon(
                    if (advancedExpanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = stringResource(id = R.string.advanced),
                    tint = TextMuted,
                )
            }

            if (advancedExpanded) {

                Text(
                    stringResource(id = R.string.private_extender),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    stringResource(id = R.string.private_extender_hint),
                    style = TextStyle(fontSize = 12.sp),
                    color = TextFaint,
                )

                Spacer(modifier = Modifier.height(16.dp))

                ExtenderSettingField(
                    label = stringResource(id = R.string.private_extender_ip),
                    value = privateIp,
                    onValueChange = { privateIp = it },
                    defaultValue = "",
                )

                Spacer(modifier = Modifier.height(16.dp))

                ExtenderSettingField(
                    label = stringResource(id = R.string.private_extender_secret),
                    value = privateSecret,
                    onValueChange = { privateSecret = it },
                    defaultValue = "",
                )

                Spacer(modifier = Modifier.height(24.dp))

                URButton(
                    onClick = {
                        val saved = viewModel.savePrivateExtender(
                            ip = privateIp.text,
                            secret = privateSecret.text,
                        )
                        if (saved) {
                            Toast.makeText(
                                context,
                                R.string.extender_settings_saved,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                ) { style ->
                    Text(stringResource(id = R.string.save), style = style)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            HorizontalDivider()

            ExtenderActionRow(
                text = stringResource(id = R.string.share_extenders),
                onClick = { navController.navigate(Route.ShareExtenders) },
            )

            HorizontalDivider()

            ExtenderActionRow(
                text = stringResource(id = R.string.import_extenders),
                onClick = { navController.navigate(Route.ImportExtenders) },
            )

            HorizontalDivider()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * One settings field. The derived default is the placeholder, so an empty box
 * reads as "the default is in force" rather than as a missing value.
 */
@Composable
private fun ExtenderSettingField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    defaultValue: String,
    supportingText: String? = null,
    enabled: Boolean = true,
    maxLines: Int = 1,
) {
    URTextInput(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = if (defaultValue.isNotEmpty()) {
            stringResource(id = R.string.extender_default_value, defaultValue)
        } else {
            ""
        },
        supportingText = supportingText,
        enabled = enabled,
        maxLines = maxLines,
    )
}

@Composable
private fun ExtenderActionRow(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text)
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = text,
            tint = TextMuted,
        )
    }
}

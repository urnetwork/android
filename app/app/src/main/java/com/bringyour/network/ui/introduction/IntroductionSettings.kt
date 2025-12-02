package com.bringyour.network.ui.introduction

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bringyour.network.ui.IntroRoute
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.R
import com.bringyour.network.ui.components.ProvideCellPicker
import com.bringyour.network.ui.components.ProvideControlModePicker
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.NeueBitLargeTextStyle
import com.bringyour.network.utils.lighten

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroductionSettings(
    navController: NavController,
    provideControlMode: ProvideControlMode,
    setProvideControlMode: (ProvideControlMode) -> Unit,
    provideIndicatorColor: Color,
    allowProvideCell: Boolean,
    toggleProvideCell: () -> Unit,
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {Text("")},
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "URnetwork",
                        modifier = Modifier.size(128.dp),

                        )
                }

                Text(
                    stringResource(id = R.string.reliability_settings),
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(id = R.string.reliability_settings_details),
                    style = NeueBitLargeTextStyle,
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    stringResource(id = R.string.adjust_setting_always_fill_data_faster),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MainTintedBackgroundBase.lighten(0.1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {

                    ProvideControlModePicker(
                        provideControlMode,
                        setProvideControlMode,
                        provideIndicatorColor
                    )

                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    stringResource(id = R.string.allow_provider_cell_network_unlimited_plan),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MainTintedBackgroundBase.lighten(0.1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {

                    ProvideCellPicker(
                        allowProvideCell = allowProvideCell,
                        toggleProvideCell = toggleProvideCell
                    )
                }

            }

            Column {

                Spacer(modifier = Modifier.height(16.dp))

                URButton(onClick = {
                    navController.navigate(IntroRoute.IntroductionReferral)
                }) { btnStyle ->
                    Text(
                        stringResource(id = R.string.next),
                        style = btnStyle
                    )
                }
            }
        }
    }
}
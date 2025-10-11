package com.bringyour.network.ui.introduction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.ui.IntroRoute
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroductionInitial(
    navController: NavHostController,
    dismiss: () -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(
                        onClick = dismiss
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "close")
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
                .padding(innerPadding)
                .padding(16.dp)
        ) {

            Text(
                "Welcome to URnetwork",
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                "URnetwork is the most local and most private network on the planet. With over 20x usable cities of other leading VPNs, and 100x fewer users per IP address. Unlock all the fun in the world and all the privacy without compromises.",
                style = MaterialTheme.typography.bodyMedium
            )

            Column(
                modifier = Modifier
//                    .padding(horizontal = 16.dp)
                    .background(
                        OffBlack,
                        RoundedCornerShape(12.dp)
                    )
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Upgrade options here")
            }

            Text("or")

            Column(
                modifier = Modifier
//                    .padding(horizontal = 16.dp)
                    .background(
                        OffBlack,
                        RoundedCornerShape(12.dp)
                    )
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Participate in the network and get free access to the community edition.",
                    style = TopBarTitleTextStyle
                )

                Text(
                    "URnetwork is powered by a patented protocol that keeps everyone safe and secure.",
                    style = MaterialTheme.typography.bodyMedium
                )

                URButton(onClick = {
                    navController.navigate(IntroRoute.IntroductionUsageBar)
                }) { btnStyle ->
                    Text("Participate", style = btnStyle)
                }
            }

        }
    }

}
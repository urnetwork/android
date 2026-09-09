package com.bringyour.network.ui.introduction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bringyour.network.ui.components.tabletReadableColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.widgets.QuickConnectAndWidgets
import com.bringyour.network.widgets.WidgetEntry

/**
 * The last onboarding page: the quick connect tile and the home screen
 * widgets. The body is [QuickConnectAndWidgets], the same content Account >
 * Widgets shows later, so the two never drift apart; this page only adds the
 * onboarding headline, the step bar and the closing button.
 */
@Composable
fun IntroductionQuickConnect(
    navController: NavController,
    dismiss: () -> Unit,
    // the closing button: the offer page next (the holdout leaves the flow)
    next: () -> Unit = dismiss,
) {
    Scaffold(
        topBar = {
            IntroductionTopBar(step = 5, onSkip = dismiss, onBack = { navController.popBackStack() })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .tabletReadableColumn()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column {

                Text(
                    stringResource(id = R.string.intro_quick_connect_title),
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // nothing real exists before sign-in: the sample stands in
                QuickConnectAndWidgets(entry = remember { WidgetEntry.sample() })

                Spacer(modifier = Modifier.height(24.dp))
            }

            URButton(onClick = {
                next()
            }) { btnStyle ->
                Text(
                    stringResource(id = R.string.get_connected),
                    style = btnStyle
                )
            }
        }
    }
}

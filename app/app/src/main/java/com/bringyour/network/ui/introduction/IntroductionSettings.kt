package com.bringyour.network.ui.introduction

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bringyour.network.ui.IntroRoute
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.Pink
import androidx.core.net.toUri
import com.bringyour.network.ui.components.ProvideCellPicker
import com.bringyour.network.ui.components.ProvideControlModePicker
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
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

    val context = LocalContext.current
    val annotatedText = buildAnnotatedString {
        append("A local provider will run when you are connected to the network. There are over 30k providers today, and we have zero known security or ISP incidents. We value the ability for people to participate without issues, and have built the protocol to put safety first. ")

        // Start annotation for the link
        val startIndex = length
        val linkText = "Learn more at the protocol page"
        append(linkText)
        val endIndex = length

        // Add annotation for the link
        addStringAnnotation(
            tag = "URL",
            annotation = "https://ur.io/protocol",
            start = startIndex,
            end = endIndex
        )

        // Style the link text
        addStyle(
            style = SpanStyle(
                color = Pink,
                fontSize = 16.sp
            ),
            start = startIndex,
            end = endIndex
        )
    }

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

                Text(
                    "Step 1",
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    modifier = Modifier
                        .background(
                            OffBlack,
                            RoundedCornerShape(12.dp)
                        )
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    ClickableText(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        onClick = { offset ->
                            annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    val intent = Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                                    context.startActivity(intent)
                                }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "You can adjust the setting to Always to fill the free data faster.",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "You can also allow the provider to use cell network data, which works great if you have an unlimited plan.",
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

            }

            URButton(onClick = {
                navController.navigate(IntroRoute.IntroductionReferral)
            }) { btnStyle ->
                Text("Next")
            }

        }
    }

}
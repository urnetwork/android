package com.bringyour.network

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.URNetworkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import com.bringyour.network.ui.components.buttonTextStyle
import com.bringyour.network.ui.theme.TextMuted


@Composable()
fun LoginInitialActivity() {
    val emailState = remember { mutableStateOf(TextFieldValue()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally

    ) {

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {

            Image(
                painter = painterResource(id = R.drawable.initial_login_1), // Replace with your PNG's resource ID
                contentDescription = "See all the world's content with URnetwork",
                modifier = Modifier.size(256.dp) // Set the desired size (here, 100dp x 100dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("See all the", style = MaterialTheme.typography.headlineLarge)
                Text("world's content", style = MaterialTheme.typography.headlineLarge)
                Text("with URnetwork", style = MaterialTheme.typography.headlineMedium)
            }

        }

        Spacer(modifier = Modifier.height(64.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                "Email or phone",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = TextMuted
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        URTextInput(
            value = emailState.value,
            onValueChange = { newValue ->
                emailState.value = newValue
            },
            placeholder = "Enter your phone number or email"
        )

        Spacer(modifier = Modifier.height(32.dp))

        URButton(
            onClick = {}
        ) { buttonTextStyle ->
            Text("Get Started", style = buttonTextStyle)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("or",
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        URButton(
            style = ButtonStyle.SECONDARY,
            onClick = {}
        ) { buttonTextStyle ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                // todo - this looks a little blurry
                Image(
                    painter = painterResource(id = R.drawable.google_login_icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Text("Log in with Google", style = buttonTextStyle)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row() {
            Text(
                "Commitment issues?",
                color = TextMuted
            )
            Spacer(
                modifier = Modifier.width(4.dp)
            )
            Text("Try Guest Mode")
        }
    }
}

@Preview()
@Composable
fun LoginInitialPreview() {
    URNetworkTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LoginInitialActivity()
            }
        }
    }
}
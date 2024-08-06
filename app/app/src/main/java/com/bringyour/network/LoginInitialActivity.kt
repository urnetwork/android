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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.components.TextInput
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable()
fun LoginInitialActivity() {

    val context = LocalContext.current

    val imageBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.initial_login_1)
    }
    val maskBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.initial_login_vector)
    }

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
            Box(
                modifier = Modifier
                    .size(256.dp) // Set dimensions to 256x256 dp
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    drawImage(
                        image = imageBitmap.asImageBitmap(),
                        dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt())
                    )
//                    drawImage(
//                        image = maskBitmap.asImageBitmap(),
//                        dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt()),
//                        blendMode = BlendMode.SrcIn
//                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("See all the", style = MaterialTheme.typography.headlineLarge)
                Text("world's content", style = MaterialTheme.typography.headlineLarge)
                Text("with URnetwork", style = MaterialTheme.typography.headlineMedium)
            }

        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                "Email or phone",
                style = TextStyle(
                    fontSize = 12.sp,
                    // todo - use theme for this
                    color = Color.LightGray
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        TextInput(
            value = emailState.value,
            onValueChange = { newValue ->
                emailState.value = newValue
            },
            placeholder = "Enter your phone number or email"
        )
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
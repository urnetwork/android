package com.bringyour.network.ui.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.ppNeueBitBold

@Composable
fun ConnectButton(
    onClick: () -> Unit,
    connectStatus: ConnectStatus
) {
    Box(
        modifier = Modifier
            .size(256.dp)
            .clipToBounds()
            .background(color = Color(0x0AFFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .zIndex(0f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = -(12).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                if (connectStatus == ConnectStatus.DISCONNECTED) {

                    Text(
                        "Tap to connect",
                        style = TextStyle(
                            fontSize = 20.sp,
                            lineHeight = 20.sp,
                            fontFamily = ppNeueBitBold,
                            fontWeight = FontWeight(700),
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(color = BlueMedium, shape = CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(color = Black, shape = CircleShape)
                                .align(Alignment.Center)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(color = BlueMedium, shape = CircleShape)
                                    .align(Alignment.Center)
                            ) {

                            }
                        }
                    }

                }
            }
        }

        Image(
            painter = painterResource(id = R.drawable.connect_mask),
            contentDescription = "Clickable Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(512.dp)
                .align(Alignment.Center)
                .zIndex(1f)
        )
    }
}

@Preview
@Composable
fun ConnectButtonPreview() {
    URNetworkTheme {
        ConnectButton(
            onClick = {},
            connectStatus = ConnectStatus.DISCONNECTED
        )
    }
}
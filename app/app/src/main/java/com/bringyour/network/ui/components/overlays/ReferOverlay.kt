package com.bringyour.network.ui.components.overlays

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.shared.viewmodels.ReferralCodeViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green300
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

@Composable
fun ReferOverlay(
    onDismiss: () -> Unit,
    referralCodeViewModel: ReferralCodeViewModel
) {

    ReferOverlay(
        onDismiss,
        referralLink = referralCodeViewModel.referralLink
    )
}

@Composable
fun ReferOverlay(
    onDismiss: () -> Unit,
    referralLink: String?
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // todo - fetch network referral code
    // val referralCode = "https://ur.io/network/my-referral-code/asdlfkjsldkfjsdf"

    OverlayBackground(
        onDismiss = onDismiss,
        bgImageResourceId = R.drawable.overlay_refer_bg
    ) {

        OverlayContent(
            backgroundColor = Green300,
            onDismiss = onDismiss
        ) {
            Text(
                stringResource(id = R.string.refer_friends_header),
                style = MaterialTheme.typography.headlineLarge,
                color = Black
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(id = R.string.refer_friends_detail),
                style = MaterialTheme.typography.bodyLarge,
                color = Black
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (referralLink != null) {

                QRCodeWithImage(
                    text = referralLink,
                    imageResId = R.drawable.qr_code_center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .background(
                            Color.White,
                            shape = RoundedCornerShape(size = 24.dp)
                        )
                        .padding(16.dp)

                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            referralLink,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )

                        Text(
                            stringResource(id = R.string.copy),
                            modifier = Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(referralLink))
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = BlueMedium
                            ),
                        )
                    }
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(24.dp)
                        .height(24.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = TextMuted,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            URButton(onClick = {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, referralLink)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(shareIntent, null))
            }) { textStyle ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.share),
                        style = textStyle
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.icon_share),
                        contentDescription = "Share Icon",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun QRCodeWithImage(
    text: String,
    imageResId: Int
) {

    val padding = 8.dp

    Box(
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(padding)
        ) {

            val size = constraints.maxWidth

            val paddingPx = with(LocalDensity.current) { padding.toPx() }

            // Adjust the size to account for padding
            val qrCodeSize = (size - 2 * paddingPx).toInt()

            val bitmap = generateQRCode(text, qrCodeSize)
            val trimmedBitmap = trimWhiteSpace(bitmap)

            Canvas(modifier = Modifier.fillMaxSize()) {

                drawIntoCanvas { canvas ->

                    val offsetX = (size - trimmedBitmap.width) / 2f
                    val offsetY = (size - trimmedBitmap.height) / 2f

                    withTransform({
                        scale(
                            size / trimmedBitmap.width.toFloat(),
                            size / trimmedBitmap.height.toFloat()
                        )
                    }) {
                        canvas.drawImage(
                            trimmedBitmap.asImageBitmap(),
                            Offset(offsetX, offsetY),
                            Paint()
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp) // design specifies 64.dp, but wasn't working for scanning
                    .align(Alignment.Center)
                    .background(Color.White, shape = CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
            ) {
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = "Center Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

fun generateQRCode(text: String, size: Int): Bitmap {
    val bitMatrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bitmap = createBitmap(size, size)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

fun trimWhiteSpace(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    var top = 0
    var bottom = height
    var left = 0
    var right = width

    // Find the top edge
    loop@ for (y in 0 until height) {
        for (x in 0 until width) {
            if (bitmap.getPixel(x, y) != -1) { // -1 represents white
                top = y
                break@loop
            }
        }
    }

    // Find the bottom edge
    loop@ for (y in height - 1 downTo 0) {
        for (x in 0 until width) {
            if (bitmap.getPixel(x, y) != -1) {
                bottom = y + 1
                break@loop
            }
        }
    }

    // Find the left edge
    loop@ for (x in 0 until width) {
        for (y in top until bottom) {
            if (bitmap.getPixel(x, y) != -1) {
                left = x
                break@loop
            }
        }
    }

    // Find the right edge
    loop@ for (x in width - 1 downTo 0) {
        for (y in top until bottom) {
            if (bitmap.getPixel(x, y) != -1) {
                right = x + 1
                break@loop
            }
        }
    }

    // Crop the bitmap
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

@Preview
@Composable
private fun ReferOverlayPreview() {
    URNetworkTheme {
        ReferOverlay(
            onDismiss = {},
            referralLink = "https://ur.io/network/my-referral-code/asdlfkjsldkfjsdf"
        )
    }
}

@Preview
@Composable
private fun QRCodeWithImagePreview() {
    QRCodeWithImage(
        text = "Hello, Compose!",
        imageResId = R.drawable.qr_code_center)
}
package com.bringyour.network.ui.components.overlays

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.bringyour.network.R
import com.bringyour.network.ui.components.referral.GoldAura
import com.bringyour.network.ui.components.referral.GoldOverlayScaffold
import com.bringyour.network.ui.components.referral.GoldShareButton
import com.bringyour.network.ui.components.referral.REFERRAL_BONUS_GIB_PER_DAY
import com.bringyour.network.ui.components.referral.REFERRAL_MAX_REFERRALS
import com.bringyour.network.ui.components.referral.ReferralFrog
import com.bringyour.network.ui.components.referral.ReferralGoldCodePill
import com.bringyour.network.ui.theme.ReferralGoldLight
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * The refer-friends overlay in the referral king-frog gold theme, matching
 * the ur.io referral panel. Once the network has referrals the panel is
 * "crowned": royal heading, crown line and a faster gold pulse.
 */
@Composable
fun ReferOverlay(
    onDismiss: () -> Unit,
    referralCode: String?,
    totalReferrals: Long = 0L,
) {

    val crowned = 0L < totalReferrals

    GoldOverlayScaffold(
        onDismiss = onDismiss
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 512.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // the crowned state pulses faster, like the site's royal aura
                    GoldAura(
                        modifier = Modifier.size(200.dp),
                        pulseMillis = if (crowned) 3400 else 5000
                    ) {
                        ReferralFrog(size = 120.dp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        if (crowned) {
                            stringResource(id = R.string.referral_royalty)
                        } else {
                            stringResource(id = R.string.refer_friends_header)
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        color = ReferralGoldLight,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(id = R.string.refer_friends_detail),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    if (crowned) {

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👑")

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                pluralStringResource(
                                    id = R.plurals.referral_crowned_congrats,
                                    count = totalReferrals.toInt(),
                                    totalReferrals.toInt(),
                                    minOf(totalReferrals, REFERRAL_MAX_REFERRALS.toLong()).toInt() * REFERRAL_BONUS_GIB_PER_DAY
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ReferralGoldLight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (referralCode != null) {

                        /**
                         * referrals no longer use deep links; friends enter the code
                         * when they sign up
                         */
                        Text(
                            stringResource(id = R.string.refer_friends_code_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ReferralGoldCodePill(
                            code = referralCode,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        GoldShareButton(
                            shareMessage = stringResource(
                                id = R.string.referral_share_message,
                                referralCode
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp),
                            color = ReferralGoldLight,
                            trackColor = TextMuted,
                        )
                    }
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
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(256.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            val size = with(LocalDensity.current) {256.dp.toPx()}

            val paddingPx = with(LocalDensity.current) { padding.toPx() }

            // Adjust the size to account for padding
            val qrCodeSize = (size - 2 * paddingPx).toInt()

            val trimmedBitmap = remember(text, qrCodeSize) {
                val bitmap = generateQRCode(text, qrCodeSize)
                trimWhiteSpace(bitmap)
            }

            val imageBitmap = remember(trimmedBitmap) { trimmedBitmap.asImageBitmap() }

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
                            imageBitmap,
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
            referralCode = "asdlfkjsldkfjsdf"
        )
    }
}

@Preview
@Composable
private fun ReferOverlayCrownedPreview() {
    URNetworkTheme {
        ReferOverlay(
            onDismiss = {},
            referralCode = "asdlfkjsldkfjsdf",
            totalReferrals = 4
        )
    }
}

@Preview(
    device = "spec:width=1920dp,height=1080dp,dpi=480"
)
@Composable
private fun ReferOverlayLandscapePreview() {
    URNetworkTheme {
        ReferOverlay(
            onDismiss = {},
            referralCode = "asdlfkjsldkfjsdf"
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

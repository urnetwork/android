package com.bringyour.network.ui.components.referral

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import java.net.URLEncoder

/**
 * The connect-link target of a referral code: the sdk's ConnectLinkUrl turns
 * it into https://ur.io/c?bonus=<code>, the link ur.io/c opens in the app (or
 * Play, with the link as the install referrer) on Android and in web signup
 * everywhere else.
 */
fun referralLinkTarget(code: String): String = "bonus=" + URLEncoder.encode(code, "UTF-8")

/**
 * The invitation the share buttons send (support inbox 1698): the localized
 * message, which names the code, then the code's ur.io/c link on its own
 * line. The code stays in the message: installs from F-Droid or a dApp store
 * carry no referrer, and those friends still type it.
 */
fun referralShareText(message: String, link: String?): String =
    if (link.isNullOrBlank()) message else "$message\n$link"

/** [referralShareText] for this network space's connect link. */
@Composable
fun referralShareMessage(referralCode: String): String {
    val message = stringResource(id = R.string.referral_share_message, referralCode)
    val context = LocalContext.current
    val link = remember(referralCode) {
        (context.applicationContext as? MainApplication)
            ?.deviceManager
            ?.networkSpace
            ?.connectLinkUrl(referralLinkTarget(referralCode))
    }
    return referralShareText(message, link)
}

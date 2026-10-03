package com.bringyour.network.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.bringyour.network.R

/** A support contact: the text shown as a link and the uri it opens. */
data class SupportLink(
    val text: String,
    val uri: String,
)

/**
 * The support contacts the feedback, settings and API error screens offer.
 * The feedback and settings screens offered only a Discord invite, which is
 * unreachable in some regions, so each entry point now lists the support
 * email first.
 */
object SupportContact {
    const val EMAIL = "support@ur.io"
    const val MAILTO = "mailto:$EMAIL"
    const val DISCORD_NAME = "Discord"
    const val DISCORD_INVITE = "https://discord.com/invite/RUNZXMwPRK"

    val emailLink = SupportLink(text = EMAIL, uri = MAILTO)
    val discordLink = SupportLink(text = DISCORD_NAME, uri = DISCORD_INVITE)

    val feedbackLinks: List<SupportLink> = listOf(emailLink, discordLink)
    val settingsLinks: List<SupportLink> = listOf(emailLink, discordLink)
    val apiErrorLinks: List<SupportLink> = listOf(emailLink, discordLink)
}

/** A tappable range of a sentence and the uri it opens. */
data class SupportLinkSpan(
    val start: Int,
    val end: Int,
    val uri: String,
)

/**
 * The spans of `text` that are link texts, first occurrence of each. A link
 * text a translation left out has no span, rather than a negative range.
 */
fun supportLinkSpans(text: String, links: List<SupportLink>): List<SupportLinkSpan> =
    links.mapNotNull { link ->
        val start = text.indexOf(link.text)
        if (start < 0 || link.text.isEmpty()) {
            null
        } else {
            SupportLinkSpan(start = start, end = start + link.text.length, uri = link.uri)
        }
    }

/**
 * Opens a support uri. A mailto with no mail app on the device copies the
 * address instead of failing silently (or throwing out of a tap handler).
 */
fun openSupportUri(context: Context, uri: String) {
    val intent = if (uri.startsWith("mailto:")) {
        Intent(Intent.ACTION_SENDTO, uri.toUri())
    } else {
        Intent(Intent.ACTION_VIEW, uri.toUri())
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        if (uri.startsWith("mailto:")) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(SupportContact.EMAIL, SupportContact.EMAIL))
            Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }
    }
}

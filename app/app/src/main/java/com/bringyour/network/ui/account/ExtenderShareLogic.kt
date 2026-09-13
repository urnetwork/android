package com.bringyour.network.ui.account

import com.bringyour.network.R

/**
 * The plain shapes and decisions of the extender account section (EXTENDER.md
 * K6, K7).
 *
 * The sdk owns the payload: encoding, decoding, the foreign host rule and the
 * settings write all live in its view controller, so this file never parses a
 * share. What lives here is what the screens do with the sdk's answers —
 * which is the part worth pinning, and the part that must stay testable
 * without the gomobile bindings.
 */

/** The three edited settings of K6 as the form holds them. */
data class ExtenderSettingsUi(
    // the effective value; `dnsNameDefault` says whether it is the derived
    // default, in which case the form shows it as a placeholder rather than
    // filling the box (an empty field means the default)
    val dnsName: String = "",
    val dnsNameDefault: Boolean = true,
    val gossipUrl: String = "",
    val gossipUrlDefault: Boolean = true,
    val hosts: List<String> = listOf(),
    // the host this space's extender network is keyed by, empty for a space
    // that runs none
    val networkHost: String = "",
) {
    /** The configured dns name, i.e. empty while the default is in force. */
    val dnsNameField: String
        get() = if (dnsNameDefault) "" else dnsName

    val gossipUrlField: String
        get() = if (gossipUrlDefault) "" else gossipUrl

    /**
     * The derived default behind an empty field, which the box shows as its
     * placeholder (K6). The sdk reports the EFFECTIVE value, so the default is
     * known only while it is the one in force: a field carrying an override
     * gets no placeholder rather than a placeholder that names the override
     * and calls it the default. Clearing the box and saving brings the real
     * default -- and this placeholder -- back.
     */
    val dnsNamePlaceholder: String
        get() = if (dnsNameDefault) dnsName else ""

    val gossipUrlPlaceholder: String
        get() = if (gossipUrlDefault) gossipUrl else ""
}

/** The legacy single private extender with its secret (K6, advanced). */
data class ExtenderPrivateUi(
    val ip: String = "",
    val secret: String = "",
) {
    val configured: Boolean
        get() = ip.isNotBlank()
}

/** A built share payload (K7). */
data class ExtenderShareUi(
    val text: String = "",
    val count: Int = 0,
    val includesSettings: Boolean = false,
) {
    /** Whether there is a payload at all — a space with no addresses has none. */
    val present: Boolean
        get() = text.isNotEmpty()
}

/**
 * The payload the share screen shows for the state of its "include extender
 * settings" switch. The screen derives nothing from the payload itself: the
 * count and whether the settings block made it in are the sdk's answers for
 * exactly the flag it was asked for, and a space with nothing to share
 * answers an empty payload. Passing the flag through unchanged is therefore
 * the whole rule, and rebuilding on every toggle is what keeps the rendered
 * code and the copied text the same payload.
 */
fun extenderShareFor(
    includeSettings: Boolean,
    build: (Boolean) -> ExtenderShareUi,
): ExtenderShareUi = build(includeSettings)

/** What a scanned, chosen or pasted payload turns out to be, before anything is applied (K7). */
data class ExtenderDecodeUi(
    val ok: Boolean = false,
    // one of the sdk's error key ids, empty when ok
    val errorKey: String = "",
    val networkHost: String = "",
    val foreignHost: Boolean = false,
    val count: Int = 0,
    val hasSettings: Boolean = false,
    val settingsHost: String = "",
)

/** The outcome of an import (K7). */
data class ExtenderImportUi(
    val ok: Boolean = false,
    val errorKey: String = "",
    val importedCount: Int = 0,
)

/**
 * What the import screen must do next with a decoded payload, given whether
 * the user asked to take the payload's settings too.
 */
sealed class ExtenderImportStep {
    /** Not a share payload at all, or none decoded yet. */
    data object Invalid : ExtenderImportStep()

    /**
     * Another operator's network, and the settings were not asked for: K7
     * refuses this, since its addresses could never be verified against this
     * space's records. A payload with no settings block can never leave this
     * state, which is what the message says.
     */
    data class ForeignHost(val networkHost: String) : ExtenderImportStep()

    /**
     * The payload carries settings and the user asked to take them, so the
     * operator host replacing this space's is confirmed first.
     */
    data class ConfirmSettings(val settingsHost: String) : ExtenderImportStep()

    /** Addresses only, for this space's own network: import outright. */
    data object Ready : ExtenderImportStep()
}

/**
 * The import decision (K7). `useSettings` is the screen's switch, which is
 * only offered when the payload has a settings block.
 */
fun extenderImportStep(decode: ExtenderDecodeUi?, useSettings: Boolean): ExtenderImportStep {
    if (decode == null || !decode.ok) {
        return ExtenderImportStep.Invalid
    }
    if (decode.foreignHost) {
        // a foreign payload is taken only together with its settings, and only
        // when it has some
        if (!useSettings || !decode.hasSettings) {
            return ExtenderImportStep.ForeignHost(decode.networkHost)
        }
        return ExtenderImportStep.ConfirmSettings(decode.settingsHost)
    }
    // this space's own network, but replacing the dns name, gossip url and
    // anchor is still a replacement worth confirming
    if (useSettings && decode.hasSettings) {
        return ExtenderImportStep.ConfirmSettings(decode.settingsHost)
    }
    return ExtenderImportStep.Ready
}

/** Whether the "use extender settings" switch is offered for a payload (K7). */
fun extenderUseSettingsOffered(decode: ExtenderDecodeUi?): Boolean =
    decode != null && decode.ok && decode.hasSettings

/**
 * The localized message for one of the sdk's error key ids. The sdk answers
 * with a key rather than a sentence so each app maps it to its own string;
 * anything this build does not know reads as an invalid payload.
 */
fun extenderImportErrorResId(errorKey: String): Int = when (errorKey) {
    // mirrors Sdk.ExtenderImportErrorForeignHost / ExtenderImportErrorInvalid
    "import_extenders_foreign_host" -> R.string.import_extenders_foreign_host
    else -> R.string.import_extenders_invalid
}

/** The manual bootstrap hosts as the multiline field shows them (K6). */
fun extenderHostsText(hosts: List<String>): String = hosts.joinToString("\n")

/**
 * The hosts of a multiline field, one per line. Blank lines and surrounding
 * whitespace are not hosts; the order the user typed is kept, since the list
 * is a bootstrap order.
 */
fun extenderHostsFromText(text: String): List<String> =
    text.split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

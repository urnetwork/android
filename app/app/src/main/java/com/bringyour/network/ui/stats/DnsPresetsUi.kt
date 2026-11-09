package com.bringyour.network.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R

/*
 * The DNS provider picker of connect ADBLOCK3RD (F1, F2) as plain data: the
 * picks, the summary of the settings in force and the status of the selected
 * preset, copied off the sdk's gomobile objects by DnsSettingsViewModel, and
 * the strings of the localization key ids the sdk hands the screens.
 *
 * The sdk owns every rule (dns_presets_ui.go): the picks and their order, the
 * pick in force, the personal id check, the summary, the pill, the status
 * lines and when the blocked count shows. What lives here is what the screens
 * do with its answers, kept off the gomobile classes so it runs in a JVM test.
 * Provider names, jurisdictions and urls are shown as the sdk has them, never
 * translated; a string that names the provider takes it as an argument. Each
 * preset has a name of its own (owner, 2026-11-09), a key with the adblock
 * provider's short name (dnsPresetName): the lines that name the selected
 * preset take that name, and the lines that name the adblock provider (the
 * disclosure, the personal id field, the verification line) take its name.
 */

// The sdk's pick kinds (Sdk.DnsPickKind*) and its selection of URnetwork's own
// dns (Sdk.DnsPresetIdDefault), as literals so this file stays off the native
// class's initializer.
const val DNS_PICK_KIND_DEFAULT = "default"
const val DNS_PICK_KIND_PRESET = "preset"
const val DNS_PICK_KIND_CUSTOM = "custom"
const val DNS_PICK_KIND_REGIONAL = "regional"
const val DNS_PRESET_ID_DEFAULT = "default"

// The sdk's error id of a personal id that does not have the provider's form
// (Sdk.DnsPresetErrorPersonalIdInvalid), which the provider's format message
// explains.
const val DNS_PRESET_ERROR_PERSONAL_ID_INVALID = "dns_preset_error_personal_id_invalid"

/** The ur.io help on DNS providers, behind "Learn more" on the DNS screen. */
const val DNS_PROVIDERS_HELP_URL = "https://ur.io/docs/faq#dns-providers"

/** The section of that help on where each provider shows the profile ID, and the block response to keep. */
const val DNS_PERSONAL_IDS_HELP_URL = "https://ur.io/docs/faq#dns-personal-ids"

/**
 * A preset's name as the sdk gives it (DnsPreset.NameKey and NameProvider),
 * shown by dnsPresetName: "AdGuard Family". Empty when there is none, as for
 * a dropped preset this release never knew.
 */
data class DnsPresetNameUi(
    // a dns_preset_name_* key, which takes the adblock provider's name
    val key: String = "",
    // the adblock provider's short name, as it is ("AdGuard" for AdGuard DNS)
    val provider: String = "",
)

/** One preset (sdk DnsPreset). */
data class DnsPresetUi(
    val presetId: String,
    // the adblock provider's name, for the lines that name it rather than the
    // preset: the disclosure, the personal id field and its copy line
    val providerName: String,
    // the preset's name, the row's label
    val name: DnsPresetNameUi = DnsPresetNameUi(),
    val jurisdiction: String,
    val privacyUrl: String,
    val helpUrl: String,
    // what it blocks, a dns_preset_copy_* key
    val copyKey: String,
    // a personal preset ("Use my account"), which takes the user's id
    val personalIdRequired: Boolean = false,
    // the provider's own example of an id, the field's placeholder
    val personalIdExample: String = "",
    // the provider's dashboard
    val personalIdHelpUrl: String = "",
    val personalIdWhereKey: String = "",
    val personalIdFormatKey: String = "",
    // the free plan's note, or empty
    val quotaNoteKey: String = "",
)

/** One row of the picker (sdk DnsPick), in the sdk's order. */
data class DnsPickUi(
    // DNS_PICK_KIND_*
    val kind: String,
    // the selection a tap saves; empty for Custom, which opens its editor
    val presetId: String,
    // the heading of a preset's group, empty for the other picks
    val groupKey: String = "",
    // the label of the default, custom and regional picks; a preset shows its name
    val labelKey: String = "",
    // the line under the label
    val copyKey: String = "",
    // the country of a regional pick
    val countryCode: String = "",
    val unencrypted: Boolean = false,
    val preset: DnsPresetUi? = null,
)

/** The settings in force as the summary row and the screen show them (sdk DnsSelectionSummary). */
data class DnsSelectionUi(
    // DNS_PICK_KIND_*; a stored preset that cannot apply reads as default, with dropped set
    val kind: String,
    // the pick in force; empty for Custom
    val presetId: String,
    // the selected preset's name, for the summary row, the Fast DNS note and
    // the Private DNS line, and its adblock provider's name, for the
    // disclosure; empty for the other kinds
    val name: DnsPresetNameUi = DnsPresetNameUi(),
    val providerName: String = "",
    // a preset's summary row takes its name
    val summaryKey: String = "",
    // the notice of a preset this release no longer offers, naming it as it
    // showed (empty for an id this release never knew)
    val dropped: Boolean = false,
    val droppedName: DnsPresetNameUi = DnsPresetNameUi(),
    // the pill over the summary, or empty, and the country it names
    val pillKey: String = "",
    val pillCountryCode: String = "",
)

/** What the status line, the verification line and the blocked count show (sdk DnsStatusSummary). */
data class DnsStatusUi(
    // empty unless a preset is selected
    val statusKey: String = "",
    // the selected preset's name, for the status line and the blocked count
    val name: DnsPresetNameUi = DnsPresetNameUi(),
    // its adblock provider's name, for the verification line and its link
    val providerName: String = "",
    val statusDetailKey: String = "",
    val verificationKey: String = "",
    // the provider's dashboard and its link, for a profile that cannot be confirmed
    val verificationUrl: String = "",
    val verificationLinkKey: String = "",
    val showUseDefault: Boolean = false,
    val showChangeLocation: Boolean = false,
    // the selected preset filters, so its blocked lookups are counted and the
    // screen reads the count again while it shows (sdk ShowBlockedCount)
    val countsBlocked: Boolean = false,
    // this connection's blocked lookups as shown, a floor; null shows none
    // (dnsShownBlockedCount)
    val blockedCount: Long? = null,
)

/**
 * This connection's blocked count as the DNS screen shows it, or null to show
 * none: only while the sdk counts for the selection in force and the device's
 * status describes that same preset. Right after the selection changes the
 * status can still name the previous preset, whose count is not the new one's,
 * and without a status (no device) there is nothing to count; both show
 * nothing rather than a stale number.
 */
fun dnsShownBlockedCount(
    // DnsStatusSummary.ShowBlockedCount and BlockedCount
    countsBlocked: Boolean,
    blockedCount: Long,
    // the preset the device's status describes (DnsResolverStatus.PresetId),
    // null without a status
    statusPresetId: String?,
    // the preset in force (DnsSelectionSummary.PresetId), null while unknown
    selectionPresetId: String?,
): Long? {
    if (!countsBlocked || statusPresetId == null || statusPresetId != selectionPresetId) {
        return null
    }
    return blockedCount.coerceAtLeast(0L)
}

/** A card of the picker: a preset group under its heading, or one pick with no group. */
data class DnsPickerSection(
    // empty for URnetwork's default, Custom and Regional
    val groupKey: String,
    val picks: List<DnsPickUi>,
)

/**
 * The picker's cards, in the sdk's order: the consecutive presets of a group
 * together, so each group shows once under its heading, and every pick with
 * no group alone, so Custom's editor opens right under it.
 */
fun dnsPickerSections(picks: List<DnsPickUi>): List<DnsPickerSection> {
    val sections = mutableListOf<DnsPickerSection>()
    for (pick in picks) {
        val last = sections.lastOrNull()
        if (last != null && pick.groupKey.isNotEmpty() && last.groupKey == pick.groupKey) {
            sections[sections.size - 1] = last.copy(picks = last.picks + pick)
        } else {
            sections.add(DnsPickerSection(groupKey = pick.groupKey, picks = listOf(pick)))
        }
    }
    return sections
}

/**
 * Whether the pick is the one in force: the summary's pick, or Custom for
 * custom settings. A stored preset that cannot apply marks URnetwork's
 * default, which is what applies.
 */
fun dnsPickSelected(pick: DnsPickUi, selection: DnsSelectionUi?): Boolean {
    if (selection == null) {
        return false
    }
    return if (pick.kind == DNS_PICK_KIND_CUSTOM) {
        selection.kind == DNS_PICK_KIND_CUSTOM
    } else {
        selection.kind != DNS_PICK_KIND_CUSTOM && pick.presetId == selection.presetId
    }
}

/**
 * The string of a localization key id the sdk hands the screens: a group
 * heading, a pick's label or line, a summary, a status or verification line,
 * a personal id note, an error id or a pill. Each id is its string's name.
 * Null for an empty id, or one this build does not know, which shows nothing.
 */
fun dnsKeyResId(keyId: String): Int? = when (keyId) {
    // the group headings
    "block_ads_and_trackers" -> R.string.block_ads_and_trackers
    "dns_group_block_malware" -> R.string.dns_group_block_malware
    "dns_group_family" -> R.string.dns_group_family
    "dns_group_my_account" -> R.string.dns_group_my_account
    "dns_group_no_filtering" -> R.string.dns_group_no_filtering
    // the labels and lines of the picks with no provider
    "dns_provider_default" -> R.string.dns_provider_default
    "dns_provider_default_copy" -> R.string.dns_provider_default_copy
    "dns_provider_custom" -> R.string.dns_provider_custom
    "dns_provider_custom_copy" -> R.string.dns_provider_custom_copy
    "dns_provider_regional" -> R.string.dns_provider_regional
    "dns_recommendation_message" -> R.string.dns_recommendation_message
    // each preset's name
    "dns_preset_name_ad_blocking" -> R.string.dns_preset_name_ad_blocking
    "dns_preset_name_malware_blocking" -> R.string.dns_preset_name_malware_blocking
    "dns_preset_name_family" -> R.string.dns_preset_name_family
    "dns_preset_name_child" -> R.string.dns_preset_name_child
    "dns_preset_name_child_ads" -> R.string.dns_preset_name_child_ads
    "dns_preset_name_no_filtering" -> R.string.dns_preset_name_no_filtering
    "dns_preset_name_personal" -> R.string.dns_preset_name_personal
    // what each preset blocks
    "dns_preset_copy_ads_phishing" -> R.string.dns_preset_copy_ads_phishing
    "dns_preset_copy_ads_malware" -> R.string.dns_preset_copy_ads_malware
    "dns_preset_copy_ads_malware_phishing" -> R.string.dns_preset_copy_ads_malware_phishing
    "dns_preset_copy_malware" -> R.string.dns_preset_copy_malware
    "dns_preset_copy_malware_phishing" -> R.string.dns_preset_copy_malware_phishing
    "dns_preset_copy_ads_adult_safe_search" -> R.string.dns_preset_copy_ads_adult_safe_search
    "dns_preset_copy_malware_adult" -> R.string.dns_preset_copy_malware_adult
    "dns_preset_copy_ads_malware_adult_drugs" -> R.string.dns_preset_copy_ads_malware_adult_drugs
    "dns_preset_copy_child" -> R.string.dns_preset_copy_child
    "dns_preset_copy_child_ads" -> R.string.dns_preset_copy_child_ads
    "dns_preset_copy_nothing" -> R.string.dns_preset_copy_nothing
    "dns_preset_copy_court_orders" -> R.string.dns_preset_copy_court_orders
    "dns_preset_copy_personal" -> R.string.dns_preset_copy_personal
    // the summary row; a selected preset's is dns_status_provider, with its
    // name
    "dns_summary_default" -> R.string.dns_summary_default
    "dns_summary_custom" -> R.string.dns_summary_custom
    "dns_summary_custom_unencrypted" -> R.string.dns_summary_custom_unencrypted
    "dns_summary_regional" -> R.string.dns_summary_regional
    // the status line and the line under it
    "dns_status_provider" -> R.string.dns_status_provider
    "dns_status_paused" -> R.string.dns_status_paused
    "dns_status_paused_detail" -> R.string.dns_status_paused_detail
    "dns_status_fallback" -> R.string.dns_status_fallback
    "dns_status_unreachable" -> R.string.dns_status_unreachable
    "dns_status_unreachable_detail" -> R.string.dns_status_unreachable_detail
    // the verification line and its link
    "dns_verify_not_filtering" -> R.string.dns_verify_not_filtering
    "dns_verify_unconfirmed" -> R.string.dns_verify_unconfirmed
    "dns_verify_rejected_controld" -> R.string.dns_verify_rejected_controld
    "dns_verify_malformed_adguard" -> R.string.dns_verify_malformed_adguard
    "dns_personal_id_dashboard_link" -> R.string.dns_personal_id_dashboard_link
    // the personal id field's notes
    "dns_personal_id_where_nextdns" -> R.string.dns_personal_id_where_nextdns
    "dns_personal_id_where_controld" -> R.string.dns_personal_id_where_controld
    "dns_personal_id_where_adguard" -> R.string.dns_personal_id_where_adguard
    "dns_personal_id_format_nextdns" -> R.string.dns_personal_id_format_nextdns
    "dns_personal_id_format_controld" -> R.string.dns_personal_id_format_controld
    "dns_personal_id_format_adguard" -> R.string.dns_personal_id_format_adguard
    "dns_personal_id_quota_nextdns" -> R.string.dns_personal_id_quota_nextdns
    "dns_personal_id_quota_adguard" -> R.string.dns_personal_id_quota_adguard
    // the error ids of a pick and its personal id
    "dns_preset_error_unknown" -> R.string.dns_preset_error_unknown
    "dns_preset_error_personal_id_required" -> R.string.dns_preset_error_personal_id_required
    "dns_preset_error_personal_id_invalid" -> R.string.dns_preset_error_personal_id_invalid
    "dns_preset_error_personal_id_other_provider" -> R.string.dns_preset_error_personal_id_other_provider
    "dns_preset_error_personal_id_not_accepted" -> R.string.dns_preset_error_personal_id_not_accepted
    // the pills
    "dns_pill_recommended" -> R.string.dns_pill_recommended
    "dns_pill_defaults" -> R.string.dns_pill_defaults
    else -> null
}

/**
 * The text of an sdk key id, or null for an id that shows nothing. The
 * arguments go only to a string that takes them, one with a "%1$" conversion,
 * so a caller can pass the provider's name with every key of a kind whose
 * strings may name it; a string with no placeholder is never formatted, as
 * it may hold a bare "%".
 */
@Composable
internal fun dnsKeyText(keyId: String, vararg formatArgs: Any): String? {
    val resId = dnsKeyResId(keyId) ?: return null
    val text = stringResource(id = resId)
    if (formatArgs.isEmpty() || !text.contains("%1\$")) {
        return text
    }
    return stringResource(resId, *formatArgs)
}

/**
 * A preset's name (owner, 2026-11-09): its key with the adblock provider's
 * short name, as it is, "AdGuard Family". A key this build does not know
 * shows the adblock provider's name alone; an empty name, nothing.
 */
@Composable
internal fun dnsPresetName(name: DnsPresetNameUi): String =
    dnsKeyText(name.key, name.provider) ?: name.provider

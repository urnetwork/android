package com.bringyour.network.ui.stats

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DNS provider picker's plain rules (DnsPresetsUi): every localization key
 * id the sdk hands the screens (sdk dns_presets_ui.go, ADBLOCK3RD G1) shows
 * the string of the same name, the picker groups the sdk's picks without
 * reordering them, and the pick in force is the summary's. Synthetic presets
 * stand in for the catalog, which only the sdk's tests read.
 */
class DnsPresetsUiTest {

    private fun stringName(id: Int): String =
        R.string::class.java.fields.first { it.getInt(null) == id }.name

    // every key id the sdk returns in a pick, a preset, a summary or a status,
    // and its error ids, as the localization store has them
    private val sdkKeyIds = listOf(
        "block_ads_and_trackers",
        "dns_group_block_malware",
        "dns_group_family",
        "dns_group_my_account",
        "dns_group_no_filtering",
        "dns_provider_default",
        "dns_provider_default_copy",
        "dns_provider_custom",
        "dns_provider_custom_copy",
        "dns_provider_regional",
        "dns_recommendation_message",
        "dns_preset_copy_ads_phishing",
        "dns_preset_copy_ads_malware",
        "dns_preset_copy_ads_malware_phishing",
        "dns_preset_copy_malware",
        "dns_preset_copy_malware_phishing",
        "dns_preset_copy_ads_adult_safe_search",
        "dns_preset_copy_malware_adult",
        "dns_preset_copy_ads_malware_adult_drugs",
        "dns_preset_copy_child",
        "dns_preset_copy_child_ads",
        "dns_preset_copy_nothing",
        "dns_preset_copy_court_orders",
        "dns_preset_copy_personal",
        "dns_summary_default",
        "dns_summary_ad_blocking",
        "dns_summary_malware_blocking",
        "dns_summary_family",
        "dns_summary_no_filtering",
        "dns_summary_personal",
        "dns_summary_custom",
        "dns_summary_custom_unencrypted",
        "dns_summary_regional",
        "dns_status_provider",
        "dns_status_paused",
        "dns_status_paused_detail",
        "dns_status_fallback",
        "dns_status_unreachable",
        "dns_status_unreachable_detail",
        "dns_verify_not_filtering",
        "dns_verify_unconfirmed",
        "dns_verify_rejected_controld",
        "dns_verify_malformed_adguard",
        "dns_personal_id_dashboard_link",
        "dns_personal_id_where_nextdns",
        "dns_personal_id_where_controld",
        "dns_personal_id_where_adguard",
        "dns_personal_id_format_nextdns",
        "dns_personal_id_format_controld",
        "dns_personal_id_format_adguard",
        "dns_personal_id_quota_nextdns",
        "dns_personal_id_quota_adguard",
        "dns_preset_error_unknown",
        "dns_preset_error_personal_id_required",
        "dns_preset_error_personal_id_invalid",
        "dns_preset_error_personal_id_other_provider",
        "dns_preset_error_personal_id_not_accepted",
        "dns_pill_recommended",
        "dns_pill_defaults",
    )

    @Test
    fun everySdkKeyIdShowsTheStringOfTheSameName() {
        for (keyId in sdkKeyIds) {
            val resId = dnsKeyResId(keyId)
            assertNotNull(keyId, resId)
            assertEquals(keyId, stringName(resId!!))
        }
        assertEquals(DNS_PRESET_ERROR_PERSONAL_ID_INVALID, stringName(dnsKeyResId(DNS_PRESET_ERROR_PERSONAL_ID_INVALID)!!))
    }

    @Test
    fun anEmptyOrUnknownKeyIdShowsNothing() {
        assertNull(dnsKeyResId(""))
        // a key an older sdk returned, which the store never had
        assertNull(dnsKeyResId("dns_status_personal"))
        assertNull(dnsKeyResId("dns_group_block_ads"))
    }

    private fun preset(presetId: String, providerName: String) = DnsPresetUi(
        presetId = presetId,
        providerName = providerName,
        jurisdiction = "Example",
        privacyUrl = "https://privacy.example/",
        helpUrl = "https://help.example/",
        copyKey = "dns_preset_copy_nothing",
    )

    private val defaultPick = DnsPickUi(
        kind = DNS_PICK_KIND_DEFAULT,
        presetId = DNS_PRESET_ID_DEFAULT,
        labelKey = "dns_provider_default",
        copyKey = "dns_provider_default_copy",
    )
    private val alphaPick = DnsPickUi(
        kind = DNS_PICK_KIND_PRESET,
        presetId = "alpha",
        groupKey = "block_ads_and_trackers",
        preset = preset("alpha", "Alpha DNS"),
    )
    private val betaPick = DnsPickUi(
        kind = DNS_PICK_KIND_PRESET,
        presetId = "beta",
        groupKey = "block_ads_and_trackers",
        preset = preset("beta", "Beta DNS"),
    )
    private val gammaPick = DnsPickUi(
        kind = DNS_PICK_KIND_PRESET,
        presetId = "gamma",
        groupKey = "dns_group_no_filtering",
        preset = preset("gamma", "Gamma DNS"),
    )
    private val customPick = DnsPickUi(
        kind = DNS_PICK_KIND_CUSTOM,
        presetId = "",
        labelKey = "dns_provider_custom",
        copyKey = "dns_provider_custom_copy",
    )
    private val regionalPick = DnsPickUi(
        kind = DNS_PICK_KIND_REGIONAL,
        presetId = "regional-zz",
        labelKey = "dns_provider_regional",
        copyKey = "dns_recommendation_message",
        countryCode = "zz",
        unencrypted = true,
    )
    private val picks = listOf(defaultPick, alphaPick, betaPick, gammaPick, customPick, regionalPick)

    @Test
    fun theSectionsKeepTheSdkOrderAndShowEachGroupOnce() {
        val sections = dnsPickerSections(picks)
        assertEquals(
            listOf(
                DnsPickerSection(groupKey = "", picks = listOf(defaultPick)),
                DnsPickerSection(groupKey = "block_ads_and_trackers", picks = listOf(alphaPick, betaPick)),
                DnsPickerSection(groupKey = "dns_group_no_filtering", picks = listOf(gammaPick)),
                // Custom alone, so its editor opens right under it, then Regional
                DnsPickerSection(groupKey = "", picks = listOf(customPick)),
                DnsPickerSection(groupKey = "", picks = listOf(regionalPick)),
            ),
            sections,
        )
        assertEquals(picks, sections.flatMap { it.picks })
        assertTrue(dnsPickerSections(listOf()).isEmpty())
    }

    @Test
    fun thePickInForceIsTheSummarys() {
        val onDefault = DnsSelectionUi(kind = DNS_PICK_KIND_DEFAULT, presetId = DNS_PRESET_ID_DEFAULT)
        assertEquals(listOf(defaultPick), picks.filter { dnsPickSelected(it, onDefault) })

        val onBeta = DnsSelectionUi(kind = DNS_PICK_KIND_PRESET, presetId = "beta", providerName = "Beta DNS")
        assertEquals(listOf(betaPick), picks.filter { dnsPickSelected(it, onBeta) })

        val onCustom = DnsSelectionUi(kind = DNS_PICK_KIND_CUSTOM, presetId = "")
        assertEquals(listOf(customPick), picks.filter { dnsPickSelected(it, onCustom) })

        val onRegional = DnsSelectionUi(kind = DNS_PICK_KIND_REGIONAL, presetId = "regional-zz")
        assertEquals(listOf(regionalPick), picks.filter { dnsPickSelected(it, onRegional) })

        // a stored preset this release dropped applies as URnetwork's default
        val dropped = DnsSelectionUi(
            kind = DNS_PICK_KIND_DEFAULT,
            presetId = DNS_PRESET_ID_DEFAULT,
            dropped = true,
            droppedProviderName = "Delta DNS",
        )
        assertEquals(listOf(defaultPick), picks.filter { dnsPickSelected(it, dropped) })
    }

    @Test
    fun nothingIsInForceWhileTheSettingsAreUnknown() {
        for (pick in picks) {
            assertFalse(dnsPickSelected(pick, null))
        }
    }
}

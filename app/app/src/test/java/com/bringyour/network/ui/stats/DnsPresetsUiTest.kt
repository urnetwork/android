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
 * reordering them, the pick in force is the summary's, and the blocked count
 * shows only for the preset the device's status names. Synthetic presets
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
        "dns_preset_name_ad_blocking",
        "dns_preset_name_malware_blocking",
        "dns_preset_name_family",
        "dns_preset_name_child",
        "dns_preset_name_child_ads",
        "dns_preset_name_no_filtering",
        "dns_preset_name_personal",
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
        // a preset's summary row before it had a name, which the sdk no longer
        // returns: "DNS: <name>" (dns_status_provider) replaced it
        assertNull(dnsKeyResId("dns_summary_ad_blocking"))
        assertNull(dnsKeyResId("dns_summary_personal"))
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

        val onBeta = DnsSelectionUi(
            kind = DNS_PICK_KIND_PRESET,
            presetId = "beta",
            name = DnsPresetNameUi(key = "dns_preset_name_ad_blocking", provider = "Beta"),
            providerName = "Beta DNS",
        )
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
            droppedName = DnsPresetNameUi(key = "dns_preset_name_family", provider = "Delta"),
        )
        assertEquals(listOf(defaultPick), picks.filter { dnsPickSelected(it, dropped) })
    }

    @Test
    fun nothingIsInForceWhileTheSettingsAreUnknown() {
        for (pick in picks) {
            assertFalse(dnsPickSelected(pick, null))
        }
    }

    /**
     * The blocked count belongs to the preset the device's status names. The
     * count showed whatever the device last counted under the selected
     * provider's name, so right after a change it could show the previous
     * preset's count, and with no device a 0 that counted nothing.
     */
    @Test
    fun theBlockedCountShowsOnlyForTheStatusOfThePresetInForce() {
        // the status describes the preset in force
        assertEquals(12L, dnsShownBlockedCount(countsBlocked = true, blockedCount = 12L, statusPresetId = "alpha", selectionPresetId = "alpha"))
        // right after a change the status still names the previous preset:
        // none, never its count
        assertNull(dnsShownBlockedCount(countsBlocked = true, blockedCount = 12L, statusPresetId = "alpha", selectionPresetId = "beta"))
        // once the status names the new preset, its own count, from zero
        assertEquals(0L, dnsShownBlockedCount(countsBlocked = true, blockedCount = 0L, statusPresetId = "beta", selectionPresetId = "beta"))
        // no status, as without a device: none rather than a 0
        assertNull(dnsShownBlockedCount(countsBlocked = true, blockedCount = 0L, statusPresetId = null, selectionPresetId = "alpha"))
        // settings not known yet
        assertNull(dnsShownBlockedCount(countsBlocked = true, blockedCount = 5L, statusPresetId = "alpha", selectionPresetId = null))
        // a selection that counts nothing: a no-filtering preset, Custom
        assertNull(dnsShownBlockedCount(countsBlocked = false, blockedCount = 3L, statusPresetId = "gamma", selectionPresetId = "gamma"))
        assertNull(dnsShownBlockedCount(countsBlocked = false, blockedCount = 0L, statusPresetId = "", selectionPresetId = ""))
    }

    private fun source(name: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { java.io.File(it + "src/main/java/com/bringyour/network/ui/stats/$name") }
            .firstOrNull { it.isFile }
        assertNotNull(name, file)
        return file!!.readText()
    }

    /**
     * The view model gates the count by the preset the status itself names,
     * and its poll reads the whole status again, never the bare count, which
     * does not say whose it is.
     */
    @Test
    fun theViewModelCountsFromTheStatusOfThePresetInForce() {
        val viewModel = source("DnsSettingsViewModel.kt")
        assertTrue(viewModel, viewModel.contains("statusPresetId = resolverStatus?.presetId,"))
        assertTrue(viewModel, viewModel.contains("status = statusUi(sdkSettings, device?.dnsResolverStatus, nextSelection?.presetId)"))
        val poll = viewModel.substring(viewModel.indexOf("    fun refreshBlockedCount() {"))
            .substringBefore("\n    }\n")
        assertTrue(poll, poll.contains("statusUi(currentSdkSettings(), device?.dnsResolverStatus, selection?.presetId)"))
        assertFalse(viewModel, viewModel.contains(".dnsBlockedCount"))
    }
}

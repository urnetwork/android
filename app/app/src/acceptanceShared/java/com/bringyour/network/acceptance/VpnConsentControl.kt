package com.bringyour.network.acceptance

import java.util.Locale

internal const val ANDROID_FRAMEWORK_PACKAGE = "android"
internal const val STOCK_VPN_CONSENT_PACKAGE = "com.android.vpndialogs"
internal const val OPLUS_VPN_CONSENT_PACKAGE = "com.oplus.wirelesssettings"
internal const val VPN_CONSENT_BUTTON_RESOURCE = "android:id/button1"
internal const val AUTOFILL_SAVE_DECLINE_RESOURCE = "android:id/autofill_save_no"
internal const val AUTOFILL_SAVE_ACCEPT_RESOURCE = "android:id/autofill_save_yes"

internal data class VpnConsentControlIdentity(
    val packageName: String?,
    val resourceName: String?,
    val text: String?,
    val enabled: Boolean = true,
    val clickable: Boolean = true,
)

internal enum class VpnConsentStackAction {
    DISMISS_AUTOFILL_SAVE,
    ACCEPT_VPN,
    ABSENT,
    REFUSE_AUTOFILL_SAVE_ACCEPT,
    REFUSE_INCOMPLETE_CONTROL,
    REFUSE_FOREIGN_CONTROL,
    REFUSE_FOREIGN_ACTION,
    REFUSE_UNUSABLE_CONTROL,
    REFUSE_AMBIGUOUS_CONTROLS,
}

internal data class VpnConsentStackDecision(
    val action: VpnConsentStackAction,
    val selectedIndex: Int?,
)

/**
 * Selects only the two controls the acceptance harness is allowed to click.
 *
 * Android's autofill save sheet may obscure an OEM VPN dialog. Its exact
 * negative resource is therefore selected first. After that sheet disappears,
 * only the stock VPN package or the observed OPlus VPN package may own the
 * framework positive button. ANR and crash dialogs share that button resource,
 * so every incomplete, foreign, or ambiguous snapshot fails closed.
 */
internal fun classifyVpnConsentStack(
    controls: List<VpnConsentControlIdentity>,
): VpnConsentStackDecision {
    if (controls.isEmpty()) {
        return VpnConsentStackDecision(VpnConsentStackAction.ABSENT, null)
    }

    val declines = controls.indices.filter {
        controls[it].resourceName?.trim() == AUTOFILL_SAVE_DECLINE_RESOURCE
    }
    if (declines.size > 1) {
        return VpnConsentStackDecision(VpnConsentStackAction.REFUSE_AMBIGUOUS_CONTROLS, null)
    }
    if (declines.size == 1) {
        val index = declines.single()
        val control = controls[index]
        val packageName = control.packageName?.trim().orEmpty()
        if (packageName.isEmpty()) {
            return VpnConsentStackDecision(VpnConsentStackAction.REFUSE_INCOMPLETE_CONTROL, null)
        }
        if (packageName != ANDROID_FRAMEWORK_PACKAGE) {
            return VpnConsentStackDecision(VpnConsentStackAction.REFUSE_FOREIGN_CONTROL, null)
        }
        if (!control.enabled || !control.clickable) {
            return VpnConsentStackDecision(VpnConsentStackAction.REFUSE_UNUSABLE_CONTROL, null)
        }
        return VpnConsentStackDecision(VpnConsentStackAction.DISMISS_AUTOFILL_SAVE, index)
    }

    val accepts = controls.indices.filter {
        controls[it].resourceName?.trim() == AUTOFILL_SAVE_ACCEPT_RESOURCE
    }
    if (accepts.isNotEmpty()) {
        val exactAccepts = accepts.filter {
            controls[it].packageName?.trim() == ANDROID_FRAMEWORK_PACKAGE
        }
        val action = if (exactAccepts.size == accepts.size) {
            VpnConsentStackAction.REFUSE_AUTOFILL_SAVE_ACCEPT
        } else {
            VpnConsentStackAction.REFUSE_FOREIGN_CONTROL
        }
        return VpnConsentStackDecision(action, null)
    }

    val vpnButtons = controls.indices.filter {
        controls[it].resourceName?.trim() == VPN_CONSENT_BUTTON_RESOURCE
    }
    if (vpnButtons.size > 1) {
        return VpnConsentStackDecision(VpnConsentStackAction.REFUSE_AMBIGUOUS_CONTROLS, null)
    }
    if (vpnButtons.isEmpty()) {
        return VpnConsentStackDecision(classifyUnknownControls(controls), null)
    }

    val index = vpnButtons.single()
    val control = controls[index]
    val packageName = control.packageName?.trim().orEmpty()
    val action = control.text?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (packageName.isEmpty() || action.isEmpty()) {
        return VpnConsentStackDecision(VpnConsentStackAction.REFUSE_INCOMPLETE_CONTROL, null)
    }
    if (packageName != STOCK_VPN_CONSENT_PACKAGE && packageName != OPLUS_VPN_CONSENT_PACKAGE) {
        return VpnConsentStackDecision(VpnConsentStackAction.REFUSE_FOREIGN_CONTROL, null)
    }
    if (!control.enabled || !control.clickable) {
        return VpnConsentStackDecision(VpnConsentStackAction.REFUSE_UNUSABLE_CONTROL, null)
    }
    val verifiedAction = when (packageName) {
        STOCK_VPN_CONSENT_PACKAGE -> action == "allow" || action == "ok"
        OPLUS_VPN_CONSENT_PACKAGE -> action == "allow"
        else -> false
    }
    if (!verifiedAction) {
        return VpnConsentStackDecision(VpnConsentStackAction.REFUSE_FOREIGN_ACTION, null)
    }
    if (controls.size != 1) {
        return VpnConsentStackDecision(VpnConsentStackAction.REFUSE_AMBIGUOUS_CONTROLS, null)
    }
    return VpnConsentStackDecision(VpnConsentStackAction.ACCEPT_VPN, index)
}

private fun classifyUnknownControls(
    controls: List<VpnConsentControlIdentity>,
): VpnConsentStackAction {
    return if (controls.any {
            it.packageName?.trim().isNullOrEmpty() ||
                it.resourceName?.trim().isNullOrEmpty()
        }
    ) {
        VpnConsentStackAction.REFUSE_INCOMPLETE_CONTROL
    } else {
        VpnConsentStackAction.REFUSE_FOREIGN_CONTROL
    }
}

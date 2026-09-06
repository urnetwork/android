package com.bringyour.network.acceptance

import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

private const val VPN_CONSENT_WAIT_MILLIS = 8_000L
private const val AUTOFILL_DISMISS_WAIT_MILLIS = 2_000L

/**
 * Dismisses the exact Android autofill save sheet when it obscures VPN consent,
 * then clicks only a verified stock or OPlus VPN action. Every other matching
 * framework control is left untouched and reported as a harness failure.
 */
internal fun UiDevice.clickVerifiedVpnConsentIfPresent() {
    val deadline = SystemClock.elapsedRealtime() + VPN_CONSENT_WAIT_MILLIS
    while (true) {
        val candidates = vpnConsentCandidates()
        val decision = classifyVpnConsentStack(candidates.map { it.identity() })
        when (decision.action) {
            VpnConsentStackAction.ABSENT -> {
                if (SystemClock.elapsedRealtime() >= deadline) {
                    return
                }
                SystemClock.sleep(100)
            }

            VpnConsentStackAction.DISMISS_AUTOFILL_SAVE -> {
                candidates.getValue(decision.selectedIndex).click()
                check(
                    wait(
                        Until.gone(By.res(AUTOFILL_SAVE_DECLINE_RESOURCE)),
                        AUTOFILL_DISMISS_WAIT_MILLIS,
                    ) && wait(
                        Until.gone(By.res(AUTOFILL_SAVE_ACCEPT_RESOURCE)),
                        AUTOFILL_DISMISS_WAIT_MILLIS,
                    ),
                ) { "verified autofill save sheet did not close" }
            }

            VpnConsentStackAction.ACCEPT_VPN -> {
                candidates.getValue(decision.selectedIndex).click()
                return
            }

            else -> error("refusing VPN consent control stack: ${decision.action}")
        }
    }
}

private fun UiDevice.vpnConsentCandidates(): List<UiObject2> = buildList {
    addAll(findObjects(By.res(AUTOFILL_SAVE_DECLINE_RESOURCE)))
    addAll(findObjects(By.res(AUTOFILL_SAVE_ACCEPT_RESOURCE)))
    addAll(findObjects(By.res(VPN_CONSENT_BUTTON_RESOURCE)))
}

private fun UiObject2.identity() = VpnConsentControlIdentity(
    packageName = applicationPackage,
    resourceName = resourceName,
    text = text,
    enabled = isEnabled,
    clickable = isClickable,
)

private fun <T> List<T>.getValue(index: Int?): T {
    check(index != null && index in indices) { "classifier returned no verified control" }
    return this[index]
}

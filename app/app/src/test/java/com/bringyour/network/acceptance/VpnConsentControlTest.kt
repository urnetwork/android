package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnConsentControlTest {
    @Test
    fun dismissesOnlyTheExactAndroidAutofillNegativeActionFirst() {
        assertDecision(
            expected = VpnConsentStackAction.DISMISS_AUTOFILL_SAVE,
            expectedIndex = 0,
            control(
                packageName = ANDROID_FRAMEWORK_PACKAGE,
                resourceName = AUTOFILL_SAVE_DECLINE_RESOURCE,
                text = "Not now",
            ),
            control(
                packageName = ANDROID_FRAMEWORK_PACKAGE,
                resourceName = AUTOFILL_SAVE_ACCEPT_RESOURCE,
                text = "Continue",
            ),
            oplusVpnControl(),
        )
    }

    @Test
    fun acceptsExactStockAndOplusVpnActions() {
        assertDecision(VpnConsentStackAction.ACCEPT_VPN, 0, stockVpnControl(text = "Allow"))
        assertDecision(VpnConsentStackAction.ACCEPT_VPN, 0, stockVpnControl(text = " OK "))
        assertDecision(VpnConsentStackAction.ACCEPT_VPN, 0, oplusVpnControl(text = " Allow "))
    }

    @Test
    fun refusesAnrAndForeignFrameworkButtons() {
        assertDecision(
            VpnConsentStackAction.REFUSE_FOREIGN_CONTROL,
            null,
            control(
                packageName = ANDROID_FRAMEWORK_PACKAGE,
                resourceName = VPN_CONSENT_BUTTON_RESOURCE,
                text = "Wait",
            ),
        )
        assertDecision(
            VpnConsentStackAction.REFUSE_FOREIGN_CONTROL,
            null,
            control(
                packageName = "com.android.systemui",
                resourceName = VPN_CONSENT_BUTTON_RESOURCE,
                text = "Allow",
            ),
        )
        assertDecision(VpnConsentStackAction.REFUSE_FOREIGN_ACTION, null, oplusVpnControl(text = "OK"))
        assertDecision(VpnConsentStackAction.REFUSE_FOREIGN_ACTION, null, stockVpnControl(text = "Wait"))
    }

    @Test
    fun refusesPositiveSaveOrUnusableControlsWhenNoDeclineExists() {
        assertDecision(
            VpnConsentStackAction.REFUSE_AUTOFILL_SAVE_ACCEPT,
            null,
            control(
                packageName = ANDROID_FRAMEWORK_PACKAGE,
                resourceName = AUTOFILL_SAVE_ACCEPT_RESOURCE,
                text = "Continue",
            ),
        )
        assertDecision(
            VpnConsentStackAction.REFUSE_UNUSABLE_CONTROL,
            null,
            stockVpnControl(enabled = false),
        )
        assertDecision(
            VpnConsentStackAction.REFUSE_UNUSABLE_CONTROL,
            null,
            control(
                packageName = ANDROID_FRAMEWORK_PACKAGE,
                resourceName = AUTOFILL_SAVE_DECLINE_RESOURCE,
                text = "Not now",
                clickable = false,
            ),
        )
        assertDecision(
            VpnConsentStackAction.REFUSE_AUTOFILL_SAVE_ACCEPT,
            null,
            control(
                packageName = ANDROID_FRAMEWORK_PACKAGE,
                resourceName = AUTOFILL_SAVE_ACCEPT_RESOURCE,
                text = "Continue",
            ),
            oplusVpnControl(),
        )
        assertDecision(
            VpnConsentStackAction.REFUSE_FOREIGN_CONTROL,
            null,
            control(
                packageName = "com.example.foreign",
                resourceName = AUTOFILL_SAVE_DECLINE_RESOURCE,
                text = "Not now",
            ),
        )
    }

    @Test
    fun refusesIncompleteAndAmbiguousStacks() {
        assertDecision(
            VpnConsentStackAction.REFUSE_INCOMPLETE_CONTROL,
            null,
            stockVpnControl(packageName = null),
        )
        assertDecision(
            VpnConsentStackAction.REFUSE_INCOMPLETE_CONTROL,
            null,
            stockVpnControl(text = "   "),
        )
        assertDecision(
            VpnConsentStackAction.REFUSE_FOREIGN_CONTROL,
            null,
            control(
                packageName = STOCK_VPN_CONSENT_PACKAGE,
                resourceName = "android:id/button2",
                text = "Allow",
            ),
        )
        assertDecision(
            VpnConsentStackAction.REFUSE_INCOMPLETE_CONTROL,
            null,
            control(
                packageName = STOCK_VPN_CONSENT_PACKAGE,
                resourceName = null,
                text = "Allow",
            ),
        )
        assertDecision(
            VpnConsentStackAction.REFUSE_AMBIGUOUS_CONTROLS,
            null,
            stockVpnControl(),
            oplusVpnControl(),
        )
        assertDecision(
            VpnConsentStackAction.REFUSE_AMBIGUOUS_CONTROLS,
            null,
            control(
                packageName = ANDROID_FRAMEWORK_PACKAGE,
                resourceName = AUTOFILL_SAVE_DECLINE_RESOURCE,
                text = "Not now",
            ),
            control(
                packageName = ANDROID_FRAMEWORK_PACKAGE,
                resourceName = AUTOFILL_SAVE_DECLINE_RESOURCE,
                text = "No thanks",
            ),
        )
    }

    @Test
    fun reportsNoActionForAnEmptySnapshot() {
        assertDecision(VpnConsentStackAction.ABSENT, null)
    }

    private fun assertDecision(
        expected: VpnConsentStackAction,
        expectedIndex: Int?,
        vararg controls: VpnConsentControlIdentity,
    ) {
        assertEquals(
            VpnConsentStackDecision(expected, expectedIndex),
            classifyVpnConsentStack(controls.toList()),
        )
    }

    private fun stockVpnControl(
        packageName: String? = STOCK_VPN_CONSENT_PACKAGE,
        text: String? = "Allow",
        enabled: Boolean = true,
        clickable: Boolean = true,
    ) = control(
        packageName = packageName,
        resourceName = VPN_CONSENT_BUTTON_RESOURCE,
        text = text,
        enabled = enabled,
        clickable = clickable,
    )

    private fun oplusVpnControl(text: String? = "Allow") = control(
        packageName = OPLUS_VPN_CONSENT_PACKAGE,
        resourceName = VPN_CONSENT_BUTTON_RESOURCE,
        text = text,
    )

    private fun control(
        packageName: String?,
        resourceName: String?,
        text: String?,
        enabled: Boolean = true,
        clickable: Boolean = true,
    ) = VpnConsentControlIdentity(
        packageName = packageName,
        resourceName = resourceName,
        text = text,
        enabled = enabled,
        clickable = clickable,
    )
}

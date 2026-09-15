package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same seam handles auth actions and existing wait polls, not app settings. */
class AutofillSaveControlTest {
    @Test
    fun externalSaveWindowIsDeclinedBeforeAuthAction() {
        val ui = ExternalSaveWindow(saveControls())

        ui.performAuthAction()

        assertTrue(ui.nextTagVisible)
        assertEquals(listOf("decline:1", "auth"), ui.operations)
    }

    @Test
    fun waitPollDeclinesModalBeforeCheckingTag() {
        val ui = ExternalSaveWindow(saveControls())
        ui.nextTagVisible = true

        assertTrue(ui.pollNextTag())
        assertEquals(listOf("decline:1", "poll"), ui.operations)
    }

    @Test
    fun lateArrivalBetweenPollsIsDismissedWithinTheSameWait() {
        val ui = ExternalSaveWindow(emptyList())
        assertFalse(ui.pollNextTag())

        ui.controls = saveControls()
        ui.nextTagVisible = true

        assertTrue(ui.pollNextTag())
        assertEquals(listOf("poll", "decline:1", "poll"), ui.operations)
    }

    @Test
    fun absentSheetLeavesNormalAuthActionUntouched() {
        val ui = ExternalSaveWindow(emptyList())

        ui.performAuthAction()

        assertTrue(ui.pollNextTag())
        assertEquals(listOf("auth", "poll"), ui.operations)
    }

    @Test
    fun refusesForeignNegativeControl() {
        assertRefused(
            listOf(negativeControl().copy(packageName = "example.foreign")),
            VpnConsentStackAction.REFUSE_FOREIGN_CONTROL,
        )
    }

    @Test
    fun refusesPositiveOnlySaveControl() {
        assertRefused(
            listOf(positiveControl()),
            VpnConsentStackAction.REFUSE_AUTOFILL_SAVE_ACCEPT,
        )
    }

    @Test
    fun refusesAmbiguousNegativeControls() {
        assertRefused(
            listOf(negativeControl(), negativeControl()),
            VpnConsentStackAction.REFUSE_AMBIGUOUS_CONTROLS,
        )
    }

    @Test
    fun refusesUnusableNegativeControl() {
        assertRefused(
            listOf(negativeControl().copy(enabled = false)),
            VpnConsentStackAction.REFUSE_UNUSABLE_CONTROL,
        )
    }

    @Test
    fun refusesIncompleteNegativeControl() {
        assertRefused(
            listOf(negativeControl().copy(packageName = null)),
            VpnConsentStackAction.REFUSE_INCOMPLETE_CONTROL,
        )
    }

    @Test
    fun authSeamNeverAcceptsEvenVerifiedVpnConsent() {
        assertRefused(
            listOf(
                VpnConsentControlIdentity(
                    packageName = STOCK_VPN_CONSENT_PACKAGE,
                    resourceName = VPN_CONSENT_BUTTON_RESOURCE,
                    text = "Allow",
                ),
            ),
            VpnConsentStackAction.ACCEPT_VPN,
        )
    }

    /** Rejected snapshots must not reach either a UI action or a dismissal. */
    private fun assertRefused(
        controls: List<VpnConsentControlIdentity>,
        expected: VpnConsentStackAction,
    ) {
        var dismissed = false
        var acted = false
        val error = assertThrows(IllegalStateException::class.java) {
            performAutofillReadyAction(
                controls = controls,
                dismissVerifiedNegative = { dismissed = true },
                action = { acted = true },
            )
        }
        assertEquals("refusing autofill save control stack: $expected", error.message)
        assertFalse(dismissed)
        assertFalse(acted)
    }

    /** Synthetic framework controls expose no credentials or fixture identity. */
    private fun negativeControl() = VpnConsentControlIdentity(
        packageName = ANDROID_FRAMEWORK_PACKAGE,
        resourceName = AUTOFILL_SAVE_DECLINE_RESOURCE,
        text = "Cancel",
    )

    private fun positiveControl() = VpnConsentControlIdentity(
        packageName = ANDROID_FRAMEWORK_PACKAGE,
        resourceName = AUTOFILL_SAVE_ACCEPT_RESOURCE,
        text = "Save",
    )

    private fun saveControls() = listOf(positiveControl(), negativeControl())

    /** A separate modal window can obstruct app progress despite app semantics. */
    private class ExternalSaveWindow(var controls: List<VpnConsentControlIdentity>) {
        var nextTagVisible = false
        val operations = mutableListOf<String>()

        fun performAuthAction() = performAutofillReadyAction(
            controls = controls,
            dismissVerifiedNegative = ::decline,
        ) {
            check(controls.isEmpty()) { "external save window obstructed auth action" }
            operations += "auth"
            nextTagVisible = true
        }

        fun pollNextTag(): Boolean = performAutofillReadyAction(
            controls = controls,
            dismissVerifiedNegative = ::decline,
        ) {
            operations += "poll"
            controls.isEmpty() && nextTagVisible
        }

        /** The fake is strict: even an attempted positive click is a failure. */
        private fun decline(index: Int) {
            check(controls[index].packageName == ANDROID_FRAMEWORK_PACKAGE)
            check(controls[index].resourceName == AUTOFILL_SAVE_DECLINE_RESOURCE)
            operations += "decline:$index"
            controls = emptyList()
        }
    }
}

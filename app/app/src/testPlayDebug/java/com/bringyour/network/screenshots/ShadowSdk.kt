package com.bringyour.network.screenshots

import com.bringyour.sdk.PriceEquivalent
import com.bringyour.sdk.Sdk
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * The few sdk answers the screens read without a null check, while the sdk
 * is inert on the JVM (ScreenshotTest): there each native call returns null,
 * so these hand back an empty answer instead, whose fields all read as zero.
 */
@Implements(value = Sdk::class, isInAndroidSdk = false)
class ShadowSdk {
    companion object {
        /** The plan price rule, which the account screen runs on every draw (PlanPresentations.sdkEquivalent). */
        @JvmStatic
        @Implementation
        fun computePriceEquivalent(yearlyAmount: Double, monthlyAmount: Double, minorUnitDigits: Long): PriceEquivalent =
            PriceEquivalent()
    }
}

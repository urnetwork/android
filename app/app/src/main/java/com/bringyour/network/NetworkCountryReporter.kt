package com.bringyour.network

/**
 * The country of the mobile network the device is on, reported to the sdk for
 * the extender spoof list (open bug P052).
 *
 * The sdk fronts its extender dials with a name from the spoof list of the
 * country the device is in, which the extender hint from the api tells it. On
 * a whitelist-only mobile network (Russian carriers) only domestic addresses
 * and names are routable, so that hint cannot be fetched. The sdk then falls
 * back to the network country reported here (Sdk.setNetworkCountryCode):
 * TelephonyManager.networkCountryIso while the default network is cellular,
 * and nothing on Wi-Fi or any other network, whose country telephony does not
 * describe. The value never leaves the device.
 *
 * The selection is pure and the reporter takes its two Android touches as
 * functions, so both are unit testable (NetworkCountryReporterTest).
 */

/**
 * The country to report for the default network: the lower case ISO 3166-1
 * alpha-2 network country while it is cellular, else "". A country that is not
 * two letters -- telephony answers "" while the radio is not registered -- is
 * "" as well.
 */
internal fun networkCountryCodeFor(isCellular: Boolean, networkCountryIso: String?): String {
    if (!isCellular) {
        return ""
    }
    val countryCode = networkCountryIso?.trim()?.lowercase() ?: return ""
    if (countryCode.length != 2 || !countryCode.all { it in 'a'..'z' }) {
        return ""
    }
    return countryCode
}

/**
 * Reports the network country of each default network to the sdk, once per
 * change. [readNetworkCountryIso] reads TelephonyManager.networkCountryIso and
 * is called only on cellular; [report] is Sdk.setNetworkCountryCode. Called on
 * the main looper, which is where the default network callback is delivered.
 */
internal class NetworkCountryReporter(
    private val readNetworkCountryIso: () -> String?,
    private val report: (String) -> Unit,
) {
    private var reportedCountryCode: String? = null

    /**
     * The default network is now cellular or not; false as well when there is
     * no default network at all.
     */
    fun defaultNetworkChanged(isCellular: Boolean) {
        val countryCode = networkCountryCodeFor(
            isCellular,
            if (isCellular) readNetworkCountryIso() else null,
        )
        if (countryCode == reportedCountryCode) {
            return
        }
        reportedCountryCode = countryCode
        report(countryCode)
    }
}

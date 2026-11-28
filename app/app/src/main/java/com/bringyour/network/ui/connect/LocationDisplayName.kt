package com.bringyour.network.ui.connect

import com.bringyour.network.ui.connection_region.platformCountryName
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.Sdk

/**
 * The name the location list (and the connect screen's location button) shows
 * for a location. A country reads in the app's language: the platform's name
 * for its code, the one lookup the connection region screens use, so both name
 * a country alike. The server's English name stays the fallback, for a code the
 * platform does not name and for every region, city, group and device, which
 * the platform cannot name.
 */
fun locationDisplayName(location: ConnectLocation): String =
    locationDisplayName(
        isCountry = location.locationType == Sdk.LocationTypeCountry,
        countryCode = location.countryCode,
        serverName = location.name,
        countryName = ::platformCountryName,
    )

/** Pure, for tests: [countryName] is "" for a code it does not name. */
fun locationDisplayName(
    isCountry: Boolean,
    countryCode: String?,
    serverName: String,
    countryName: (String) -> String,
): String {
    if (!isCountry || countryCode == null || countryCode.length != 2) {
        return serverName
    }
    return countryName(countryCode).ifEmpty { serverName }
}

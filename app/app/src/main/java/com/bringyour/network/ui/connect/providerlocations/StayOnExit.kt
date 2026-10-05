package com.bringyour.network.ui.connect.providerlocations

import com.bringyour.network.ui.connect.shortClientIdLabel

/**
 * "Stay on this exit": reconnect to one provider of the current connection, by
 * its client id, so new connections keep that provider's IP address. The sdk
 * dials a client id location directly (connect's fixed destination: nothing is
 * discovered and nothing replaces it), and the location is not marked as a
 * network peer, so the provider keeps carrying the traffic as the public exit
 * it already is.
 *
 * The rows are the user's own current exits, so this pins one of them. It is
 * not a way to browse or pick from all providers.
 */
data class StayOnExitTarget(
    val clientId: String,
    // the location name the connect drawer shows, "018f…5c6d · Berlin, Germany"
    val name: String,
    val city: String,
    val region: String,
    val country: String,
    val countryCode: String,
)

/** What a provider row shows for "Stay on this exit". */
enum class StayOnExitState {
    NONE,
    // the selected row offers the action
    OFFER,
    // the connection already stays on this provider
    STAYING,
}

/**
 * The selected row offers to stay on its provider; the provider the connection
 * already stays on says so instead, selected or not. [stayingClientId] is the
 * client id of the current location when it is a client id location (a stayed
 * exit or a network peer), else null.
 */
fun stayOnExitState(
    row: ProviderLocationRow,
    selectedClientId: String?,
    stayingClientId: String?,
): StayOnExitState {
    if (row.clientId.isBlank()) {
        return StayOnExitState.NONE
    }
    if (row.clientId.equals(stayingClientId, ignoreCase = true)) {
        return StayOnExitState.STAYING
    }
    if (row.clientId.equals(selectedClientId, ignoreCase = true)) {
        return StayOnExitState.OFFER
    }
    return StayOnExitState.NONE
}

/**
 * "018f…5c6d · Berlin, Germany": the short client id, which is what makes the
 * location one provider, then the city (or the region) and the country. The id
 * comes first so a narrow drawer trims the place rather than the id. Just the
 * short id when the server does not know where the provider is.
 */
fun stayOnExitName(row: ProviderLocationRow): String {
    val id = row.clientId.trim()
    val shortId = shortClientIdLabel(id, id)
    val place = listOf(row.city.ifEmpty { row.region }, row.country)
        .filter { it.isNotEmpty() }
        .joinToString(", ")
    return if (place.isEmpty()) shortId else "$shortId · $place"
}

/** What "Stay on this exit" connects to for [row], or null without a client id. */
fun stayOnExitTarget(row: ProviderLocationRow): StayOnExitTarget? {
    if (row.clientId.isBlank()) {
        return null
    }
    return StayOnExitTarget(
        clientId = row.clientId.trim(),
        name = stayOnExitName(row),
        city = row.city,
        region = row.region,
        country = row.country,
        countryCode = row.countryCode,
    )
}

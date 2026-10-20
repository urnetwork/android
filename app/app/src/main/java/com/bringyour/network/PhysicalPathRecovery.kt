package com.bringyour.network

import java.net.InetAddress

/** The return from having no physical path at all re-dials the transports. */
internal const val RECOVERY_PHYSICAL_NETWORK_RETURNED = "physical-network-returned"

/** The physical default network changed identity, including loss -> return. */
internal const val RECOVERY_DEFAULT_NETWORK_CHANGED = "default-network-changed"

/** The physical default kept its identity but lost what open sockets use. */
internal const val RECOVERY_DEFAULT_LINK_CHANGED = "default-link-changed"

/**
 * What an open socket on a physical network can be bound to: the interface and
 * its addresses. Link-local addresses are left out; no platform dial uses them,
 * and IPv6 adds and removes them on every attach.
 */
internal data class PhysicalLinkSnapshot(
    val interfaceName: String,
    val addresses: Set<String>,
)

internal fun physicalLinkSnapshot(
    interfaceName: String?,
    addresses: Collection<InetAddress>,
): PhysicalLinkSnapshot = PhysicalLinkSnapshot(
    interfaceName = interfaceName.orEmpty(),
    addresses = addresses
        .filterNot { it.isLinkLocalAddress }
        .mapNotNullTo(linkedSetOf()) { it.hostAddress },
)

/**
 * True when sockets opened before [next] may be stranded: the interface was
 * replaced, or an address they could be bound to disappeared. New addresses
 * (IPv6 arriving after IPv4, temporary addresses), DNS servers, domains, routes
 * and MTU do not strand an open socket, so they must not reconnect working
 * transports.
 */
internal fun physicalLinkChangeStrandsSockets(
    previous: PhysicalLinkSnapshot,
    next: PhysicalLinkSnapshot,
): Boolean = previous.interfaceName != next.interfaceName ||
    !next.addresses.containsAll(previous.addresses)

/** One physical membership event: whether any path remains, and any re-dial. */
internal data class PhysicalPathUpdate(
    val available: Boolean,
    val topologyChanged: Boolean,
    val recoveryReason: String?,
)

/**
 * Decides when a physical path change must re-dial the platform transports.
 *
 * Those sockets are unbound, so they leave on the physical default network
 * (the system default; this app is outside its own capture tunnel) and stay
 * on the path they were opened on. MainApplication feeds two callbacks in:
 * the membership of every physical internet network, and the physical default
 * itself. Only three events move or strand sockets:
 * - the physical default changes identity, including loss -> return;
 * - the physical default keeps its identity but loses its interface or an
 *   address (physicalLinkChangeStrandsSockets);
 * - a path returns after there was none, including the first path after a
 *   registration that found none (startedWithoutNetwork): failed dials have
 *   been backing off, and only a re-dial signal resets that backoff.
 * A second network joining, or a non-default one leaving (cell lingering out
 * after a move to wifi, a background cell bounce), leaves every socket where
 * it was. Re-dialing for those killed working connections, and a join arrives
 * before the platform validates the new network, so it re-dialed over the old
 * path just before the real default change.
 *
 * Pure and main-looper confined, so every switch sequence is unit testable
 * (PhysicalPathRecoveryTest). Recovery reasons go to TunnelRecoveryPolicy,
 * which coalesces and paces them.
 */
internal class PhysicalPathRecovery<T> {
    private val members = AvailableNetworkTracker<T>()
    private val physicalDefault = DefaultNetworkTracker<T>()

    val memberCount: Int
        get() = members.size

    /**
     * The registration found no physical network: the first one to arrive is
     * a return from offline, not a baseline. Call before the first callback.
     */
    fun startedWithoutNetwork() {
        members.startedWithoutNetwork()
    }

    fun onMemberAvailable(network: T): PhysicalPathUpdate {
        val change = members.onAvailable(network)
        return PhysicalPathUpdate(
            available = change.available,
            topologyChanged = change.topologyChanged,
            recoveryReason = if (change.recoveryRequired) RECOVERY_PHYSICAL_NETWORK_RETURNED else null,
        )
    }

    fun onMemberLost(network: T): PhysicalPathUpdate {
        val change = members.onLost(network)
        // Losing a non-default network strands nothing. Losing the default is
        // a physical default change, and losing the last path defers recovery
        // until one returns.
        return PhysicalPathUpdate(
            available = change.available,
            topologyChanged = change.topologyChanged,
            recoveryReason = null,
        )
    }

    fun onMemberCapabilitiesChanged(
        network: T,
        next: PhysicalNetworkCapabilitiesSnapshot,
    ): PhysicalNetworkAttributeChange = members.onCapabilitiesChanged(network, next)

    fun onMemberBlockedStatusChanged(network: T, blocked: Boolean): PhysicalNetworkAttributeChange =
        members.onBlockedStatusChanged(network, blocked)

    fun onDefaultAvailable(network: T): String? =
        if (physicalDefault.onAvailable(network)) RECOVERY_DEFAULT_NETWORK_CHANGED else null

    fun onDefaultLost(network: T) {
        physicalDefault.onLost(network)
    }

    fun onDefaultLinkPropertiesChanged(network: T, next: PhysicalLinkSnapshot): String? =
        if (physicalDefault.onLinkPropertiesChanged(network, next)) RECOVERY_DEFAULT_LINK_CHANGED else null

    fun isPhysicalDefault(network: T): Boolean = physicalDefault.isCurrent(network)
}

    package com.bringyour.network

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.IpPrefix
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.system.OsConstants.AF_INET
import android.system.OsConstants.AF_INET6
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bringyour.network.utils.sdkStringListToList
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.IoLoop
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import com.bringyour.sdk.WindowStatus
import java.net.InetAddress
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

    // see https://developer.android.com/develop/connectivity/vpn
    @SuppressLint("VpnServicePolicy")
    class MainService : VpnService() {
    companion object {
        const val NOTIFICATION_ID = 101
        const val NOTIFICATION_CHANNEL_ID = "URnetwork"
        /**
         * IPv4 used to establish the tunnel when the SDK has not handed back a
         * tunnel address, so a tunnel is always built (fail-closed). TEST-NET-1
         * (192.0.2.0/24) is reserved documentation space. With capture routes it
         * is a blocking blackhole; with no routes (escape) it routes nothing.
         */
        const val ESCAPE_FALLBACK_ADDRESS = "192.0.2.1"

        fun defaultExcludedPackageNames(): List<String> {
            // TODO grass, spectrum, session, discord
            return listOf(
                "com.discord",
                // session
                "network.loki.messenger"
            ) + solanaMobilePackageNames() + spectrumPackageNames()
        }

        fun solanaMobilePackageNames(): List<String> {
            return listOf(
                "com.solanamobile.dappstore",
                "com.solanamobile.wallet",
                "io.getgrass.www"
            )
        }

        fun spectrumPackageNames(): List<String> {
            return listOf(
                // spectrum
                "com.TWCableTV",
                "com.spectrum.access",
                "com.brighthouse.mybhn",
                "com.twcable.twcnews",
                "com.spectrum.tv.android.tvsa",
                "com.twcsports.android",
                "com.charter.university"
            )
        }
    }



    // clientIpv4 (the TUN interface address) is sourced at tunnel-build time from the
    // SDK device's tunnelLocalAddress() (reserved from connect's pool); see updatePfd.
    val clientIpv4PrefixLength = 32
    // static fallback for when the sdk device is unavailable; the builder dns
    // normally comes from the device (see `tunnelDnsServers`). matches the SDK
    // Fallback tunnel DNS identity for the unlikely interval where no SDK device
    // is available. This is not the upstream resolver: UpgradeMux claims plain
    // :53 and resolves over DoH. Normally tunnelDnsServers() returns the device's
    // configured DnsUpgradeMaskAddress. The fallback belongs to URnetwork's
    // 65.49.70.64/27 public subnet and stands in for the UpgradeMux; it is not
    // an upstream DNS resolver.
    val dnsIpv4s = listOf("65.49.70.65")

    //    private var pfd: ParcelFileDescriptor? = null
    private var packetFlow: PacketFlow? = null
    private var alwaysOnGuardPfd: ParcelFileDescriptor? = null
    private var boundDevice: DeviceLocal? = null
    private var foregroundStarted: Boolean = false

    private var deviceOfflineSub: Sub? = null
    private var windowStatusChangeSub: Sub? = null
    /** Rebuilds the TUN when the kill switch (routeLocal) toggles. */
    private var routeLocalSub: Sub? = null
    /** Rebuilds the TUN when a connect request starts or stops. */
    private var connectChangeSub: Sub? = null
    private var blockActionOverridesSub: Sub? = null
    private var dnsResolverSettingsSub: Sub? = null
    private var connected: Boolean = false

    // Committed only after Builder.establish() succeeds. A failed seamless
    // handover must remain visibly unapplied so the next listener/recovery
    // callback retries it.
    private var appliedTunnelConfiguration: VpnPacketFlowConfiguration? = null

    // the pinned app set currently installed as the flow-owner lookup, used
    // to skip reinstalling an identical one (see applyPinnedAppLookup).
    // guarded by applyPinnedAppLookup's own synchronization
    private var appliedPinnedAppIds: Set<String>? = null

    @Volatile
    private var closeMonitorStarted: Boolean = false

    private val closeMonitorGeneration = java.util.concurrent.atomic.AtomicLong(0)

        private var offline: Boolean = false


    override fun onStartCommand(intent : Intent?, flags: Int, startId : Int): Int {
        val app = application as MainApplication

        val stop = intent?.getBooleanExtra("stop", false) ?: false
        val start = intent?.getBooleanExtra("start", true) ?: false
        val redelivered =
            flags and android.app.Service.START_FLAG_REDELIVERY != 0 ||
                flags and android.app.Service.START_FLAG_RETRY != 0
        val source = intent?.getStringExtra("source")
        val frameworkAlwaysOn =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && runCatching { isAlwaysOn }.getOrDefault(false)
        val systemStart = vpnServiceSystemStart(source, frameworkAlwaysOn)
        val commandVersion = intent?.getIntExtra("command_version", 0) ?: 0
        // Read restored SDK state once. A listener can change the device state
        // while Android is delivering this command; the admission decision and
        // its diagnostic must describe the same snapshot.
        val deviceRequiresVpn = app.restoredVpnServiceRequired()
        val decision = decideVpnServiceStart(
            VpnServiceStartFacts(
                stopRequested = stop,
                startRequested = start,
                appMarkedActive = app.serviceActive,
                deviceRequiresVpn = deviceRequiresVpn,
                redelivered = redelivered,
                systemStart = systemStart,
                commandCompatible = vpnServiceCommandCompatible(source, commandVersion),
            ),
        )

        if (decision == VpnServiceStartDecision.STOP) {
            Log.i(
                TAG,
                    "[service]reject start stop=$stop start=$start redelivered=$redelivered " +
                    "systemStart=$systemStart appActive=${app.serviceActive} " +
                    "deviceRequiresVpn=$deviceRequiresVpn",
            )
            stop()
            // A stop/stale intent must not itself be retained for another
            // redelivery. START_REDELIVER_INTENT is only for a service we
            // actually accepted and intend Android to restore.
            return START_NOT_STICKY
        }

        val foreground = if (intent?.hasExtra("foreground") == true) {
            intent.getBooleanExtra("foreground", false)
        } else {
            app.restoredVpnForegroundDesired()
        }

        if (app.service?.get() != this) {
            app.service?.get().let { currentService ->
                if (currentService != this) {
                    currentService?.stop()
                }
            }
            app.vpnServiceDidStart(this, foreground, systemStart)

//            val offline = intent.getBooleanExtra("offline", false)

            if (foreground || app.isSystemAlwaysOnVpnActive()) {
                startForegroundNotification("On")
                // update the notification `NOTIFICATION_ID` and it will update the displayed notification
                // see https://stackoverflow.com/questions/5528288/how-do-i-update-the-notification-text-for-a-foreground-service-in-android
            } else {
                stopForegroundNotification()
            }

            attachToCurrentDevice()
            if (app.device == null && systemStart) {
                establishAlwaysOnGuard()
            }
            startCloseMonitor()
        } else {
            // Duplicate delivery (including a redelivery racing the app's own
            // reconcile): adopt the exact instance and update notification
            // policy in place without rebuilding the TUN.
            app.vpnServiceDidStart(this, foreground, systemStart)
            setForegroundEnabled(foreground || app.isSystemAlwaysOnVpnActive())
            onDeviceAvailable()
            if (app.device == null && systemStart) {
                establishAlwaysOnGuard()
            }
        }

        // see https://developer.android.com/reference/android/app/Service#START_REDELIVER_INTENT
        return START_REDELIVER_INTENT
    }

    private fun attachToCurrentDevice() {
        val app = application as MainApplication
        val currentDevice = app.device ?: return
        if (boundDevice === currentDevice) {
            reconcilePfd()
            return
        }

        detachDeviceBindings(closePacketFlow = false)
        boundDevice = currentDevice
        connected = currentDevice.windowStatus?.let {
            0 < it.providerStateAdded
        } ?: false

        fun currentOffline(): Boolean =
            currentDevice.offline && !currentDevice.vpnInterfaceWhileOffline
        offline = currentOffline()

        deviceOfflineSub = currentDevice.addOfflineChangeListener { _, _ ->
            Handler(mainLooper).post {
                if (boundDevice !== currentDevice) return@post
                val nextOffline = currentOffline()
                if (offline != nextOffline) {
                    offline = nextOffline
                    reconcilePfd()
                }
            }
        }

        fun updateWindowStatus(windowStatus: WindowStatus) {
            connected = 0 < windowStatus.providerStateAdded
            reconcilePfd()
        }
        windowStatusChangeSub = currentDevice.addWindowStatusChangeListener { windowStatus ->
            Handler(mainLooper).post {
                if (boundDevice === currentDevice) updateWindowStatus(windowStatus)
            }
        }
        currentDevice.windowStatus?.let(::updateWindowStatus)

        blockActionOverridesSub = currentDevice.addBlockActionOverridesChangeListener {
            Handler(mainLooper).post {
                if (boundDevice !== currentDevice) return@post
                applyPinnedAppLookup()
                reconcilePfd()
            }
        }
        applyPinnedAppLookup()
        registerPackageChangeReceiver()

        dnsResolverSettingsSub = currentDevice.addDnsResolverSettingsChangeListener {
            Handler(mainLooper).post {
                if (boundDevice === currentDevice) reconcilePfd()
            }
        }
        // killSwitch (routeLocal) and connectRequested (connectEnabled) are
        // material inputs to vpnPacketFlowMode: toggling either must rebuild
        // the TUN so the routing mode changes, not just the service start.
        routeLocalSub = currentDevice.addRouteLocalChangeListener {
            Handler(mainLooper).post {
                if (boundDevice === currentDevice) reconcilePfd()
            }
        }
        connectChangeSub = currentDevice.addConnectChangeListener {
            Handler(mainLooper).post {
                if (boundDevice === currentDevice) reconcilePfd()
            }
        }
        reconcilePfd()
    }

    fun onDeviceAvailable() {
        if (android.os.Looper.myLooper() != mainLooper) {
            Handler(mainLooper).post { onDeviceAvailable() }
            return
        }
        val app = application as MainApplication
        if (app.service?.get() !== this) return
        attachToCurrentDevice()
    }

    private fun desiredTunnelConfiguration(): VpnPacketFlowConfiguration {
        val app = application as MainApplication
        val (includedAppIds, excludedAppIds) = tunnelAppSplit()
        val clientIpv4 = vpnTunnelIpv4Address(app.device?.tunnelLocalAddress())
        // The tunnel binds this address (falling back to ESCAPE_FALLBACK_ADDRESS)
        // in updatePfd, so the DNS self-collision filter must use the same bound
        // address: a DNS entry equal to the bound address is locally terminated.
        val boundIpv4 = clientIpv4 ?: ESCAPE_FALLBACK_ADDRESS
        val deviceDnsIpv4s = tunnelDnsServers()
        return VpnPacketFlowConfiguration(
            offline = offline,
            connected = connected,
            killSwitch = app.device?.routeLocal == false,
            connectRequested = app.device?.connectEnabled == true,
            includedAppIds = includedAppIds.toSet(),
            excludedAppIds = excludedAppIds.toSet(),
            dnsIpv4s = vpnDnsServersForClient(boundIpv4, deviceDnsIpv4s, dnsIpv4s),
            clientIpv4 = clientIpv4,
        )
    }

    private fun reconcilePfd() {
        val app = application as MainApplication
        if (vpnAlwaysOnGuardRequired(
                systemAlwaysOn = app.isSystemAlwaysOnVpnActive(),
                deviceAvailable = boundDevice != null,
                providerConnected = connected,
            )
        ) {
            establishAlwaysOnGuard()
            return
        }
        if (boundDevice == null) {
            Log.i(TAG, "[service]device unavailable outside Always-on; stopping")
            stop()
            return
        }
        val desired = desiredTunnelConfiguration()
        if (vpnPacketFlowNeedsRebuild(
                packetFlow?.isActive() ?: false,
                appliedTunnelConfiguration,
                desired,
            )
        ) {
            updatePfd(desired)
        }
    }

    private fun updatePfd(configuration: VpnPacketFlowConfiguration) {
        val app = application as MainApplication

        val builder = Builder()
        builder.setSession("URnetwork")
        // Matches connect's provider packetizer and keeps one encrypted tunnel
        // packet within H3's single-DATAGRAM payload ceiling.
        builder.setMtu(Sdk.getDefaultTunnelMtu())
        builder.setBlocking(false)
        builder.setUnderlyingNetworks(null)
        val tunnelIncludedAppIds = configuration.includedAppIds
        val tunnelExcludedAppIds = configuration.excludedAppIds
        val tunnelDnsIpv4s = configuration.dnsIpv4s

        // Routing mode is a pure decision so the fail-closed/escape path is
        // unit-testable without an Android runtime. Named args avoid a silent
        // positional swap of the two booleans. ESCAPE (offline, or up purely
        // for provide with no live exit) keeps the tunnel established but adds
        // no routes and no DNS, so Android points nothing at it: every app
        // (including the tunnel owner) keeps its native network and DNS, and
        // there is no dependence on an installed allow-listed package.
        val mode = vpnPacketFlowMode(
            offline = configuration.offline,
            connected = configuration.connected,
            killSwitch = configuration.killSwitch,
            connectRequested = configuration.connectRequested,
            includedAppIds = tunnelIncludedAppIds,
        )
        when (mode) {
            VpnPacketFlowMode.ESCAPE -> {
                // no app rules; the escape build below adds routes/DNS only for
                // non-escape modes, so nothing is captured
            }
            VpnPacketFlowMode.PER_APP_ALLOWLIST -> {
                    // per-app inclusions take precedence: allowlist mode, only
                    // the included apps use the tunnel. tunnelAppSplit
                    // sanitizes the VPN owner before this mode decision, so a
                    // stale self-only rule cannot create an empty Android UID
                    // set.
                    for (includedPackageName in tunnelIncludedAppIds) {
                        try {
                            builder.addAllowedApplication(includedPackageName)
                        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                        }
                    }
                }
                VpnPacketFlowMode.DENYLIST -> {
                    // denylist mode: everything not the tunnel owner uses the
                    // tunnel, except the default excluded apps and per-app
                    // exclusions
                    builder.addDisallowedApplication(packageName)
                    for (excludedPackageName in defaultExcludedPackageNames() + tunnelExcludedAppIds) {
                        try {
                            builder.addDisallowedApplication(excludedPackageName)
                        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                        }
                    }
                }
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        when (configuration.ipv6Policy) {
            VpnIpv6Policy.BLOCK_UNSUPPORTED -> {
                // For CAPTURE modes (allowlist/denylist): omit IPv6 entirely.
                // Remote providers are IPv4-only, so the tunnel cannot forward
                // IPv6; blocking the unconfigured family is correct there.
                // The ESCAPE path below separately calls allowFamily(AF_INET6)
                // so the phone's other apps keep their own IPv6 connectivity.
            }
        }

        val clientIpv4 = configuration.clientIpv4
        val isEscape = mode == VpnPacketFlowMode.ESCAPE
        // Always establish a tunnel, even when the SDK has not handed back a
        // tunnel address. Without an address builder.establish() throws and
        // the catch retains the previous interface, or with no prior interface
        // leaves no TUN at all. For a kill-switch or connected state that
        // fails OPEN (traffic to the ISP in the clear). Use a fixed
        // documentation address (same class the always-on guard uses) as a
        // fail-closed fallback: with capture routes it is a blocking blackhole;
        // with no routes (escape) it routes nothing.
        val tunnelAddress = clientIpv4 ?: ESCAPE_FALLBACK_ADDRESS
        val ipv6Report = if (isEscape) "on" else "off"
        if (isEscape) {
            // Escaping: allow BOTH families. With no app rules every app is in
            // scope, so without allowFamily(AF_INET6) the unconfigured IPv6
            // family would be BLOCKED for every app (the exact bug this fix
            // removes, on the v6 half). Address only, no routes, no DNS:
            // Android routes nothing into it and every app keeps its native
            // network and DNS.
            builder.allowFamily(AF_INET)
            builder.allowFamily(AF_INET6)
        } else {
            builder.allowFamily(AF_INET)
        }
        builder.addAddress(
            tunnelAddress,
            clientIpv4PrefixLength
        )
            if (!isEscape) {
                // DNS from the SDK device (see `tunnelDnsServers`). It must
                // be a distinct address routed through the TUN: Android
                // locally terminates packets addressed to clientIpv4 before
                // PacketFlow can hand them to UpgradeMux.
                for (dnsIpv4 in tunnelDnsIpv4s) {
                    builder.addDnsServer(dnsIpv4)
                }
            if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                builder.addRoute("0.0.0.0", 0)
                builder.excludeRoute(IpPrefix(InetAddress.getByName("10.0.0.0"), 8))
                builder.excludeRoute(IpPrefix(InetAddress.getByName("172.16.0.0"), 12))
                builder.excludeRoute(IpPrefix(InetAddress.getByName("192.168.0.0"), 16))
            } else {
                /*
                python script:

                n = [ipaddress.ip_network('0.0.0.0/0')]
                for m in [ipaddress.ip_network('10.0.0.0/8'), ipaddress.ip_network('172.16.0.0/12'), ipaddress.ip_network('192.168.0.0/16')]:
                    n = [
                        b
                        for a in n
                        for b in (list(a.address_exclude(m)) if a.overlaps(m) else [a])
                    ]
                for a in n:
                    print('builder.addRoute("{}", {})'.format(a.network_address, a.prefixlen))
                */
                builder.addRoute("224.0.0.0", 3)
                builder.addRoute("208.0.0.0", 4)
                builder.addRoute("200.0.0.0", 5)
                builder.addRoute("196.0.0.0", 6)
                builder.addRoute("194.0.0.0", 7)
                builder.addRoute("193.0.0.0", 8)
                builder.addRoute("192.0.0.0", 9)
                builder.addRoute("192.192.0.0", 10)
                builder.addRoute("192.128.0.0", 11)
                builder.addRoute("192.176.0.0", 12)
                builder.addRoute("192.160.0.0", 13)
                builder.addRoute("192.172.0.0", 14)
                builder.addRoute("192.170.0.0", 15)
                builder.addRoute("192.169.0.0", 16)
                builder.addRoute("128.0.0.0", 3)
                builder.addRoute("176.0.0.0", 4)
                builder.addRoute("160.0.0.0", 5)
                builder.addRoute("168.0.0.0", 6)
                builder.addRoute("174.0.0.0", 7)
                builder.addRoute("173.0.0.0", 8)
                builder.addRoute("172.128.0.0", 9)
                builder.addRoute("172.64.0.0", 10)
                builder.addRoute("172.32.0.0", 11)
                builder.addRoute("172.0.0.0", 12)
                builder.addRoute("64.0.0.0", 2)
                builder.addRoute("32.0.0.0", 3)
                builder.addRoute("16.0.0.0", 4)
                builder.addRoute("0.0.0.0", 5)
                builder.addRoute("12.0.0.0", 6)
                builder.addRoute("8.0.0.0", 7)
                builder.addRoute("11.0.0.0", 8)
            }
            }
        app.device?.let { device ->
            val pfd = try {
                builder.establish()
            } catch (e: Exception) {
                Log.i(TAG, "[service]WARNING tunnel handover failed; retaining the existing interface: ${e.message}")
                return
            }
            pfd?.let {
                // cancel the previous packet flow after the new fd is in place, to avoid leaking packets
                val replacedPacketFlow = packetFlow
                packetFlow = PacketFlow(device, it) {
                    Handler(mainLooper).post {
                        if (packetFlow == it) {
                            packetFlow = null
                            if (app.service?.get() == this@MainService) {
                                device.tunnelStarted = false
                                reconcilePfd()
                            }
                        }
                    }
                }
                appliedTunnelConfiguration = configuration
                val replacedGuard = alwaysOnGuardPfd
                alwaysOnGuardPfd = null
                replacedGuard?.close()
                replacedPacketFlow?.close()
                Log.i(
                    TAG,
                    "[service]tunnel applied offline=${configuration.offline} connected=${configuration.connected} " +
                        "included=${configuration.includedAppIds.size} excluded=${configuration.excludedAppIds.size} " +
                        "dns=${configuration.dnsIpv4s} address=${configuration.clientIpv4} " +
                        "tunnelIpv6=${ipv6Report}",
                )
                if (app.service?.get() == this@MainService) {
                    device.tunnelStarted = true
                } else {
                    stop()
                }
            } ?: run {
                Log.i(TAG, "[service]WARNING tunnel was not started. Another existing tunnel may be blocking the start.")
                stop()
            }
        } ?: run {
            if (app.isSystemAlwaysOnVpnActive()) {
                Log.i(TAG, "[service]device disappeared during tunnel handover; retaining Always-on guard")
                establishAlwaysOnGuard()
            } else {
                stop()
            }
        }
    }

    /**
     * Android can start an Always-on VPN before the user has authenticated (or
     * keep it selected after logout). Hold a real IPv4 VPN interface so the OS
     * does not enter a restart loop. Traffic is intentionally fail-closed until
     * a DeviceLocal is restored; the VPN app itself is excluded so login and
     * provider discovery can recover the session.
     */
    private fun establishAlwaysOnGuard(): Boolean {
        if (alwaysOnGuardPfd != null) {
            packetFlow?.close()
            packetFlow = null
            appliedTunnelConfiguration = null
            return true
        }
        val builder = Builder()
            .setSession("URnetwork — sign in required")
            .setMtu(Sdk.getDefaultTunnelMtu())
            .setBlocking(false)
            .setUnderlyingNetworks(null)
            .addDisallowedApplication(packageName)
            .addAddress("192.0.2.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("192.0.2.2")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        val established = try {
            builder.establish()
        } catch (e: Exception) {
            Log.i(TAG, "[service]could not establish Always-on authentication guard: ${e.message}")
            null
        } ?: return false

        val previous = alwaysOnGuardPfd
        val replacedPacketFlow = packetFlow
        alwaysOnGuardPfd = established
        packetFlow = null
        appliedTunnelConfiguration = null
        previous?.close()
        replacedPacketFlow?.close()
        Log.i(TAG, "[service]Always-on authentication guard established")
        return true
    }

    /** Keep the OS-owned VPN alive while MainApplication replaces/logs out its device. */
    fun retainAlwaysOnWithoutDevice(): Boolean {
        if (android.os.Looper.myLooper() != mainLooper) {
            Handler(mainLooper).post { retainAlwaysOnWithoutDevice() }
            return true
        }
        val app = application as MainApplication
        if (app.service?.get() !== this || !establishAlwaysOnGuard()) return false
        app.device?.tunnelStarted = false
        detachDeviceBindings(closePacketFlow = true)
        return true
    }

    private fun detachDeviceBindings(closePacketFlow: Boolean) {
        val app = application as MainApplication
        unregisterPackageChangeReceiver()
        boundDevice?.setFlowOwnerLookup(null)
        appliedPinnedAppIds = null
        deviceOfflineSub?.close()
        deviceOfflineSub = null
        windowStatusChangeSub?.close()
        windowStatusChangeSub = null
        blockActionOverridesSub?.close()
        blockActionOverridesSub = null
        dnsResolverSettingsSub?.close()
        dnsResolverSettingsSub = null
        routeLocalSub?.close()
        routeLocalSub = null
        connectChangeSub?.close()
        connectChangeSub = null
        boundDevice = null
        if (closePacketFlow) {
            packetFlow?.close()
            packetFlow = null
            appliedTunnelConfiguration = null
        }
    }

    /**
     * Installs (or clears) the flow-owner lookup that powers per-app pinning:
     * the Go side asks it once per new flow which pinned app owns the flow,
     * and every flow of that app then rides one exit -- one egress IP for the
     * whole app, API session and CDNs alike. Swapped wholesale on every rules
     * change; null when nothing is pinned so the Go side skips the machinery
     * entirely.
     */
    @Synchronized
    private fun applyPinnedAppLookup(force: Boolean = false) {
        val app = application as MainApplication
        val device = app.device ?: return
        val pinnedPackages = sdkStringListToSet(device.pinnedAppIds)
        // installing a lookup is not free on the go side: it invalidates the
        // flow-owner cache and the recorded app placements, so every live
        // flow's next packet pays a fresh platform call on the single tun
        // reader for an answer only a NEW flow consumes. This fires on any
        // block-action change and on any package broadcast -- a play store
        // batch update would otherwise replay that burst dozens of times.
        //
        // force bypasses the memo: a package event never changes the pinned
        // package-name SET, only the uid map frozen inside the lookup, so
        // the receiver's rebuild would otherwise always be a no-op -- and a
        // reinstalled (uid-recycled) pinned app would keep a stale map.
        if (!force && pinnedPackages == appliedPinnedAppIds) {
            return
        }
        appliedPinnedAppIds = pinnedPackages
        // getConnectionOwnerUid is api 29+; below that a pin rule simply has
        // no per-app effect (the constellation table still groups by domain).
        // the two conditions are separate ifs so the NewApi lint sees a plain
        // version guard rather than a disjunction it may not fold
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            device.setFlowOwnerLookup(null)
            return
        }
        if (pinnedPackages.isEmpty()) {
            device.setFlowOwnerLookup(null)
            return
        }
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        device.setFlowOwnerLookup(
            PinnedAppFlowLookup(connectivityManager, packageManager, pinnedPackages)
        )
    }

    /**
     * Rebuilds the pinned-app lookup when packages change. The lookup maps
     * uid -> package once at construction, and uids are NOT stable across an
     * uninstall/reinstall -- worse, Android recycles them, so a stale map can
     * attribute a newly installed app's flows to a pinned package. Cheap to
     * rebuild, so rebuild on any package event.
     */
    private val packageChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            // getPackageUid per pinned app plus two gomobile crossings: small,
            // but this runs on the main looper inside a broadcast, and a
            // package replace fires it more than once
            val changedPackage = intent?.data?.schemeSpecificPart
            thread {
                // only a PINNED package's install state can invalidate the
                // frozen uid map; every other app's broadcast is noise, and
                // reinstalling an identical lookup wipes the go-side flow
                // caches (a play store batch update would replay that burst
                // dozens of times). An event with no package forces, safely.
                val app = application as MainApplication
                val pinned = sdkStringListToSet(app.device?.pinnedAppIds)
                if (changedPackage != null && changedPackage !in pinned) {
                    return@thread
                }
                applyPinnedAppLookup(force = true)
            }
        }
    }

    @Volatile
    private var packageChangeReceiverRegistered = false

    private fun registerPackageChangeReceiver() {
        if (packageChangeReceiverRegistered) {
            return
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        // these are framework protected-broadcasts, so the export flag is not
        // required at runtime -- but UnspecifiedRegisterReceiverFlag is an
        // error-severity lint at this targetSdk, and lintVitalRelease is fatal
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        packageChangeReceiverRegistered = true
    }

    private fun unregisterPackageChangeReceiver() {
        if (!packageChangeReceiverRegistered) {
            return
        }
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: IllegalArgumentException) {
        }
        packageChangeReceiverRegistered = false
    }

    /**
     * The (allow-list, deny-list) app sets for the tunnel builder.
     *
     * The sdk names these from the ROUTE's point of view and this inverts
     * them: a local-routed app bypasses the vpn (builder disallow), a
     * remote-routed app uses it (builder allow).
     *
     * Pinned apps need care. Pinning holds an app to one exit INSIDE the
     * tunnel -- it is placement, not membership -- so the sdk deliberately
     * omits pinned apps from both sets. But "allowlist mode" is built from
     * the allow set ALONE ("excluded by omission" below), so simply omitting
     * a pinned app drops it out of the vpn entirely the moment any other app
     * is included. Union the pinned apps into the allow set exactly when
     * allowlist mode is active; in denylist mode omission is correct, since
     * everything not denied already uses the tunnel.
     */
    private fun tunnelAppSplit(): Pair<Set<String>, Set<String>> {
        val app = application as MainApplication
        val device = app.device ?: return Pair(emptySet(), emptySet())
        val overrideAppIds = device.localOverrideAppIds ?: return Pair(emptySet(), emptySet())
        var tunnelIncluded = sdkStringListToSet(overrideAppIds.excluded)
        val tunnelExcluded = sdkStringListToSet(overrideAppIds.included)
        if (tunnelIncluded.isNotEmpty()) {
            // subtract what the denylist branch would have excluded anyway
            // (this app, the default exclusions, and any explicit exclude
            // rule), or a pinned app that is also default-excluded would be
            // in the tunnel in allowlist mode and out of it in denylist mode
            // -- the same rule meaning opposite things depending on whether
            // an unrelated include rule happens to exist
            val neverTunneled = defaultExcludedPackageNames().toSet() +
                tunnelExcluded +
                packageName
            tunnelIncluded = tunnelIncluded +
                (sdkStringListToSet(device.pinnedAppIds) - neverTunneled)
        }
        return sanitizeTunnelAppSplit(
            packageName,
            tunnelIncluded,
            tunnelExcluded,
            isPackageInstalled = { isPackageInstalled(it) },
        )
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                packageManager.getApplicationInfo(
                    packageName,
                    android.content.pm.PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            true
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * The IPv4 DNS servers for the tunnel builder, from the SDK device like the
     * tunnel address. The tunnel deliberately does not consume the SDK's IPv6
     * DNS list until remote providers can forward IPv6.
     */
    private fun tunnelDnsServers(): List<String> {
        val app = application as MainApplication
        val device = app.device ?: return dnsIpv4s
        return sdkStringListToList(device.tunnelDnsAddressesIpv4()).ifEmpty { dnsIpv4s }
    }

    private fun sdkStringListToSet(list: com.bringyour.sdk.StringList?): Set<String> {
        if (list == null) {
            return emptySet()
        }
        val set = mutableSetOf<String>()
        val n = list.len()
        for (i in 0 until n) {
            set.add(list.get(i))
        }
        return set
    }

    override fun onDestroy() {
        super.onDestroy()

        stop()
    }

    override fun onRevoke() {
        super.onRevoke()

        stop()
    }

//    override fun onLowMemory() {
//        super.onLowMemory()
//
//        Sdk.freeMemory()
//    }

    fun stop() {
        val app = application as MainApplication

        detachDeviceBindings(closePacketFlow = true)
        alwaysOnGuardPfd?.close()
        alwaysOnGuardPfd = null

        stopForegroundNotification()
        stopSelf()

        if (app.service?.get() == this) {
            app.device?.tunnelStarted = false
            app.vpnServiceDidStop(this)
        }
    }


    private fun startForegroundNotification(message: String) {
        val notificationManager = getSystemService(
                NOTIFICATION_SERVICE
                ) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setOngoing(true)
//                .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
            .setSmallIcon(R.drawable.ic_status)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setContentText(message)
            .setContentTitle(getString(R.string.app_name))
                .setContentIntent(pendingIntent)
//                .setTicker(message)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        foregroundStarted = true
    }

    /**
     * Foreground notification policy is orthogonal to the TUN descriptor.
     * Updating it in place avoids a service/TUN restart when Auto providing
     * hands off to a client connection (or back again).
     */
    fun setForegroundEnabled(enabled: Boolean): Boolean {
        return try {
            if (enabled) {
                startForegroundNotification("On")
            } else {
                stopForegroundNotification()
            }
            true
        } catch (e: Exception) {
            Log.i(TAG, "Unable to update VPN foreground state in place: ${e.message}")
            false
        }
    }


    private fun stopForegroundNotification() {
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
    }

        private fun startCloseMonitor() {
            val app = application as MainApplication
            // Reset the flag so a new monitor can always be started,
            // even if a previous monitor is still winding down.
            closeMonitorStarted = true
            val generation = closeMonitorGeneration.incrementAndGet()

            thread {
                var done = false
                while (!done) {
                    if (app.service?.get() != this@MainService) {
                        done = true
                    }
                    if (!done) {
                        synchronized(app.serviceActiveMonitor) {
                            if (!app.serviceActive) {
                                done = true
                            }
                            app.serviceActiveMonitor.wait(1000, 0)
                        }
                    }
                }
                Handler(mainLooper).post {
                    // Only stop if no newer monitor has been started since this one.
                    if (closeMonitorGeneration.get() == generation) {
                        stop()
                        closeMonitorStarted = false
                    }
                }
            }
        }

}


private class PacketFlow(deviceLocal: DeviceLocal, pfd: ParcelFileDescriptor, endCallback: (packetFlow: PacketFlow)->Unit) {

    val stateLock = ReentrantLock()
//    val closed: Condition = stateLock.newCondition()
    var active: Boolean = true

    val ioLoop: IoLoop = Sdk.newIoLoop(deviceLocal, pfd.detachFd()) {
        close()
        endCallback(this@PacketFlow)
    }

    fun close() {
        stateLock.lock()
        try {
            active = false
        } finally {
            stateLock.unlock()
        }
        ioLoop.close()
    }

    fun isActive(): Boolean {
        stateLock.lock()
        try {
            return active
        } finally {
            stateLock.unlock()
        }
    }
}

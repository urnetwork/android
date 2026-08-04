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
import java.lang.ref.WeakReference
import java.net.InetAddress
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

    // see https://developer.android.com/develop/connectivity/vpn
    @SuppressLint("VpnServicePolicy")
    class MainService : VpnService() {
    companion object {
        const val NOTIFICATION_ID = 101
        const val NOTIFICATION_CHANNEL_ID = "URnetwork"

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

    val clientIpv6: String? = null
    val clientIpv6PrefixLength = 64
    val dnsIpv6s = emptyList<String>()


    //    private var pfd: ParcelFileDescriptor? = null
    private var packetFlow: PacketFlow? = null
    private var foregroundStarted: Boolean = false

    private var deviceOfflineSub: Sub? = null
    private var windowStatusChangeSub: Sub? = null
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

        if (stop || !app.serviceActive) {
            stop()
        } else if (start && app.service?.get() != this) {
            app.service?.get().let { currentService ->
                if (currentService != this) {
                    currentService?.stop()
                }
            }
            app.service = WeakReference(this)

            val foreground = intent?.getBooleanExtra("foreground", false) ?: false
//            val offline = intent.getBooleanExtra("offline", false)

            if (foreground) {
                startForegroundNotification("On")
                // update the notification `NOTIFICATION_ID` and it will update the displayed notification
                // see https://stackoverflow.com/questions/5528288/how-do-i-update-the-notification-text-for-a-foreground-service-in-android
            } else {
                stopForegroundNotification()
            }

            connected = app.device?.windowStatus?.let {
                0 < it.providerStateAdded
            } ?: false

            fun offline():Boolean {
                return app.device?.let { device ->
                    device.offline && !device.vpnInterfaceWhileOffline
                } ?: false
            }
            this@MainService.offline = offline()

            deviceOfflineSub?.close()
            deviceOfflineSub = app.device?.addOfflineChangeListener { _, _ ->
                Handler(mainLooper).post {
                    val offline = offline()
                    if (this@MainService.offline != offline) {
                        this@MainService.offline = offline
                        reconcilePfd()
                    }
                }
            }

            fun updateWindowStatus(windowStatus: WindowStatus) {
                this@MainService.connected = 0 < windowStatus.providerStateAdded
                reconcilePfd()
            }

            windowStatusChangeSub?.close()
            windowStatusChangeSub = app.device?.addWindowStatusChangeListener { windowStatus ->
                Handler(mainLooper).post {
                    updateWindowStatus(windowStatus)
                }
            }
            app.device?.windowStatus?.let { windowStatus ->
                updateWindowStatus(windowStatus)
            }

            // rebuild the tunnel when the per-app split rules change so the
            // allowed/disallowed application sets stay in sync -- and refresh
            // the pinned-app flow lookup, which is how a new pin rule takes
            // effect without a tunnel rebuild (pinned apps stay IN the
            // tunnel; only their exit placement is held)
            blockActionOverridesSub?.close()
            blockActionOverridesSub = app.device?.addBlockActionOverridesChangeListener {
                Handler(mainLooper).post {
                    // install/refresh the pinned-app flow-owner lookup first:
                    // a pin change alters what tunnelAppSplit returns, which
                    // reconcilePfd's configuration compare then picks up
                    applyPinnedAppLookup()
                    reconcilePfd()
                }
            }
            applyPinnedAppLookup()
            registerPackageChangeReceiver()

            // rebuild the tunnel when the dns settings change the builder dns
            // servers (e.g. unencrypted local servers set or cleared)
            dnsResolverSettingsSub?.close()
            dnsResolverSettingsSub = app.device?.addDnsResolverSettingsChangeListener {
                Handler(mainLooper).post {
                    reconcilePfd()
                }
            }

            startCloseMonitor()
        }

        // see https://developer.android.com/reference/android/app/Service#START_REDELIVER_INTENT
        return START_REDELIVER_INTENT
    }

    private fun desiredTunnelConfiguration(): VpnPacketFlowConfiguration {
        val app = application as MainApplication
        val (includedAppIds, excludedAppIds) = tunnelAppSplit()
        val clientIpv4 = app.device?.tunnelLocalAddress()
        val (deviceDnsIpv4s, dnsIpv6s) = tunnelDnsServers()
        return VpnPacketFlowConfiguration(
            offline = offline,
            connected = connected,
            includedAppIds = includedAppIds.toSet(),
            excludedAppIds = excludedAppIds.toSet(),
            dnsIpv4s = vpnDnsServersForClient(clientIpv4, deviceDnsIpv4s, dnsIpv4s),
            dnsIpv6s = dnsIpv6s.toList(),
            clientIpv4 = clientIpv4,
        )
    }

    private fun reconcilePfd() {
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
        builder.setMtu(1440)
        builder.setBlocking(false)
        builder.setUnderlyingNetworks(null)
        val tunnelIncludedAppIds = configuration.includedAppIds
        val tunnelExcludedAppIds = configuration.excludedAppIds
        val tunnelDnsIpv4s = configuration.dnsIpv4s
        val tunnelDnsIpv6s = configuration.dnsIpv6s

        if (configuration.offline) {
//            Log.i(TAG, "[io]OFFLINE")
            // when offline, only allow traffic from a fake package name
            // in this way, the vpn service remains active but no apps detect it as an interface
            builder.addAllowedApplication("${packageName}.offline")
        } else if (tunnelIncludedAppIds.isNotEmpty()) {
            // per-app inclusions take precedence: allowlist mode, only the
            // included apps use the tunnel. tunnelAppSplit sanitizes the VPN
            // owner before this mode decision, so a stale self-only rule
            // cannot create an empty Android UID set.
            for (includedPackageName in tunnelIncludedAppIds) {
                try {
                    builder.addAllowedApplication(includedPackageName)
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                }
            }
        } else {
            // denylist mode: everything uses the tunnel except this app,
            // the default excluded apps, and the per-app exclusions
            builder.addDisallowedApplication(packageName)
            for (excludedPackageName in defaultExcludedPackageNames() + tunnelExcludedAppIds) {
                try {
                    builder.addDisallowedApplication(excludedPackageName)
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val clientIpv4 = configuration.clientIpv4
        if (clientIpv4 != null) {
            builder.allowFamily(AF_INET)
            builder.addAddress(
                clientIpv4,
                clientIpv4PrefixLength
            )
            // DNS from the SDK device (see `tunnelDnsServers`). It must be a
            // distinct address routed through the TUN: Android locally
            // terminates packets addressed to clientIpv4 before PacketFlow can
            // hand them to UpgradeMux.
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
        if (clientIpv6 != null) {
            builder.allowFamily(AF_INET6)
            builder.addAddress(
                clientIpv6,
                clientIpv6PrefixLength
            )
            for (dnsIpv6 in tunnelDnsIpv6s) {
                builder.addDnsServer(dnsIpv6)
            }
            if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                builder.addRoute("::", 0)
                builder.excludeRoute(IpPrefix(InetAddress.getByName("fd00::"), 8))
            } else {
                /*
                python script:

                n = [ipaddress.ip_network('::/0')]
                for m in [ipaddress.ip_network('fd00::/8')]:
                    n = [
                        b
                        for a in n
                        for b in (list(a.address_exclude(m)) if a.overlaps(m) else [a])
                    ]
                for a in n:
                    print('builder.addRoute("{}", {})'.format(a.network_address, a.prefixlen))
                */

                builder.addRoute("::", 1)
                builder.addRoute("8000::", 2)
                builder.addRoute("c000::", 3)
                builder.addRoute("e000::", 4)
                builder.addRoute("f000::", 5)
                builder.addRoute("f800::", 6)
                builder.addRoute("fe00::", 7)
                builder.addRoute("fc00::", 8)
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
                replacedPacketFlow?.close()
                Log.i(
                    TAG,
                    "[service]tunnel applied offline=${configuration.offline} connected=${configuration.connected} " +
                        "included=${configuration.includedAppIds.size} excluded=${configuration.excludedAppIds.size} " +
                        "dns=${configuration.dnsIpv4s + configuration.dnsIpv6s} address=${configuration.clientIpv4}",
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
            Log.i(TAG, "[service]WARNING tunnel was not started due to missing device.")
            stop()
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
     * The (ipv4, ipv6) dns servers for the tunnel builder, from the sdk device
     * like the tunnel address: the dns settings' unencrypted local servers when
     * set, otherwise the distinct DnsUpgradeMaskAddress, which the UpgradeMux
     * intercepts and upgrades. Falls back to the static identity when the device
     * is unavailable.
     */
    private fun tunnelDnsServers(): Pair<List<String>, List<String>> {
        val app = application as MainApplication
        val device = app.device ?: return Pair(dnsIpv4s, dnsIpv6s)
        return Pair(
            sdkStringListToList(device.tunnelDnsAddressesIpv4()).ifEmpty { dnsIpv4s },
            sdkStringListToList(device.tunnelDnsAddressesIpv6()),
        )
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

        unregisterPackageChangeReceiver()
        // the lookup holds this service's ConnectivityManager (and through it
        // this Context); leaving it installed on the device would retain the
        // destroyed service until the next start replaced it
        app.device?.setFlowOwnerLookup(null)
        appliedPinnedAppIds = null

        deviceOfflineSub?.close()
        deviceOfflineSub = null

        windowStatusChangeSub?.close()
        windowStatusChangeSub = null

        blockActionOverridesSub?.close()
        blockActionOverridesSub = null

        dnsResolverSettingsSub?.close()
        dnsResolverSettingsSub = null

        packetFlow?.close()
        packetFlow = null
        appliedTunnelConfiguration = null

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

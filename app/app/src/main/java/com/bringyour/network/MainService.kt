    package com.bringyour.network

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.system.OsConstants.AF_INET
import android.system.OsConstants.AF_INET6
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.IoLoop
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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



    val clientIpv4: String? = "169.254.2.1"
    val clientIpv4PrefixLength = 32
    // see:
    // https://security.googleblog.com/2022/07/dns-over-http3-in-android.html#fn2
    // only Google DNS and CloudFlare DNS will auto-enable DoT/DoH on Android
    // *important* removing the Google public server will disable DoT/DoH
    val dnsIpv4s = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

    val clientIpv6: String? = null
    val clientIpv6PrefixLength = 64
    val dnsIpv6s = emptyList<String>()



    //    private var pfd: ParcelFileDescriptor? = null
    private var packetFlow: PacketFlow? = null
    private var foregroundStarted: Boolean = false

    private var windowStatusChangeSub: Sub? = null
    private var connected: Boolean = false

    private var closeMonitorStarted: Boolean = false


    override fun onStartCommand(intent : Intent?, flags: Int, startId : Int): Int {
        val app = application as MainApplication

        val stop = intent?.getBooleanExtra("stop", false) ?: false
        val start = intent?.getBooleanExtra("start", true) ?: false

        if (stop || !app.serviceActive) {
            stop()
        } else if (start) {
            app.service?.get().let { currentService ->
                if (currentService != this) {
                    currentService?.stop()
                }
            }
            app.service = WeakReference(this)

            val foreground = intent.getBooleanExtra("foreground", false)
            val source = intent.getStringExtra("source") ?: "unknown"
            val offline = intent.getBooleanExtra("offline", false)

            if (foreground) {
                startForegroundNotification("On")
                // update the notification `NOTIFICATION_ID` and it will update the displayed notification
                // see https://stackoverflow.com/questions/5528288/how-do-i-update-the-notification-text-for-a-foreground-service-in-android
            } else {
                stopForegroundNotification()
            }

            // FIXME just let the user toggle route local which is also called "kill switch"
            /*
            // see https://developer.android.com/develop/connectivity/vpn#detect_always-on
            var alwaysOn = source != "app"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isAlwaysOn) {
                    alwaysOn = true
                }
            }
            if (alwaysOn) {
                // this was started with always-on mode
                // turn off local routing
                app.deviceManager.routeLocal = false
            }
            */

            connected = app.device?.windowStatus?.let {
                0 < it.providerStateAdded
            } ?: false

            if (canUpdatePfd(source)) {
                updatePfd(offline)
            }

            windowStatusChangeSub?.close()
            windowStatusChangeSub = app.device?.addWindowStatusChangeListener { windowStatus ->
                runBlocking(Dispatchers.Main.immediate) {
                    val connected = windowStatus?.let {
                        0 < it.providerStateAdded
                    } ?: false
                    if (this@MainService.connected != connected) {
                        this@MainService.connected = connected
                        if (connected) {
                            // delaying the tunnel reset seems to help with stability
                            Handler(mainLooper).post {
                                if (this@MainService.connected && canUpdatePfd(source)) {
                                    updatePfd(offline)
                                }
                            }
                        }
                    }
                }
            }
        }

        startCloseMonitor()

        // see https://developer.android.com/reference/android/app/Service#START_REDELIVER_INTENT
        return START_REDELIVER_INTENT
    }

    fun canUpdatePfd(source: String): Boolean {
        // see https://developer.android.com/develop/connectivity/vpn#detect_always-on
        var alwaysOn = source != "app"
        if (Build.VERSION_CODES.Q <= Build.VERSION.SDK_INT) {
            if (isAlwaysOn) {
                alwaysOn = true
            }
        }

        if (alwaysOn) {
            // when always on, it appears we cannot recreate the pfd
            // TODO is there some documentation on this?
            return !(packetFlow?.isActive() ?: false)
        } else {
            return true
        }
    }

    fun updatePfd(offline: Boolean) {
        val app = application as MainApplication

        val builder = Builder()
        builder.setSession("URnetwork")
        builder.setMtu(1440)
        builder.setBlocking(false)
        builder.setUnderlyingNetworks(null)
        if (offline) {
            // when offline, only allow traffic from a fake package name
            // in this way, the vpn service remains active but no apps detect it as an interface
            builder.addAllowedApplication("${packageName}.offline")
        } else {
            builder.addDisallowedApplication(packageName)
            // add split tunnel excluded configuration
            for (excludedPackageName in defaultExcludedPackageNames()) {
                builder.addDisallowedApplication(excludedPackageName)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        if (clientIpv4 != null) {
            builder.allowFamily(AF_INET)
            builder.addAddress(
                clientIpv4,
                clientIpv4PrefixLength
            )
            for (dnsIpv4 in dnsIpv4s) {
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
            for (dnsIpv6 in dnsIpv6s) {
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
            builder.establish()?.let { pfd ->
                // cancel the previous packet flow after the new fd is in place, to avoid leaking packets
                val replacedPacketFlow = packetFlow
                packetFlow = PacketFlow(device, pfd) {
                    runBlocking(Dispatchers.Main.immediate) {
                        if (packetFlow == it) {
                            // unexpected exit
                            packetFlow = null
                            if (app.service?.get() == this@MainService) {
                                device.tunnelStarted = false
                            }
                        }
                        // else the ended packet flow was replaced by a new one
                    }
                }
                replacedPacketFlow?.close()
                if (app.service?.get() == this@MainService) {
                    device.tunnelStarted = true
                }
            } ?: run {
                Log.i(TAG, "[service]WARNING tunnel was not started. Another existing tunnel may be blocking the start.")
            }
        } ?: run {
            Log.i(TAG, "[service]WARNING tunnel was not started due to missing device.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        stop()
    }

    override fun onRevoke() {
        super.onRevoke()

        stop()
    }

    override fun onLowMemory() {
        super.onLowMemory()

        Sdk.freeMemory()
    }

    fun stop() {
        val app = application as MainApplication

        windowStatusChangeSub?.close()
        windowStatusChangeSub = null

        packetFlow?.close()
        packetFlow = null

        stopForegroundNotification()
        stopSelf()

        if (app.service?.get() == this) {
            app.device?.tunnelStarted = false
            app.service = null
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


    private fun stopForegroundNotification() {
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
    }

        private fun startCloseMonitor() {
            val app = application as MainApplication
            if (!closeMonitorStarted) {
                closeMonitorStarted = true

                thread {
                    var done = false
                    while (!done) {
                        runBlocking(Dispatchers.Main.immediate) {
                            if (app.service?.get() != this@MainService) {
                                done = true
                            }
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
                    runBlocking(Dispatchers.Main.immediate) {
                        stop()
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
        var closed = false
        stateLock.lock()
        try {
            if (active) {
                active = false
                closed = true
//                closed.signalAll()
            }
        } finally {
            stateLock.unlock()
        }
        if (closed) {
            ioLoop.close()
        }
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


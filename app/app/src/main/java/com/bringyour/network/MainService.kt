package com.bringyour.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants.AF_INET
import android.system.OsConstants.AF_INET6
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bringyour.sdk.DeviceLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock


// see https://developer.android.com/develop/connectivity/vpn
class MainService : VpnService() {
    companion object {
        const val NOTIFICATION_ID = 101
        const val NOTIFICATION_CHANNEL_ID = "URnetwork"
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


    override fun onStartCommand(intent : Intent?, flags: Int, startId : Int): Int {
        val app = application as MainApplication

        intent?.getBooleanExtra("stop", false)?.let { stop ->
            if (stop) {
                stop()
            }
        }

        intent?.getBooleanExtra("start", true)?.let { start ->
            if (start) {
                intent.getBooleanExtra("foreground", false).let { foreground ->
                    if (foreground) {
                        startForegroundNotification("On")
                        // update the notification `NOTIFICATION_ID` and it will update the displayed notification
                        // see https://stackoverflow.com/questions/5528288/how-do-i-update-the-notification-text-for-a-foreground-service-in-android
                    } else {
                        stopForegroundNotification()
                    }
                }

                // see https://developer.android.com/develop/connectivity/vpn#detect_always-on
                if (intent.getStringExtra("source") != "app") {
                    // this was started with always-on mode
                    // turn off local routing
                    app.deviceManager.routeLocal = false
                }

                val offline = intent.getBooleanExtra("offline", false)


                val builder = Builder()
                builder.setSession("URnetwork")
                builder.setMtu(1440)
                builder.setBlocking(true)
                if (offline) {
                    // when offline, only allow traffic from a fake package name
                    // in this way, the vpn service remains active but no apps detect it as an interface
                    builder.addAllowedApplication("${packageName}.offline")
                } else {
                    builder.addDisallowedApplication(packageName)
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

                builder.establish()?.let { pfd ->

                    val previousPacketFlow = this.packetFlow
                    app.device?.let { device ->
                        packetFlow = PacketFlow(device, pfd) {
                            runBlocking(Dispatchers.Main.immediate) {
                                if (packetFlow == it) {
                                    packetFlow = null
                                    // FIXME notify tunnel closed
//                                    device.tunnelStarted = false

                                }

                            }
                        }
                        // FIXME
//                        device.tunnelStarted = true
                    }
                    // cancel the previous packet flow after the new packet flow is set
                    previousPacketFlow?.cancel()
                }

            }
        }

        // see https://developer.android.com/reference/android/app/Service#START_REDELIVER_INTENT
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        Log.i("MainService", "DESTROY SERVICE")

        super.onDestroy()

        stop()
    }

    override fun onRevoke() {
        Log.i("MainService", "REVOKE SERVICE")

        super.onRevoke()

        stop()
    }

//    override fun onTaskRemoved(rootIntent: Intent?) {
//        super.onTaskRemoved(rootIntent)
//
//        stop()
//    }


    private fun stop() {
        packetFlow?.cancel()
        packetFlow = null

        stopForegroundNotification()
        stopSelf()
    }


    private fun startForegroundNotification(message: String) {
        if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
            val notificationManager = getSystemService(
                    NOTIFICATION_SERVICE
                    ) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

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

}


private class PacketFlow(deviceLocal: DeviceLocal, pfd: ParcelFileDescriptor, endCallback: (packetFlow: PacketFlow)->Unit) {

    val stateLock: Lock
    val closed: Condition
    var active: Boolean = true


    init {
        stateLock = ReentrantLock()
        closed = stateLock.newCondition()

        thread {
            val fis = FileInputStream(pfd.fileDescriptor)
            val fos = FileOutputStream(pfd.fileDescriptor)
            try {

                val receiveSub = deviceLocal.addReceivePacket {
                    try {
                        fos.write(it)
                    } catch (_: IOException) {
                        try {
                            fos.close()
                        } catch (_: IOException) {
                        }
                        cancel()
                    }
                }
                try {

                    thread {
                        try {
                            val buffer = ByteArray(2048)
                            while (true) {
                                val n = fis.read(buffer)

                                if (0 < n) {
                                    // note sendPacket makes a copy of the buffer
                                    val success = deviceLocal.sendPacket(buffer, n)
                                    if (!success) {
                                        Log.i("Router", "Send packet dropped.")
                                    }
                                }
                            }
                        } catch (_: IOException) {
                            try {
                                fis.close()
                            } catch (_: IOException) { }
                            cancel()
                        }
                    }

                    stateLock.lock()
                    try {
                        while (active) {
                            closed.await()
                        }
                    } finally {
                        stateLock.unlock()
                    }

                } finally {
                    receiveSub.close()
                }
            } finally {
                try {
                    pfd.close()
                } catch (_: IOException) {
                }
                cancel()

                endCallback(this)
            }
        }
    }

    fun cancel() {
        stateLock.lock()
        try {
            active = false
            closed.signalAll()
        } finally {
            stateLock.unlock()
        }
    }
}


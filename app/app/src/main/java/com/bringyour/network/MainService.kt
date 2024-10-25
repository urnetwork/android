package com.bringyour.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.IpPrefix
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.system.OsConstants.AF_INET
import android.system.OsConstants.AF_INET6
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import java.io.IOException
import java.net.InetAddress


// see https://developer.android.com/develop/connectivity/vpn



class MainService : VpnService() {
    companion object {

        const val NOTIFICATION_ID = 101
        const val NOTIFICATION_CHANNEL_ID = "URnetwork"

    }



    // TODO opt this process out of the vpn network?

    // addDisallowedApplication (String packageName)

    private var pfd: ParcelFileDescriptor? = null
    private var foregroundStarted: Boolean = false

//    private var managed: Boolean = false


    override fun onStartCommand(intent : Intent?, flags: Int, startId : Int): Int {

        // FIXME get the local IPv4 and IPv6 address from the platform
        // FIXME when using auth login, the jwt should contain the local addresses


        // FIXME bundle args to start, restart, stop

        val app = application as MainApplication

//        Log.i(TAG,"START INTENT = $intent")

//        managed = intent?.getBooleanExtra("managed", false) ?: false


        intent?.getBooleanExtra("stop", false)?.let { stop ->
            if (stop) {
                try {
                    pfd?.close()
                } catch (e: IOException) {
                    // ignore
                }
                pfd = null
            }
        }

        intent?.getBooleanExtra("start", true)?.let { start ->
            if (start) {
                // see https://developer.android.com/develop/connectivity/vpn#detect_always-on
                if (intent.getStringExtra("source") != "app") {
                    // this was started with always-on mode
                    // turn off local routing
                    app.byDeviceManager.routeLocal = false
                }

                val offline = intent.getBooleanExtra("offline", false)

                app.router?.let { router ->
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

                    if (router.clientIpv4 != null) {
                        builder.allowFamily(AF_INET)
                        builder.addAddress(
                            router.clientIpv4,
                            router.clientIpv4PrefixLength
                        )
                        for (dnsIpv4 in router.dnsIpv4s) {
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
                    if (router.clientIpv6 != null) {
                        builder.allowFamily(AF_INET6)
                        builder.addAddress(
                            router.clientIpv6,
                            router.clientIpv6PrefixLength
                        )
                        for (dnsIpv6 in router.dnsIpv6s) {
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

//        val pfd = builder.establish()

                    // fis
                    // fos

                    // thread to read fis, post to user remote nat
                    // callback on user remote nat to synchronize on fos, write to fos

                    // startForeground
                    // show the transfer stats

                    // stop self when turned off

                    builder.establish()?.let { pfd ->
                        try {
                            this.pfd?.close()
                        } catch (e: IOException) {
                            // ignore
                        }
                        this.pfd = pfd
                        router.activateLocalInterface(pfd)
                    }
                }

            }
        }

        intent?.getBooleanExtra("foreground", false)?.let { notification ->
            if (notification) {
                if (!foregroundStarted) {
                    startForegroundNotification("On")
                    foregroundStarted = true
                }
                // update the notification `NOTIFICATION_ID` and it will update the displayed notification
                // see https://stackoverflow.com/questions/5528288/how-do-i-update-the-notification-text-for-a-foreground-service-in-android
            } else {
                if (foregroundStarted) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    foregroundStarted = false
                }
            }
        }

    // FIXME configure intent

//        startForeground(1, getStatusNotificationBuilder().build());

        // see https://developer.android.com/reference/android/app/Service#START_STICKY
        return START_REDELIVER_INTENT
//        return START_STICKY
    }

    override fun onDestroy() {
        Log.i("MainService", "DESTROY SERVICE")

        super.onDestroy()

//        val app = application as MainApplication


        try {
            pfd?.close()
        } catch (e: IOException) {
            // ignore
        }
        pfd = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

//        if (!managed) {
//            app.stop()
//        }
    }

    override fun onRevoke() {
        Log.i("MainService", "REVOKE SERVICE")

        super.onRevoke()

//        val app = application as MainApplication

        try {
            pfd?.close()
        } catch (e: IOException) {
            // ignore
        }
        pfd = null

//        if (!managed) {
//            app.stop()
//        }
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
    }

}

package com.bringyour.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import java.io.IOException


// see https://developer.android.com/develop/connectivity/vpn

class MainService : VpnService() {


    // TODO opt this process out of the vpn network?

    // addDisallowedApplication (String packageName)

    private var pfd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent : Intent?, flags: Int, startId : Int): Int {

        // FIXME get the local IPv4 and IPv6 address from the platform
        // FIXME when using auth login, the jwt should contain the local addresses

        val app = application as MainApplication
        val router = app.router!!

        // TODO
        // builder
        // blockingx
        // establish
        val builder = Builder()
        builder.setSession("BringYour")
        builder.setMtu(1440)
        builder.setBlocking(true)
        builder.addDisallowedApplication(packageName)

        if (router.clientIpv4 != null) {
            builder.addAddress(
                router.clientIpv4,
                router.clientIpv4PrefixLength
            )
            for (dnsIpv4 in router.dnsIpv4s) {
                builder.addDnsServer(dnsIpv4)
            }
            builder.addRoute("0.0.0.0", 0)
        }
        if (router.clientIpv6 != null) {
            builder.addAddress(
                router.clientIpv6,
                router.clientIpv6PrefixLength
            )
            for (dnsIpv6 in router.dnsIpv6s) {
                builder.addDnsServer(dnsIpv6)
            }
            builder.addRoute("::", 0)
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
            this.pfd = pfd
            router.activateLocalInterface(pfd)
        }


        updateForegroundNotification("BringYour")

    // FIXME configure intent

//        startForeground(1, getStatusNotificationBuilder().build());

        // `START_STICKY` means that `onStartCommand` will be called if a new process is created
        // the intent may be null
        // see https://developer.android.com/reference/android/app/Service#START_STICKY
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i("MainService", "DESTROY SERVICE")

        stopForeground(STOP_FOREGROUND_REMOVE)
    }


    override fun onRevoke() {
        Log.i("MainService", "REVOKE SERVICE")

        try {
            this.pfd?.close()
        } catch (e: IOException) {
            // ignore
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }


    val NOTIFICATION_ID = 101
    val NOTIFICATION_CHANNEL_ID = "BringYour"

    private fun updateForegroundNotification(message: String) {


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
            Intent(this, MainService::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setOngoing(true)
//                .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
            .setSmallIcon(R.drawable.ic_status)
            .setContentText(message)
            .setContentTitle(message)
                .setContentIntent(pendingIntent)
//                .setTicker(message)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

}

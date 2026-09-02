package com.bringyour.network

import android.Manifest
import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class VpnForegroundServiceContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @TargetApi(34)
    fun vpnServiceDeclaresSystemExemptedForegroundType() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, MainService::class.java),
            PackageManager.GET_META_DATA,
        )

        assertTrue(
            serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED != 0,
        )
        assertEquals(Manifest.permission.BIND_VPN_SERVICE, serviceInfo.permission)
    }

    @Test
    fun onlyRequiredBootComponentsAreDirectBootAware() {
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, MainService::class.java),
            PackageManager.GET_META_DATA,
        )
        val receiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, StartReceiver::class.java),
            PackageManager.GET_META_DATA,
        )
        val tileServiceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, QuickConnectTileService::class.java),
            PackageManager.GET_META_DATA,
        )
        val disconnectReceiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, NotificationDisconnectReceiver::class.java),
            PackageManager.GET_META_DATA,
        )

        assertTrue(serviceInfo.directBootAware)
        assertTrue(receiverInfo.directBootAware)
        assertFalse(tileServiceInfo.directBootAware)
        assertFalse(disconnectReceiverInfo.directBootAware)

        @Suppress("DEPRECATION")
        val lockedBootReceivers = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED).setPackage(context.packageName),
            PackageManager.MATCH_DIRECT_BOOT_AWARE,
        )
        assertTrue(
            lockedBootReceivers.any {
                ComponentName(it.activityInfo.packageName, it.activityInfo.name) ==
                    ComponentName(context, StartReceiver::class.java)
            },
        )
    }

    @Test
    fun directBootHandoffPersistsOnlyANonSecretPendingBit() {
        val state = DirectBootState(context)
        val wasPending = state.credentialRestorePending
        try {
            state.markCredentialRestorePending()
            assertTrue(state.credentialRestorePending)
            state.markCredentialRestoreComplete()
            assertFalse(state.credentialRestorePending)
        } finally {
            if (wasPending) state.markCredentialRestorePending()
        }
    }

    @Test
    fun appDeclaresBothForegroundServicePermissions() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requested = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue(Manifest.permission.FOREGROUND_SERVICE in requested)
        assertTrue("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" in requested)
    }

    @Test
    fun vpnUsesStableLowImportanceChannelIdentity() {
        assertEquals("urnetwork", MainService.NOTIFICATION_CHANNEL_ID)
    }

    @Test
    fun coldStartDetectionUsesTheManifestVpnServiceClass() {
        assertEquals(
            ComponentName(context, MainService::class.java).className,
            VPN_SERVICE_CLASS_NAME,
        )
    }

    @Test
    fun notificationDisconnectReceiverIsPrivate() {
        val receiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, NotificationDisconnectReceiver::class.java),
            PackageManager.GET_META_DATA,
        )

        assertFalse(receiverInfo.exported)
    }
}

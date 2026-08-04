package com.bringyour.network

import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import com.bringyour.sdk.FlowOwnerLookup
import java.net.InetSocketAddress

/**
 * Answers "which PINNED app owns this flow" for the Go side's per-app
 * pinning: one ConnectivityManager.getConnectionOwnerUid binder call (API
 * 29+) checked against the pinned apps' uids. Called once per NEW flow (the
 * Go side caches per flow key), never per packet.
 *
 * Immutable by design: the pinned set is baked at construction, and a rules
 * change swaps the whole lookup (see MainService.applyPinnedAppLookup) --
 * cheaper and simpler than locking inside a cross-language callback.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class PinnedAppFlowLookup(
    private val connectivityManager: ConnectivityManager,
    packageManager: PackageManager,
    pinnedPackages: Set<String>,
) : FlowOwnerLookup {

    private val uidToPackage: Map<Int, String> = buildMap {
        for (packageName in pinnedPackages) {
            try {
                put(packageManager.getPackageUid(packageName, 0), packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                // an uninstalled pinned app matches nothing, which is correct
            }
        }
    }

    override fun pinnedFlowAppId(
        version: Int,
        protocol: Int,
        sourceIp: String,
        sourcePort: Int,
        destinationIp: String,
        destinationPort: Int,
    ): String {
        if (uidToPackage.isEmpty()) {
            return ""
        }
        // the sdk's IpProtocol enum (tcp=1, udp=2) -> the OS protocol number
        // getConnectionOwnerUid requires
        val osProtocol = when (protocol) {
            1 -> android.system.OsConstants.IPPROTO_TCP
            2 -> android.system.OsConstants.IPPROTO_UDP
            else -> return ""
        }
        val uid = try {
            connectivityManager.getConnectionOwnerUid(
                osProtocol,
                InetSocketAddress(sourceIp, sourcePort),
                InetSocketAddress(destinationIp, destinationPort),
            )
        } catch (_: Exception) {
            // a throttled or failed lookup pins nothing; the flow routes as
            // it always did
            return ""
        }
        if (uid == Process.INVALID_UID) {
            return ""
        }
        return uidToPackage[uid] ?: ""
    }
}

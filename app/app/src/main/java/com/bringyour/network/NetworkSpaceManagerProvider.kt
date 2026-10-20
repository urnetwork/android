package com.bringyour.network

import com.bringyour.sdk.Sdk
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.NetworkSpaceManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkSpaceManagerProvider @Inject constructor() {

    @Volatile
    private var networkSpaceManager: NetworkSpaceManager? = null
    @Volatile
    private var networkSpace: NetworkSpace? = null

    /** The constructor copies this owner's choice before restoring any space. */
    fun init(filesDirPath: String, apiAltEnabled: Boolean = true) {
        networkSpaceManager = Sdk.newNetworkSpaceManagerWithApiAlt(filesDirPath, apiAltEnabled)
    }

    val setNetworkSpace: (NetworkSpace?) -> Unit = { updatedNetworkSpace ->
        networkSpace = updatedNetworkSpace
    }

    val getNetworkSpace: () -> NetworkSpace? = {
        networkSpace
    }

    val getNetworkSpaceManager: () -> NetworkSpaceManager? = {
        networkSpaceManager
    }

}

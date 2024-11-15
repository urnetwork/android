package com.bringyour.network

import com.bringyour.sdk.Sdk
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.NetworkSpaceManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkSpaceManagerProvider @Inject constructor() {

    private var networkSpaceManager: NetworkSpaceManager? = null
    private var networkSpace: NetworkSpace? = null

    val init: (filesDirPath: String) -> Unit = { filesDirPath ->
        networkSpaceManager = Sdk.newNetworkSpaceManager(filesDirPath)
        // networkSpaceManager
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
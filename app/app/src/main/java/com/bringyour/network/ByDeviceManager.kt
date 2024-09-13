package com.bringyour.network

import com.bringyour.client.BringYourApi
import com.bringyour.client.BringYourDevice
import com.bringyour.client.Client
import com.bringyour.client.Id
import javax.inject.Inject
import javax.inject.Singleton
import com.bringyour.client.NetworkSpace

@Singleton
class ByDeviceManager @Inject constructor() {

    private var byDevice: BringYourDevice? = null

    fun initDevice(
        // byApi: BringYourApi?,
        networkSpace: NetworkSpace,
        byClientJwt: String,
        instanceId: Id,
        provideMode: Long,
        platformUrl: String,
        deviceDescription: String,
        deviceSpec: String
    ) {
        byDevice?.close()  // Ensure old instance is cleaned up
        byDevice = Client.newBringYourDeviceWithDefaults(
            // byApi,
            networkSpace,
            byClientJwt,
            platformUrl,
            deviceDescription,
            deviceSpec,
            getAppVersion(),
            instanceId
        )
        byDevice?.provideMode = provideMode
    }

    fun getByDevice(): BringYourDevice? {
        return byDevice
    }

    fun clearByDevice() {
        byDevice?.close()
        byDevice = null
    }

    private fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }
}
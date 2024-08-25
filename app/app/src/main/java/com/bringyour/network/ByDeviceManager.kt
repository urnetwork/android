package com.bringyour.network

import android.content.Context
import com.bringyour.client.BringYourDevice
import com.bringyour.client.Client
import com.bringyour.client.Id
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ByDeviceManager @Inject constructor() {

    private var byDevice: BringYourDevice? = null

    fun initDevice(
        byClientJwt: String,
        instanceId: Id,
        provideMode: Long,
        platformUrl: String,
        apiUrl: String,
        deviceDescription: String,
        deviceSpec: String
    ) {
        byDevice?.close()  // Ensure old instance is cleaned up
        byDevice = Client.newBringYourDeviceWithDefaults(
            byClientJwt,
            platformUrl,
            apiUrl,
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
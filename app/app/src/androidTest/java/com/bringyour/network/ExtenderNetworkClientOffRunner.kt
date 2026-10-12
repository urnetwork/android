package com.bringyour.network

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.bringyour.network.acceptance.keepsLiveStartup
import com.bringyour.sdk.Sdk

/**
 * The default instrumentation runner (testInstrumentationRunner). Its onCreate
 * runs before the app's Application.onCreate installs the bundled network
 * space, so it turns the SDK's extender network client off there for every run
 * but the live ones (keepsLiveStartup). The harness also launches acceptance
 * through the stock AndroidJUnitRunner component, which the test APK still
 * declares and which leaves the client on.
 */
class ExtenderNetworkClientOffRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        if (!keepsLiveStartup { arguments?.getString(it) }) {
            Sdk.setExtenderNetworkClientEnabled(false)
        }
        super.onCreate(arguments)
    }
}

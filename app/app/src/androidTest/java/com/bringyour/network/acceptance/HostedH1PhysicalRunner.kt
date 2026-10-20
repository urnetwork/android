package com.bringyour.network.acceptance

import android.app.Application
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.bringyour.network.BuildConfig
import com.bringyour.network.MainApplication

/** Opt-in test component; the normal and local-test runners retain their defaults. */
class HostedH1PhysicalRunner : AndroidJUnitRunner() {
    private var startupValidated = false

    override fun onCreate(arguments: Bundle?) {
        require(BuildConfig.DEBUG && BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME == "main") {
            "hosted H1 runner requires a debug main build"
        }
        requireHostedH1Startup { arguments?.getString(it) }
        startupValidated = true
        super.onCreate(arguments)
    }

    override fun callApplicationOnCreate(app: Application) {
        check(startupValidated) { "hosted H1 startup was not validated" }
        val application = app as? MainApplication ?: error("hosted H1 application type is invalid")
        startHostedH1Application(application.networkSpaceApiAltStartup) {
            super.callApplicationOnCreate(app)
        }
    }
}

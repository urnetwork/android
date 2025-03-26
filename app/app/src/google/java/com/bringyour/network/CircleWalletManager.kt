package com.bringyour.network

import android.content.Context
import circle.programmablewallet.sdk.WalletSdk
import circle.programmablewallet.sdk.presentation.SettingsManagement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CircleWalletManager @Inject constructor() {

    var circleWalletSdk: WalletSdk? = null
        private set

    fun init(
        applicationContext: Context,
        addId: String,
        settingsManagement: SettingsManagement,
    ) {

        val endpoint = applicationContext.getString(R.string.circle_endpoint)

        WalletSdk.init(
            applicationContext,
            WalletSdk.Configuration(endpoint, addId, settingsManagement)
        )

        circleWalletSdk = WalletSdk
    }

}
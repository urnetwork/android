package com.bringyour.network

import android.content.Context
import circle.programmablewallet.sdk.WalletSdk
import circle.programmablewallet.sdk.presentation.SecurityQuestion
import circle.programmablewallet.sdk.presentation.SettingsManagement
import com.bringyour.network.ui.account.CircleLayoutProvider
import com.bringyour.network.ui.account.CircleViewSetterProvider
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
        layoutProvider: CircleLayoutProvider,
        setterProvider: CircleViewSetterProvider
    ) {

        val endpoint = applicationContext.getString(R.string.circle_endpoint)

        WalletSdk.init(
            applicationContext,
            WalletSdk.Configuration(endpoint, addId, settingsManagement)
        )

        WalletSdk.setSecurityQuestions(
            arrayOf(
                SecurityQuestion("What is your favorite color?"),
                SecurityQuestion("What is your favorite shape?"),
                SecurityQuestion("What is your favorite animal?"),
                SecurityQuestion("What is your favorite place?"),
                SecurityQuestion("What is your favorite material?"),
                SecurityQuestion("What is your favorite sound?"),
                SecurityQuestion("What would you explore in space?"),
                SecurityQuestion("Pick a word, any word."),
                SecurityQuestion("Pick a date, any date.", SecurityQuestion.InputType.datePicker),
            ))

        WalletSdk.setLayoutProvider(layoutProvider)
        WalletSdk.setViewSetterProvider(setterProvider)

        circleWalletSdk = WalletSdk
    }

}
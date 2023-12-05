package com.bringyour.network.ui.account

import android.content.Context
import circle.programmablewallet.sdk.api.ApiError.ErrorCode
import circle.programmablewallet.sdk.presentation.IconTextConfig
import circle.programmablewallet.sdk.presentation.LayoutProvider
import circle.programmablewallet.sdk.presentation.LocalImageSetter
import circle.programmablewallet.sdk.presentation.RemoteImageSetter
import circle.programmablewallet.sdk.presentation.TextConfig
import circle.programmablewallet.sdk.presentation.Resource.TextsKey
import circle.programmablewallet.sdk.presentation.Resource.IconTextsKey
import com.bringyour.network.R

class CircleLayoutProvider(val context: Context) : LayoutProvider() {

    override fun getTextConfig(key: String?): TextConfig? {
        return super.getTextConfig(key)
    }

    private fun getHeadingColors(): Int {
        return context.getColor(R.color.by_p_blue)
    }

    override fun getTextConfigs(key: TextsKey?): Array<TextConfig>? {
        when (key) {
            TextsKey.securityQuestionHeaders -> return arrayOf(
                TextConfig("Choose your 1st question"),
                TextConfig("Choose your 2nd question")
            )

            TextsKey.securitySummaryQuestionHeaders -> return arrayOf(
                TextConfig("1st Question"),
                TextConfig("2nd Question")
            )

            TextsKey.enterPinCodeHeadline -> return arrayOf(
                TextConfig("Enter your "),
                TextConfig("PIN", getHeadingColors(), null)
            )

            TextsKey.securityIntroHeadline -> return arrayOf(
                TextConfig("Set up your "),
                TextConfig("Recovery Method", getHeadingColors(), null)
            )

            TextsKey.newPinCodeHeadline -> return arrayOf(
                TextConfig("Enter your "),
                TextConfig("PIN", getHeadingColors(), null)
            )

            TextsKey.securityIntroLink -> return arrayOf(
                TextConfig("Learn more"),
                TextConfig("https://bringyour.com/privacy")
            )

            TextsKey.recoverPinCodeHeadline -> return arrayOf(
                TextConfig("Recover your "),
                TextConfig("PIN", getHeadingColors(), null)
            )

            else -> {}
        }
        return super.getTextConfigs(key)
    }

    override fun getIconTextConfigs(key: IconTextsKey?): Array<IconTextConfig>? {
        when (key) {
            IconTextsKey.securityConfirmationItems -> return arrayOf<IconTextConfig>(
                IconTextConfig(
                    LocalImageSetter(R.drawable.ic_intro_item0_icon),
                    TextConfig("This is the only way to recover my account access. ")
                ),
                IconTextConfig(
                    LocalImageSetter(R.drawable.ic_intro_item1_icon),
                    TextConfig("Neither BringYour or Circle will store my answers so it’s my responsibility to remember them.")
                ),
                IconTextConfig(
                    LocalImageSetter(R.drawable.ic_intro_item2_icon),
                    TextConfig("I will lose access to my wallet and my digital assets if I forget my answers. ")
                )
            )

            else -> {}
        }
        return super.getIconTextConfigs(key)
    }

    override fun getErrorString(code: ErrorCode?): String? {
        return super.getErrorString(code)
    }
}
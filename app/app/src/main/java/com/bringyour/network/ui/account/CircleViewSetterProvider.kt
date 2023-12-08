package com.bringyour.network.ui.account

import android.content.Context
import circle.programmablewallet.sdk.presentation.IImageViewSetter
import circle.programmablewallet.sdk.presentation.IToolbarSetter
import circle.programmablewallet.sdk.presentation.LocalImageSetter
import circle.programmablewallet.sdk.presentation.LocalToolbarImageSetter
import circle.programmablewallet.sdk.presentation.RemoteImageSetter
import circle.programmablewallet.sdk.presentation.RemoteToolbarImageSetter
import circle.programmablewallet.sdk.presentation.Resource
import circle.programmablewallet.sdk.presentation.Resource.ToolbarIcon
import circle.programmablewallet.sdk.presentation.ViewSetterProvider
import com.bringyour.network.R

class CircleViewSetterProvider(val context: Context) : ViewSetterProvider() {

    override fun getToolbarImageSetter(type: ToolbarIcon?): IToolbarSetter? {
        when (type) {
            ToolbarIcon.back -> return LocalToolbarImageSetter(R.drawable.ic_back)

            ToolbarIcon.close -> return LocalToolbarImageSetter(R.drawable.ic_close)

            else -> {}
        }
        return super.getToolbarImageSetter(type)
    }

    override fun getImageSetter(type: Resource.Icon?): IImageViewSetter? {
        when (type) {
            Resource.Icon.securityIntroMain -> return LocalImageSetter(R.drawable.ic_intro_main_icon)

            Resource.Icon.selectCheckMark -> return LocalImageSetter(R.drawable.ic_checkmark)

            Resource.Icon.dropdownArrow -> return LocalImageSetter(R.drawable.ic_dropdown_arrow)

            Resource.Icon.errorInfo -> return LocalImageSetter(R.drawable.ic_error_info)

            Resource.Icon.securityConfirmMain -> return LocalImageSetter(R.drawable.ic_confirm_main_icon)

            Resource.Icon.biometricsAllowMain -> return LocalImageSetter(R.drawable.ic_biometrics_general)

            Resource.Icon.showPin -> return LocalImageSetter(R.drawable.ic_show_pin)

            Resource.Icon.hidePin -> return LocalImageSetter(R.drawable.ic_hide_pin)

            else -> {}
        }
        return super.getImageSetter(type)
    }
}
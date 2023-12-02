package com.bringyour.network.ui.account

import android.content.Context
import circle.programmablewallet.sdk.presentation.IImageViewSetter
import circle.programmablewallet.sdk.presentation.IToolbarSetter
import circle.programmablewallet.sdk.presentation.RemoteImageSetter
import circle.programmablewallet.sdk.presentation.RemoteToolbarImageSetter
import circle.programmablewallet.sdk.presentation.Resource
import circle.programmablewallet.sdk.presentation.Resource.ToolbarIcon
import circle.programmablewallet.sdk.presentation.ViewSetterProvider
import com.bringyour.network.R

class CircleViewSetterProvider(val context: Context) : ViewSetterProvider() {

    override fun getToolbarImageSetter(type: ToolbarIcon?): IToolbarSetter? {
        when (type) {
            ToolbarIcon.back -> return RemoteToolbarImageSetter(
                R.drawable.ic_back,
                "https://path/ic_back"
            )

            ToolbarIcon.close -> return RemoteToolbarImageSetter(
                R.drawable.ic_close,
                "https://path/ic_close"
            )

            else -> {}
        }
        return super.getToolbarImageSetter(type)
    }

    override fun getImageSetter(type: Resource.Icon?): IImageViewSetter? {
        when (type) {
            Resource.Icon.securityIntroMain -> return RemoteImageSetter(
                R.drawable.ic_intro_main_icon,
                "https://path/ic_intro_main_icon"
            )

            Resource.Icon.selectCheckMark -> return RemoteImageSetter(
                R.drawable.ic_checkmark,
                "https://path/ic_checkmark"
            )

            Resource.Icon.dropdownArrow -> return RemoteImageSetter(
                R.drawable.ic_dropdown_arrow,
                "https://path/ic_dropdown_arrow"
            )

            Resource.Icon.errorInfo -> return RemoteImageSetter(
                R.drawable.ic_error_info,
                "https://path/ic_error_info"
            )

            Resource.Icon.securityConfirmMain -> return RemoteImageSetter(
                R.drawable.ic_confirm_main_icon,
                "https://path/ic_confirm_main_icon"
            )

            else -> {}
        }
        return super.getImageSetter(type)
    }
}
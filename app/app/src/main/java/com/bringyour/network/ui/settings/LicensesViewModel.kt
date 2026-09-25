package com.bringyour.network.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.LicenseInfoList
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One entry of the SDK's license list (sdk/license.yml), copied off the gomobile object. */
data class LicenseUi(
    val name: String,
    val version: String,
    // "data", "software" or "font"
    val kind: String,
    val url: String,
    val spdx: String,
    // newline separated
    val copyright: String,
    // a notice that must be shown verbatim; usually empty
    val notice: String,
    val text: String,
) {
    val isData get() = kind == KIND_DATA

    /** "version · spdx", omitting empties. */
    val subtitle get() = listOf(version, spdx).filter { it.isNotBlank() }.joinToString(" · ")

    companion object {
        const val KIND_DATA = Sdk.LicenseKindData
    }
}

/**
 * Account -> Settings -> Licenses. The SDK embeds the license list; the first
 * read parses it (tens of ms on a phone), so it is loaded off the main thread.
 */
@HiltViewModel
class LicensesViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel() {

    // null while loading
    private val _licenses = MutableStateFlow<List<LicenseUi>?>(null)
    val licenses: StateFlow<List<LicenseUi>?> = _licenses

    init {
        viewModelScope.launch {
            _licenses.value = withContext(Dispatchers.Default) {
                // the device's list and the package-level list are the same
                // embedded data; the package-level call covers the moment
                // before the device is (re)created
                val list = deviceManager.device?.getLicenses(Sdk.LicenseAppAndroid)
                    ?: Sdk.getLicenses(Sdk.LicenseAppAndroid)
                toUi(list)
            }
        }
    }

    private fun toUi(list: LicenseInfoList?): List<LicenseUi> {
        if (list == null) {
            return listOf()
        }
        val n = list.len()
        val out = ArrayList<LicenseUi>(n.toInt())
        for (i in 0 until n) {
            val l = list.get(i) ?: continue
            out.add(
                LicenseUi(
                    name = l.name.orEmpty(),
                    version = l.version.orEmpty(),
                    kind = l.kind.orEmpty(),
                    url = l.url.orEmpty(),
                    spdx = l.spdx.orEmpty(),
                    copyright = l.copyright.orEmpty(),
                    notice = l.notice.orEmpty(),
                    text = l.text.orEmpty(),
                )
            )
        }
        return out
    }
}
